<h1 align="center">
  <img src="src/main/resources/icon.png" alt="QueryExe icon" width="80"><br>
  query-exe-launcher
</h1>

<p align="center">
  <b>The auto-updating launcher for <a href="https://github.com/B077AS/query-exe">QueryExe</a> — a free, cross-platform database client.</b><br>
  Windows installer · Linux AppImage · Zero-maintenance updates · No hub, no server
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white">
  <img alt="JavaFX 22" src="https://img.shields.io/badge/JavaFX-22-1B6AC6">
  <img alt="jpackage + jlink" src="https://img.shields.io/badge/jpackage-private%20runtime-555555">
  <img alt="Windows | Linux" src="https://img.shields.io/badge/Windows%20installer-Linux%20AppImage-4CAF50">
  <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue">
</p>

---

## What is QueryExe?

[QueryExe](https://github.com/B077AS/query-exe) is a free, cross-platform desktop client for working with relational databases — MySQL, MariaDB, PostgreSQL, SQL Server, H2 and SQLite, with a modern JavaFX interface, a SQL editor with autocomplete, and result-grid editing.

The project has two pieces:

| Piece | Role |
|---|---|
| [query-exe](https://github.com/B077AS/query-exe) (repo root) | Desktop client (JavaFX, Windows & Linux) |
| **launcher/** (this folder, in the query-exe repo) | Auto-updating launcher, kept up to date by the client in return |

This repo is the source for a small, Discord-style bootstrapper that installs the QueryExe client, keeps it up to date on every start, and then gets out of the way. Install once, never think about updates again.

> 📥 **Just want to use QueryExe?** Grab the Windows installer or Linux AppImage from **[query-exe's Releases page](https://github.com/B077AS/query-exe/releases)** — the client repo's own release pipeline checks out this repo's latest release and bundles it into the installer/AppImage. This repo's own [Releases](../../releases) page only carries self-update payloads (`query-exe-launcher-windows.jar` / `query-exe-launcher-linux.AppImage`) for a launcher you've already installed — not something you'd download directly.

## What the launcher does

- **Checks for updates on every start** — asks GitHub for the latest query-exe release and swaps in the new JAR automatically, with a live progress bar
- **Works offline** — a bundled client seed makes the very first start work without internet; later, if GitHub is unreachable, the installed client launches as-is
- **Never breaks your install** — downloads are verified before they replace anything, and the swap is atomic (see [The update rules](#the-update-rules))
- **Ships its own Java** — installer and AppImage carry a private jlink runtime shared by launcher and client, so users never install Java
- **Looks like QueryExe** — a frameless, transparent, draggable window styled with the client's own color tokens and icon; on Windows the client process even shows as **"QueryExe"** in Task Manager, not "Java Platform SE binary"

## How an update works

There's no hub — the launcher queries GitHub's Releases API directly:

```
┌──────────────┐  1. GET /repos/.../query-exe/releases/latest  ┌──────────────┐
│   Launcher   │ ─────────────────────────────────────────────►│    GitHub    │  ← hosts the latest
│  (this repo) │ ◄───────────────────────────────────────────── │              │    client fat JAR
└──────┬───────┘   { tag_name, assets: [...] }                 └──────────────┘
       │
       │  2. stale/missing? stream the jar asset, verify against
       │     its published .sha256 sidecar, atomically swap query-exe.jar
       ▼
┌──────────────┐
│  query-exe   │  3. launched on the bundled runtime
│  (query-exe.jar) │
└──────────────┘
```

1. **Check** — `GET https://api.github.com/repos/B077AS/query-exe/releases/latest` returns the release's tag (the version, a leading `v` stripped) and its asset list.
2. **Compare** — against the `app.version` embedded in the installed JAR's `app.properties`. The JAR is **self-describing** — no separate version file to drift out of sync.
3. **Download** — if the installed JAR is missing or stale, the matching `query-exe-<version>.jar` asset streams down with determinate progress, is verified (it must be able to state its own version, and its SHA-256 must match the `.sha256` sidecar asset published alongside it — a truncated, corrupt or tampered download is discarded, never installed), then atomically replaces `query-exe.jar`.
4. **Launch** — the client starts on the bundled runtime, and the launcher closes.

### The update rules

- **Every update is mandatory.** Once GitHub has a newer release, a failed download shows an error — the launcher never falls back to a known-stale client. Everyone runs the current version.
- **Offline is fine.** Only when GitHub itself is unreachable (you're offline, rate-limited, or GitHub is down) does an already-installed client launch as-is.
- **A corrupt download never replaces a working install.** The downloaded JAR must prove it can state its own version, and match the expected checksum, before it's moved into place.

## How the launcher updates itself

Everything above updates the *client*. But the launcher binary itself — the installer/AppImage you actually have on disk — has no equivalent "check on every start," because by the time there'd be anything to check, the launcher has already handed off to the client and exited. So **the client checks on the launcher's behalf**, once per start, and swaps it in the background:

```
┌──────────────┐  -Dlauncher.version=X   ┌──────────────┐  GET .../query-exe-launcher/releases/latest  ┌──────────────┐
│   Launcher   │ ──────────────────────► │ query-exe    │ ─────────────────────────────────────────────►│    GitHub    │
│  (this repo) │   forwarded at launch   │  client      │◄──────────────────────────────────────────────│              │
└──────────────┘                         └──────┬───────┘   { tag_name, assets: [...] }                └──────────────┘
                                                 │
                                                 │  stale (or version missing entirely)?
                                                 │  download, verify sha256, swap — in the background
                                                 ▼
                                   Windows: overwrite app/query-exe-launcher.jar
                                   Linux:   overwrite the .AppImage at $APPIMAGE
```

- **The launcher is self-describing**, the same way the client JAR is — `launcher.version` in `app.properties`, set from this repo's own GitHub release tag at build time. `UpdateManager` forwards it to the client as `-Dlauncher.version=...` when spawning it.
- **A missing version means "definitely outdated."** Launchers built before this existed simply don't pass the flag at all — the client treats that exactly like this repo's own `UpdateManager` treats a missing/unreachable client version: assume stale, update.
- **Windows and Linux need genuinely different artifacts, not just different natives.** On Windows, the launcher's own code is a discrete jar (`app/query-exe-launcher.jar`) inside an otherwise-untouched install directory, so the client just overwrites that one file — safe even while the client is running, since the launcher process that loaded it already exited right after spawning the client. On Linux there's no equivalent: an AppImage is one opaque, read-only-mounted unit, so "update the launcher" means replacing the *entire* `.AppImage` the client is running from — also safe while mounted, thanks to ordinary POSIX unlink-while-open semantics: the running instance keeps working off the old file until it next exits, the *path* just points somewhere new from then on.
- **No UI, no restart prompt.** The swap is entirely passive — the new version is picked up the next time the user launches through the (already-updated) launcher, whenever that is.
- **This is why the launcher has its own release pipeline** — `.github/workflows/release.yml` in *this* repo, separate from the client's, publishing `query-exe-launcher-windows.jar` and `query-exe-launcher-linux.AppImage` (plus `.sha256` sidecars) on every launcher release. It's deliberately decoupled from client release cadence, so a launcher-only fix reaches every installed user without waiting on the next client release.

## What's inside the installer / AppImage

Both packages are built the same way — a jpackage **app-image** wrapped in a platform-native shell:

```
QueryExe-Setup-<version>.exe  /  QueryExe-<version>-x86_64.AppImage
│
├─ QueryExe(.exe)                  the launcher itself
├─ app/query-exe-launcher.jar      launcher fat JAR
├─ app/query-exe-client-seed.jar   the client version current at build time
└─ runtime/                        private jlink runtime (Java 21 + JavaFX)
   └─ bin/QueryExe.exe             rebranded javaw.exe — the client's process
                                   (Windows; shows as "QueryExe" in Task Manager)
```

- **The client seed** makes the first start work offline: on first run, `BundledClientSeeder` copies it into the app-data directory, after which the normal GitHub-update flow owns the JAR. The installer/AppImage is named after the client version it seeds — but any old installer stays valid forever, because the launcher updates the client on first contact anyway.
- **The private runtime is shared** by launcher and client — the launcher starts the client with its own `java.home`'s `javaw`/`java`, so users never install or update Java.
- **Task Manager branding (Windows)** — the build rewrites a copy of the runtime's `javaw.exe` into `runtime/bin/QueryExe.exe` with [rcedit](https://github.com/electron/rcedit) (icon + version resources), and the launcher prefers that copy when launching the client.
- **AppImage mount guard (Linux)** — an AppImage's FUSE mount only lives as long as the process that started it, but the client runs off the mounted runtime. When the launcher detects it's running from an AppImage, a non-daemon `appimage-mount-keeper` thread keeps the (window-less) launcher process alive until the client exits, so the runtime never vanishes underneath it.

### First run

The launcher creates the app-data directory shared with the client — `%APPDATA%\QueryExe` on Windows, `~/.config/QueryExe` on Linux:

| Directory | Contents |
|---|---|
| `bin/` | `query-exe.jar` — the installed client, owned by the update flow |
| `logs/` | Launcher logs (`launcher.log`) and the client's own captured stdout/stderr (`client-stdout.log`) |

## Building from source (developers)

Requirements: **Java 21** and Maven.

```bash
# Dev run — checks GitHub, downloads/updates the client jar, launches it
mvn javafx:run

# Scripted visual demo — walks every phase with fake progress, no network or jar needed
mvn javafx:run -Dlauncher.demo=true

# Fat jar with Windows + Linux JavaFX natives bundled
mvn clean package -Ppackage
```

There's no hub URL to bake in and no `prod` profile — the launcher always talks to the same public GitHub repos (`github.owner` / `github.repo.client` in `app.properties`).

### Windows installer

```bash
mvn clean package -Pinstaller -Dclient.version=<version>
# → target/installer/QueryExe-Setup-<version>.exe
```

Needs the [query-exe](https://github.com/B077AS/query-exe) client fat JAR built in the repo root (`mvn clean package -Ppackage`) and **Inno Setup 6** (`winget install JRSoftware.InnoSetup`; override the compiler location with `-Discc.path=...`). The build runs three steps: jpackage assembles the app-image with the private runtime, a PowerShell step rebrands the client's `javaw.exe` copy with rcedit (auto-downloaded on first use; override with `-Drcedit.path=...`), and Inno Setup wraps it all in a branded wizard — welcome art, install directory, desktop icon task, launch-on-finish.

### Linux AppImage

```bash
mvn clean package -Pappimage -Dclient.version=<version>
# → target/appimage/QueryExe-<version>-x86_64.AppImage
```

Same client-JAR prerequisite, plus [appimagetool](https://github.com/AppImage/appimagetool) (default `~/.local/bin/appimagetool`; override with `-Dappimagetool.path=...`). `packaging/linux/make-appimage.sh` turns the jpackage app-image into an AppDir (AppRun, `.desktop`, icon) and runs appimagetool over it.

Both profiles accept an explicit jar path instead of resolving one from `client.version`:

```bash
mvn clean package -Pinstaller -Dclient.jar=C:\path\query-exe.jar
```

### Self-update artifacts

Two more profiles exist purely for [the launcher's own self-update pipeline](#how-the-launcher-updates-itself) — they carry only the platform-specific JavaFX natives, none of the installer/AppImage packaging steps, and just produce the plain fat jar:

```bash
mvn clean package -Pwin-natives    # target/query-exe-launcher.jar, Windows natives
mvn clean package -Plinux-natives  # target/query-exe-launcher.jar, Linux natives
```

`.github/workflows/release.yml` in this repo uses these to build `query-exe-launcher-windows.jar` directly, and (via the `appimage` profile, seeded with whatever the latest published query-exe client jar happens to be) `query-exe-launcher-linux.AppImage`.

## Under the hood

| Class | Responsibility |
|---|---|
| `Launcher` | Entry point — resolves app-data dirs, wires logging, hands off to the UI |
| `LauncherApp` | The frameless JavaFX window: phases, progress bar, animations; closes on ✕ or Esc |
| `LauncherConfig` | Reads `app.properties` (`github.owner`, `github.repo.client`, `launcher.version`) |
| `update/UpdateManager` | The whole check → download → verify → install → launch sequence on a worker thread, driven entirely by GitHub's Releases API |
| `update/BundledClientSeeder` | First-run copy of the bundled client seed into the app-data dir |
| `update/GitHubRelease` / `GitHubAsset` | Small Gson models for the GitHub Releases API response |
| `update/Phase` | The six status phrases (`CHECKING`, `DOWNLOADING`, `INSTALLING`, `STARTING`, `UP_TO_DATE`, `DONE`) |

`icon.png` is copied from the client, and `launcher.css` re-declares the client's own `-color-*` tokens — the launcher is deliberately a visual extension of the client, not a separate-looking app.

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 21 |
| UI | JavaFX 22, Ikonli (Feather icons) |
| Networking | Java `HttpClient` (GitHub Releases API + JAR streaming) |
| System integration | JNA / JNA Platform (OS detection, Windows specifics) |
| Packaging | jpackage app-image + private jlink runtime, Inno Setup 6 (Windows wizard), appimagetool (Linux), rcedit (exe rebranding) |
| Build | Maven — `javafx-maven-plugin` for dev, `maven-shade-plugin` for the fat JAR, profiles for each package format |
| Misc | Gson, Lombok, Logback |

## Related repositories

| Repo | What it is |
|---|---|
| [query-exe](https://github.com/B077AS/query-exe) (repo root) | Desktop client (JavaFX, Windows & Linux) |
| launcher/ | This folder — auto-updating launcher, Windows installer & Linux AppImage |

## FAQ

**Why a launcher instead of a normal installer?** So you're always running the current client without ever clicking "download update." Install once; every start after that is automatically up to date.

**Does the launcher phone home anywhere?** No. It makes requests only to `api.github.com`, for the latest release of `query-exe` and (from inside the client) `query-exe-launcher` — both public repos, both read-only.

**Do I need Java installed?** No. The installer and AppImage bundle a private jlink runtime that both the launcher and the client run on.

**What happens if an update download fails mid-way?** Nothing bad — the download goes to a temporary file and is verified before it replaces anything. Your working install is only ever replaced by a JAR that proved it's intact. If GitHub has announced a new release, though, the update is mandatory: the launcher shows an error rather than starting an outdated client.

**How does the launcher itself get updated?** See [How the launcher updates itself](#how-the-launcher-updates-itself) — in short, the *client* checks on the launcher's behalf once per start and swaps it in the background, since the launcher process itself is already gone by the time the client is running.

## License

This project is licensed under the [MIT License](LICENSE).
