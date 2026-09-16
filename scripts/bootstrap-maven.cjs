// Windows fallback when the Maven wrapper's PowerShell TLS download fails.
// Uses Node HTTPS validation and verifies the official SHA-512 checksum.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
async function main() {
  const root = path.resolve(__dirname, '..');
  const properties = fs.readFileSync(path.join(root, 'stockflow-backend/.mvn/wrapper/maven-wrapper.properties'), 'utf8');
  const url = properties.match(/^distributionUrl=(.+)$/m)[1].trim();
  const name = path.basename(url).replace(/-bin.zip$/, '');
  const hash = crypto.createHash('sha256').update(url).digest('hex');
  const parent = path.join(root, '.tools/maven/wrapper/dists', name);
  const destination = path.join(parent, hash);
  if (fs.existsSync(path.join(destination, 'bin/mvn.cmd'))) return console.log('Local Maven is ready.');
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Download failed: ${response.status}`);
  const archive = Buffer.from(await response.arrayBuffer());
  const checksumResponse = await fetch(url + '.sha512');
  if (!checksumResponse.ok) throw new Error('Unable to fetch Maven checksum');
  const expected = (await checksumResponse.text()).trim().split(/\s+/)[0];
  if (crypto.createHash('sha512').update(archive).digest('hex') !== expected) throw new Error('Maven checksum mismatch');
  fs.mkdirSync(parent, { recursive: true });
  const zip = path.join(parent, 'maven.zip');
  fs.writeFileSync(zip, archive);
  execFileSync('tar.exe', ['-xf', zip, '-C', parent], { stdio: 'inherit' });
  fs.renameSync(path.join(parent, name), destination);
  fs.unlinkSync(zip);
  console.log('Verified Maven installed in .tools/maven. Set MAVEN_USER_HOME as shown in README.');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });


