# Backup and UI integration

The local scheduler is independent of Drive authentication and uses unique WorkManager periodic work. Backup settings belong under **Ustawienia → Kopia zapasowa i synchronizacja**. The UI should reuse the shared theme tokens and should surface actionable errors only on Home or the widget.

## Local worker contract

- Preferences: `workmeter_local_backup`; keys `enabled` (default false), `interval_hours` (1–168, default 24), and `retention` (1–30, default 7).
- After changing a preference, call `LocalBackupWorker.schedule(context)` to update or cancel the unique job. Existing startup calls to `DriveBackupWorker.schedule` also reconcile the local schedule, including when Drive is disconnected.
- `last_success` is an ISO instant, `last_file` is the private ZIP basename, and `error` is the latest Polish failure message. A failure preserves the last successful result. The UI must check that the corresponding file still exists before displaying it as available.
- ZIP files are under `filesDir/local-backups`, with per-entry SHA-256 and a dated name. A complete archive includes the journal and referenced photo bytes. A missing attachment prevents publication and pruning. Copies in this directory can be lost when the app is uninstalled.
- The configuration controls, local ZIP picker and preview/restore screens still require integration by the UI owner. Never replace existing work or finance records without preview, consent and a recovery checkpoint.

## Initial readiness audit

| Capability | State | Evidence |
|---|---|---|
| ZIP format and preflight | Library implemented | `WorkMeterArchiveTest`, `ArchivePreflightTest` |
| Journal/attachment export and verification | Library implemented, not exposed by app settings | `JournalSnapshotTest`, `PhotoArchivePackageTest` |
| Local scheduled ZIP | Store and worker implemented, UI opt-in pending | `LocalBackupStoreTest`; scheduling on device pending |
| Safe restore | Plan, staging and recovery components implemented; full user flow pending | `RestorePlanTest`, `PhotoRestoreStageTest` |
| Google Drive | Existing JSON upload and authorization; ZIP listing/import pending | `DriveBackupWorker`; real account verification pending |
| CSV / PDF | Export components present; integration and device validation pending | `WorkMeterCsvTest`, `BackupOutputTest` |
| UI / themes | Integration contract only | Three-theme and accessibility QA pending |
