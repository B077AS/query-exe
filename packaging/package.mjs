// Builds what a release ships, from the fat jar `mvn package` leaves in target/. Used by the release workflow and by hand:
//
//   cd frontend && npm run build        the page, into src/main/resources/ui
//   mvn clean package -DskipTests       the fat jar, target/query-exe-<version>.jar
//   node packaging/package.mjs <kind> [--version 1.2.3] [--out target/dist]
//
// kinds:  windows-installer   QueryExe-<v>-windows-x64-setup.exe   (a branded wizard; needs Inno Setup 6: winget install JRSoftware.InnoSetup)
//         linux-deb           QueryExe-<v>-linux-amd64.deb          (on Linux, needs fakeroot and dpkg; build it on the oldest Ubuntu you want it to
//                                                                    install on: the package names its dependencies as that system calls them)
//         linux-tarball       QueryExe-<v>-linux-x64.tar.gz         (on Linux; unpack and run QueryExe/bin/QueryExe)
//         linux-appimage      QueryExe-<v>-linux-x86_64.AppImage    (on Linux, needs appimagetool on the PATH; it still needs the system's GTK, WebKitGTK and
//                                                                    libsecret, so it gains little over the .deb and the .tar.gz)
//         jars                QueryExe-<v>.jar                      (one jar with everything in it; any system with Java 25: java -jar QueryExe-<v>.jar)
//
// Everything but the jar carries its own Java runtime (made with jlink from the modules listed below), so users need no Java.
// Needs JDK 25 on the PATH (jpackage, jlink) and Node 20+.

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const targetDir = path.join(root, 'target');

// What the app needs at run time: the UI helpers (clipboard), JDBC and its XA/rowset/JNDI/Kerberos/SASL parts used by the database drivers,
// TLS elliptic curves (jdk.crypto.ec) and the less common character sets (jdk.charsets), which jdeps cannot see.
const MODULES = [
  'java.base', 'java.desktop', 'java.sql', 'java.sql.rowset', 'java.transaction.xa', 'java.logging', 'java.naming', 'java.xml',
  'java.management', 'java.net.http', 'java.security.sasl', 'java.security.jgss', 'java.xml.crypto',
  'jdk.unsupported', 'jdk.crypto.ec', 'jdk.charsets', 'jdk.naming.dns',
].join(',');

// Packages the .deb needs on the user's machine (the web view and the OS keystore are the system's, not bundled).
const DEB_DEPENDS = 'libgtk-3-0, libwebkit2gtk-4.1-0, libsecret-1-0';

const APP_NAME = 'QueryExe';

function fail(message) {
  console.error('packaging: ' + message);
  process.exit(1);
}

function run(command, args, options = {}) {
  console.log('> ' + [command, ...args].join(' '));
  execFileSync(command, args, { stdio: 'inherit', ...options });
}

function parseArgs(argv) {
  const [kind, ...rest] = argv;
  const options = { kind, version: null, out: path.join(root, 'target', 'dist') };
  for (let i = 0; i < rest.length; i += 2) {
    if (rest[i] === '--version') options.version = rest[i + 1];
    else if (rest[i] === '--out') options.out = path.resolve(rest[i + 1]);
    else fail('unknown option ' + rest[i]);
  }
  return options;
}

function pomVersion() {
  const pom = fs.readFileSync(path.join(root, 'pom.xml'), 'utf8');
  const match = pom.match(/<artifactId>query-exe<\/artifactId>\s*<version>([^<]+)<\/version>/);
  if (!match) fail('could not read the version from pom.xml');
  return match[1];
}

const options = parseArgs(process.argv.slice(2));
if (!options.kind) fail('say what to build: windows-installer, linux-deb, linux-tarball, linux-appimage or jars');
const version = (options.version ?? pomVersion()).replace(/^v/, '');
// jpackage wants 1, 1.2 or 1.2.3 (digits only); a tag like 1.2.0-beta1 keeps its full name on the files and 1.2.0 inside.
const appVersion = (version.match(/^\d+(\.\d+){0,2}/) ?? [])[0];
if (!appVersion) fail(`"${version}" does not start with a version number such as 1.2.3`);

