# R3 Gate 1.2

Personal Android work tracker, Polish interface, Android 8 or later.

## Updated workflows
- Android Back follows the menu tree; forms keep a local draft on Back. System bars have black backgrounds and safe insets, including cutouts and the keyboard.
- All record forms put date first (dd-MM-yyyy), then object. New dates default to the open shift's start date, otherwise today; calendar selection is available.
- Objects: generated city-prefix tags with persistent counters, city, address, organization, notes/contact, optional HTTPS Google Maps link. Archived objects keep all historical relationships and tags.
- Work starts on a chosen object. Switching objects closes the previous work segment. Travel/shopping remain paid time in SMS totals but are excluded from individual object hours. Object work is split by local calendar day, including midnight and daylight-saving transitions.
- Materials: object, date, name, photos, quantity in szt. or m; decimal metres, whole pieces; filters and totals keep units separate.
- Notes: optional object, text or photos or both; Actual/Done state; active list and complete history. Completing a note removes it from Active without deleting it.
- Photos: camera originals or Android document picker; grouped by object/date, filters and retries for Drive upload.
- Existing JSON data migrates in place. Ambiguous old object names are not guessed. Records without an old date show an unknown-date label until edited. Old tags remain unchanged. No work history is invented.

## Google Drive setup (required before end-to-end use)
The source implements Google Identity Services AuthorizationClient and Drive REST uploads with the drive.file scope. Access tokens stay in memory and are never put in JSON backups or logs. Each upload is initiated by the user. Uploads use resumable sessions; completed photos are remembered and retries query appProperties to avoid duplicates. Keep the app open while uploading. It is not a background upload service.

In your own Google Cloud project:
1. Enable Google Drive API.
2. Configure the OAuth consent screen for personal testing and add your Google account as a test user if needed.
3. Register an Android OAuth client for package com.r3gate.app and the SHA-1 fingerprint of the installed APK. Find the fingerprint in Google Drive settings in the app or in signing-report.txt in the APK artifact.
4. Use a stable signing key before registering the final OAuth client (see below). No client secret belongs in the Android source.
5. On the phone, tap GOOGLE DRIVE > Połącz Google Drive, select an account and approve the requested Drive access. Test a photo upload. Cloud setup and real account upload require device verification; passing CI does not verify them.

Folders: R3 Gate / TAG organization / dd-MM-yyyy; unassigned notes use Bez obiektu. No public sharing permissions are created. Disconnect revokes authorization. Local record deletion does not delete Drive files.

## Build and signing
GitHub Actions runs testDebugUnitTest, assembleDebug, lintDebug and signingReport. Download R3-Gate-APK from a successful run. Test and lint reports are in R3-Gate-check-results.

IMPORTANT: old builds generated a fresh debug key on each runner. A new APK can therefore fail to install over an earlier APK. Do not uninstall until exporting JSON via LOGI / KOPIA and verifying the backup. Preserve the old app until your migration is planned.

For repeatable personal-test updates, configure the repository Actions secret R3_DEBUG_KEYSTORE_BASE64 containing your base64-encoded standard Android debug keystore (alias androiddebugkey; store/key password android). This is an optional personal-test key, not a production release signing solution. The workflow restores it privately. Never commit a private keystore or secrets to this public repository. Without the secret, builds use an ephemeral debug key and OAuth registration can change between builds.

## Backup limitations
JSON exports include records and photo references, not photo bytes or persistent document access grants. Keep photo originals and uploaded Drive copies. Local camera photos and gallery access may be lost on uninstall or device transfer. Existing Drive folder URLs remain in exported records. PIN configuration is separate from JSON.

## Device acceptance checks
- Start/stop with PIN, switch object, travel/shopping, return from every submenu with system Back.
- Check black status bar and legible clock on a phone with a cutout, rotation and keyboard.
- Create two objects in the same city; archive one with PIN; check numbering and preserved history.
- Add 12.5 m and 2 pieces; filter inclusive dates; confirm separate totals in the object.
- Create a photo-only note without object; mark Actual, then Done; confirm Active/History lists.
- Cancel camera/gallery/Google authorization; confirm no record or photo is lost.
- Upload once, retry, disconnect/reconnect and verify Drive folders and no duplicate completed files.
- Export/import on test data only; inspect legacy dates and object links.

Known: photo thumbnails decode sampled images synchronously; large photo collections may need pagination. Shift time edits clip existing object segments to the revised shift boundaries; extensions outside recorded segments cannot reconstruct object assignment.


## Version 1.2: dashboard and trip flow

Dark navy screens, cyan action tiles and orange stop/travel controls follow the supplied visual reference. System bars stay black. All displayed dates, including SMS and widget dates, use dd-MM-yyyy; ISO dates remain internal for sorting.

The standalone shopping button is removed. During an open shift, PRZEJAZD starts a travel segment. Tapping the highlighted button again asks for the PIN and offers active objects, Zakupy (no shop records or tags), or creation of a new object. Cancelling either dialog preserves the running travel segment. Saving a new object returns home without ending travel; tap PRZEJAZD again to select it. Selecting a destination closes travel at that moment. The object switch button in both app and widget requires the PIN and closes the previous segment before starting the next object's time. Whole-shift paid time remains continuous.

Notes support the Zakupy / paragon checkbox with optional receipt photos and text, plus a shopping-only history filter. Completed shifts can be deleted from Mój raport using a PIN and confirmation; deleting a shift also removes its object-hour contributions, without deleting materials/photos/notes. Existing record deletion remains available in record details.

The widget shows the current object or travel/shopping status, an elapsed timer, and highlighted travel controls. Start/stop visibility depends on whether the day is open. Its minimum size is increased so controls remain readable. Existing launcher placements may need resizing after updating.

Android acceptance checks cover travel cancellation, creation of an object while travelling, arrival and shopping transitions, wrong/correct PINs for object switches, receipt filtering, shift deletion and displayed dates. Captured emulator screenshots are included in R3-Gate-check-results.
