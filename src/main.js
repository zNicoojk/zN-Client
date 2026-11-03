const { app, BrowserWindow, dialog, ipcMain, nativeImage, safeStorage, shell } = require('electron');
const { autoUpdater } = require('electron-updater');
const { Client } = require('minecraft-launcher-core');
const { Auth } = require('msmc');
const AdmZip = require('adm-zip');
const extractZip = require('extract-zip');
const { createHash } = require('node:crypto');
const { createReadStream, createWriteStream } = require('node:fs');
const fsSync = require('node:fs');
const { Transform } = require('node:stream');
const os = require('node:os');
const fs = require('node:fs/promises');
const path = require('node:path');
const { pipeline } = require('node:stream/promises');
const { Readable } = require('node:stream');

const VERSION_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const ADOPTIUM_ASSETS = 'https://api.adoptium.net/v3/assets/latest';
const FABRIC_META = 'https://meta.fabricmc.net/v2';
const CLIENT_MOD_GAME_VERSION = '26.3';
app.setName('zN Client');
let mainWindow;
let accountToken;
let accountProfile;
let activeAccountId;
let preferredAccountId;
const savedAccounts = new Map();
const accountSessions = new Map();
const accountSkins = new Map();
let currentGame;
let manifestCache;
let launchInProgress = false;
let activeLaunch;
let updatePromptOpen = false;
let legacyDataMigration;
let legacyModsMigration;

const DEFAULT_MEMORY_GB = 2;
const MEMORY_CONFIG_VERSION = 1;
const defaultConfig = {
  version: '',
  memory: getRecommendedMemory(),
  memoryConfigVersion: MEMORY_CONFIG_VERSION,
};

function getMemoryChoices() {
  const physicalGb = Math.floor(os.totalmem() / (1024 ** 3));
  // Leave at least 4 GB for Windows and shared graphics memory.
  const safeMax = Math.max(2, Math.min(16, physicalGb - 4));
  return [2, 4, 6, 8, 12, 16].filter((gb) => gb <= safeMax || gb === 2);
}

function getRecommendedMemory() {
  const availableGb = os.freemem() / (1024 ** 3);
  return availableGb >= 6 ? 4 : 2;
}

function send(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(channel, payload);
}

function userConfigPath() {
  return path.join(app.getPath('userData'), 'config.json');
}

function accountStorePath() {
  return path.join(app.getPath('userData'), 'accounts.json');
}

function getAccountList() {
  return [...savedAccounts.values()].map(({ id, name }) => ({
    id,
    name,
    skin: accountSkins.get(id) || null,
  }));
}

function getAccountState() {
  return {
    account: accountProfile || null,
    accounts: getAccountList(),
    activeAccountId: activeAccountId || null,
  };
}

function sendAccountState() {
  send('account:changed', getAccountState());
}

async function loadAccountSkin(accountId) {
  if (accountSkins.has(accountId)) return accountSkins.get(accountId);
  try {
    const uuid = accountId.replaceAll('-', '');
    if (!/^[a-f\d]{32}$/i.test(uuid)) throw new Error('El perfil tiene un identificador no válido.');
    const profileResponse = await fetch(
      `https://sessionserver.mojang.com/session/minecraft/profile/${uuid}`,
      { signal: AbortSignal.timeout(8000) },
    );
    if (!profileResponse.ok) throw new Error(`Minecraft devolvió ${profileResponse.status} al consultar la skin.`);
    const profile = await profileResponse.json();
    const textureProperty = profile.properties?.find((property) => property.name === 'textures');
    if (!textureProperty?.value) throw new Error('El perfil no contiene una textura de skin.');
    const textures = JSON.parse(Buffer.from(textureProperty.value, 'base64').toString('utf8'));
    const skinUrlValue = textures.textures?.SKIN?.url;
    if (typeof skinUrlValue !== 'string' || !skinUrlValue.trim()) {
      accountSkins.set(accountId, null);
      return null;
    }
    const skinUrl = new URL(skinUrlValue.trim(), 'https://textures.minecraft.net');
    const skinHost = skinUrl.hostname.toLowerCase().replace(/\.$/, '');
    if (!['http:', 'https:'].includes(skinUrl.protocol) ||
        skinHost !== 'textures.minecraft.net' ||
        skinUrl.username || skinUrl.password) {
      throw new Error(`Minecraft devolvió una URL de skin no válida (${skinUrl.protocol}//${skinHost}).`);
    }
    skinUrl.protocol = 'https:';
    skinUrl.port = '';

    const skinResponse = await fetch(skinUrl, { signal: AbortSignal.timeout(8000) });
    if (!skinResponse.ok) throw new Error(`No se pudo descargar la skin (${skinResponse.status}).`);
    const skinImage = nativeImage.createFromBuffer(Buffer.from(await skinResponse.arrayBuffer()));
    const { width, height } = skinImage.getSize();
    if (skinImage.isEmpty() || width < 16 || height < 16) {
      throw new Error('La imagen de skin no tiene un tamaño válido.');
    }
    const avatar = skinImage.crop({ x: 8, y: 8, width: 8, height: 8 })
      .resize({ width: 64, height: 64, quality: 'best' })
      .toDataURL();
    accountSkins.set(accountId, avatar);
    return avatar;
  } catch (error) {
    console.warn('[zN Client] No se pudo cargar la skin:', error.message);
    return null;
  }
}

function refreshActiveAccountSkin(accountId) {
  loadAccountSkin(accountId).then((skin) => {
    if (activeAccountId !== accountId || accountProfile?.id !== accountId) return;
    accountProfile = { ...accountProfile, skin };
    sendAccountState();
  });
}

function getGameInstallDirectory() {
  return path.join(app.getPath('userData'), 'minecraft');
}

function getVersionDataDirectory(versionId) {
  if (typeof versionId !== 'string' || !/^[a-zA-Z0-9._-]{1,80}$/.test(versionId)) {
    throw new Error('Selecciona una versión válida de Minecraft.');
  }
  return path.join(getGameInstallDirectory(), 'instances', versionId);
}

async function migrateLegacyGameData(versionId) {
  const gameInstallDirectory = getGameInstallDirectory();
  const migrationMarker = path.join(gameInstallDirectory, '.znclient-instance-data-migrated');
  try {
    await fs.access(migrationMarker);
    return;
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
  if (legacyDataMigration) return legacyDataMigration;

  legacyDataMigration = (async () => {
    const sharedInstallDirectories = new Set([
      'assets',
      'instances',
      'libraries',
      'natives',
      'runtime',
      'versions',
      'webcache',
      'znclient-mods',
    ]);
    const destination = getVersionDataDirectory(versionId);
    await fs.mkdir(destination, { recursive: true });
    const entries = await fs.readdir(gameInstallDirectory, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.isSymbolicLink() || sharedInstallDirectories.has(entry.name) ||
          entry.name === path.basename(migrationMarker)) continue;
      await fs.cp(
        path.join(gameInstallDirectory, entry.name),
        path.join(destination, entry.name),
        { recursive: true, force: false, errorOnExist: false },
      );
    }
    await fs.writeFile(migrationMarker, JSON.stringify({
      migratedAt: new Date().toISOString(),
      version: versionId,
    }, null, 2), { flag: 'wx' });
  })();
  try {
    await legacyDataMigration;
  } catch (error) {
    legacyDataMigration = undefined;
    throw error;
  }
}

function getExplicitMinecraftVersionFromModName(fileName) {
  const taggedPatterns = [
    /(?:^|[.+_-])mc(?:raft)?[.+_-]?(\d{1,2}\.\d+(?:\.\d+)?)(?=$|[.+_-])/i,
    /\+(\d{1,2}\.\d+(?:\.\d+)?)(?=$|[.+_-])/,
    /fabric\.(\d{1,2}\.\d+(?:\.\d+)?)(?=$|[.+_-])/i,
  ];
  for (const pattern of taggedPatterns) {
    const match = fileName.match(pattern);
    if (match) return normalizeMinecraftVersionId(match[1]);
  }
  const fabricSuffix = /-(\d{1,2}\.\d+(?:\.\d+)?)-fabric(?:[.+_-]|$)/i.exec(fileName);
  if (fabricSuffix && (fileName.match(/\d{1,2}\.\d+(?:\.\d+)?/g) || []).length > 1) {
    return normalizeMinecraftVersionId(fabricSuffix[1]);
  }
  return null;
}

function normalizeMinecraftVersionId(versionId) {
  return versionId.replace(/^(\d+\.\d+)\.0$/, '$1');
}

function getModMinecraftVersion(archive, fileName) {
  const fileVersion = getExplicitMinecraftVersionFromModName(fileName);
  if (fileVersion) return fileVersion;
  const metadataEntry = archive.getEntry('fabric.mod.json') || archive.getEntry('quilt.mod.json');
  if (!metadataEntry || metadataEntry.header.size > 1024 * 1024) return null;
  try {
    const metadata = parseModJson(metadataEntry.getData().toString('utf8'));
    const loader = metadata.quilt_loader || metadata;
    const versionRange = loader.depends?.minecraft || metadata.depends?.minecraft;
    if (typeof versionRange !== 'string') return null;
    const match = versionRange.trim().match(/^[<>=~^]*(\d{1,2}\.\d+(?:\.\d+)?)(?:[.*x-])?$/i);
    return match ? normalizeMinecraftVersionId(match[1]) : null;
  } catch {
    return null;
  }
}

async function copyLegacyModToVersion(filePath, fileName, versionId) {
  const destinationDirectory = path.join(getVersionDataDirectory(versionId), 'znclient-mods');
  const destinationPath = path.join(destinationDirectory, fileName);
  await fs.mkdir(destinationDirectory, { recursive: true });
  try {
    await fs.copyFile(filePath, destinationPath, fsSync.constants.COPYFILE_EXCL);
  } catch (error) {
    if (error?.code !== 'EEXIST') throw error;
    const [sourceHash, destinationHash] = await Promise.all([
      hashFile(filePath),
      hashFile(destinationPath),
    ]);
    if (sourceHash !== destinationHash) {
      console.error(`[zN Client] No se copió ${fileName} a ${versionId}: ya existe un mod diferente con ese nombre.`);
      return false;
    }
  }
  return true;
}

async function archiveLegacyMod(filePath, fileName, versionId) {
  const archiveDirectory = path.join(getGameInstallDirectory(), 'legacy-mods', versionId);
  await fs.mkdir(archiveDirectory, { recursive: true });
  let destinationPath = path.join(archiveDirectory, fileName);
  let suffix = 1;
  while (true) {
    try {
      await fs.access(destinationPath);
      const extension = path.extname(fileName);
      destinationPath = path.join(
        archiveDirectory,
        `${path.basename(fileName, extension)}-${suffix++}${extension}`,
      );
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
      await fs.rename(filePath, destinationPath);
      return;
    }
  }
}

async function migrateLegacyMods() {
  if (legacyModsMigration) return legacyModsMigration;
  legacyModsMigration = (async () => {
    const gameInstallDirectory = getGameInstallDirectory();
    const legacyModsDirectory = path.join(gameInstallDirectory, 'znclient-mods');
    let legacyVersion = (await getConfig()).version || (await getManifest()).latest.release;
    try {
      const marker = JSON.parse(await fs.readFile(
        path.join(gameInstallDirectory, '.znclient-instance-data-migrated'),
        'utf8',
      ));
      if (typeof marker.version === 'string') legacyVersion = marker.version;
    } catch (error) {
      if (error?.code !== 'ENOENT') {
        console.error('[zN Client] No se pudo leer la versión de los datos antiguos:', error);
      }
    }
    if (!legacyVersion || !/^[a-zA-Z0-9._-]{1,80}$/.test(legacyVersion)) return;

    const migrationMarker = path.join(gameInstallDirectory, '.znclient-per-version-mods-migrated');
    try {
      await fs.access(migrationMarker);
      return;
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
    }

    const existingInstanceMods = path.join(getVersionDataDirectory(legacyVersion), 'znclient-mods');
    try {
      const entries = await fs.readdir(existingInstanceMods, { withFileTypes: true });
      for (const entry of entries) {
        if (!entry.isFile() || path.extname(entry.name).toLowerCase() !== '.jar') continue;
        const archive = new AdmZip(path.join(existingInstanceMods, entry.name));
        const targetVersion = getModMinecraftVersion(archive, entry.name);
        if (targetVersion && targetVersion !== legacyVersion &&
            /^[a-zA-Z0-9._-]{1,80}$/.test(targetVersion)) {
          const copied = await copyLegacyModToVersion(
            path.join(existingInstanceMods, entry.name),
            entry.name,
            targetVersion,
          );
          if (copied) {
            await fs.unlink(path.join(existingInstanceMods, entry.name));
          } else {
            await archiveLegacyMod(
              path.join(existingInstanceMods, entry.name),
              entry.name,
              legacyVersion,
            );
          }
        }
      }
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
    }

    try {
      const entries = await fs.readdir(legacyModsDirectory, { withFileTypes: true });
      for (const entry of entries) {
        if (!entry.isFile() || path.extname(entry.name).toLowerCase() !== '.jar') continue;
        const archive = new AdmZip(path.join(legacyModsDirectory, entry.name));
        const targetVersion = getModMinecraftVersion(archive, entry.name) || legacyVersion;
        if (!/^[a-zA-Z0-9._-]{1,80}$/.test(targetVersion)) continue;
        await copyLegacyModToVersion(path.join(legacyModsDirectory, entry.name), entry.name, targetVersion);
      }
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
    }

    await fs.writeFile(migrationMarker, JSON.stringify({
      migratedAt: new Date().toISOString(),
    }, null, 2), { flag: 'wx' });
  })();
  try {
    await legacyModsMigration;
  } catch (error) {
    legacyModsMigration = undefined;
    throw error;
  }
}

async function resolveVersionId(versionId) {
  const requested = typeof versionId === 'string' && versionId
    ? versionId
    : (await getConfig()).version;
  if (!requested) throw new Error('Selecciona una versión válida de Minecraft.');
  getVersionDataDirectory(requested);
  return requested;
}

