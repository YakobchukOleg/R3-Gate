package com.r3gate.app.backup

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.r3gate.app.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/** Real navigation/SAF/Google host. Tokens and callbacks never enter saved state. */
class BackupSettingsActivity : Activity(), BackupSettingsController.Host {
    private lateinit var controller: BackupSettingsController
    private val io = Executors.newSingleThreadExecutor()
    private var documentResult: ((Uri?) -> Unit)? = null
    private var authorizationResult: ((String?) -> Unit)? = null
    private var selectionDialog: AlertDialog? = null
    private var stopped = false
    private var interruptedPicker = false
    private val prefs by lazy { getSharedPreferences("r3_gate", MODE_PRIVATE) }
    private val theme get() = WorkMeterAppearance.theme(this)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        interruptedPicker = savedInstanceState?.getBoolean("externalPending") == true
        val palette = theme.palette
        window.statusBarColor = palette.header
        window.navigationBarColor = palette.background
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }
        frame.addView(TextView(this).apply {
            text = "Ustawienia"
            textSize = 23f
            setTextColor(palette.onHeader)
            setBackgroundColor(palette.header)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            typeface = resources.getFont(com.r3gate.app.R.font.clash_grotesk)
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        }, LinearLayout.LayoutParams(-1, -2))
        frame.addView(workMeterButton(this, "← Wstecz", WorkMeterButtonRole.SECONDARY) { finish() },
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(8), dp(16), 0) })
        controller = BackupSettingsController(this, object : BackupSettingsPanel.Appearance {
            override fun surface(view: View) { view.setBackgroundColor(palette.background) }
            override fun text(view: TextView) { view.setTextColor(palette.textPrimary) }
            override fun button(view: Button, primary: Boolean) {
                styleWorkMeterButton(view, if (primary) WorkMeterButtonRole.PRIMARY else WorkMeterButtonRole.SECONDARY)
            }
            override fun input(view: View) {
                view.background = GradientDrawable().apply {
                    setColor(palette.surface); cornerRadius = dp(12).toFloat(); setStroke(dp(1), palette.border)
                }
                if (view is android.widget.EditText) view.setHintTextColor(palette.secondaryText)
            }
        }, this)
        frame.addView(controller.view, LinearLayout.LayoutParams(-1, 0, 1f))
        // Edge-to-edge enforcement on Android 15 also applies to this settings destination.
        frame.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            } else view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(frame)
        frame.requestApplyInsets()
        controller.refresh(if (interruptedPicker) "Wybór pliku lub konta został przerwany. Ponów operację." else null)
    }

    override fun onResume() {
        super.onResume()
        if (::controller.isInitialized) controller.refresh()
    }

    override fun chooseDocument(intent: Intent, selected: (Uri?) -> Unit) {
        check(documentResult == null)
        documentResult = selected
        try { startActivityForResult(intent, DOCUMENT) }
        catch (_: android.content.ActivityNotFoundException) { takeDocument(null) }
        catch (_: SecurityException) { takeDocument(null) }
    }

    private fun takeDocument(uri: Uri?) {
        val callback = documentResult
        documentResult = null
        callback?.invoke(uri)
    }

    override fun authorizeDrive(authorized: (String?) -> Unit) {
        check(authorizationResult == null)
        authorizationResult = authorized
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if (!stopped && authorizationResult != null) {
                    if (result.hasResolution()) {
                        try { startIntentSenderForResult(requireNotNull(result.pendingIntent).intentSender,
                            AUTHORIZE, null, 0, 0, 0) }
                        catch (_: Exception) { takeAuthorization(null) }
                    } else acceptAuthorization(result)
                }
            }.addOnFailureListener { if (!stopped) takeAuthorization(null) }
    }

    private fun acceptAuthorization(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank() || DRIVE_SCOPE !in result.grantedScopes) {
            takeAuthorization(null); return
        }
        io.execute {
            val email = runCatching {
                DriveStore().json("GET", "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", token)
                    .optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotBlank() }
            }.getOrNull()
            runOnUiThread {
                if (!stopped && authorizationResult != null) {
                    if (email == null) takeAuthorization(null) else {
                        prefs.edit().putBoolean("driveConnected", true).putString("driveEmail", email).apply()
                        DriveBackupWorker.schedule(this)
                        takeAuthorization(token)
                    }
                }
            }
        }
    }

    private fun takeAuthorization(token: String?) {
        val callback = authorizationResult
        authorizationResult = null
        callback?.invoke(token)
    }

    override fun chooseDriveCopy(copies: List<DriveBackups.Copy>, selected: (DriveBackups.Copy?) -> Unit) {
        val date = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault())
        var decided = false
        fun deliver(copy: DriveBackups.Copy?) { if (!decided) { decided = true; selected(copy) } }
        selectionDialog = AlertDialog.Builder(this, WorkMeterAppearance.dialogStyle(theme))
            .setTitle("Wybierz kopię z Google Drive")
            .setItems(copies.map { "${date.format(it.modifiedAt)} · ${it.name}" }.toTypedArray()) { _, index -> deliver(copies[index]) }
            .setNegativeButton("Anuluj") { _, _ -> deliver(null) }
            .setOnCancelListener { deliver(null) }
            .create().also { it.show() }
    }

    override fun restored() {
        // MainActivity reads the authoritative preferences on return; no cached journal is reused.
        GateWidget.refresh(this)
        CoreGpsStageBridge.refresh(this)
    }

    override fun beforeRestore() {
        if (GpsWorkdaySession.workdayOpen(prefs))
            throw BackupRestoreBlocked("Zamknij bieżący dzień pracy przed przywróceniem kopii.")
        val jobs = androidx.work.WorkManager.getInstance(this).getWorkInfosByTag("driveUpload")
            .get(5, java.util.concurrent.TimeUnit.SECONDS)
        if (jobs.any { !it.state.isFinished })
            throw BackupRestoreBlocked("Poczekaj na zakończenie wysyłania zdjęć przed przywróceniem kopii.")
    }

    @Deprecated("Legacy activity result host")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (stopped) return
        when (requestCode) {
            DOCUMENT -> takeDocument(if (resultCode == RESULT_OK) data?.data else null)
            AUTHORIZE -> if (resultCode != RESULT_OK || data == null) takeAuthorization(null) else {
                try { acceptAuthorization(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)) }
                catch (_: Exception) { takeAuthorization(null) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("externalPending", documentResult != null || authorizationResult != null)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        stopped = true
        if (::controller.isInitialized) controller.close()
        documentResult = null
        authorizationResult = null
        selectionDialog?.dismiss()
        io.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val DOCUMENT = 401
        const val AUTHORIZE = 402
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