// The shade plugin replaces the plain jar with the fat one; `original-*.jar` is the plain one it keeps.
const jar = fs.readdirSync(targetDir).find((f) => /^query-exe-.*\.jar$/.test(f) && !f.startsWith('original-'));
if (!jar) fail('target has no query-exe jar: run `mvn clean package -DskipTests` first');
fs.mkdirSync(options.out, { recursive: true });

// A fresh scratch folder for jpackage's own output, so a half-finished earlier run can never leak into this one.
const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'queryexe-package-'));
// jpackage packs everything in its input folder, so give it a folder with the fat jar and nothing else.
const inputDir = path.join(scratch, 'input');
fs.mkdirSync(inputDir);
fs.copyFileSync(path.join(targetDir, jar), path.join(inputDir, jar));
const appImageParent = path.join(scratch, 'out');
fs.mkdirSync(appImageParent);

function findInnoSetup() {
  const candidates = [
    process.env.ISCC_PATH,
    path.join(process.env.LOCALAPPDATA ?? '', 'Programs', 'Inno Setup 6', 'ISCC.exe'), // a per-user install, which winget makes
    'C:/Program Files (x86)/Inno Setup 6/ISCC.exe',
    'C:/Program Files/Inno Setup 6/ISCC.exe',
  ];
  const found = candidates.find((c) => c && fs.existsSync(c));
  if (!found) fail('Inno Setup 6 is not installed: run  winget install JRSoftware.InnoSetup  (or set ISCC_PATH to ISCC.exe)');
  return found;
}

function jpackage(extra) {
  const args = [
    '--input', inputDir,
    '--main-jar', jar,
    '--main-class', 'io.github.b077as.queryexe.Launcher',
    '--name', APP_NAME,
    '--app-version', appVersion,
    '--vendor', APP_NAME,
    '--description', 'A desktop database client',
    // Jolt calls native code through the Foreign Function & Memory API.
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--add-modules', MODULES,
    '--dest', appImageParent,
    // The licence asks that a copy travels with the program: it ends up next to the app in every package.
    ...(fs.existsSync(path.join(root, 'LICENSE')) ? ['--app-content', path.join(root, 'LICENSE')] : []),
    ...extra,
  ];
  run('jpackage', args);
}

function finish(from, target) {
  const file = path.join(options.out, target);
  // jpackage's output is read-only, which would make the next run unable to replace it.
  if (fs.existsSync(file)) fs.chmodSync(file, 0o666);
  fs.rmSync(file, { force: true });
  fs.copyFileSync(from, file);
  fs.chmodSync(file, 0o644);
  console.log(`\nbuilt ${file} (${(fs.statSync(file).size / 1048576).toFixed(1)} MB)`);
}

const linuxIcon = path.join(root, 'packaging', 'linux', 'icons', '256.png');

