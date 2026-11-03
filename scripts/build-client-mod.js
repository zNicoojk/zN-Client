const { createHash } = require('node:crypto');
const { createReadStream, createWriteStream } = require('node:fs');
const fs = require('node:fs/promises');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { pipeline } = require('node:stream/promises');
const { Readable } = require('node:stream');
const extractZip = require('extract-zip');

const ROOT = path.resolve(__dirname, '..');
const TOOL_DIR = path.join(ROOT, 'build', 'client-mod-toolchain');
const JDK_DIR = path.join(TOOL_DIR, 'jdk-25');
const MOD_PROJECT = path.join(ROOT, 'client-mod');
const MOD_OUTPUT = path.join(ROOT, 'build', 'client-mods');
const MOD_VERSION = '0.1.0';
const FABRIC_API_VERSION = '0.164.0+26.3';
const FABRIC_API_SHA512 = '2ce7b0826f75801a56e8150eb5ceca467cdf8b4b44e160b5a5381e5412567d024f46a16c679f29c4a9346b2687fa1740f91ed6026a31b2ed1f3d4b5035968d69';

async function download(url, destination) {
  const response = await fetch(url, { redirect: 'follow' });
  if (!response.ok || !response.body) throw new Error(`Descarga fallida (${response.status}): ${url}`);
  await fs.mkdir(path.dirname(destination), { recursive: true });
  await pipeline(Readable.fromWeb(response.body), createWriteStream(destination));
}

async function sha256(file) {
  const hash = createHash('sha256');
  await pipeline(createReadStream(file), hash);
  return hash.digest('hex');
}

async function sha512Buffer(buffer) {
  return createHash('sha512').update(buffer).digest('hex');
}

async function findFile(directory, fileName, depth = 0) {
  if (depth > 5) return null;
  let entries;
  try {
    entries = await fs.readdir(directory, { withFileTypes: true });
  } catch {
    return null;
  }
  for (const entry of entries) {
    const candidate = path.join(directory, entry.name);
    if (entry.isFile() && entry.name.toLowerCase() === fileName.toLowerCase()) return candidate;
    if (entry.isDirectory()) {
      const nested = await findFile(candidate, fileName, depth + 1);
      if (nested) return nested;
    }
  }
  return null;
}

async function ensureJdk() {
  const existing = await findFile(JDK_DIR, 'javac.exe');
  if (existing) return path.dirname(path.dirname(existing));

  console.log('Descargando JDK 25 para compilar el mod de zN Client…');
  const assetsResponse = await fetch('https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse');
  if (!assetsResponse.ok) throw new Error(`No se encontró JDK 25 (${assetsResponse.status}).`);
  const assets = await assetsResponse.json();
  const archiveInfo = assets?.[0]?.binary?.package;
  if (!archiveInfo?.link || !archiveInfo?.checksum) throw new Error('Adoptium no devolvió el paquete de JDK 25.');

  await fs.mkdir(JDK_DIR, { recursive: true });
  const archive = path.join(TOOL_DIR, archiveInfo.name || 'jdk-25.zip');
  try {
    await download(archiveInfo.link, archive);
    const actualChecksum = await sha256(archive);
    if (actualChecksum.toLowerCase() !== archiveInfo.checksum.toLowerCase()) {
      throw new Error('La verificación SHA-256 del JDK 25 no coincide.');
    }
    await extractZip(archive, { dir: JDK_DIR });
  } finally {
    await fs.rm(archive, { force: true });
  }

  const javac = await findFile(JDK_DIR, 'javac.exe');
  if (!javac) throw new Error('El JDK 25 se descargó, pero no se encontró javac.exe.');
  return path.dirname(path.dirname(javac));
}

function runGradle(javaHome) {
  return new Promise((resolve, reject) => {
    const command = '.\\gradlew.bat build --no-daemon';
    const environment = {
      ...process.env,
      JAVA_HOME: javaHome,
      PATH: `${path.join(javaHome, 'bin')};${process.env.PATH || ''}`,
      GRADLE_USER_HOME: path.join(ROOT, 'build', 'gradle-home'),
    };
    const child = spawn(process.env.ComSpec || 'cmd.exe', ['/d', '/c', command], {
      cwd: MOD_PROJECT,
      env: environment,
      stdio: 'inherit',
      windowsHide: true,
    });
    child.once('error', reject);
    child.once('exit', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`Gradle terminó con código ${code}.`));
    });
  });
}

async function installFabricApi() {
  const destination = path.join(MOD_OUTPUT, `fabric-api-${FABRIC_API_VERSION}.jar`);
  try {
    const existing = await fs.readFile(destination);
    if (await sha512Buffer(existing) === FABRIC_API_SHA512) return;
  } catch {
    // Fetch the pinned artifact below when it is not cached.
  }

  console.log('Descargando Fabric API para Minecraft 26.3…');
  const metadataUrl = 'https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%2226.3%22%5D&loaders=%5B%22fabric%22%5D';
  const metadataResponse = await fetch(metadataUrl);
  if (!metadataResponse.ok) throw new Error(`No se pudo consultar Fabric API (${metadataResponse.status}).`);
  const versions = await metadataResponse.json();
  const release = versions.find((entry) => entry.version_number === FABRIC_API_VERSION);
  const artifact = release?.files?.find((file) => file.primary) || release?.files?.[0];
  if (!artifact?.url || artifact.hashes?.sha512 !== FABRIC_API_SHA512) {
    throw new Error('La versión oficial fijada de Fabric API no está disponible o su checksum cambió.');
  }

  const response = await fetch(artifact.url);
  if (!response.ok) throw new Error(`No se pudo descargar Fabric API (${response.status}).`);
  const buffer = Buffer.from(await response.arrayBuffer());
  if (await sha512Buffer(buffer) !== FABRIC_API_SHA512) throw new Error('La verificación SHA-512 de Fabric API no coincide.');
  await fs.writeFile(destination, buffer);
}

async function main() {
  await fs.mkdir(MOD_OUTPUT, { recursive: true });
  const javaHome = await ensureJdk();
  await runGradle(javaHome);

  const libraries = await fs.readdir(path.join(MOD_PROJECT, 'build', 'libs'));
  const modJarName = libraries.find((name) => name.endsWith(`-${MOD_VERSION}.jar`) && !name.includes('sources') && !name.includes('-dev'));
  if (!modJarName) throw new Error('Gradle no generó el JAR remapeado de zN Client.');
  await fs.copyFile(path.join(MOD_PROJECT, 'build', 'libs', modJarName), path.join(MOD_OUTPUT, 'zn-client-mod.jar'));
  await installFabricApi();
  console.log('Mod de zN Client y Fabric API listos para el instalador.');
}

main().catch((error) => {
  console.error(error.stack || error.message || error);
  process.exitCode = 1;
});
