const { createHash } = require('node:crypto');
const { createReadStream, createWriteStream } = require('node:fs');
const fs = require('node:fs/promises');
const path = require('node:path');
const { pipeline } = require('node:stream/promises');
const { Readable } = require('node:stream');
const extractZip = require('extract-zip');

const VERSION_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const ADOPTIUM_ASSETS = 'https://api.adoptium.net/v3/assets/latest';
const projectRoot = path.resolve(__dirname, '..');
const stageRoot = path.join(projectRoot, 'build', 'java-runtime');

async function getJson(url, description) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${description} (HTTP ${response.status}).`);
  return response.json();
}

async function findJavaExecutable(directory, depth = 0) {
  if (depth > 5) return null;
  const direct = path.join(directory, 'bin', 'java.exe');
  try {
    await fs.access(direct);
    return direct;
  } catch {
    // Temurin archives contain a vendor directory above bin/java.exe.
  }

  let entries;
  try {
    entries = await fs.readdir(directory, { withFileTypes: true });
  } catch {
    return null;
  }
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const result = await findJavaExecutable(path.join(directory, entry.name), depth + 1);
    if (result) return result;
  }
  return null;
}

async function downloadFile(url, destination) {
  const response = await fetch(url, { redirect: 'follow' });
  if (!response.ok || !response.body) throw new Error(`No se pudo descargar Java (HTTP ${response.status}).`);
  await pipeline(Readable.fromWeb(response.body), createWriteStream(destination));
}

async function hashFile(filePath) {
  const hash = createHash('sha256');
  await pipeline(createReadStream(filePath), hash);
  return hash.digest('hex');
}

async function main() {
  console.log('Buscando el runtime Java compatible con la última versión de Minecraft…');
  const manifest = await getJson(VERSION_MANIFEST, 'No se pudo consultar el catálogo de Minecraft');
  const latestRelease = manifest.versions.find((version) => version.id === manifest.latest.release);
  if (!latestRelease) throw new Error('No se encontró la última versión estable de Minecraft.');

  const metadata = await getJson(latestRelease.url, `No se pudo consultar Minecraft ${latestRelease.id}`);
  const javaMajor = Number(metadata.javaVersion?.majorVersion)
    || (Number.parseFloat(latestRelease.id) >= 1.17 ? 17 : 8);
  const query = new URLSearchParams({
    architecture: 'x64',
    image_type: 'jre',
    os: 'windows',
    vendor: 'eclipse',
  });
  const assets = await getJson(
    `${ADOPTIUM_ASSETS}/${javaMajor}/hotspot?${query}`,
    `No se encontró Java ${javaMajor}`,
  );
  const packageInfo = assets?.[0]?.binary?.package || assets?.[0]?.binaries?.[0]?.package;
  if (!packageInfo?.link || !packageInfo?.checksum) {
    throw new Error(`No hay un paquete Java ${javaMajor} verificable para Windows de 64 bits.`);
  }

  await fs.rm(stageRoot, { recursive: true, force: true });
  const runtimeRoot = path.join(stageRoot, `java-${javaMajor}`);
  await fs.mkdir(runtimeRoot, { recursive: true });
  const archive = path.join(stageRoot, 'runtime.zip');
  try {
    console.log(`Descargando Eclipse Temurin Java ${javaMajor} para Minecraft ${latestRelease.id}…`);
    await downloadFile(packageInfo.link, archive);
    const actualChecksum = await hashFile(archive);
    if (actualChecksum.toLowerCase() !== packageInfo.checksum.toLowerCase()) {
      throw new Error('La suma de comprobación del paquete Java no coincide.');
    }
    await extractZip(archive, { dir: runtimeRoot });
  } finally {
    await fs.rm(archive, { force: true });
  }

  if (!(await findJavaExecutable(runtimeRoot))) {
    throw new Error('El paquete Java no contiene bin/java.exe.');
  }
  await fs.writeFile(path.join(stageRoot, 'runtime-info.json'), JSON.stringify({
    minecraftVersion: latestRelease.id,
    javaMajor,
    vendor: 'Eclipse Temurin',
  }, null, 2));
  console.log(`Java ${javaMajor} quedó listo para incluirse en el instalador.`);
}

main().catch((error) => {
  console.error(`No se pudo preparar Java para el instalador: ${error.message}`);
  process.exitCode = 1;
});