function getModMetadata(archive, fileName) {
  const modJson = archive.getEntry('fabric.mod.json') || archive.getEntry('quilt.mod.json');
  if (modJson) {
    try {
      if (modJson.header.size > 1024 * 1024) throw new Error('Los metadatos del mod son demasiado grandes.');
      const metadata = parseModJson(modJson.getData().toString('utf8'));
      const loader = metadata.quilt_loader || metadata;
      const details = loader.metadata || metadata;
      const icon = details.icon || loader.icon || metadata.icon;
      return {
        name: String(details.name || loader.name || loader.id || path.basename(fileName, '.jar')),
        version: String(loader.version || metadata.version || ''),
        description: String(details.description || loader.description || ''),
        icon: typeof icon === 'string' ? icon : Object.values(icon || {}).find((item) => typeof item === 'string') || '',
      };
    } catch (error) {
      console.error(`[zN Client] No se pudieron leer los metadatos de ${fileName}:`, error);
    }
  }

  const forgeEntry = archive.getEntry('META-INF/neoforge.mods.toml') || archive.getEntry('META-INF/mods.toml');
  if (forgeEntry) {
    if (forgeEntry.header.size > 1024 * 1024) throw new Error(`Los metadatos de ${fileName} son demasiado grandes.`);
    const section = forgeEntry.getData().toString('utf8').match(/\[\[mods\]\]([\s\S]*?)(?=\n\[\[|$)/)?.[1] || '';
    const readValue = (key) => section.match(new RegExp(`^${key}\\s*=\\s*"([^"]*)"`, 'm'))?.[1] || '';
    return {
      name: readValue('displayName') || readValue('modId') || path.basename(fileName, '.jar'),
      version: readValue('version'),
      description: readValue('description'),
      icon: '',
    };
  }
  return { name: path.basename(fileName, '.jar'), version: '', description: '', icon: '' };
}

function parseModJson(source) {
  let inString = false;
  let escaped = false;
  let sanitized = '';
  for (const character of source) {
    const code = character.charCodeAt(0);
    if (inString && !escaped && code < 0x20) {
      sanitized += code === 0x0a ? '\\n' : code === 0x0d ? '\\r' : code === 0x09 ? '\\t' : `\\u${code.toString(16).padStart(4, '0')}`;
      continue;
    }
    sanitized += character;
    if (inString && !escaped && character === '"') inString = false;
    else if (!inString && character === '"') inString = true;
    if (inString && character === '\\' && !escaped) escaped = true;
    else escaped = false;
  }
  return JSON.parse(sanitized);
}

async function listMods(versionId) {
  const selectedVersion = await resolveVersionId(versionId);
  await migrateLegacyMods();
  const directory = path.join(getVersionDataDirectory(selectedVersion), 'znclient-mods');
  await fs.mkdir(directory, { recursive: true });
  const entries = await fs.readdir(directory, { withFileTypes: true });
  const mods = [];
  for (const entry of entries) {
    if (!entry.isFile() || path.extname(entry.name).toLowerCase() !== '.jar') continue;
    const filePath = path.join(directory, entry.name);
    let mod = {
      fileName: entry.name,
      name: path.basename(entry.name, path.extname(entry.name)),
      version: '',
      description: '',
      icon: null,
    };
    try {
      const archive = new AdmZip(filePath);
      const metadata = getModMetadata(archive, entry.name);
      mod = { ...mod, ...metadata };
      const iconPath = String(metadata.icon || '').replaceAll('\\', '/').replace(/^\/+/, '');
      if (iconPath && !iconPath.split('/').includes('..')) {
        const iconEntry = archive.getEntry(iconPath);
        const extension = path.extname(iconPath).toLowerCase();
        const mimeType = {
          '.gif': 'image/gif',
          '.jpeg': 'image/jpeg',
          '.jpg': 'image/jpeg',
          '.png': 'image/png',
          '.webp': 'image/webp',
        }[extension];
        if (iconEntry && mimeType && iconEntry.header.size <= 1024 * 1024) {
          mod.icon = `data:${mimeType};base64,${iconEntry.getData().toString('base64')}`;
        }
      }
    } catch (error) {
      console.error(`[zN Client] No se pudo leer el mod ${entry.name}:`, error);
      mod.description = 'No se pudieron leer los detalles de este archivo.';
    }
    mods.push(mod);
  }
  return mods.sort((first, second) => first.name.localeCompare(second.name, 'es'));
}

async function encryptRefreshToken(refreshToken) {
  if (typeof safeStorage.isAsyncEncryptionAvailable === 'function') {
    const available = await safeStorage.isAsyncEncryptionAvailable();
    if (!available && typeof safeStorage.isEncryptionAvailable === 'function' && !safeStorage.isEncryptionAvailable()) {
      throw new Error('El almacenamiento seguro de Windows no está disponible.');
    }
  }

  let encrypted;
  if (typeof safeStorage.encryptStringAsync === 'function') {
    encrypted = await safeStorage.encryptStringAsync(refreshToken);
  } else {
    encrypted = safeStorage.encryptString(refreshToken);
  }
  return encrypted.toString('base64');
}

async function saveAccountStore() {
  await fs.mkdir(app.getPath('userData'), { recursive: true });
  const accountData = {
    lastAccountId: activeAccountId || preferredAccountId || null,
    accounts: [...savedAccounts.values()],
  };
  await fs.writeFile(accountStorePath(), JSON.stringify(accountData, null, 2), 'utf8');
}

async function loadAccountStore() {
  try {
    const accountData = JSON.parse(await fs.readFile(accountStorePath(), 'utf8'));
    for (const account of accountData.accounts || []) {
      if (typeof account?.id !== 'string' || typeof account?.name !== 'string') continue;
      if (typeof account.encryptedRefreshToken !== 'string') continue;
      savedAccounts.set(account.id, account);
    }
    if (savedAccounts.has(accountData.lastAccountId)) {
      preferredAccountId = accountData.lastAccountId;
    } else if (savedAccounts.size === 1) {
      preferredAccountId = [...savedAccounts.keys()][0];
    }
  } catch {
    // The account list is empty on a new install or if its local file is unreadable.
  }
}

async function activateAccount(accountId) {
  const saved = savedAccounts.get(accountId);
  if (!saved) throw new Error('No se encontró esa cuenta guardada.');

  let session = accountSessions.get(accountId);
  if (!session) {
    if (typeof safeStorage.isAsyncEncryptionAvailable === 'function') {
      const available = await safeStorage.isAsyncEncryptionAvailable();
      if (!available && typeof safeStorage.isEncryptionAvailable === 'function' && !safeStorage.isEncryptionAvailable()) {
        throw new Error('No se puede descifrar la sesión guardada en este equipo.');
      }
    }

    let refreshToken;
    try {
      if (typeof safeStorage.decryptStringAsync === 'function') {
        const decrypted = await safeStorage.decryptStringAsync(
          Buffer.from(saved.encryptedRefreshToken, 'base64')
        );
        refreshToken = typeof decrypted === 'string' ? decrypted : decrypted?.result;
      }
      if (!refreshToken && typeof safeStorage.decryptString === 'function') {
        refreshToken = safeStorage.decryptString(Buffer.from(saved.encryptedRefreshToken, 'base64'));
      }
    } catch {
      try {
        if (typeof safeStorage.decryptString === 'function') {
          refreshToken = safeStorage.decryptString(Buffer.from(saved.encryptedRefreshToken, 'base64'));
        }
      } catch {
        // failed both
      }
    }

    if (!refreshToken) {
      throw new Error(`No se pudo abrir la sesión de ${saved.name}. Vuelve a agregar la cuenta.`);
    }

    const auth = new Auth('select_account');
    const xbox = await auth.refresh(refreshToken);
    const minecraft = await xbox.getMinecraft();
    if (minecraft.profile.id !== saved.id) {
      throw new Error('Microsoft devolvió un perfil diferente. Vuelve a agregar esa cuenta.');
    }
    session = { minecraft, refreshToken: xbox.save() };
    accountSessions.set(accountId, session);
    savedAccounts.set(accountId, {
      id: accountId,
      name: minecraft.profile.name,
      encryptedRefreshToken: await encryptRefreshToken(session.refreshToken),
    });
  }

  activeAccountId = accountId;
  preferredAccountId = accountId;
  accountToken = session.minecraft;
  accountProfile = {
    id: accountId,
    name: session.minecraft.profile.name,
    skin: accountSkins.get(accountId) || null,
  };
  await saveAccountStore();
  sendAccountState();
  refreshActiveAccountSkin(accountId);
  return getAccountState();
}

async function getConfig() {
  let config;
  try {
    config = { ...defaultConfig, ...JSON.parse(await fs.readFile(userConfigPath(), 'utf8')) };
  } catch {
    return { ...defaultConfig };
  }

  if (config.memoryConfigVersion !== MEMORY_CONFIG_VERSION) {
    if (config.memory === DEFAULT_MEMORY_GB && getRecommendedMemory() > DEFAULT_MEMORY_GB) {
      config.memory = getRecommendedMemory();
    }
    config = await saveConfig(config);
  }
  return config;
}

async function saveConfig(config) {
  const choices = getMemoryChoices();
  const selectedMemory = Number(config.memory) || getRecommendedMemory();
  const safeConfig = {
    version: String(config.version || ''),
    memory: choices.includes(selectedMemory) ? selectedMemory : getRecommendedMemory(),
    memoryConfigVersion: MEMORY_CONFIG_VERSION,
  };
  await fs.mkdir(app.getPath('userData'), { recursive: true });
  await fs.writeFile(userConfigPath(), JSON.stringify(safeConfig, null, 2), 'utf8');
  return safeConfig;
}

async function getManifest() {
  if (!manifestCache) {
    const response = await fetch(VERSION_MANIFEST);
    if (!response.ok) throw new Error(`No se pudo consultar el catálogo de Minecraft (${response.status}).`);
    manifestCache = await response.json();
  }
  return manifestCache;
}

async function getReleases() {
  const manifest = await getManifest();
  return manifest.versions
    .filter((entry) => entry.type === 'release')
    .slice(0, 80)
    .map(({ id, url }) => ({ id, url }));
}

async function downloadFile(url, destination, onProgress, signal) {
  const response = await fetch(url, { redirect: 'follow', signal });
  if (!response.ok || !response.body) throw new Error(`Falló la descarga (${response.status}).`);
  await fs.mkdir(path.dirname(destination), { recursive: true });
  const total = Number(response.headers.get('content-length')) || 0;
  let downloaded = 0;
  const progress = new Transform({
    transform(chunk, _encoding, callback) {
      downloaded += chunk.length;
      if (onProgress) onProgress(downloaded, total);
      callback(null, chunk);
    },
  });
  await pipeline(Readable.fromWeb(response.body), progress, createWriteStream(destination));
}

async function hashFile(filePath) {
  const hash = createHash('sha256');
  await pipeline(createReadStream(filePath), hash);
  return hash.digest('hex');
}

async function findJavaExecutable(directory, depth = 0) {
  if (depth > 5) return null;
  const direct = path.join(directory, 'bin', 'java.exe');
  try {
    await fs.access(direct);
    return direct;
  } catch {
    // Search the extracted vendor folder below this directory.
  }

  let entries;
  try {
    entries = await fs.readdir(directory, { withFileTypes: true });
  } catch {
    return null;
  }
  for (const entry of entries) {
    if (entry.isDirectory()) {
      const result = await findJavaExecutable(path.join(directory, entry.name), depth + 1);
      if (result) return result;
    }
  }
  return null;
}

function createLaunchCancelledError() {
  const error = new Error('Instalación cancelada.');
  error.code = 'LAUNCH_CANCELLED';
  return error;
}

function throwIfLaunchCancelled(launch) {
  if (launch.cancelled) throw createLaunchCancelledError();
}

async function applyNativeCrashGraphicsRecovery(gameDir, launch) {
  const recoveryPath = path.join(gameDir, 'logs', 'znclient-safe-graphics.json');
  const appliedPath = path.join(gameDir, 'logs', 'znclient-safe-graphics-applied.json');
  let recovery;
  try {
    recovery = JSON.parse(await fs.readFile(recoveryPath, 'utf8'));
  } catch (error) {
    if (error?.code !== 'ENOENT') {
      throw new Error(`No se pudo leer el aviso de recuperación gráfica: ${error.message}`);
    }
    const diagnosticPath = path.join(gameDir, 'logs', 'znclient-launch-diagnostics.log');
    try {
      const handle = await fs.open(diagnosticPath, 'r');
      try {
        const fileSize = (await handle.stat()).size;
        const length = Math.min(fileSize, 8192);
        const tail = Buffer.alloc(length);
        await handle.read(tail, 0, length, fileSize - length);
        const exits = [...tail.toString('utf8').matchAll(/^\[([^\]]+)\] Proceso cerrado\. C.digo de salida: (\d+)\./gm)];
        const previousExit = exits.at(-1);
        const previousExitCode = Number(previousExit?.[2]);
        if (previousExitCode !== 0xC0000005) return false;
        recovery = { exitCode: previousExitCode, crashAt: previousExit[1] };
      } finally {
        await handle.close();
      }
    } catch (diagnosticError) {
      if (diagnosticError?.code === 'ENOENT') return false;
      throw new Error(`No se pudo comprobar el último cierre de Minecraft: ${diagnosticError.message}`);
    }
  }

  try {
    const applied = JSON.parse(await fs.readFile(appliedPath, 'utf8'));
    if (applied.crashAt === recovery.crashAt) return false;
  } catch (error) {
    if (error?.code !== 'ENOENT') {
      throw new Error(`No se pudo leer el estado de recuperación gráfica: ${error.message}`);
    }
  }

  const optionsPath = path.join(gameDir, 'options.txt');
  const backupPath = path.join(gameDir, 'options.txt.znclient-backup');
  try {
    await fs.copyFile(optionsPath, backupPath, fsSync.constants.COPYFILE_EXCL);
  } catch (error) {
    if (error?.code !== 'EEXIST') {
      throw new Error(`No se pudo respaldar la configuración gráfica: ${error.message}`);
    }
  }

  const currentOptions = await fs.readFile(optionsPath, 'utf8');
  const safeValues = new Map([
    ['fullscreen', 'false'],
    ['maxFps', '60'],
    ['renderDistance', '6'],
    ['simulationDistance', '6'],
    ['entityShadows', 'false'],
  ]);
  const updatedOptions = currentOptions.split(/\r?\n/).map((line) => {
    const separator = line.indexOf(':');
    if (separator < 0) return line;
    const key = line.slice(0, separator);
    const value = safeValues.get(key);
    if (value === undefined) return line;
    safeValues.delete(key);
    return `${key}:${value}`;
  });
  updatedOptions.push(...[...safeValues].map(([key, value]) => `${key}:${value}`));
  await fs.writeFile(optionsPath, updatedOptions.join('\n'), 'utf8');
  throwIfLaunchCancelled(launch);
  await fs.writeFile(appliedPath, JSON.stringify({
    crashAt: recovery.crashAt || recovery.detectedAt,
    appliedAt: new Date().toISOString(),
  }, null, 2), 'utf8');
  try {
    await fs.unlink(recoveryPath);
  } catch (error) {
    if (error?.code !== 'ENOENT') {
      throw new Error(`No se pudo cerrar el aviso de recuperación gráfica: ${error.message}`);
    }
  }
  send('launch:status', {
    text: `Se aplicó una configuración gráfica segura tras el cierre anterior (código ${recovery.exitCode}). Se guardó el respaldo options.txt.znclient-backup.`,
    percent: 0,
  });
  return true;
}

async function rememberNativeCrash(gameDir, exitCode, recordDiagnostic) {
  if (exitCode !== 0xC0000005) return;
  const recoveryPath = path.join(gameDir, 'logs', 'znclient-safe-graphics.json');
  try {
    await fs.writeFile(recoveryPath, JSON.stringify({
      exitCode,
      detectedAt: new Date().toISOString(),
      reason: 'Windows access violation; game log ended while using AMD Radeon OpenGL.',
    }, null, 2), 'utf8');
    recordDiagnostic('Se preparó el modo gráfico seguro para el siguiente inicio tras un fallo nativo 0xC0000005.');
  } catch (error) {
    recordDiagnostic(`No se pudo guardar la recuperación gráfica: ${error.message}`);
    send('launch:status', {
      text: `Minecraft sufrió un fallo nativo 0xC0000005 y no se pudo preparar el modo gráfico seguro: ${error.message}`,
      percent: 0,
    });
  }
}

async function ensureJava(majorVersion, launch) {
  throwIfLaunchCancelled(launch);
  if (app.isPackaged) {
    const bundledRoot = path.join(process.resourcesPath, 'java-runtime', `java-${majorVersion}`);
    const bundledJava = await findJavaExecutable(bundledRoot);
    if (bundledJava) return bundledJava;
  }

  const runtimeRoot = path.join(app.getPath('userData'), 'runtime', `java-${majorVersion}`);
  const existing = await findJavaExecutable(runtimeRoot);
  if (existing) return existing;

  send('launch:status', { text: `Preparando Java ${majorVersion}…`, percent: 0 });
  const params = new URLSearchParams({ architecture: 'x64', image_type: 'jre', os: 'windows', vendor: 'eclipse' });
  const assetResponse = await fetch(`${ADOPTIUM_ASSETS}/${majorVersion}/hotspot?${params}`);
  if (!assetResponse.ok) throw new Error(`No se encontró un runtime compatible de Java ${majorVersion}.`);
  const assets = await assetResponse.json();
  const packageInfo = assets?.[0]?.binary?.package || assets?.[0]?.binaries?.[0]?.package;
  if (!packageInfo?.link || !packageInfo?.checksum) {
    throw new Error(`No hay un paquete Java ${majorVersion} para Windows de 64 bits.`);
  }

  await fs.mkdir(runtimeRoot, { recursive: true });
  const archive = path.join(runtimeRoot, 'runtime.zip');
  try {
    await downloadFile(packageInfo.link, archive, (downloaded, total) => {
      const percent = total ? Math.min(99, Math.round((downloaded / total) * 100)) : 0;
      const sizeMb = (downloaded / (1024 * 1024)).toFixed(0);
      const totalMb = total ? (total / (1024 * 1024)).toFixed(0) : '?';
      send('launch:status', { text: `Descargando Java ${majorVersion} · ${sizeMb} / ${totalMb} MB`, percent });
    }, launch.controller.signal);
    throwIfLaunchCancelled(launch);
    const actualChecksum = await hashFile(archive);
    if (actualChecksum.toLowerCase() !== packageInfo.checksum.toLowerCase()) {
      throw new Error('La comprobación de Java descargado no coincide. Vuelve a intentarlo.');
    }
    await extractZip(archive, { dir: runtimeRoot });
  } finally {
    await fs.rm(archive, { force: true });
  }

  const executable = await findJavaExecutable(runtimeRoot);
  if (!executable) throw new Error('Java se descargó, pero no se encontró java.exe dentro del paquete.');
  send('launch:status', { text: `Java ${majorVersion} listo.`, percent: 0 });
  return executable;
}

async function getJavaMajor(versionUrl, versionId, signal) {
  const response = await fetch(versionUrl, { signal });
  if (!response.ok) throw new Error(`No se pudieron cargar los datos de Minecraft ${versionId}.`);
  const metadata = await response.json();
  return Number(metadata.javaVersion?.majorVersion) || (Number.parseFloat(versionId) >= 1.17 ? 17 : 8);
}

async function ensureClientMods(gameInstallDirectory, versionDataDirectory, versionId, launch, safeGraphicsMode = false) {
  const userModsDirectory = path.join(versionDataDirectory, 'znclient-mods');
  const launcherModNames = new Set(['zn-client-mod.jar', 'fabric-api-0.164.0+26.3.jar']);
  await Promise.all([...launcherModNames].map((name) =>
    fs.rm(path.join(userModsDirectory, name), { force: true }),
  ));
  if (versionId !== CLIENT_MOD_GAME_VERSION) return null;
  throwIfLaunchCancelled(launch);
  send('launch:status', { text: 'Preparando el menú y Zoom de zN Client…', percent: 0 });

  const signal = launch.controller.signal;
  const loadersResponse = await fetch(`${FABRIC_META}/versions/loader/${versionId}`, { signal });
  if (!loadersResponse.ok) throw new Error(`No se encontró Fabric para Minecraft ${versionId}.`);
  const loaders = await loadersResponse.json();
  const loader = loaders.find((entry) => entry.loader?.stable) || loaders[0];
  if (!loader?.loader?.version) throw new Error(`No hay una versión de Fabric compatible con Minecraft ${versionId}.`);
  throwIfLaunchCancelled(launch);

  const profileResponse = await fetch(
    `${FABRIC_META}/versions/loader/${versionId}/${encodeURIComponent(loader.loader.version)}/profile/json`,
    { signal },
  );
  if (!profileResponse.ok) throw new Error('No se pudo preparar el perfil Fabric de zN Client.');
  const profile = await profileResponse.json();
  if (profile.inheritsFrom !== versionId || !/^[a-zA-Z0-9._-]+$/.test(profile.id || '')) {
    throw new Error('El perfil de Fabric no corresponde a la versión de Minecraft seleccionada.');
  }
  throwIfLaunchCancelled(launch);

  const sourceDirectory = app.isPackaged
    ? path.join(process.resourcesPath, 'client-mods')
    : path.join(__dirname, '..', 'build', 'client-mods');
  const modPath = path.join(sourceDirectory, 'zn-client-mod.jar');
  const apiPath = path.join(sourceDirectory, 'fabric-api-0.164.0+26.3.jar');
  await fs.access(modPath);
  await fs.access(apiPath);
  await fs.mkdir(userModsDirectory, { recursive: true });

  const runtimeModsDirectory = await fs.mkdtemp(path.join(os.tmpdir(), 'zn-client-mods-'));
  launch.runtimeModsDirectory = runtimeModsDirectory;
  if (!safeGraphicsMode) {
    const entries = await fs.readdir(userModsDirectory, { withFileTypes: true });
    for (const entry of entries) {
      if (!entry.isFile() || path.extname(entry.name).toLowerCase() !== '.jar') continue;
      if (!launcherModNames.has(entry.name.toLowerCase())) {
        await fs.copyFile(
          path.join(userModsDirectory, entry.name),
          path.join(runtimeModsDirectory, entry.name),
        );
      }
    }
  } else {
    send('launch:status', {
      text: 'Modo seguro: se iniciará sin mods externos para evitar otro cierre gráfico.',
      percent: 0,
    });
  }
  await Promise.all([
    fs.copyFile(modPath, path.join(runtimeModsDirectory, 'zn-client-mod.jar')),
    fs.copyFile(apiPath, path.join(runtimeModsDirectory, 'fabric-api-0.164.0+26.3.jar')),
  ]);

  const profileDirectory = path.join(gameInstallDirectory, 'versions', profile.id);
  await fs.mkdir(profileDirectory, { recursive: true });
  await fs.writeFile(path.join(profileDirectory, `${profile.id}.json`), JSON.stringify(profile, null, 2), 'utf8');
  throwIfLaunchCancelled(launch);
  return { profileId: profile.id, modsDirectory: runtimeModsDirectory };
}

async function cleanupRuntimeMods(launch, recordDiagnostic) {
  const directory = launch.runtimeModsDirectory;
  if (!directory) return;
  launch.runtimeModsDirectory = undefined;
  try {
    await fs.rm(directory, { recursive: true, force: true });
  } catch (error) {
    recordDiagnostic(`No se pudo limpiar la carpeta temporal de mods: ${error.message}`);
    console.error('[zN Client] No se pudo limpiar la carpeta temporal de mods:', error);
  }
}

function makeMinecraftDownloadsCancelable(launcher, launch) {
  const handler = launcher.handler;
  const activeRequests = new Set();

  launch.cancelDownloads = () => {
    for (const request of activeRequests) request.abort();
  };

  handler.downloadAsync = function downloadAsync(url, directory, name, retry, type) {
    if (launch.cancelled) return Promise.reject(createLaunchCancelledError());

    return new Promise((resolve, reject) => {
      fsSync.mkdirSync(directory, { recursive: true });
      const destination = path.join(directory, name);
      const request = this.baseRequest(url);
      activeRequests.add(request);

      let receivedBytes = 0;
      let totalBytes = 0;
      let settled = false;
      const file = fsSync.createWriteStream(destination);

      const cleanupRequest = () => activeRequests.delete(request);
      const finish = (value) => {
        if (settled) return;
        settled = true;
        cleanupRequest();
        resolve(value);
      };
      const removePartialFile = async () => {
        file.destroy();
        if (!file.closed) await new Promise((resolveClose) => file.once('close', resolveClose));
        try {
          await fs.unlink(destination);
        } catch (cleanupError) {
          if (cleanupError?.code !== 'ENOENT') {
            console.error(`[zN Client] No se pudo quitar la descarga incompleta ${name}:`, cleanupError);
          }
        }
      };
      const fail = async (error) => {
        if (settled) return;
        settled = true;
        cleanupRequest();
        request.abort();
        await removePartialFile();

        if (launch.cancelled) {
          reject(createLaunchCancelledError());
          return;
        }
        if (retry) {
          try {
            resolve(await this.downloadAsync(url, directory, name, false, type));
          } catch (retryError) {
            reject(retryError);
          }
          return;
        }
        this.client.emit('debug', `[MCLC]: Failed to download ${url} due to ${error}.`);
        reject(error);
      };

      request.on('response', (response) => {
        if (response.statusCode === 404) {
          this.client.emit('debug', `[MCLC]: Failed to download ${url} due to: File not found...`);
          settled = true;
          cleanupRequest();
          request.abort();
          void removePartialFile().then(() => resolve(false));
          return;
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
          void fail(new Error(`HTTP ${response.statusCode} al descargar ${name}.`));
          return;
        }
        totalBytes = Number.parseInt(response.headers['content-length'], 10) || 0;
      });
      request.on('data', (chunk) => {
        receivedBytes += chunk.length;
        this.client.emit('download-status', {
          name,
          type,
          current: receivedBytes,
          total: totalBytes,
        });
      });
      request.on('error', fail);
      request.on('abort', () => {
        if (launch.cancelled) fail(createLaunchCancelledError());
      });
      file.once('error', fail);
      file.once('finish', () => {
        if (settled) return;
        this.client.emit('download', name);
        finish({ failed: false, asset: null });
      });
      request.pipe(file);

      if (launch.cancelled) request.abort();
    });
  };
}

function createWindow() {
  const iconPath = process.platform === 'win32' && fsSync.existsSync(path.join(__dirname, '..', 'zNClient.ico'))
    ? path.join(__dirname, '..', 'zNClient.ico')
    : path.join(__dirname, '..', 'zNClient.png');

  mainWindow = new BrowserWindow({
    width: 1180,
    height: 760,
    minWidth: 900,
    minHeight: 640,
    backgroundColor: '#080b0e',
    title: 'zN Client',
    icon: iconPath,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  mainWindow.loadFile(path.join(__dirname, 'index.html'));
  mainWindow.on('closed', () => { mainWindow = null; });
}

function checkForAppUpdates() {
  if (!app.isPackaged) return;

  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.on('error', (error) => {
    console.error('[zN Client] Error del actualizador:', error);
    send('launch:status', { text: 'No se pudo comprobar o descargar la actualización de zN Client.' });
  });
  autoUpdater.on('update-available', async (info) => {
    if (!mainWindow || mainWindow.isDestroyed() || updatePromptOpen) return;
    updatePromptOpen = true;
    try {
      const { response } = await dialog.showMessageBox(mainWindow, {
        type: 'info',
        title: 'Actualización disponible',
        message: `zN Client ${info.version} está disponible.`,
        detail: '¿Quieres descargarla ahora? La actualización se aplicará al reiniciar el launcher.',
        buttons: ['Descargar actualización', 'Ahora no'],
        defaultId: 0,
        cancelId: 1,
      });
      if (response === 0) await autoUpdater.downloadUpdate();
    } catch (error) {
      console.error('[zN Client] No se pudo iniciar la actualización:', error);
      send('launch:status', { text: 'No se pudo iniciar la descarga de la actualización.' });
    } finally {
      updatePromptOpen = false;
    }
  });
  autoUpdater.on('download-progress', (progress) => {
    send('launch:status', {
      text: `Descargando actualización de zN Client… ${Math.round(progress.percent)} %`,
      percent: progress.percent,
    });
  });
  autoUpdater.on('update-downloaded', async (info) => {
    if (!mainWindow || mainWindow.isDestroyed()) return;
    try {
      const { response } = await dialog.showMessageBox(mainWindow, {
        type: 'info',
        title: 'Actualización lista',
        message: `zN Client ${info.version} ya está descargado.`,
        detail: 'Reinicia ahora para actualizar zN Client o continúa; la actualización se aplicará al cerrar el launcher.',
        buttons: ['Reiniciar y actualizar', 'Más tarde'],
        defaultId: 0,
        cancelId: 1,
      });
      if (response === 0) {
        send('launch:status', { text: 'Reiniciando para actualizar zN Client…' });
        autoUpdater.quitAndInstall();
      }
    } catch (error) {
      console.error('[zN Client] No se pudo completar la actualización:', error);
    }
  });

  autoUpdater.checkForUpdates().catch((error) => {
    console.error('[zN Client] No se pudo comprobar si hay actualizaciones:', error);
    send('launch:status', { text: 'No se pudo comprobar si hay actualizaciones de zN Client.' });
  });
}

async function migrateLegacyUserData() {
  const appData = app.getPath('appData');
  const currentDirectory = app.getPath('userData');
  await fs.mkdir(currentDirectory, { recursive: true });

  for (const oldName of ['Cubic Client', 'cubic-client']) {
    const oldDirectory = path.join(appData, oldName);
    try {
      await fs.access(oldDirectory);
    } catch (error) {
      if (error?.code === 'ENOENT') continue;
      console.error(`No se pudo revisar la carpeta anterior de Cubic Client: ${error.message}`);
      continue;
    }

    for (const entry of ['config.json', 'minecraft', 'runtime']) {
      const oldPath = path.join(oldDirectory, entry);
      const newPath = path.join(currentDirectory, entry);
      try {
        await fs.access(newPath);
        continue;
      } catch {
        // Keep any existing zN Client data and migrate missing files below.
      }
      try {
        await fs.rename(oldPath, newPath);
      } catch (error) {
        if (error?.code !== 'ENOENT') {
          console.error(`No se pudo conservar ${entry} de Cubic Client: ${error.message}`);
        }
      }
    }

    try {
      await fs.rmdir(oldDirectory);
    } catch {
      // Leave any unrelated files in the old profile untouched.
    }
  }
}

const gotSingleInstanceLock = app.requestSingleInstanceLock();
if (!gotSingleInstanceLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(async () => {
    await migrateLegacyUserData();
    await loadAccountStore();
    createWindow();
    setTimeout(checkForAppUpdates, 3000);
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });
}

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

ipcMain.handle('app:initial-state', async () => {
  const [config, releases] = await Promise.all([getConfig(), getReleases()]);
  let accountError;
  const initialTargetAccountId = (preferredAccountId && savedAccounts.has(preferredAccountId))
    ? preferredAccountId
    : (savedAccounts.size === 1 ? [...savedAccounts.keys()][0] : undefined);

  if (initialTargetAccountId && savedAccounts.has(initialTargetAccountId)) {
    try {
      await activateAccount(initialTargetAccountId);
    } catch {
      accountError = `No se pudo recuperar ${savedAccounts.get(initialTargetAccountId).name}. Selecciónala para volver a iniciar sesión.`;
      activeAccountId = undefined;
      accountToken = undefined;
      accountProfile = undefined;
    }
  }
  const latest = manifestCache.latest.release;
  const memoryChoices = getMemoryChoices();
  return {
    config: {
      ...config,
      version: config.version || latest,
      memory: memoryChoices.includes(config.memory) ? config.memory : getRecommendedMemory(),
    },
    memoryChoices,
    systemMemoryGb: Number((os.totalmem() / (1024 ** 3)).toFixed(1)),
    availableMemoryGb: Number((os.freemem() / (1024 ** 3)).toFixed(1)),
    recommendedMemory: getRecommendedMemory(),
    releases,
    latest,
    account: accountProfile,
    accounts: getAccountList(),
    activeAccountId: activeAccountId || null,
    accountError,
    gameRunning: Boolean(currentGame),
  };
});

ipcMain.handle('settings:save', async (_event, config) => saveConfig(config));

ipcMain.handle('account:add', async () => {
  try {
    if (launchInProgress || currentGame) {
      throw new Error('Cierra Minecraft antes de agregar otra cuenta.');
    }
if (!(await safeStorage.isAsyncEncryptionAvailable())) {
  throw new Error('Windows no permite guardar cuentas de forma segura en este equipo.');
}
    send('launch:status', { text: 'Abriendo el inicio de sesión de Microsoft…', percent: 0 });
    const auth = new Auth('select_account');
    const xbox = await auth.launch('electron', { width: 500, height: 700, resizable: true });
    const minecraft = await xbox.getMinecraft();
    const id = minecraft.profile.id;
    const session = { minecraft, refreshToken: xbox.save() };
    accountSessions.set(id, session);
    savedAccounts.set(id, {
      id,
      name: minecraft.profile.name,
      encryptedRefreshToken: await encryptRefreshToken(session.refreshToken),
    });
    activeAccountId = id;
    preferredAccountId = id;
    accountToken = minecraft;
    accountProfile = {
      name: minecraft.profile.name,
      id,
      skin: accountSkins.get(id) || null,
    };
    await saveAccountStore();
    sendAccountState();
    refreshActiveAccountSkin(id);
    send('launch:status', { text: `Sesión iniciada como ${accountProfile.name}.`, percent: 0 });
    return { ok: true, ...getAccountState() };
  } catch (error) {
    const message = error?.message || 'No se pudo iniciar sesión con Microsoft.';
    send('launch:status', { text: message, percent: 0 });
    return { ok: false, error: message };
  }
});

ipcMain.handle('account:select', async (_event, accountId) => {
  if (launchInProgress || currentGame) return { ok: false, error: 'Cierra Minecraft antes de cambiar de cuenta.' };
  try {
    return { ok: true, ...await activateAccount(String(accountId || '')) };
  } catch (error) {
    return { ok: false, error: error?.message || 'No se pudo abrir esa cuenta.' };
  }
});

ipcMain.handle('account:logout', async (_event, accountId) => {
  if (launchInProgress || currentGame) return { ok: false, error: 'Cierra Minecraft antes de cerrar sesión.' };
  const id = String(accountId || '');
  if (!savedAccounts.has(id)) return { ok: false, error: 'No se encontró esa cuenta.' };

  savedAccounts.delete(id);
  accountSessions.delete(id);
  if (activeAccountId === id) {
    activeAccountId = undefined;
    accountToken = undefined;
    accountProfile = undefined;
  }
  if (preferredAccountId === id) preferredAccountId = undefined;
  await saveAccountStore();
  sendAccountState();
  return { ok: true, ...getAccountState() };
});

ipcMain.handle('game:cancel', async () => {
  if (!activeLaunch || !launchInProgress) return { ok: false };
  activeLaunch.cancelled = true;
  activeLaunch.controller.abort();
  activeLaunch.cancelDownloads?.();
  send('launch:status', { text: 'Cancelando instalación…', percent: 0 });
  return { ok: true };
});

ipcMain.handle('game:launch', async (_event, selection) => {
  if (launchInProgress || currentGame) return { ok: false, error: 'Minecraft ya está abierto o iniciándose.' };
  if (!accountToken || !accountProfile) return { ok: false, error: 'Inicia sesión con Microsoft antes de jugar.' };

  launchInProgress = true;
  const launch = { cancelled: false, controller: new AbortController() };
  activeLaunch = launch;
  let diagnosticLog;
  let gameDir;
  const recordDiagnostic = (message) => {
    if (diagnosticLog && !diagnosticLog.destroyed && !diagnosticLog.writableEnded) {
      diagnosticLog.write(`[${new Date().toISOString()}] ${message}\n`);
    }
  };
  send('launch:status', { text: 'Preparando Minecraft…', percent: 0 });
  try {
    const releases = await getReleases();
    throwIfLaunchCancelled(launch);
    const selected = releases.find((entry) => entry.id === selection.version);
    if (!selected) throw new Error('Selecciona una versión válida de Minecraft.');
    const gameInstallDirectory = getGameInstallDirectory();
    const previousConfig = await getConfig();
    const legacyVersion = previousConfig.version || selected.id;
    gameDir = getVersionDataDirectory(selected.id);
    await migrateLegacyGameData(legacyVersion);
    await migrateLegacyMods();
    await fs.mkdir(path.join(gameDir, 'logs'), { recursive: true });
    const diagnosticPath = path.join(gameDir, 'logs', 'znclient-launch-diagnostics.log');
    diagnosticLog = createWriteStream(diagnosticPath, { flags: 'a' });
    diagnosticLog.on('error', (error) => console.error('[zN Client] No se pudo guardar el registro de Minecraft:', error.message));
    recordDiagnostic(`Inicio solicitado. Versi\u00f3n: ${selected.id}. Memoria elegida: ${selection?.memory || 'predeterminada'} GB. RAM libre al inicio: ${(os.freemem() / (1024 ** 3)).toFixed(1)} GB.`);

    const config = await saveConfig(selection);
    const javaMajor = await getJavaMajor(selected.url, selected.id, launch.controller.signal);
    throwIfLaunchCancelled(launch);
    const javaPath = await ensureJava(javaMajor, launch);
    throwIfLaunchCancelled(launch);
    const safeGraphicsMode = await applyNativeCrashGraphicsRecovery(gameDir, launch);
    const clientMods = await ensureClientMods(
      gameInstallDirectory,
      gameDir,
      selected.id,
      launch,
      safeGraphicsMode,
    );
    throwIfLaunchCancelled(launch);
    if (!clientMods && selected.id !== CLIENT_MOD_GAME_VERSION) {
      send('launch:status', {
        text: `El menú y Zoom de zN Client necesitan Minecraft ${CLIENT_MOD_GAME_VERSION}; se iniciará ${selected.id} sin los mods.`,
        percent: 0,
      });
    }
    send('launch:status', { text: `Descargando y preparando Minecraft ${selected.id}…`, percent: 0 });
    const availableMemoryGb = os.freemem() / (1024 ** 3);
    const requestedMemoryGb = config.memory;
    const safeMemoryLimitGb = Math.max(2, Math.floor(availableMemoryGb - 1));
    const effectiveMemoryGb = Math.min(requestedMemoryGb, safeMemoryLimitGb);
    recordDiagnostic(`RAM libre antes del proceso: ${availableMemoryGb.toFixed(1)} GB. Heap solicitado: ${requestedMemoryGb} GB. Heap efectivo: ${effectiveMemoryGb} GB.`);
    if (effectiveMemoryGb < requestedMemoryGb) {
      send('launch:status', {
        text: `Hay ${availableMemoryGb.toFixed(1)} GB de RAM libre. Usaré ${effectiveMemoryGb} GB para Minecraft en vez de ${requestedMemoryGb} GB y evitar presión de memoria; tu preferencia se conserva.`,
        percent: 0,
      });
    }

    const launcher = new Client();
    let outOfMemory = false;
    launcher.on('progress', (progress) => {
      const phase = progress?.type || progress?.task || 'Archivos';
      send('launch:status', { text: `Preparando Minecraft: ${phase}`, percent: 0 });
    });
    launcher.on('download-status', (progress) => {
      const percent = progress?.total ? Math.round((progress.current / progress.total) * 100) : 0;
      send('launch:status', { text: 'Descargando archivos del juego…', percent });
    });
    launcher.on('data', (line) => {
      const text = String(line).trim();
      for (const outputLine of String(line).split(/\r?\n/)) {
        if (/OutOfMemoryError|Java heap space/i.test(outputLine)) outOfMemory = true;
        if (/\b(?:ERROR|FATAL|SEVERE|OutOfMemoryError|UnsatisfiedLinkError)\b|(?:Exception|Caused by:|A fatal error has been detected|Could not create the Java Virtual Machine|OpenGL error|GLFW error)/i.test(outputLine)) {
          recordDiagnostic(`Minecraft: ${outputLine.trim()}`);
        }
      }
      if (text) send('game:log', text.slice(0, 500));
    });
    launcher.on('close', async (code) => {
      recordDiagnostic(`Proceso cerrado. C\u00f3digo de salida: ${code ?? 'desconocido'}.`);
      const exitCode = Number(code) >>> 0;
      try {
        await rememberNativeCrash(gameDir, exitCode, recordDiagnostic);
      } finally {
        await cleanupRuntimeMods(launch, recordDiagnostic);
      }
      diagnosticLog?.end();
      currentGame = undefined;
      launchInProgress = false;
      activeLaunch = undefined;
      send('game:closed', { code });
      const statusText = code === 0
        ? 'Minecraft se cerró correctamente.'
        : outOfMemory
          ? 'Minecraft se quedó sin memoria Java (OutOfMemoryError). Usa menos memoria para Minecraft o cierra otras aplicaciones.'
          : exitCode === 0xC0000005
            ? 'Minecraft sufrió un fallo nativo (0xC0000005), no un OutOfMemoryError. Revisa el controlador AMD/OpenGL; se buscará más detalle en logs/hs_err_pid*.log.'
            : `Minecraft se cerró con el código ${code}.`;
      send('launch:status', { text: statusText, percent: 0 });
    });

    const startMinecraft = launcher.launch.bind(launcher);
    launcher.launch = async (options) => {
      const gamePromise = startMinecraft(options);
      makeMinecraftDownloadsCancelable(launcher, launch);
      return gamePromise;
    };
    const gameProcess = await launcher.launch({
      authorization: accountToken.mclc(),
      root: gameInstallDirectory,
      version: {
        number: selected.id,
        type: 'release',
        ...(clientMods ? { custom: clientMods.profileId } : {}),
      },
      memory: { min: '1G', max: `${effectiveMemoryGb}G` },
      javaPath,
      overrides: { gameDirectory: gameDir },
      customArgs: [
        ...(clientMods ? [`-Dfabric.modsFolder=${clientMods.modsDirectory}`] : []),
        `-XX:ErrorFile=${path.join(gameDir, 'logs', 'hs_err_pid%p.log')}`,
      ],
      timeout: 120000,
    });
    if (!gameProcess && !launch.cancelled) {
      throw new Error('Minecraft no pudo iniciarse. Revisa la conexión y vuelve a intentarlo.');
    }
    if (launch.cancelled) {
      gameProcess?.kill();
      throw createLaunchCancelledError();
    }
    if (gameProcess) {
      recordDiagnostic(`Proceso iniciado. PID: ${gameProcess.pid ?? 'desconocido'}.`);
      gameProcess.once('error', (error) => recordDiagnostic(`Error del proceso: ${error.message}`));
      gameProcess.once('exit', (code, signal) => {
        recordDiagnostic(`Proceso termin\u00f3. C\u00f3digo: ${code ?? 'desconocido'}. Se\u00f1al: ${signal ?? 'ninguna'}.`);
      });
    }
    currentGame = gameProcess;
    launchInProgress = false;
    activeLaunch = undefined;
    send('launch:status', {
      text: `Minecraft ${selected.id} está iniciado con ${effectiveMemoryGb} GB de RAM.`,
      percent: 100,
    });
    send('game:started', { version: selected.id });
    return { ok: true };
  } catch (error) {
    recordDiagnostic(`No se pudo iniciar Minecraft: ${error?.name || 'Error'}: ${error?.message || 'error desconocido'}`);
    await cleanupRuntimeMods(launch, recordDiagnostic);
    diagnosticLog?.end();
    currentGame = undefined;
    launchInProgress = false;
    activeLaunch = undefined;
    const cancelled = launch.cancelled || error?.code === 'LAUNCH_CANCELLED' || error?.name === 'AbortError';
    const message = cancelled ? 'Instalación cancelada.' : error?.message || 'No se pudo iniciar Minecraft.';
    send('launch:status', { text: message, percent: 0 });
    return { ok: false, error: message };
  }
});

ipcMain.handle('app:open-game-folder', async (_event, versionId) => {
  const selectedVersion = await resolveVersionId(versionId);
  const gameDir = getVersionDataDirectory(selectedVersion);
  await fs.mkdir(gameDir, { recursive: true });
  const error = await shell.openPath(gameDir);
  if (error) return { ok: false, error: `No se pudo abrir la carpeta: ${error}` };
  return { ok: true };
});

const gameLocations = {
  mods: 'znclient-mods',
  screenshots: 'screenshots',
  saves: 'saves',
  logs: 'logs',
  resourcepacks: 'resourcepacks',
  shaderpacks: 'shaderpacks',
  config: 'config',
};

ipcMain.handle('app:open-game-location', async (_event, location, versionId) => {
  const directoryName = Object.hasOwn(gameLocations, location) ? gameLocations[location] : undefined;
  if (!directoryName) return { ok: false, error: 'Esa carpeta de Minecraft no está disponible.' };

  const selectedVersion = await resolveVersionId(versionId);
  const directory = path.join(getVersionDataDirectory(selectedVersion), directoryName);
  await fs.mkdir(directory, { recursive: true });
  const error = await shell.openPath(directory);
  if (error) return { ok: false, error: `No se pudo abrir la carpeta: ${error}` };
  return { ok: true };
});

ipcMain.handle('mods:list', (_event, versionId) => listMods(versionId));
