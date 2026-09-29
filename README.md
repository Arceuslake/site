# EsnexiusStore + Android Site Manager

This repository contains the EsnexiusStore phone-hosted website and a native Android manager under `android-manager/`.

The Android app controls the Termux-hosted site with buttons for Upload ZIP, Update, Backup, Install, Start, Stop, Refresh Status, View Log, Open Website, and one-time Termux setup.

## First use

1. Install Termux.
2. Install the APK produced by the **Build Android APK** GitHub Actions workflow.
3. Open the manager and grant **Run commands in Termux environment**.
4. Tap **TERMUX SETUP**, paste the copied command into Termux, press Enter, and approve storage access.
5. Return to the app and tap **INSTALL**.
6. Edit `~/esnexiusstore-phone-shop/.env` with your private settings, then tap **START**.

Update and Upload preserve `.env` and `data/`. Update creates a safety backup before deploying new website files.

Do not commit real admin passwords, PayMongo secret keys, webhook secrets, customer data, or your live `.env`.
