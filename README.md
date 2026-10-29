# WorkMeter 2.1

Personal Android work-time, object, materials, photos, notes and payment ledger. The existing applicationId com.r3gate.app and preferences r3_gate stay unchanged so an APK with the same signing certificate updates existing installations. The display name is WorkMeter.

## Interface

White screens, slate #37474F headers and primary controls, coral #FF5C5C accents and light-gray secondary fields follow the supplied reference. Header and settings gear stay outside the scrollable content. Ordinary controls and tiles have roughly half the previous height; long text still wraps. System bars remain black.

Home contains work controls, photos, materials, notes, objects and work-time history. Settings contains wallet (PIN), SMS report generation, Google Disk, logs/export/import, widget appearance and About with the installed version. The report keeps two calendar fields Data od / Data do. Dates shown to users use dd-MM-yyyy; ISO dates are internal.

Filtering/sorting is collapsed under Filtry on photo/material/note lists and active/history notes. Object list and object details have no filters. A dot marks applied filters. Changing an option preserves the expanded panel and collapsing does not clear the selection.

## Work and records

Back follows menu hierarchy. Start/stop and object switching require a PIN. Object switches close the previous time segment. PRZEJAZD highlights while running; the arrival dialog offers an object, Zakupy without a store database, or a new object. Cancel keeps travel running, including while creating a new object. Whole shifts count as paid time; object summaries exclude travel/shopping. Objects use generated city-prefix tags and archival with a PIN. Notes allow optional object association, photo-only records, active/done status and a shopping/receipt checkbox. Materials support pieces or metres and camera/gallery photos.

## Wallet

Wallet access is PIN protected and locks when the app leaves the foreground. Rates are saved per shift; legacy shifts use the previous 30 PLN/hour assumption. The default rate applies to new shifts. Users can explicitly change the rates of closed shifts starting in the selected period. Active shifts enter the ledger after closing. Earnings use integer grosz after aggregated decimal time calculation.

Each payment has received date, allocated work period, amount, cash/card method and an optional note. The selected-period balance includes payments fully allocated inside that period. Broad overlapping payments trigger an incomplete-balance notice; money is never silently prorated. Global balance includes every closed shift, every payment and the optional opening balance. Positive means payable; negative means overpayment. This is manual record keeping, with no bank connection or money transfers.

## Widget

The widget targets 4x2 cells with a 160dp minimum height and 140dp resize minimum. Existing launcher placements may need manual resizing. The object is shown by tag; actions remain PIN protected. Widget background transparency is adjustable from 0 to 100 percent. It affects only the separate background image: text, timer and buttons remain opaque. App UI opacity is unchanged.

## Google Disk and signing

Google Identity requests only drive.file. Drive API and an Android OAuth client must match the actual package/certificate. Settings shows actionable authorization errors; package/SHA-1 diagnostics are collapsed. Photos use WorkMeter/object/date; existing WorkMeter, WT or R3 Gate roots are reused. Tokens stay in memory and are refreshed through authorization. Uploads use resumable requests and file mappings to avoid duplicate retries.

CI requires the stable signing secret, uses an explicit keystore path and verifies the produced APK with apksigner. Stable SHA-1 is FF:9F:79:03:07:93:D6:FA:4D:C3:5E:6E:0F:A6:DD:3C:75:1E:EA:93. The previous 1.x APK used a temporary SDK certificate (14:03:E0:A9:1D:7C:71:48:71:6C:DF:0F:0A:32:F5:75:3B:BB:E3:F3); its private key was not retained, so a direct update is impossible. Export/USB-backup existing data before uninstalling anything. No private signing key is committed or printed. These are personal test APKs, not Play Store releases.

## Backups and checks

PIN-protected JSON export includes work records, payments, rates, opening balance and widget transparency. Old JSON backups remain importable. Local photo bytes are not embedded: keep originals or upload them to Disk. Import validates payment periods and amounts before replacing data, and retains a pre-import data snapshot locally.

GitHub Actions runs domain/ledger unit tests, APK build/lint and Android 15 acceptance tests for navigation, forms, travel, PIN switching, filters, wallet partial payments, backup round-trip and background-only opacity. R3-Gate-check-results contains synthetic emulator screenshots; R3-Gate-APK contains app-debug.apk and signing reports.

## WorkMeter 2.1

All confirmation, PIN and selection dialogs share the light/slate/coral theme. Object pickers include a city search (Polish diacritics optional), a magnifier action and numeric tag sorting. Arrival selection retains Shopping and New object; cancel keeps travel active. Object labels are Czas na obiekcie, Materiały and Zdjęcia. ONLINE is green in the widget.

Tap a completed shift in Mój czas for its chronological object/travel/shopping intervals, their start/end timestamps and duration. Legacy missing intervals are explicitly marked; editing shift boundaries clips existing stage display, never invents history. Ring charts are deferred to a later version.

Photo transfer runs in a persistent WorkManager queue with network constraints, foreground dataSync notification and automatic retry/backoff. Swiping the activity away or locking the phone does not own/cancel the job. Android force-stop, revoked photo access, or missing Google consent can require user action; no service bypasses those restrictions. Each run obtains a fresh token from Google Identity; no tokens are persisted. An account-email check prevents writing a resumed job to another account. Per-file IDs and Drive appProperties avoid re-uploading completed files after interruption. Upload progress and completion notifications have Dodaj zdjęcia; Google Disk settings can enable a reusable shortcut notification. The system notification permission is requested on Android 13+. No personal Google photos are used in CI.
