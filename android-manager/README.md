# Esnexius Site Manager for Android

A lightweight native Android controller for the Termux-hosted EsnexiusStore website.

## Buttons

- **INSTALL** — installs Node.js/Git/unzip/curl in Termux, clones this repository, deploys the website, and runs `npm install`.
- **UPDATE** — backs up the current website, pulls `main`, redeploys website files, preserves `.env` and `data/`, then refreshes npm dependencies.
- **UPLOAD ZIP** — lets you pick an EsnexiusStore ZIP from Android storage, copies it to Downloads, validates basic ZIP paths, backs up the current install, and deploys it.
- **BACKUP** — writes a compressed backup to `Downloads/EsnexiusStoreBackups` when Termux storage access is available; otherwise it uses `~/esnexiusstore-backups`.
- **START / STOP** — manages the Node server with a PID file and persistent log under `.manager/`.
- **REFRESH STATUS** — checks Termux, Node, install state, process state, local HTTP response, source cache, and shared storage.
- **VIEW LOG** — shows the last 100 lines of the server log.
- **OPEN WEBSITE** — opens `http://127.0.0.1:3000`.
- **TERMUX SETUP** — copies the one-time `allow-external-apps=true` + `termux-setup-storage` command and opens Termux.

## Termux requirement

Termux requires `allow-external-apps=true` in `~/.termux/termux.properties` before a third-party app can use the RUN_COMMAND service. Android also requires the user to grant this app Termux's `com.termux.permission.RUN_COMMAND` permission.

## Build

GitHub Actions builds the debug APK automatically after changes under `android-manager/` are pushed to `main`. The workflow also supports manual `workflow_dispatch` runs.
