# R3 Gate

Offline Android work-time tracker with a home-screen widget. Polish UI.

## Features
- PIN-protected start and stop of shifts
- ONLINE/OFFLINE widget with start, end and live duration
- Shift history and manual editing
- Earnings hidden behind **MÓJ RAPORT → ZAROBEK** and PIN
- Objects, materials, notes and Google Drive photo-folder links
- Local JSON event log; backup and restore using Android's document picker
- No server or external database

## Build APK (GitHub Actions)
1. Upload the project files to repository root (including `.github/workflows/android.yml`).
2. Open **Actions → Build R3 Gate APK → Run workflow**, or push to `main`.
3. When successful, open the run → **Artifacts → R3-Gate-APK**.
4. Download ZIP, extract `app-debug.apk`, and install on Android.

The debug APK is signed with the Android debug key and intended for personal testing. Do not put real PINs or private logs into this public repository. The PIN is set on the device.

Note: Android widget updates may be delayed by the launcher/battery restrictions. The Chronometer counts live while a shift is open. Back up your data before uninstalling or clearing app data.


## Updated work-time behavior
- Every minute from START to STOP is working time. Object, travel and shopping are subsegments; they do not subtract time.
- Active shift: switch object, start travel or shopping segment.
- Weekly SMS: Wednesday to Tuesday; Polish day names; copy text.
- Google Drive automatic upload/OAuth is **not implemented** in this source version. Links are supported.
- This is source code, **not a compiled or tested APK**. GitHub Actions builds a debug APK after upload.
