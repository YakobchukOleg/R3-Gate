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

`LocalBackups` is the entry point for UI integration. Use `status` and `copies` for the screen, `updateSchedule` for changes, and `export` for saving a verified local ZIP to a SAF document. `prepareRestore` and `prepareImport` return a session with a preview. Keep the session until explicit confirmation or cancellation and invoke its `commit` or `discard` accordingly. These operations perform disk I/O and should run off the UI thread. A dated export filename is available from `BackupFilePicker.suggestName`.

## Drive upload

The existing manual and daily workers now create a verified ZIP in private storage before
uploading. WorkManager waits for connectivity. Once a Drive ID is allocated, the worker
persists it with the account, destination and archive filename. Retries use those same
bytes and the same create-only ID. A conflict is accepted only after Drive confirms the
expected file metadata and transport checksum. The user-facing success status is written
after that check. Existing JSON backups are kept.

Quota exhaustion stops immediate retries. Rate limiting uses WorkManager backoff.
The next Drive backup run clears abandoned private queue directories older than 24 hours,
while preserving unfinished work and active writers. A resumable upload session is stored
privately with the pending operation. Retry queries the server offset before sending
more bytes. An expired session can be replaced using the same Drive file ID. Real-account
testing remains pending.

The settings screen can call `DriveBackups.copies` using the existing Drive authorization
token. The adapter lists accessible WorkMeter ZIP archives and checks the selected file
against Drive metadata again before downloading. `DriveBackups.prepareRestore` returns
the same preview session as local restore after checking the downloaded bytes and ZIP.
Run both operations off the UI thread and keep the token in memory. Legacy JSON files
remain in Drive. Settings integration and verification with a real account are pending.

## Manual local ZIP and restore summary

`LocalBackups.createNow` creates an offline copy even when the schedule is disabled.
`LocalBackups.exportCurrent` writes a freshly verified snapshot to the user's SAF
destination, while keeping the local copy under the configured retention policy.
Manual, scheduled and Drive backups use the same snapshot path.

Use `Session.summary` for counts of objects, archived objects, closed and active work,
verified photos, materials, notes, payments, expenses and reference warnings. It
indicates whether confirming would replace the current journal. Localize these counts
with the common UI resources. Confirming an identical archive releases its staging
directory without replacing the journal.

## Shared status

`BackupStatus.read` returns local schedule and last copy, Drive connectivity and
the verified ZIP timestamp for the connected account, plus WorkManager states.
Use `BackupState.label` for Polish labels and `needsAttention` for contextual
indicators. Present scheduled periodic work separately from a retry waiting
for connectivity. Older successful workers without a verified result are
excluded from the operation list. Read this snapshot off the UI thread.

## Recovery after restore

`JournalRestoreStore.previewRecovery` inspects the private checkpoint made before
the last replacement and checks that referenced local photos remain readable.
Present its summary and ask for explicit confirmation before calling `recover`.
Recovery rejects an edited journal or replaced checkpoint after preview. It saves
a separate rollback checkpoint before writing, leaving the original recovery copy
intact. This checkpoint contains journal data and local photo references, so it is
an on-device recovery option and requires those original files to remain available.

Drive browsing checks cancellation between result pages. Import checks the connected
account throughout the download and again before returning a restore preview. A
downloaded file is discarded after truncation, excess bytes, checksum mismatch,
revoked access or cancellation. Partial imports do not change the journal.

Import rejects unsupported explicit journal or backup versions, while retaining
unversioned and supported legacy journals. Active shift references and nested stages
are included in preflight. Duplicate explicit record IDs are shown as warnings and
preserved for review. The restore summary's warning count includes these conflicts,
so render it as general data warnings rather than only missing object references.

Schedule reconciliation now cancels an existing daily Drive job when Drive is
disconnected or daily backup is disabled. Local scheduling remains independent.
The scheduling APIs return WorkManager's operation so callers and Android tests can
wait for persistence without polling. Manual and periodic Drive backup workers share
one process-wide upload slot. An overlapping worker retries with its existing queue
entry instead of uploading concurrently.

`Snapshot.driveProblem` keeps the last unresolved Drive backup error independently
of a periodic task's current waiting state. It is scoped to the selected account,
survives reopening settings, and is cleared after a verified upload. Show its message
as an actionable problem. `driveDetail` remains the most recent operation detail.

## Settings panel for the shared UI host

`BackupSettingsPanel` renders local/cloud status, local schedule, ZIP export,
imports and existing copies. Its `Appearance` interface receives the shared theme's
surface, text, button and input styling. No backup-specific palette or theme setting
is created. Pass a themed context from the common screen.

The host loads `BackupStatus.read` and `LocalBackups.copies` off the UI thread,
then calls `render`. Handle each emitted `Action` with the existing adapters:
create/export through `LocalBackups`, schedule through `updateSchedule`,
cloud creation through `DriveBackupWorker.manual`, and imports through the
existing file picker/Drive authorization flow. A local selection emits a preview
request only. After preparing a restore session, call `preview` with its verified
summary. On explicit confirmation call `Session.commit` off the UI thread,
and on cancellation call `Session.discard`. Keep the session in the navigation
host and discard it on navigation away. Repeated confirmation is suppressed.
Connecting this panel to the common navigation remains a UI-owner task.

## Screen controller

`BackupSettingsController` owns `view`, runs backup work on a single worker
thread and handles local ZIP creation/export, schedule updates, import previews,
confirmed restoration and recovery. The host supplies existing document picking
and Drive authorization, cloud-copy selection, and a callback to reload Core data.
Host callbacks must return on the main thread. Pass the common appearance/context,
add `controller.view` to the settings destination, then call `refresh`.
Call `close` when leaving the screen. It discards a prepared restore session after
any current operation finishes. A restore already explicitly confirmed may finish.
Tokens remain in memory. Duplicate document/auth/selection callbacks are ignored.

## Manual queue cancellation and resume

The panel offers cancellation and retry only for eligible manual Drive jobs.
`BackupQueueActions` validates the selected WorkManager job before acting.
Cancellation retains the original archive and upload session. A retry resolves
the job to its persisted queue identity and verifies the connected account and
local archive. It resumes that operation with its original Drive ID and session,
rather than taking a new snapshot. An already completed or missing queue entry
cannot start another upload. Cloud files are not deleted by cancellation.

Manual requests now carry a queue-identity tag. Cleanup protects queue entries
referenced by unfinished retry jobs. Existing requests without that tag retain
their original WorkManager UUID as their queue identity. Before resuming, the
worker revalidates the stored ZIP to detect changes since its creation.

## Reconnecting Drive

Persisted problems include a required action. Revoked/missing authorization offers
`Połącz ponownie Google Drive` even when the account preference is still connected.
Storage quota retains its separate message. After the existing host returns a token,
the controller verifies the authorized Drive account off the UI thread before clearing
an access problem. This never advances the timestamp of the last verified ZIP.
Quota and file problems remain until addressed. Older records without an action remain
readable with a generic check-files action.
