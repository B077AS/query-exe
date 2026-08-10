# QueryExe

<p align="center">
  <b>A free, cross-platform desktop client for working with relational databases.</b><br>
  MySQL · MariaDB · PostgreSQL · SQL Server · H2 · SQLite · Multi-connection · SQL editor with autocomplete
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white">
  <img alt="JavaFX 22" src="https://img.shields.io/badge/JavaFX-22-1B6AC6">
  <img alt="Windows | Linux" src="https://img.shields.io/badge/Windows%20%7C%20Linux-4CAF50">
  <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue">
</p>

---

## What is QueryExe?

QueryExe is a modern database editor built with JavaFX: connect to several database engines at once, browse schemas, write SQL with syntax highlighting and autocomplete, edit result sets directly in the grid, and export what you find. No server, no account, no telemetry — it's a local desktop tool.

The project has two pieces — you only need to think about the second one if you're building from source:

| Piece | Role |
|---|---|
| **query-exe** (this repo) | The desktop client itself — connections, the SQL editor, schema browsing, result grids |
| [query-exe-launcher](https://github.com/B077AS/query-exe-launcher) | Auto-updating launcher — Windows installer & Linux AppImage, keeps the client (and itself) up to date |

> 📥 **Just want to use QueryExe?** Grab the launcher from [query-exe-launcher's releases](https://github.com/B077AS/query-exe-launcher/releases) — a Windows installer or Linux AppImage that installs the client and keeps it current automatically, every time you start it. You never need to build anything yourself.

## Features

**Multi-database support** — connect to and manage multiple database systems simultaneously: MySQL, MariaDB, PostgreSQL, SQL Server, H2 (embedded) and SQLite (serverless).

**Connection management** — manage several connections at once, monitor active connections, and configure custom JDBC drivers per connection when the bundled ones don't fit.

**Query execution** — an advanced SQL editor with syntax highlighting and intelligent autocomplete, running multiple queries concurrently, with saved queries for reuse.

**Schema management** — browse databases, schemas, tables and columns; inspect detailed table/column properties; create, alter and drop tables, databases and schemas.

**Data manipulation** — edit result sets directly in the grid, auto-generate DDL and INSERT scripts from data, and export results as CSV, JSON or XML.

**A fast, modern interface** — JavaFX with an AtlantaFX theme, syntax-highlighted SQL, and cross-platform behavior on Windows and Linux.

## Supported databases

| Database | Pre-installed driver version |
|---|---|
| MySQL | 9.5.0 |
| MariaDB | 3.5.6 |
| PostgreSQL | 42.7.8 |
| SQL Server | 13.2.1.jre11 |
| H2 | 2.4.240 |
| SQLite | 3.51.1.0 |

Every JDBC driver can be swapped out for a different version or a custom build from within the app.

## Usage

**Creating a connection** — click **New Connection**, pick a database type, enter host/port/database/credentials, test it, and save.

**Running queries** — open a query tab, write SQL with syntax highlighting and autocomplete, run it with `Ctrl+Enter` or the Run button, then view, edit or export the results.

**Managing tables** — right-click any table for create/alter/drop operations and to inspect its properties and statistics.

**Exporting data** — run a query, click **Export**, pick CSV/JSON/XML, choose a destination.

## Getting the app (users)

Download the **launcher** from [query-exe-launcher's releases page](https://github.com/B077AS/query-exe-launcher/releases) — a Windows installer or Linux AppImage. It installs the client into your app data directory and updates it automatically on every start, so install once and forget about it.

## Keeping the launcher up to date

There's no hub or server behind QueryExe — the launcher and client each query **GitHub Releases directly** for updates, using the release tag as the version. The client's `update/LauncherUpdateService` does something a little unusual: once per start, it checks whether the [query-exe-launcher](https://github.com/B077AS/query-exe-launcher) that started it is out of date, and if so, downloads and swaps its files in the background. It has to be the client that does this — by the time the client is running, the launcher process has already exited, so there's nothing left to check on its own behalf.

- It reads `System.getProperty("launcher.version")` — forwarded by the launcher when it spawns the client — and compares it against the launcher repo's latest GitHub release tag. A missing value (an old launcher, from before this existed) is always treated as outdated.
- Every downloaded file is verified against a `.sha256` sidecar published alongside it in the same release, before it replaces anything.
- On Windows it overwrites `app/query-exe-launcher.jar` next to the running install; on Linux it overwrites the `.AppImage` at `$APPIMAGE` (an AppImage is one opaque unit — there's no "just the launcher part" to update). Both are safe to replace while in use, since the launcher process that loaded them has already exited.
- It's entirely best-effort and silent: any failure is logged and swallowed, since a failed launcher self-update must never interfere with the client actually running. No UI, no restart prompt.

See [query-exe-launcher's README](https://github.com/B077AS/query-exe-launcher#how-the-launcher-updates-itself) for the full mechanics.

## Building from source (developers)

Requirements: **Java 21** and Maven.

```bash
git clone https://github.com/B077AS/query-exe
cd query-exe

# Run in development
mvn javafx:run

# Build a fat JAR for distribution (bundles Windows + Linux JavaFX natives)
mvn clean package -Ppackage
```

There's no hub URL to bake in and no `prod` profile — the client is entirely self-contained, so the only build variant is `-Ppackage`. The compiled JAR lands in `target/`.

## Under the hood

| Package | Responsibility |
|---|---|
| `components/` | UI widgets: the SQL editor, home/connections pane, menu & toolbar, modals, result grid, tree view |
| `model/` | Connection definitions per database type, result/column/query data, JDBC driver loading |
| `service/` | Connection lifecycle, query execution, app settings, recent files, export |
| `theme/` | AtlantaFX theme wiring and light/dark switching |
| `update/` | `LauncherUpdateService` — checks and self-updates the launcher that started this client (see above) |
| `utils/` | Icon coloring, Windows theme integration, tab-scroll chevrons, misc helpers |

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 21 |
| UI | JavaFX 22, [AtlantaFX](https://github.com/mkpaz/atlantafx) theme, Ikonli icon packs, RichTextFX |
| Database connectivity | JDBC drivers for MySQL, MariaDB, PostgreSQL, SQL Server, H2, SQLite |
| Networking | Java `HttpClient` (GitHub Releases API for the launcher-update check), OkHttp |
| Credentials | Windows Credential Manager via `credential-secure-storage` |
| System integration | JNA / JNA Platform |
| Serialization | Gson |
| Build | Maven — `javafx-maven-plugin` for dev, `maven-shade-plugin` for the distributable fat JAR |
| Misc | Logback, Lombok |

## Related repositories

| Repo | What it is |
|---|---|
| query-exe | This repo — desktop client (JavaFX, Windows & Linux) |
| [query-exe-launcher](https://github.com/B077AS/query-exe-launcher) | Auto-updating launcher — Windows installer & Linux AppImage |

## Screenshots

### Overall Design
![Overall Design](https://github.com/user-attachments/assets/d5023cd9-6c53-406b-a9a8-8925b4841dfe)

### Table Options
![Table Options](https://github.com/user-attachments/assets/c1cb5c79-cda0-4a85-b86b-59ba081c9df0)

### Connection Creation
![Connection Creation](https://github.com/user-attachments/assets/3ec617ef-c397-4b1c-8a8c-abf1e2581b81)

### Editor Features
![Editor Features](https://github.com/user-attachments/assets/5f03350a-4edd-4d49-9d27-ec8a302f4f33)

## Roadmap

- [ ] ER diagram generation
- [ ] Theme selection
- [ ] Query performance analysis
- [ ] Database backup and restore

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## FAQ

**Is QueryExe free?** Yes — the client and the launcher are both free, MIT-licensed, no ads, no tracking, no paid tiers.

**Do I need a server or an account?** No. QueryExe is a local desktop tool — it only talks to the databases you point it at, and to GitHub to check for updates.

**Why a launcher instead of a plain installer?** So you're always running the current version without manually checking for updates — install once, every start after that is automatically up to date.

**How do updates work?** The launcher checks GitHub for a new client release on every start and swaps in the new JAR automatically. The client returns the favor: it checks whether the *launcher* itself is out of date and self-updates it in the background too (see [Keeping the launcher up to date](#keeping-the-launcher-up-to-date)).

## Acknowledgments

- Built with [JavaFX](https://openjfx.io/)
- UI powered by [AtlantaFX](https://github.com/mkpaz/atlantafx)
- Icons from [Ikonli](https://kordamp.org/ikonli/)
- SQL editing with [RichTextFX](https://github.com/FXMisc/RichTextFX)

## License

This project is licensed under the [MIT License](LICENSE).

---

<p align="center">
<b>Made with Java</b><br>
If you find QueryExe useful, consider giving it a ⭐!
</p>
