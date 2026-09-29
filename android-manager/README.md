# Esnexius Site Manager for Android

A lightweight native Android controller for the Termux-hosted EsnexiusStore website. Version 2 focuses on safe deployment, recovery, monitoring, and Android 9 compatibility.

## Main features

- **INSTALL** — installs the required Termux packages and can deploy an already uploaded ZIP.
- **UPLOAD ZIP / SAFE UPDATE** — validates the ZIP, stages the new site away from production, installs dependencies, backs up the current site, activates the staged copy, and performs an HTTP health check.
- **Automatic rollback** — if the new deployment cannot start or pass its local HTTP health check, the previous installation is restored automatically.
- **BACKUP NOW** — creates a compressed backup while excluding node_modules and manager runtime files.
- **BACKUP MANAGER** — lists backups and lets you restore or delete them. Restore first creates another safety backup of the current site.
- **START / STOP / RESTART SERVER** — manages the Node server with a PID file and persistent log under .manager/.
- **AUTO START** — optionally starts the server after device/app startup when Termux is ready.
- **KEEP SERVER ALIVE** — optional foreground monitoring that checks the local server periodically and restarts it when needed.
- **Dashboard** — shows server/HTTP state, Node version, site version, PID, uptime, storage, backup count, free space, and dependency mode.
- **Persistent results** — command state/output survives leaving and reopening the app.
- **Notifications** — deployment/backup/restore/server operations report completion through Android notifications.
- **VIEW LOG** — displays the latest server log lines.
- **OPEN WEBSITE** — opens http://127.0.0.1:3000.
- **TERMUX SETUP** — copies the one-time allow-external-apps=true and storage setup command.

## Deployment safety

Uploaded ZIPs are rejected when they contain traversal paths, .git content, excessive file counts, a very large expanded payload, or detected symbolic links. A deployment must contain server.js and package.json.

If package-lock.json exists, the manager uses npm ci --omit=dev. Otherwise it falls back to npm install --omit=dev.

The staged tree is checked with Node syntax validation and npm ls before it can replace the live install.

## Termux requirement

Termux requires allow-external-apps=true in ~/.termux/termux.properties before a third-party app can use the RUN_COMMAND service. Android also requires the user to grant this app Termux's com.termux.permission.RUN_COMMAND permission.

## Build

GitHub Actions runs Android lint and builds the debug APK automatically for changes under android-manager/.

The workflow also supports an optional signed release APK when these repository secrets are configured:

- ESNEXIUS_KEYSTORE_B64
- ESNEXIUS_KEYSTORE_PASSWORD
- ESNEXIUS_KEY_ALIAS
- ESNEXIUS_KEY_PASSWORD

Artifacts include a SHA256SUMS.txt file for APK integrity verification.