switch (options.kind) {
  case 'windows-installer': {
    if (process.platform !== 'win32') fail('the Windows installer is built on Windows');
    const icon = path.join(root, 'src', 'main', 'resources', 'icon.ico');
    // Two steps: jpackage makes the app folder (QueryExe.exe, the jar and a private Java runtime), then Inno Setup wraps it in the wizard.
    jpackage(['--type', 'app-image', '--icon', icon]);
    const outName = `${APP_NAME}-${version}-windows-x64-setup`;
    const setup = path.join(options.out, outName + '.exe');
    if (fs.existsSync(setup)) fs.chmodSync(setup, 0o666);
    fs.rmSync(setup, { force: true });
    const fileVersion = [...appVersion.split('.'), '0', '0', '0'].slice(0, 4).join('.');
    run(findInnoSetup(), [
      '/Qp', // quiet, but still print problems
      `/DAppVersion=${version}`,
      `/DFileVersion=${fileVersion}`,
      `/DAppImageDir=${path.join(appImageParent, APP_NAME)}`,
      `/DOutputDir=${options.out}`,
      `/DOutputName=${outName}`,
      `/DIconFile=${icon}`,
      path.join(root, 'packaging', 'windows', 'queryexe.iss'),
    ]);
    console.log(`\nbuilt ${setup} (${(fs.statSync(setup).size / 1048576).toFixed(1)} MB)`);
    break;
  }
  case 'linux-deb': {
    if (process.platform !== 'linux') fail('the .deb is built on Linux');
    jpackage([
      '--type', 'deb',
      '--icon', linuxIcon,
      '--resource-dir', path.join(root, 'packaging', 'linux', 'jpackage'),
      '--linux-package-name', 'query-exe',
      '--linux-app-category', 'database',
      '--linux-menu-group', 'Development;Database;',
      '--linux-shortcut',
      '--linux-package-deps', DEB_DEPENDS,
      // Whoever is named here is shown as the package's maintainer (the release workflow passes the publisher's GitHub no-reply address).
      ...(process.env.QUERYEXE_DEB_MAINTAINER ? ['--linux-deb-maintainer', process.env.QUERYEXE_DEB_MAINTAINER] : []),
    ]);
    const deb = fs.readdirSync(appImageParent).find((f) => f.endsWith('.deb'));
    if (!deb) fail('jpackage produced no .deb');
    finish(path.join(appImageParent, deb), `${APP_NAME}-${version}-linux-amd64.deb`);
    break;
  }
  case 'linux-tarball': {
    if (process.platform !== 'linux') fail('the tarball is built on Linux');
    jpackage(['--type', 'app-image', '--icon', linuxIcon]);
    const file = path.join(options.out, `${APP_NAME}-${version}-linux-x64.tar.gz`);
    fs.rmSync(file, { force: true });
    run('tar', ['-czf', file, '-C', appImageParent, APP_NAME]);
    console.log(`\nbuilt ${file} (${(fs.statSync(file).size / 1048576).toFixed(1)} MB)`);
    break;
  }
  case 'linux-appimage': {
    if (process.platform !== 'linux') fail('the AppImage is built on Linux');
    // The same app folder as the tarball, wrapped in an AppDir: AppRun starts it, and the desktop file and icon sit at the top.
    jpackage(['--type', 'app-image', '--icon', linuxIcon]);
    const appDir = path.join(scratch, `${APP_NAME}.AppDir`);
    fs.mkdirSync(appDir);
    fs.renameSync(path.join(appImageParent, APP_NAME), path.join(appDir, APP_NAME));
    fs.writeFileSync(path.join(appDir, 'AppRun'), `#!/bin/sh\nHERE="$(dirname "$(readlink -f "$0")")"\nexec "$HERE/${APP_NAME}/bin/${APP_NAME}" "$@"\n`, { mode: 0o755 });
    fs.writeFileSync(path.join(appDir, 'com.queryexe.app.desktop'),
      fs.readFileSync(path.join(root, 'packaging', 'linux', 'com.queryexe.app.desktop'), 'utf8').replace('Exec=query-exe', 'Exec=AppRun'));
    fs.copyFileSync(linuxIcon, path.join(appDir, 'com.queryexe.app.png'));
    const file = path.join(options.out, `${APP_NAME}-${version}-linux-x86_64.AppImage`);
    fs.rmSync(file, { force: true });
    run('appimagetool', [appDir, file], { env: { ...process.env, ARCH: 'x86_64' } });
    console.log(`\nbuilt ${file} (${(fs.statSync(file).size / 1048576).toFixed(1)} MB)`);
    break;
  }
  case 'jars': {
    // One jar with every dependency inside (maven-shade): `java -jar QueryExe-<v>.jar` is all it takes.
    finish(path.join(targetDir, jar), `${APP_NAME}-${version}.jar`);
    break;
  }
  default:
    fail(`unknown kind "${options.kind}"`);
}

fs.rmSync(scratch, { recursive: true, force: true });
