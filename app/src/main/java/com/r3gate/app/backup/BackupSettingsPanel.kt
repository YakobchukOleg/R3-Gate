package com.r3gate.app.backup

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings content for the shared navigation host. Rendering performs no storage or network I/O.
 * The host supplies the app theme and handles actions, file picking and existing Drive authorization.
 */
class BackupSettingsPanel(
    context: Context,
    private val appearance: Appearance,
    private val onAction: (Action) -> Unit
) : ScrollView(context) {
    interface Appearance {
        fun surface(view: View)
        fun text(view: TextView)
        fun button(view: Button, primary: Boolean)
        fun input(view: View)
    }

    data class State(
        val status: BackupStatus.Snapshot?,
        val copies: List<LocalBackups.Copy> = emptyList(),
        val busy: Boolean = false,
        val message: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
        data object CreateLocal : Action
        data object ExportFile : Action
        data object ImportFile : Action
        data object ConnectDrive : Action
        data object CreateDrive : Action
        data object ImportDrive : Action
        data object PreviewRecovery : Action
        data class CancelOperation(val id: java.util.UUID) : Action
        data class RetryOperation(val id: java.util.UUID) : Action
        data class RestoreLocal(val name: String) : Action
        data class UpdateSchedule(val schedule: LocalBackups.Schedule) : Action
    }

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val spacing = dp(16)
        setPadding(spacing, spacing, spacing, spacing)
    }
    private val date = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm", Locale.forLanguageTag("pl"))
        .withZone(ZoneId.systemDefault())

    init {
        isFillViewport = true
        appearance.surface(this)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Call on the UI thread after loading the snapshot off the UI thread. */
    fun render(state: State) {
        val scrollPosition = scrollY
        content.removeAllViews()
        text("Kopia zapasowa i synchronizacja", heading = true)
        state.message?.takeIf(String::isNotBlank)?.let { text(it) }
        if (state.busy) text("Trwa wykonywanie operacji…")
        val status = state.status
        if (status == null) {
            if (state.busy || state.message == null) {
                text("Wczytywanie kopii zapasowych…")
            } else {
                button("Spróbuj ponownie", enabled = true) { onAction(Action.Refresh) }
            }
            return
        }

        val local = status.local
        text("Kopia zapasowa", heading = true)
        text(local.lastSuccess?.let { "Ostatnia kopia lokalna: ${date.format(it)}" }
            ?: "Brak zapisanej kopii lokalnej.")
        local.error?.let { text(it) }
        text("Kopie lokalne są przechowywane w aplikacji. Zapisz ZIP do pliku, aby zachować go po odinstalowaniu.")
        button("Utwórz kopię lokalną", !state.busy, primary = true) { onAction(Action.CreateLocal) }
        button("Eksportuj ZIP do pliku", !state.busy) { onAction(Action.ExportFile) }

        text("Automatyczne kopie", heading = true)
        schedule(local.schedule, state.busy)

        text("Google Drive", heading = true)
        text(if (status.driveConnected) "Google Drive jest połączony." else "Google Drive nie jest połączony.")
        status.lastVerifiedDriveCopy?.let { text("Ostatnia kopia w Google Drive: ${date.format(it)}") }
        status.driveProblem?.let { text("${it.state.label}. ${it.message}") }
        if (status.driveConnected) {
            if (status.driveProblem?.requiredAction == BackupStatus.RequiredAction.RECONNECT_DRIVE) {
                button("Połącz ponownie Google Drive", !state.busy) { onAction(Action.ConnectDrive) }
            }
            button("Utwórz kopię w Google Drive", !state.busy) { onAction(Action.CreateDrive) }
            button("Importuj z Google Drive", !state.busy) { onAction(Action.ImportDrive) }
        } else {
            button("Połącz Google Drive", !state.busy) { onAction(Action.ConnectDrive) }
        }

        text("Przywracanie", heading = true)
        text("Przed zastąpieniem danych zobaczysz zawartość kopii i potwierdzisz przywrócenie.")
        button("Importuj z pliku", !state.busy) { onAction(Action.ImportFile) }
        button("Sprawdź kopię sprzed przywrócenia", !state.busy) { onAction(Action.PreviewRecovery) }
        if (state.copies.isEmpty()) text("Brak kopii lokalnych do przywrócenia.")
        for (copy in state.copies) {
            button("Sprawdź kopię lokalną z ${date.format(copy.modifiedAt)}", !state.busy) {
                onAction(Action.RestoreLocal(copy.name))
            }
        }

        if (status.operations.isNotEmpty()) {
            text("Operacje kopii zapasowych", heading = true)
            for (operation in status.operations) {
                val destination = if (operation.destination == BackupStatus.Destination.DEVICE) "Telefon" else "Google Drive"
                text("$destination · ${operation.state.label}")
                if (operation.canCancel) {
                    button("Anuluj wysyłanie", !state.busy) { onAction(Action.CancelOperation(operation.id)) }
                }
                if (operation.canRetry) {
                    button("Ponów wysyłanie", !state.busy) { onAction(Action.RetryOperation(operation.id)) }
                }
            }
        }
        post { scrollTo(0, scrollPosition) }
    }

    /** Confirmation content. The host owns the prepared session and performs commit/discard off the UI thread. */
    fun preview(summary: RestoreSummary, confirmed: () -> Unit, cancelled: () -> Unit) {
        content.removeAllViews()
        text("Znaleziono kopię zapasową", heading = true)
        text("Obiekty: ${summary.objects} · archiwalne: ${summary.archivedObjects}")
        text("Zakończone dni pracy: ${summary.closedShifts}")
        text("Otwarty dzień pracy: " + if (summary.activeShift) "tak" else "nie")
        text("Zdjęcia: ${summary.photos} · materiały: ${summary.materials} · notatki: ${summary.notes}")
        text("Wpłaty: ${summary.payments} · wydatki: ${summary.expenses}")
        if (summary.referenceWarnings > 0) {
            text("Wykryto możliwe konflikty: ${summary.referenceWarnings}. Sprawdź dane przed przywróceniem.")
        }
        text(if (summary.replacesJournal) "Przywrócenie zastąpi bieżący dziennik wybraną kopią."
            else "Ta kopia zawiera bieżący dziennik.")
        var decided = false
        fun decide(action: () -> Unit) {
            if (decided) return
            decided = true
            for (index in 0 until content.childCount) {
                val child = content.getChildAt(index)
                if (child is Button) child.isEnabled = false
            }
            action()
        }
        button("Przywróć dane", enabled = true, primary = true) { decide(confirmed) }
        button("Anuluj", enabled = true) { decide(cancelled) }
        scrollTo(0, 0)
    }

    private fun schedule(current: LocalBackups.Schedule, busy: Boolean) {
        val enabled = Switch(context).apply {
            text = "Automatyczna kopia lokalna"
            isChecked = current.enabled
            isEnabled = !busy
            minHeight = dp(48)
            appearance.text(this)
            appearance.input(this)
        }
        content.addView(enabled)
        text("Częstotliwość")
        val hours = (listOf(1, 6, 12, 24, 48, 168) + current.intervalHours).distinct().sorted()
        val choices = hours.map { "Co $it godz." }
        val interval = Spinner(context).apply {
            adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, choices) {
                init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getView(position, convertView, parent).also { appearance.text(it as TextView) }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getDropDownView(position, convertView, parent).also { appearance.text(it as TextView) }
            }
            setSelection(hours.indexOf(current.intervalHours))
            isEnabled = !busy
            minimumHeight = dp(48)
            contentDescription = "Częstotliwość kopii lokalnej"
            appearance.input(this)
        }
        content.addView(interval, row())
        text("Liczba zachowanych kopii (1–30)")
        val retention = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.retention.toString())
            isEnabled = !busy
            minHeight = dp(48)
            contentDescription = "Liczba zachowanych kopii"
            appearance.text(this)
            appearance.input(this)
        }
        content.addView(retention, row())
        button("Zapisz harmonogram", !busy) {
            val count = retention.text.toString().toIntOrNull()
            if (count == null || count !in 1..30) {
                retention.error = "Wpisz liczbę od 1 do 30."
                retention.requestFocus()
            } else {
                onAction(Action.UpdateSchedule(LocalBackups.Schedule(enabled.isChecked,
                    hours[interval.selectedItemPosition], count)))
            }
        }
    }

    private fun text(value: String, heading: Boolean = false) {
        content.addView(TextView(context).apply {
            text = value
            textSize = if (heading) 20f else 16f
            val spacing = dp(if (heading) 12 else 6)
            setPadding(0, spacing, 0, dp(6))
            appearance.text(this)
            if (heading) androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        }, row())
    }

    private fun button(label: String, enabled: Boolean, primary: Boolean = false, clicked: () -> Unit) {
        content.addView(Button(context).apply {
            text = label
            isAllCaps = false
            minHeight = dp(48)
            isEnabled = enabled
            appearance.text(this)
            appearance.button(this, primary)
            setOnClickListener { clicked() }
        }, row())
    }

    private fun row() = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
