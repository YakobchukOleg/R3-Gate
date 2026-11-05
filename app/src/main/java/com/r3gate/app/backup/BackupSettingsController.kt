package com.r3gate.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.r3gate.app.DriveBackupWorker
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Owns backup work and restore sessions for one settings screen.
 * Construct, invoke and close on the main thread. File and network work runs on one worker thread.
 */
class BackupSettingsController(
    context: Context,
    appearance: BackupSettingsPanel.Appearance,
    host: Host
) : Closeable {
    interface Host {
        /** Launch the supplied SAF intent and return its result URI, or null on cancellation. */
        fun chooseDocument(intent: Intent, selected: (Uri?) -> Unit)
        /** Reuse existing Drive authorization. Return the access token in memory only. */
        fun authorizeDrive(authorized: (String?) -> Unit)
        fun chooseDriveCopy(copies: List<DriveBackups.Copy>, selected: (DriveBackups.Copy?) -> Unit)
        /** Reload the authoritative app data after a confirmed restore. */
        fun restored()
    }

    private val application = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var host: Host? = host
    @Volatile private var closed = false
    private var busy = false
    private var previewVisible = false
    private var state = BackupSettingsPanel.State(null)
    private var photos: PhotoRestoreCoordinator.Session? = null
    private var recovery: JournalRestoreStore.RecoveryPreview? = null

    val view = BackupSettingsPanel(context, appearance, ::handle)

    fun refresh(message: String? = null) {
        checkMain()
        if (closed || busy || previewVisible) return
        load(message)
    }

    private fun load(message: String?) {
        task({
            BackupSettingsPanel.State(BackupStatus.read(application), LocalBackups.copies(application),
                message = message)
        }) { loaded ->
            state = loaded
            view.render(state)
        }
    }

    private fun handle(action: BackupSettingsPanel.Action) {
        checkMain()
        if (closed || busy) return
        when (action) {
            BackupSettingsPanel.Action.Refresh -> load(null)
            is BackupSettingsPanel.Action.CancelOperation -> task({
                BackupQueueActions.cancel(application, action.id).result.get(5, TimeUnit.SECONDS)
            }) { load("Anulowano wysyłanie.") }
            is BackupSettingsPanel.Action.RetryOperation -> task({
                BackupQueueActions.retry(application, action.id).result.get(5, TimeUnit.SECONDS)
            }) { load(BackupState.WAITING_TO_SEND.label) }
            BackupSettingsPanel.Action.CreateLocal -> task({ LocalBackups.createNow(application) }) {
                load("Zapisano lokalnie.")
            }
            BackupSettingsPanel.Action.ExportFile -> document(
                BackupFilePicker.createIntent(BackupFilePicker.Kind.BACKUP,
                    BackupFilePicker.suggestName(BackupFilePicker.Kind.BACKUP))
            ) { uri -> task({ LocalBackups.exportCurrent(application, uri) }) { load("Zapisano plik ZIP.") } }
            BackupSettingsPanel.Action.ImportFile -> document(BackupFilePicker.openBackupIntent()) { uri ->
                prepare { LocalBackups.prepareImport(application, uri) }
            }
            BackupSettingsPanel.Action.ConnectDrive -> authorize { load(null) }
            BackupSettingsPanel.Action.CreateDrive -> task({ DriveBackupWorker.manual(application).result.get(5, TimeUnit.SECONDS) }) {
                load(BackupState.WAITING_TO_SEND.label)
            }
            BackupSettingsPanel.Action.ImportDrive -> authorize { token ->
                task({ DriveBackups.copies(application, token) }) { copies ->
                    if (copies.isEmpty()) {
                        load("Brak kopii ZIP w Google Drive.")
                    } else {
                        waitForHost()
                        var delivered = false
                        host?.chooseDriveCopy(copies) { copy ->
                            checkMain()
                            if (!closed && !delivered) {
                                delivered = true
                                busy = false
                                if (copy == null) load(null) else prepare {
                                    DriveBackups.prepareRestore(application, token, copy)
                                }
                            }
                        }
                    }
                }
            }
            BackupSettingsPanel.Action.PreviewRecovery -> task({
                discardPending()
                recovery = JournalRestoreStore.previewRecovery(application)
                recovery?.summary
            }) { summary ->
                if (summary == null) load("Brak kopii sprzed przywrócenia.") else showPreview(summary)
            }
            is BackupSettingsPanel.Action.RestoreLocal -> prepare {
                LocalBackups.prepareRestore(application, action.name)
            }
            is BackupSettingsPanel.Action.UpdateSchedule -> task({
                LocalBackups.updateSchedule(application, action.schedule).result.get(5, TimeUnit.SECONDS)
            }) { load("Zapisano harmonogram.") }
        }
    }

    private fun document(intent: Intent, chosen: (Uri) -> Unit) {
        waitForHost()
        var delivered = false
        host?.chooseDocument(intent) { uri ->
            checkMain()
            if (!closed && !delivered) {
                delivered = true
                busy = false
                if (uri == null) load(null) else chosen(uri)
            }
        }
    }

    private fun authorize(authorized: (String) -> Unit) {
        waitForHost()
        var delivered = false
        host?.authorizeDrive { token ->
            checkMain()
            if (!closed && !delivered) {
                delivered = true
                busy = false
                if (token.isNullOrBlank()) load("Nie połączono Google Drive.") else authorized(token)
            }
        }
    }

    private fun waitForHost() {
        busy = true
        view.render(state.copy(busy = true, message = null))
    }

    private fun prepare(create: () -> PhotoRestoreCoordinator.Session) {
        task({
            discardPending()
            photos = create()
            requireNotNull(photos).summary
        }, ::showPreview)
    }

    private fun showPreview(summary: RestoreSummary) {
        previewVisible = true
        view.preview(summary, confirmed = {
            if (!busy && !closed) {
                previewVisible = false
                task({
                    val session = photos
                    val previous = recovery
                    photos = null
                    recovery = null
                    if (session != null) {
                        try {
                            session.commit(application, confirmed = true)
                        } catch (failure: Exception) {
                            runCatching { session.discard() }
                            throw failure
                        }
                    } else {
                        JournalRestoreStore.recover(application, requireNotNull(previous), confirmed = true)
                    }
                }) {
                    host?.restored()
                    if (!closed) load("Przywrócono dane.")
                }
            }
        }, cancelled = {
            if (!busy && !closed) {
                previewVisible = false
                task({ discardPending() }) { load("Anulowano przywracanie.") }
            }
        })
    }

    private fun discardPending() {
        photos?.let { session ->
            photos = null
            session.discard()
        }
        recovery = null
    }

    private fun <T> task(work: () -> T, completed: (T) -> Unit) {
        if (closed) return
        waitForHost()
        executor.execute {
            val result = runCatching {
                check(!closed) { "Settings screen closed" }
                work()
            }
            main.post {
                if (!closed) {
                    busy = false
                    result.fold(completed) { failure ->
                        val message = when (failure) {
                            is SecurityException -> "Brak dostępu. Wybierz plik lub połącz ponownie Google Drive."
                            is IllegalArgumentException -> "Nie można użyć tej kopii. Sprawdź plik i jego wersję."
                            else -> "Nie udało się wykonać operacji. Sprawdź dostęp i spróbuj ponownie."
                        }
                        state = state.copy(busy = false, message = message)
                        view.render(state)
                    }
                }
            }
        }
    }

    override fun close() {
        checkMain()
        if (closed) return
        closed = true
        host = null
        executor.execute { discardPending() }
        executor.shutdown()
    }

    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper()) {
        "Backup settings must be controlled on the main thread"
    }
}
