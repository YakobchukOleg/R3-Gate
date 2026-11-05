package com.r3gate.app

import android.app.*
import android.location.LocationManager
import android.location.LocationListener
import android.location.Location
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.math.BigDecimal
import java.util.concurrent.Executors
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.*
import android.text.InputType
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.time.*
import java.time.format.DateTimeFormatter
import android.content.ClipData
import android.content.ClipboardManager

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val prefs by lazy { getSharedPreferences("r3_gate", MODE_PRIVATE) }
    private val fmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
    private val clock = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var walletUnlocked = false
    private var walletDraft = JSONObject()
    private val ticker = Handler(Looper.getMainLooper())
    private var screen = "home"
    private var currentSection = "Obiekty"
    private var objectFilters: ObjectFilterUiState.State? = null
    private var objectSearch = ""
    private val expandedFilters = mutableSetOf<String>()
    private var formKind = ""
    private var formReturn = "home"
    private var startAfterObjectSave = false
    private var startObjectSessionId = ""
    private var cancelStartAttempt: (() -> Unit)? = null
    private var draft = JSONObject()
    private var quickPhotoUpload=false
    private var cameraPath: String? = null
    private val io = Executors.newSingleThreadExecutor()
    private var backupController: com.r3gate.app.backup.BackupSettingsController? = null
    private var backupDocumentResult: ((Uri?) -> Unit)? = null
    private var backupDriveResult: ((String?) -> Unit)? = null
    private var driveToken: String? = null
    private var pendingUpload = ""
    private val uploading:Boolean get()=prefs.all.any { it.key.startsWith("uploadQueued_") && it.value==true }
    private var homeVisible = false
    private var smsParent="settings"
    private var smsFrom: LocalDate? = null
    private var smsTo: LocalDate? = null
    private val tick = object : Runnable { override fun run() { if (::root.isInitialized && homeVisible) home(); ticker.postDelayed(this, 60000) } }
    private val refreshUploads=Runnable {
        if(::root.isInitialized && (screen=="queue" || screen.startsWith("list:links:") || screen.startsWith("detail:links:"))) {
            val oldY=(root.parent as? ScrollView)?.scrollY ?: 0
            openScreen(screen)
            (root.parent as? ScrollView)?.let { scroll -> scroll.post { scroll.scrollTo(0,oldY) } }
        }
    }
    private val uploadChanges=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
        if(key=="data" || key?.startsWith("uploadError_")==true || key?.startsWith("uploadFailedPhotos_")==true) {
            ticker.removeCallbacks(refreshUploads);ticker.postDelayed(refreshUploads,150)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.setSystemBarsAppearance(0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        }
        migrate()
        PhotoQueue.preserveLegacyRequests(this)
        if (!GpsWorkdaySession.workdayOpen(prefs) &&
            (prefs.getBoolean("gps_enabled",false) || prefs.getBoolean("gps_resume_pending",false))) {
            if (GpsWorkdaySession.hasConsent(prefs)) prefs.edit().putBoolean(GpsWorkdaySession.CONSENT,true).apply()
            setGpsEnabled(false)
        }
        migrateWallet()
        DriveBackupWorker.schedule(this)
        expandedFilters.addAll(savedInstanceState?.getStringArrayList("expandedFilters") ?: emptyList())
        currentSection=savedInstanceState?.getString("currentSection") ?: "Obiekty"
        savedInstanceState?.getBundle("objectFilters")?.let { filters ->
            objectFilters = ObjectFilterUiState.State(filters.getBoolean("archived"), filters.getBoolean("grouped", true), filters.getString("city"))
            objectSearch = filters.getString("query").orEmpty()
        }
        walletDraft = JSONObject(savedInstanceState?.getString("walletDraft") ?: "{}")
        quickPhotoUpload=savedInstanceState?.getBoolean("quickPhotoUpload") ?: false
        cameraPath = savedInstanceState?.getString("cameraPath")
        pendingUpload = savedInstanceState?.getString("pendingUpload") ?: ""
        if (savedInstanceState != null && prefs.contains("pin") &&
            intent?.getStringExtra("widget_action")==null &&
            intent?.getStringExtra(CoreGpsStageBridge.INTENT_EVENT)==null &&
            intent?.getBooleanExtra("gps_resume_alert",false)!=true) {
            smsParent = savedInstanceState.getString("smsParent") ?: "settings"
            smsFrom = savedInstanceState.getString("smsFrom")?.let { LocalDate.parse(it) }
            smsTo = savedInstanceState.getString("smsTo")?.let { LocalDate.parse(it) }
            detailParent = savedInstanceState.getString("detailParent") ?: "home"
            formKind = savedInstanceState.getString("formKind") ?: ""
            formReturn = savedInstanceState.getString("formReturn") ?: "home"
            startAfterObjectSave = savedInstanceState.getBoolean("startAfterObjectSave")
            startObjectSessionId=savedInstanceState.getString("startObjectSessionId").orEmpty()
            draft = JSONObject(savedInstanceState.getString("draft") ?: "{}")
            openScreen(savedInstanceState.getString("screen") ?: "home")
            val previousScroll=savedInstanceState.getInt("screenScrollY",0)
            (root.parent as? ScrollView)?.post {
                (root.parent as? ScrollView)?.scrollTo(0,previousScroll)
            }
            return
        }
        if (prefs.getString("pin", null) == null) setupPin() else handleLaunch()
    }
    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(uploadChanges)
        ticker.removeCallbacks(tick)
        ticker.postDelayed(tick,60000)
        CoreGpsStageBridge.refresh(this)
        // Refresh the GPS arrival affordance after returning from another app.
        if(::root.isInitialized && screen=="home") home()
    }
    override fun onPause() { prefs.unregisterOnSharedPreferenceChangeListener(uploadChanges); ticker.removeCallbacks(refreshUploads); ticker.removeCallbacks(tick); super.onPause() }
    override fun onStop() { cancelStartAttempt?.invoke(); walletUnlocked=false; super.onStop() }
    override fun onRestart() {
        super.onRestart()
        // Keep the existing view (including its scroll position) when returning
        // from another app. Only lock sensitive finance screens.
        if(GpsNavigationRestoreRules.lockOnRestart(screen)) settings()
    }
    private fun hash(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun setupPin() {
        val field = EditText(ContextThemeWrapper(this,dialogStyle)).apply { hint="PIN (4–8 cyfr)"; inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this,dialogStyle).setTitle("Ustaw PIN").setMessage("Kod chroni ustawienia finansowe.").setView(field).setCancelable(false).setPositiveButton("Zapisz") { _, _ ->
            val p=field.text.toString(); if (p.length in 4..8) {
                val salt=UUID.randomUUID().toString(); prefs.edit().putString("salt",salt).putString("pin",hash(salt+p)).apply(); home()
            } else { toast("PIN musi mieć 4–8 cyfr"); setupPin() }
        }.show()
    }
    private fun confirmPin(onAbort: () -> Unit = {}, action: () -> Unit) {
        val field=EditText(ContextThemeWrapper(this,dialogStyle)).apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint="PIN" }
        AlertDialog.Builder(this,dialogStyle).setTitle("Potwierdź PIN").setView(field)
            .setOnCancelListener { onAbort() }
            .setNegativeButton("Anuluj") { _, _ -> onAbort() }
            .setPositiveButton("Potwierdź") { _, _ ->
                if (hash(prefs.getString("salt","")+field.text.toString()) == prefs.getString("pin","")) action()
                else { onAbort(); toast("Nieprawidłowy PIN") }
            }.show()
    }
    private fun confirmAction(message:String="Wykonać tę czynność?",action:()->Unit) {
        AlertDialog.Builder(this,dialogStyle).setTitle("Potwierdzenie").setMessage(message)
            .setNegativeButton("Nie",null).setPositiveButton("Tak") { _,_ -> action() }.show()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handleLaunch()
    }
    private fun handleLaunch() {
        val action=intent?.getStringExtra("widget_action")
        intent?.removeExtra("widget_action")
        val gpsResumeAlert=intent?.getBooleanExtra("gps_resume_alert",false)==true
        intent?.removeExtra("gps_resume_alert")
        home()
        val gpsProposal=intent?.getStringExtra(CoreGpsStageBridge.INTENT_EVENT)
        intent?.removeExtra(CoreGpsStageBridge.INTENT_EVENT)
        if(!gpsProposal.isNullOrBlank()) { reviewGpsStageProposal(gpsProposal); return }
        if(gpsResumeAlert) { gpsScreen(); return }
        val uploadReference=intent?.getStringExtra("upload_retry")
        intent?.removeExtra("upload_retry")
        if(!uploadReference.isNullOrBlank()) { pendingUpload=uploadReference; authorizeDrive(); return }
        when(action) {
            "photos" -> newForm("links",sendAfterSave=true)
            "camera" -> {
                newForm("links")
                camera()
            }
            "start" -> startShift()
            "stop" -> confirmWorkday(false) { stopShift() }
            "switch" -> confirmAction("Zmienić obiekt?") { switchObject() }
            "travel" -> toggleTravel()
            "shopping" -> toggleShopping()
        }
    }
    private fun activeObjectIds(d: JSONObject): Set<String> =
        ObjectIntegrityRules.assignableIds(rows(d,"objects").map {
            ObjectIntegrityRules.ObjectRef(it.optString("id"),it.optBoolean("archived"))
        })

    /** Recovery entry point when a lock-screen notification was dismissed. */
    private fun pendingGpsStageEventId(d: JSONObject, nowMs: Long): String? {
        val running=active(d) ?: return null
        val saved=(CoreGpsProposalStore.read(AndroidCoreGpsProposalStorage(this)) as?
            CoreGpsProposalStore.ReadResult.Success)?.events ?: return null
        val activeIds=activeObjectIds(d)
        val shift=CoreGpsStageRules.Shift(true,running.optString("kind","Obiekt"),
            running.optString("objectId"))
        val stageStart=maxOf(running.optLong("start"),
            running.optLong("stageStart",running.optLong("start")))
        return CoreGpsStageReviewQueue.choose(saved,shift,activeIds,stageStart,nowMs)
            .event?.takeIf {
                CoreGpsStageBridge.hasContinuousCoverage(this,it.timestampMs,nowMs) &&
                    CoreGpsStageBridge.hasJournalEvidence(this,it)
            }
            ?.eventId
    }

    private fun reviewGpsStageProposal(eventId: String) {
        val storage=AndroidCoreGpsProposalStorage(this)
        val current=CoreGpsProposalStore.read(storage) as? CoreGpsProposalStore.ReadResult.Success ?: return
        val event=current.events.find { it.eventId==eventId &&
            it.decision==CoreGpsProposalInbox.Decision.PROVISIONAL } ?: return
        val d=data(); val running=active(d) ?: return
        val activeIds=activeObjectIds(d)
        val shift=CoreGpsStageRules.Shift(true,running.optString("kind","Obiekt"),running.optString("objectId"))
        val stageStart=maxOf(running.optLong("start"),running.optLong("stageStart",running.optLong("start")))
        if(!CoreGpsStageReviewQueue.isCurrent(eventId,current.events,shift,activeIds,
                stageStart,System.currentTimeMillis()) ||
            (!CoreGpsStageBridge.hasContinuousCoverage(this,event.timestampMs,System.currentTimeMillis()) ||
                !CoreGpsStageBridge.hasJournalEvidence(this,event))) return
        val obj=rows(d,"objects").find { it.optString("id")==event.objectId } ?: return
        val stage=CoreGpsStageRules.proposal(event.kind,event.objectId,shift,activeIds) ?: return
        val arriving = stage == CoreGpsStageRules.Proposal.ARRIVE_OBJECT
        val shiftId = running.optString("id")
        var destinationId = if (arriving) event.objectId else null
        var destinationName = objectName(obj)
        var correctedTime: Long? = null
        val zone = ZoneId.systemDefault()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val time = Button(this)
        fun updateTime() {
            time.text = correctedTime?.let { "Godzina zmiany: " + fmt.format(Date(it)) }
                ?: "Godzina zmiany: chwila potwierdzenia"
        }
        updateTime()
        time.setOnClickListener {
            val selected = Instant.ofEpochMilli(correctedTime ?: event.timestampMs).atZone(zone)
            AlertDialog.Builder(this, R.style.WorkMeterDialogTheme).setTitle("Godzina zmiany")
                .setItems(arrayOf("Chwila potwierdzenia", "Godzina GPS", "Wybierz datę i godzinę")) { _, index ->
                    when (index) {
                        0 -> { correctedTime = null; updateTime() }
                        1 -> { correctedTime = event.timestampMs; updateTime() }
                        else -> pickReportDate(selected.toLocalDate()) { date ->
                            TimePickerDialog(this, { _, hour, minute ->
                                val local = date.atTime(hour, minute)
                                val resolved = WorkShiftIntegrity.resolveEditTime(local, zone, selected.toInstant().toEpochMilli())
                                if (resolved == null) toast("Wybrana godzina nie istnieje w tej strefie czasowej")
                                else { correctedTime = resolved; updateTime() }
                            }, selected.hour, selected.minute, true).show()
                        }
                    }
                }.show()
        }
        layout.addView(time)
        if (arriving) layout.addView(Button(this).apply {
            text = "Miejsce: $destinationName"
            setOnClickListener {
                chooseObject(false) { place ->
                    if (place != null) {
                        destinationId = place.optString("id")
                        destinationName = objectName(place)
                        text = "Miejsce: $destinationName"
                    }
                }
            }
        })
        val dialog = AlertDialog.Builder(this,R.style.WorkMeterDialogTheme)
            .setTitle(if(arriving) "Potwierdzić przyjazd?" else "Rozpocząć przejazd?")
            .setMessage((if(arriving) "GPS wykrył przyjazd: " else "GPS wykrył wyjazd z: ") + objectName(obj))
            .setView(layout)
            .setNegativeButton("Odrzuć") { _,_ ->
                CoreGpsProposalStore.decide(storage,eventId,CoreGpsProposalInbox.Decision.USER_REJECTED)
                CoreGpsStageBridge.dismiss(this)
            }.setNeutralButton("Później", null).setPositiveButton("Potwierdź", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val latest=data()
                val nowActive=active(latest) ?: return@setOnClickListener
                val available=activeObjectIds(latest)
                val currentShift=CoreGpsStageRules.Shift(true,nowActive.optString("kind","Obiekt"),
                    nowActive.optString("objectId"))
                val latestEvents=(CoreGpsProposalStore.read(storage) as?
                    CoreGpsProposalStore.ReadResult.Success)?.events ?: return@setOnClickListener
                val latestStageStart=maxOf(nowActive.optLong("start"),
                    nowActive.optLong("stageStart",nowActive.optLong("start")))
                val now=System.currentTimeMillis()
                if(nowActive.optString("id")!=shiftId || currentShift!=shift || latestStageStart!=stageStart ||
                    latestEvents.none { it == event } ||
                    !CoreGpsStageReviewQueue.isCurrent(eventId,latestEvents,currentShift,available,latestStageStart,now) ||
                    !CoreGpsStageBridge.hasContinuousCoverage(this,event.timestampMs,now) ||
                    !CoreGpsStageBridge.hasJournalEvidence(this,event)) {
                    toast("Propozycja jest już nieaktualna"); return@setOnClickListener
                }
                val transitionTime=correctedTime ?: now
                val recorded=(rows(nowActive,"stages") + rows(latest,"shifts")).map(::stageInterval)
                if(!CoreGpsStageRules.canConfirmCorrection(stage,destinationId,available,
                        latestStageStart,transitionTime,now,recorded)) {
                    toast("Sprawdź godzinę i miejsce. Etap nie może nakładać się na zapisany czas.")
                    return@setOnClickListener
                }
                if(!arriving) {
                    closeStage(nowActive,transitionTime)
                    val origin=nowActive.optString("object")
                    nowActive.put("stageStart",transitionTime).put("kind","Przejazd")
                        .put("object","").put("objectId","")
                        .put("stageMeta",JSONObject().put("fromObject",origin).put("fromObjectId",event.objectId))
                } else {
                    val selected=rows(latest,"objects").singleOrNull { it.optString("id")==destinationId }
                        ?: return@setOnClickListener
                    val name=objectName(selected)
                    nowActive.optJSONObject("stageMeta")?.put("toObject",name)?.put("toObjectId",destinationId)
                    closeStage(nowActive,transitionTime)
                    nowActive.remove("stageMeta")
                    nowActive.put("stageStart",transitionTime).put("kind",workStageFor(selected))
                        .put("object",name).put("objectId",destinationId)
                }
                // Commit the confirmed stage and reviewed proposal in the same durable write.
                // A process death cannot leave the work stage applied but the event pending.
                val marked=CoreGpsProposalInbox.decide(latestEvents,eventId,
                    CoreGpsProposalInbox.Decision.USER_CONFIRMED)
                arr(latest,"logs").put(JSONObject().put("at",fmt.format(Date()))
                    .put("type","GPS_STAGE_CONFIRMED").put("detail",eventId)
                    .put("transitionTimeMs",transitionTime).put("destinationId",destinationId))
                if(!prefs.edit().putString("data",latest.toString())
                    .putString(CoreGpsProposalStore.KEY,CoreGpsProposalStore.encode(marked))
                    .commit()) {
                    toast("Nie zapisano zmiany etapu, spróbuj ponownie")
                    return@setOnClickListener
                }
                GateWidget.refresh(this)
                CoreGpsStageBridge.dismiss(this)
                dialog.dismiss()
                home()
            }
        }
        dialog.show()
    }
    private fun data(): JSONObject = try { JSONObject(prefs.getString("data","{}")!!) } catch (_: Exception) { JSONObject() }
    private fun arr(d:JSONObject,key:String):JSONArray = d.optJSONArray(key) ?: JSONArray().also { d.put(key,it) }
    private fun save(d:JSONObject, kind:String, detail:String) {
        val log=arr(d,"logs"); log.put(JSONObject().put("at",fmt.format(Date())).put("type",kind).put("detail",detail)); d.put("logs",log)
        prefs.edit().putString("data",d.toString()).apply(); GateWidget.refresh(this)
        if(kind=="START" || kind=="STOP" || kind=="STAGE" || kind=="ARRIVAL")
            CoreGpsStageBridge.dismiss(this)
    }
    private fun active(d:JSONObject):JSONObject? = d.optJSONObject("active")
    private fun privateIntervals(
        shift: JSONObject,
        until: Long = shift.optLong("end", System.currentTimeMillis())
    ): List<PrivateTimeRules.Interval> {
        val finished = rows(shift, "stages").filter { it.optString("kind") == PrivateTimeRules.KIND }
            .map { PrivateTimeRules.Interval(it.optLong("start", -1L), it.optLong("end")) }
        if (shift.has("end") || shift.optString("kind") != PrivateTimeRules.KIND) return finished
        return finished + PrivateTimeRules.Interval(shift.optLong("stageStart", until), until)
    }

    private fun workedMillis(
        shift: JSONObject,
        start: Long = shift.optLong("start"),
        end: Long = shift.optLong("end", System.currentTimeMillis())
    ): Long = PrivateTimeRules.worked(start, end, privateIntervals(shift, end))

    private fun reportWorkedMillis(entries: List<Pair<JSONObject, HistoryRules.Slice>>): Long =
        PrivateTimeRules.workedTotal(
            entries.map { (_, slice) -> PrivateTimeRules.Interval(slice.start,slice.end) },
            entries.flatMap { (shift, slice) ->
                privateIntervals(shift,slice.end).mapNotNull { pause ->
                    if(pause.start<0L || pause.end<=pause.start) return@mapNotNull null
                    val start=maxOf(pause.start,slice.start)
                    val end=minOf(pause.end,slice.end)
                    PrivateTimeRules.Interval(start,end).takeIf { end>start }
                }
            }
        )

    private fun togglePrivateTime() {
        val d = data()
        val shift = active(d) ?: return
        val now = System.currentTimeMillis()
        if (!canCloseCurrentStage(shift,now)) return
        val resuming = shift.optString("kind") == PrivateTimeRules.KIND
        closeStage(shift, now)
        shift.put("stageStart", now)
        if (resuming) {
            val pausedKind = shift.optString("pausedKind", "Nieprzypisane")
            val pausedId = shift.optString("pausedObjectId")
            val activeObject = rows(d, "objects").singleOrNull { it.optString("id") == pausedId }
                ?.takeUnless { it.optBoolean("archived") }
            val canResume = !WorkPlaceRules.isOnSiteStage(pausedKind) || activeObject != null
            shift.put("kind", if (canResume) pausedKind else "Nieprzypisane")
                .put("object", if (canResume) shift.optString("pausedObject") else "")
                .put("objectId", if (canResume) pausedId else "")
            if (canResume) shift.optJSONObject("pausedStageMeta")?.let { shift.put("stageMeta", it) }
            listOf("pausedKind", "pausedObject", "pausedObjectId", "pausedStageMeta").forEach { shift.remove(it) }
        } else {
            shift.put("pausedKind", shift.optString("kind", "Obiekt"))
                .put("pausedObject", shift.optString("object"))
                .put("pausedObjectId", shift.optString("objectId"))
                .put("pausedStageMeta", shift.remove("stageMeta"))
                .put("kind", PrivateTimeRules.KIND).put("object", "").put("objectId", "")
        }
        save(d, if (resuming) "PRIVATE_END" else "PRIVATE_START", fmt.format(Date(now)))
        if (resuming) {
            if (GpsWorkdaySession.hasConsent(prefs)) setGpsEnabled(true)
        } else {
            if (GpsWorkdaySession.hasConsent(prefs))
                prefs.edit().putBoolean(GpsWorkdaySession.CONSENT, true).apply()
            setGpsEnabled(false)
        }
        home()
    }

    private fun confirmWorkday(start: Boolean, action: () -> Unit) {
        AlertDialog.Builder(this,dialogStyle)
            .setTitle(if(start) "Rozpocząć dzień pracy?" else "Zakończyć dzień pracy?")
            .setNegativeButton("Nie",null).setPositiveButton("Tak") { _, _ -> action() }.show()
    }
    private fun startShift() {
        if(active(data())!=null) { toast("Dzień pracy już rozpoczęty"); return }
        confirmWorkday(true) {
            val d=data()
            if(active(d)!=null) return@confirmWorkday
            val t=System.currentTimeMillis()
            if(!WorkShiftIntegrity.canSave(WorkShiftIntegrity.Interval(t,Long.MAX_VALUE),validClosedShiftIntervals(d))) {
                toast("Nowa zmiana koliduje z zapisaną historią"); return@confirmWorkday
            }
            d.put("active",JSONObject().put("id",UUID.randomUUID().toString()).put("start",t)
                .put("object","").put("objectId","").put("kind","Nieprzypisane")
                .put("stageStart",t).put("stages",JSONArray()).put("rate",defaultRate().toPlainString()))
            save(d,"START",fmt.format(Date(t)))
            if(GpsWorkdaySession.hasConsent(prefs)) setGpsEnabled(true)
            home()
            val waiting=AlertDialog.Builder(this,dialogStyle).setMessage("Sprawdzanie lokalizacji…")
                .setNegativeButton("Wyszukaj ręcznie",null).create()
            var cancelled=false
            waiting.setOnCancelListener { cancelled=true; cancelStartAttempt?.invoke(); cancelStartAttempt=null }
            waiting.show()
            waiting.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                cancelled=true; cancelStartAttempt?.invoke(); cancelStartAttempt=null; waiting.dismiss(); searchStartObject()
            }
            cancelStartAttempt=findStartLocation { obj ->
                if(!cancelled && active(data())?.optString("id")==d.getJSONObject("active").optString("id")) {
                    waiting.dismiss()
                    if(obj==null) searchStartObject()
                    else AlertDialog.Builder(this,dialogStyle).setTitle("Czy jesteś na tym obiekcie?")
                        .setMessage(objectName(obj)).setPositiveButton("Tak") { _, _ -> startOnObject(obj,d.getJSONObject("active").optString("id")) }
                        .setNegativeButton("Nie, wyszukaj") { _, _ -> searchStartObject() }.show()
                }
            }
        }
    }

    private fun searchStartObject() {
        val sessionId=active(data())?.optString("id").orEmpty()
        objectPicker(optional = false, arrival = false, startDay = true) { obj ->
            if (obj != null) startOnObject(obj,sessionId)
        }
    }

    private fun findStartLocation(onResult: (JSONObject?) -> Unit): (() -> Unit)? {
        val places = selectableObjects().mapNotNull { obj ->
            val id = obj.optString("id")
            val stored = prefs.getString("gps_object_" + id, null) ?: return@mapNotNull null
            try {
                val geo = JSONObject(stored)
                GpsWorkRules.Place(id, GpsWorkRules.Kind.OBJECT,
                    geo.getDouble("lat"), geo.getDouble("lon"), geo.optDouble("radius", 100.0))
            } catch (_: org.json.JSONException) {
                null
            }
        }
        if (places.isEmpty() || !GpsWorkdaySession.hasConsent(prefs) ||
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onResult(null)
            return null
        }
        val manager = getSystemService(LocationManager::class.java)
        if (manager == null) { onResult(null); return null }
        val provider = try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstOrNull { manager.isProviderEnabled(it) }
        } catch (_: RuntimeException) {
            null
        }
        if (provider == null) { onResult(null); return null }

        val signal = android.os.CancellationSignal()
        val timeout = Runnable {
            if (!signal.isCanceled) {
                signal.cancel()
                onResult(null)
            }
        }
        fun finish(fix: Location?) {
            if (signal.isCanceled) return
            ticker.removeCallbacks(timeout)
            signal.cancel()
            val point = fix?.let { GpsWorkRules.Point(it.latitude, it.longitude, it.accuracy.toDouble(), it.time) }
            val id = WorkdayObjectSearch.suggestedId(point, places, System.currentTimeMillis())
            onResult(selectableObjects().firstOrNull { it.optString("id") == id })
        }
        ticker.postDelayed(timeout, 12_000)
        try {
            androidx.core.location.LocationManagerCompat.getCurrentLocation(manager, provider, signal, androidx.core.content.ContextCompat.getMainExecutor(this)) { fix ->
                finish(fix)
            }
        } catch (_: RuntimeException) {
            finish(null)
        }
        return { ticker.removeCallbacks(timeout); signal.cancel() }
    }

    private fun startOnObject(obj: JSONObject, sessionId: String) {
        val d=data()
        val running=active(d)
        if(running==null || running.optString("id")!=sessionId || running.optString("kind")!="Nieprzypisane") {
            toast("Dzień lub etap pracy zmienił się. Wybierz obiekt ponownie."); home(); return
        }
        val current=selectableObjects(d).singleOrNull { it.optString("id")==obj.optString("id") }
            ?: run { toast("Obiekt nie jest już dostępny"); searchStartObject(); return }
        val now=System.currentTimeMillis()
        if(!canCloseCurrentStage(running,now)) return
        closeStage(running,now)
        running.put("stageStart",now).put("kind",workStageFor(current))
            .put("objectId",current.optString("id")).put("object",objectName(current))
        save(d,"STAGE",objectName(current)); home()
    }
    private fun validClosedShiftIntervals(d: JSONObject): List<WorkShiftIntegrity.Interval> =
        rows(d,"shifts").map(::stageInterval).filter { WorkShiftIntegrity.isValid(it.start,it.end) }

    private fun stopShift() {
        val d=data(); val a=active(d); if(a==null) { toast("Brak rozpoczętej pracy"); home(); return }
        val end=System.currentTimeMillis()
        if (GpsWorkdaySession.hasConsent(prefs)) prefs.edit().putBoolean(GpsWorkdaySession.CONSENT,true).apply()
        if(!canCloseCurrentStage(a,end)) {
            setGpsEnabled(false)
            return
        }
        if(!WorkShiftIntegrity.canSave(WorkShiftIntegrity.Interval(a.optLong("start",-1L),end),
                validClosedShiftIntervals(d))) {
            setGpsEnabled(false)
            toast("Zmiana koliduje z zapisaną historią")
            return
        }
        closeStage(a,end); a.put("end",end); arr(d,"shifts").put(a); d.remove("active"); save(d,"STOP",fmt.format(Date(end)))
        setGpsEnabled(false)
        home()
    }
    private fun canCloseCurrentStage(shift: JSONObject, end: Long): Boolean {
        val start=shift.optLong("start",-1L)
        if(WorkShiftIntegrity.canCloseCurrentStage(start,shift.optLong("stageStart",start),end,
                rows(shift,"stages").map(::stageInterval))) return true
        toast("Sprawdź zegar urządzenia lub wcześniejsze etapy zmiany.")
        return false
    }

    private fun closeStage(a:JSONObject, end:Long) {
        val st=a.optLong("stageStart",a.optLong("start"))
        if(end>st) {
            val segments=arr(a,"stages")
            segments.put(JSONObject().put("start",st).put("end",end)
                .put("kind",a.optString("kind","Obiekt")).put("object",a.optString("object","")).put("objectId",a.optString("objectId")))
            a.optJSONObject("stageMeta")?.let { meta -> meta.keys().forEach { key -> segments.getJSONObject(segments.length()-1).put(key,meta.get(key)) } }
            a.put("stages",segments)
        }
    }
    private fun beginStage(kind:String, target:String="") {
        if(active(data())?.optString("kind")==PrivateTimeRules.KIND) { toast("Najpierw wznów pracę"); return }
        val d=data(); val a=active(d)
        if(a==null) { toast("Najpierw rozpocznij pracę"); return }
        val now=System.currentTimeMillis()
        if(!canCloseCurrentStage(a,now)) return
        closeStage(a,now)
        val origin=if(a.optString("kind")=="Zakupy") "Zakupy" else a.optString("object"); val originId=a.optString("objectId")
        a.remove("stageMeta")
        a.put("stageStart",now).put("kind",kind)
        if(kind=="Przejazd") a.put("stageMeta",JSONObject().put("fromObject",origin).put("fromObjectId",originId))
        if(kind!="Obiekt") { a.put("object",""); a.put("objectId","") }
        if(target.isNotEmpty()) a.put("object",target)
        save(d,"STAGE","$kind: "+a.optString("object")); home()
    }
    private fun switchObject() {
        if(active(data())?.optString("kind")==PrivateTimeRules.KIND) { toast("Najpierw wznów pracę"); return }
        if (active(data()) == null) { toast("Najpierw rozpocznij pracę"); return }
        chooseObject(false) { obj ->
            if (obj != null) {
                val d = data(); val a = active(d) ?: return@chooseObject
                if(a.optString("kind")==PrivateTimeRules.KIND) return@chooseObject
                val now = System.currentTimeMillis()
                if(!canCloseCurrentStage(a,now)) return@chooseObject
                if(a.optString("kind")=="Przejazd") a.optJSONObject("stageMeta")?.put("toObject",objectName(obj))?.put("toObjectId",obj.optString("id"))
                closeStage(a,now)
                a.remove("stageMeta")
                a.put("stageStart",now).put("kind",workStageFor(obj))
                    .put("object",objectName(obj)).put("objectId",obj.optString("id"))
                save(d,"STAGE",objectName(obj)); home()
            }
        }
    }
    private fun toggleTravel() {
        if(active(data())?.optString("kind")==PrivateTimeRules.KIND) { toast("Najpierw wznów pracę"); return }
        val a=active(data()) ?: run { toast("Najpierw rozpocznij pracę"); return }
        if(a.optString("kind")!="Przejazd") { beginStage("Przejazd"); return }
        confirmAction("Zakończyć przejazd i wybrać miejsce przyjazdu?") { chooseArrival() }
    }
    private fun chooseArrival() {
        if(active(data())?.optString("kind")!="Przejazd") return
        cancelStartAttempt=findStartLocation { obj ->
            if(active(data())?.optString("kind")!="Przejazd") return@findStartLocation
            if(obj==null) chooseArrivalManual()
            else AlertDialog.Builder(this,dialogStyle).setTitle("Czy jesteś na tym obiekcie?")
                .setMessage(objectName(obj)).setNegativeButton("Nie, wyszukaj") { _, _ -> chooseArrivalManual() }
                .setPositiveButton("Tak") { _, _ -> arriveAtObject(obj) }.show()
        }
    }
    private fun arriveAtObject(obj: JSONObject) {
        val d=data(); val current=active(d) ?: return
        if(current.optString("kind")!="Przejazd" || selectableObjects(d).none { it.optString("id")==obj.optString("id") }) return
        val now=System.currentTimeMillis()
        if(!canCloseCurrentStage(current,now)) return
        current.optJSONObject("stageMeta")?.put("toObject",objectName(obj))?.put("toObjectId",obj.optString("id"))
        closeStage(current,now); current.remove("stageMeta")
        current.put("stageStart",now).put("kind",workStageFor(obj)).put("object",objectName(obj)).put("objectId",obj.optString("id"))
        save(d,"ARRIVAL",objectName(obj)); home()
    }
    private fun chooseArrivalManual() {
        if(active(data())?.optString("kind")!="Przejazd") return
        objectPicker(false,true) { obj ->
            if(obj!=null) {
                val d=data(); val current=active(d) ?: return@objectPicker
                if(current.optString("kind")!="Przejazd") return@objectPicker
                val now=System.currentTimeMillis()
                if(!canCloseCurrentStage(current,now)) return@objectPicker
                current.optJSONObject("stageMeta")?.put("toObject",objectName(obj))?.put("toObjectId",obj.optString("id"))
                closeStage(current,now); current.remove("stageMeta")
                current.put("stageStart",now).put("kind",workStageFor(obj)).put("object",objectName(obj)).put("objectId",obj.optString("id"))
                save(d,"ARRIVAL",objectName(obj)); home()
            }
        }
    }
    private fun weeklySms(parent:String="settings") {
        smsParent=parent
        val today = LocalDate.now()
        if (smsFrom == null) smsFrom = today.withDayOfMonth(1)
        if (smsTo == null) smsTo = today
        smsReport()
    }
    private fun pickReportDate(initial: LocalDate, selected: (LocalDate) -> Unit) {
        DatePickerDialog(this, { _, year, month, day ->
            selected(LocalDate.of(year, month + 1, day))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
            datePicker.calendarViewShown = true
        }.show()
    }
    private fun smsReport() {
        val from = smsFrom ?: return
        val to = smsTo ?: return
        val df = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        base("Raport SMS"); screen = "sms"; nav()
        button("Data od: ${from.format(displayDate)}") {
            pickReportDate(from) { smsFrom = it; smsReport() }
        }
        button("Data do: ${to.format(displayDate)}") {
            pickReportDate(to) { smsTo = it; smsReport() }
        }
        if (to.isBefore(from)) {
            label("Data do nie może być wcześniejsza niż Data od.")
            return
        }
        button("GENERUJ RAPORT SMS") { smsReport() }
        label("Okres: ${from.format(df)} – ${to.format(df)} (włącznie)")
        active(data())?.let { if(!localDate(it.optLong("start")).isAfter(to)) label("Uwaga: otwarta zmiana nie jest uwzględniona w raporcie ani w sumie.") }
        val zone=ZoneId.systemDefault()
        val pl = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela")
        val slices=rows(data(),"shifts").flatMap { shift ->
            HistoryRules.slicesInRange(shift.optLong("start",-1L),shift.optLong("end"),from,to,zone)
                .map { slice -> shift to slice }
        }.sortedBy { it.second.start }
        if (slices.isEmpty()) {
            label("Brak zakończonych zmian w wybranym okresie.")
            return
        }
        var sum = 0L
        val lines = slices.map { (shift,slice) ->
            val date=LocalDate.parse(slice.day)
            val work=workedMillis(shift,slice.start,slice.end)
            sum += work
            "${date.format(df)} ${pl[date.dayOfWeek.value - 1]} " +
                "${clock.format(Date(slice.start))}–${clock.format(Date(slice.end))}" +
                if(work != slice.end-slice.start) " · czas pracy: "+duration(work) else ""
        }
        val worked = reportWorkedMillis(slices)
        val minutes = worked / 60000
        val overlapNote = if(worked != sum) "\nNakładający się czas uwzględniono tylko raz." else ""
        val sms = lines.joinToString("\n\n") + "\n\nRazem\n${minutes / 60} godz. ${minutes % 60} min" + overlapNote
        label(sms)
        button("KOPIUJ RAPORT") {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("WorkMeter raport", sms))
            toast("Raport skopiowany")
        }
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private val appearance get() = WorkMeterAppearance.theme(this)
    private val palette get() = appearance.palette
    private val dialogStyle get() = WorkMeterAppearance.dialogStyle(appearance)

    private fun dp(n:Int) = (n * resources.displayMetrics.density).toInt()
    private fun background(color:Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(8).toFloat()
    }
    override fun onDestroy() {
        backupController?.close(); backupController=null
        backupDocumentResult=null; backupDriveResult=null
        cancelStartAttempt?.invoke(); io.shutdown(); super.onDestroy()
    }
    private fun backupScreen() {
        base("Kopie zapasowe"); screen="backup"; nav()
        val appearance=object: com.r3gate.app.backup.BackupSettingsPanel.Appearance {
            override fun surface(view: View) { view.setBackgroundColor(palette.surface) }
            override fun text(view: TextView) { view.setTextColor(palette.textPrimary) }
            override fun button(view: Button, primary: Boolean) {
                view.minimumHeight=dp(48); view.background=background(if(primary) palette.primary else palette.surface)
                view.backgroundTintList=null; view.setTextColor(if(primary) palette.onPrimary else palette.textPrimary)
            }
            override fun input(view: View) { view.setBackgroundColor(palette.surface); if(view is TextView) view.setTextColor(palette.textPrimary) }
        }
        val host=object: com.r3gate.app.backup.BackupSettingsController.Host {
            override fun chooseDocument(intent: Intent, selected: (Uri?) -> Unit) {
                backupDocumentResult=selected
                try { startActivityForResult(intent,410) } catch (_:RuntimeException) { backupDocumentResult=null; selected(null) }
            }
            override fun authorizeDrive(authorized: (String?) -> Unit) {
                backupDriveResult=authorized; this@MainActivity.authorizeDrive()
            }
            override fun chooseDriveCopy(copies: List<com.r3gate.app.backup.DriveBackups.Copy>, selected: (com.r3gate.app.backup.DriveBackups.Copy?) -> Unit) {
                AlertDialog.Builder(this@MainActivity,dialogStyle).setTitle("Wybierz kopię Google Drive")
                    .setItems(copies.map { it.name }.toTypedArray()) { _,i -> selected(copies[i]) }
                    .setNegativeButton("Anuluj") { _, _ -> selected(null) }.setOnCancelListener { selected(null) }.show()
            }
            override fun restored() {
                walletUnlocked=false
                cancelStartAttempt?.invoke(); cancelStartAttempt=null
                setGpsEnabled(false)
                GateWidget.refresh(this@MainActivity); CoreGpsStageBridge.refresh(this@MainActivity)
                toast("Przywrócono dane lokalne. Śledzenie GPS zatrzymane.")
            }
        }
        backupController=com.r3gate.app.backup.BackupSettingsController(this,appearance,host).also {
            root.addView(it.view,LinearLayout.LayoutParams(-1,dp(600))); it.refresh()
        }
    }
    private fun base(title:String) {
        backupController?.close(); backupController=null
        homeVisible=false
        val frame=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(palette.header) }
        val top=View(this); frame.addView(top,LinearLayout.LayoutParams(-1,0))
        val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),0,dp(8),0); setBackgroundColor(palette.header) }
        header.addView(TextView(this).apply { text=title; textSize=27f; minimumHeight=dp(56); gravity=Gravity.CENTER_VERTICAL; setTextColor(palette.onHeader); setTypeface(resources.getFont(R.font.clash_grotesk),android.graphics.Typeface.BOLD) },LinearLayout.LayoutParams(0,-2,1f).apply { gravity=Gravity.CENTER_VERTICAL })
        if(title=="WorkMeter") header.addView(TextView(this).apply {
            text="SOLO"
            textSize=11f
            setTextColor(palette.onPrimary)
            setPadding(dp(7),dp(5),dp(7),dp(5))
            background=background(palette.primary)
        },LinearLayout.LayoutParams(-2,-2).apply { rightMargin=dp(7) })
        if(title in listOf("WorkMeter","Portfel","Obiekty")) header.addView(Button(this).apply {
            text="⚙"; textSize=27f; contentDescription=when(title) { "Portfel" -> "Ustawienia portfela"; "Obiekty" -> "Ustawienia obiektów"; else -> "Ustawienia" }; setTextColor(palette.onHeader)
            background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT); backgroundTintList=null; setPadding(0,0,0,0)
            setOnClickListener { when(title) { "Portfel" -> walletSettings(); "Obiekty" -> objectSettings(); else -> settings() } }
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        frame.addView(header,LinearLayout.LayoutParams(-1,-2))
        val scroll=ScrollView(this).apply { setBackgroundColor(palette.background); isFillViewport=true }
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(24)); isFocusableInTouchMode=true; descendantFocusability=ViewGroup.FOCUS_BEFORE_DESCENDANTS }
        scroll.addView(root,FrameLayout.LayoutParams(-1,-2))
        frame.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val navigation=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(3),dp(4),dp(3),dp(3))
            setBackgroundColor(palette.background)
            elevation=dp(4).toFloat()
        }
        for (destination in WorkMeterDestination.entries) {
            navigation.addView(Button(this).apply {
                text = "${destination.icon}\n${destination.label}"
                contentDescription = destination.label
                textSize = 11f
                isAllCaps = false
                minimumHeight = dp(52)
                minHeight = dp(52)
                setPadding(dp(2), dp(6), dp(2), dp(6))
                background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                backgroundTintList = null
                post {
                    val displayedScreen = when {
                        screen == "form" -> formReturn
                        screen.startsWith("detail:") -> detailParent
                        else -> screen
                    }
                    isSelected = destination == WorkMeterDestination.forScreen(displayedScreen)
                    setTextColor(if (isSelected) palette.accentText else palette.secondaryText)
                    setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                }
                setOnClickListener {
                    val navigate: () -> Unit = {
                        when (destination) {
                            WorkMeterDestination.START -> home()
                            WorkMeterDestination.OBJECTS -> openScreen("objects")
                            WorkMeterDestination.TIME -> report()
                            WorkMeterDestination.FINANCE -> confirmPin { walletUnlocked = true; wallet() }
                            WorkMeterDestination.MORE -> menu()
                        }
                    }
                    if (screen == "form") leaveForm(navigate = navigate) else navigate()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        frame.addView(navigation, LinearLayout.LayoutParams(-1, -2))
        val bottom=View(this); frame.addView(bottom,LinearLayout.LayoutParams(-1,0))
        frame.setOnApplyWindowInsetsListener { _,insets ->
            val topInset:Int; val bottomInset:Int
            if(Build.VERSION.SDK_INT>=30) {
                val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime=insets.getInsets(WindowInsets.Type.ime())
                topInset=bars.top; bottomInset=maxOf(bars.bottom,ime.bottom)
                scroll.setPadding(bars.left,0,bars.right,0); header.setPadding(dp(16)+bars.left,0,dp(8)+bars.right,0)
            } else {
                topInset=insets.systemWindowInsetTop; bottomInset=insets.systemWindowInsetBottom
                scroll.setPadding(insets.systemWindowInsetLeft,0,insets.systemWindowInsetRight,0)
            }
            top.layoutParams=top.layoutParams.apply { height=topInset }
            bottom.layoutParams=bottom.layoutParams.apply { height=bottomInset }
            insets
        }
        setContentView(frame); frame.requestApplyInsets(); root.requestFocus()
    }
    private fun displayLegacyDates(text:String):String = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b").replace(text) {
        it.groupValues[3]+"-"+it.groupValues[2]+"-"+it.groupValues[1]
    }
    private fun label(s:String) { root.addView(TextView(this).apply {
        text=displayLegacyDates(s); textSize=16f; setTextColor(palette.textPrimary); setPadding(dp(2),dp(8),dp(2),dp(8))
    }) }
    private fun makeButton(s:String, f:()->Unit):Button {
        val accent=s.contains("ZAKOŃCZ") || s.startsWith("Usuń") || ((s.startsWith("🚗") || s=="DOTARŁEM") && active(data())?.optString("kind")=="Przejazd")
        val primary=s=="Zapisz" || s.contains("ROZPOCZNIJ") || s.startsWith("Wyślij zdjęcia")
        val role = when {
            accent -> WorkMeterButtonRole.DESTRUCTIVE
            primary -> WorkMeterButtonRole.PRIMARY
            else -> WorkMeterButtonRole.SECONDARY
        }
        return workMeterButton(this, s, role, f)
    }
    private fun button(s:String, f:()->Unit) {
        root.addView(makeButton(s,f),LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
    }
    private fun tiles(items:List<Pair<String,()->Unit>>) {
        items.chunked(2).forEach { pair ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i,item -> row.addView(makeButton(item.first,item.second),LinearLayout.LayoutParams(0,-2,1f).apply { if(i==0) rightMargin=dp(8) }) }
            if(pair.size==1) row.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
            root.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
    }
    private fun filters(key:String, applied:Boolean=false, caption:String="Filtry", content:()->Unit) {
        val parent=root
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=if(key in expandedFilters) View.VISIBLE else View.GONE
        }
        lateinit var toggle:Button
        fun title() = caption+(if(applied) " •" else "")+(if(key in expandedFilters) " ▴" else " ▾")
        toggle=makeButton(title()) {
            if(!expandedFilters.add(key)) expandedFilters.remove(key)
            panel.visibility=if(key in expandedFilters) View.VISIBLE else View.GONE
            toggle.text=title()
            toggle.contentDescription=caption+": "+if(key in expandedFilters) "rozwinięte" else "zwinięte"
        }
        toggle.contentDescription=caption+": "+if(key in expandedFilters) "rozwinięte" else "zwinięte"
        parent.addView(toggle,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        parent.addView(panel,LinearLayout.LayoutParams(-1,-2))
        root=panel
        try { content() } finally { root=parent }
    }
    private fun nav() { button("← Wstecz") { back() } }
    private fun back() {
        when {
            screen == "home" -> finish()
            screen == "sms" -> openScreen(smsParent)
            screen=="menu" -> home()
            screen=="backup" -> settings()
            screen=="walletSettings" -> wallet()
            screen=="objectSettings" -> objects()
            screen=="sectionSettings" -> settings()
            screen=="gps" -> sectionSettings("Obiekty")
            screen=="gpsPlaceKinds" -> gpsScreen()
            screen=="gpsCategories" || screen=="gpsTimeSummary" -> gpsScreen()
            screen=="gpsDiagnostics" -> gpsScreen()
            screen=="gpsPay" -> settings()
            screen.startsWith("menuFeature:") -> menu()
            screen in listOf("drive","logs","about","widgetSettings","wallet","language") -> settings()
            screen == "payment" || screen=="expenses" -> wallet()
            screen=="car" || screen.startsWith("materialGroup:") -> openScreen("list:materials:")
            screen=="queue" -> openScreen("list:links:")
            screen == "form" -> leaveForm(back = true) { openScreen(formReturn) }
            screen.startsWith("detail:") -> openScreen(detailParent)
            screen.startsWith("shift:") -> report()
            screen.startsWith("object:") -> openScreen("objects")
            screen.startsWith("list:") && screen.split(":").getOrNull(2)?.isNotBlank() == true ->
                openScreen("object:" + screen.split(":")[2])
            screen == "notesActive" || screen == "notesLog" -> openScreen("notes")
            else -> home()
        }
    }
    private fun leaveForm(back: Boolean = false, navigate: () -> Unit) {
        val message = if (back) "Zachować szkic i wrócić?" else "Zachować szkic i przejść dalej?"
        AlertDialog.Builder(this, dialogStyle).setMessage(message)
            .setNegativeButton("Zostań", null).setPositiveButton(if (back) "Wróć" else "Zachowaj") { _, _ ->
                prefs.edit().putString("draft_$formKind", draft.toString()).apply()
                navigate()
            }.show()
    }
    @Deprecated("Legacy back dispatcher")
    override fun onBackPressed() { back() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("smsParent",smsParent); out.putString("smsFrom",smsFrom?.toString()); out.putString("smsTo",smsTo?.toString()); out.putString("detailParent",detailParent); out.putString("screen",screen); out.putString("formKind",formKind); out.putString("formReturn",formReturn)
        out.putBoolean("quickPhotoUpload",quickPhotoUpload)
        out.putString("walletDraft",walletDraft.toString())
        out.putString("currentSection",currentSection)
        out.putInt("screenScrollY",(root.parent as? ScrollView)?.scrollY ?: 0)
        out.putStringArrayList("expandedFilters",ArrayList(expandedFilters))
        objectFilters?.let { filters ->
            out.putBundle("objectFilters", Bundle().apply {
                putBoolean("archived", filters.archived)
                putBoolean("grouped", filters.grouped)
                putString("city", filters.city)
                putString("query", objectSearch)
            })
        }
        out.putBoolean("startAfterObjectSave", startAfterObjectSave)
        out.putString("startObjectSessionId",startObjectSessionId)
        out.putString("draft",draft.toString()); out.putString("cameraPath",cameraPath); out.putString("pendingUpload",pendingUpload)
        super.onSaveInstanceState(out)
    }
    private fun duration(ms:Long):String { val m=(ms.coerceAtLeast(0)/60000); return "%02d:%02d".format(m/60,m%60) }
    private fun gpsHomeArrivalReady(shift:JSONObject?):Boolean {
        if(shift==null) return false
        val arrival=try { JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}") }
            catch(_:Exception) { return false }
        val fix=try { JSONObject(prefs.getString("gps_last_fix","{}") ?: "{}") }
            catch(_:Exception) { return false }
        val confirmedId=arrival.optString("confirmed").ifBlank { null }
        val fixTime=fix.optLong("time")
        val point=GpsWorkRules.Point(fix.optDouble("lat",Double.NaN),
            fix.optDouble("lon",Double.NaN),fix.optDouble("accuracy",Double.NaN),fixTime)
        val places=rows(data(),"objects").filter { !it.optBoolean("archived") }.mapNotNull { o ->
            val id=o.optString("id")
            val stored=prefs.getString("gps_object_"+id,null) ?: return@mapNotNull null
            try {
                val geo=JSONObject(stored)
                GpsWorkRules.Place(id,GpsWorkRules.Kind.OBJECT,
                    geo.getDouble("lat"),geo.getDouble("lon"),geo.optDouble("radius",100.0))
            } catch(_:Exception) { null }
        }
        return GpsHomeArrivalRules.canChooseObject(
            prefs.getBoolean("gps_enabled",false),
            prefs.getBoolean("gps_service_active",false),
            true,confirmedId,GpsWorkRules.nearest(point,places)?.place?.id,
            fixTime,arrival.optLong("last"),System.currentTimeMillis())
    }

    /** WorkMeter Solo home dashboard: visual components only; work state stays in legacy data. */
    private fun home() {
        base("WorkMeter")
        screen="home"; homeVisible=true
        val d=data(); val a=active(d)
        val recent=rows(d,"shifts").lastOrNull(); val shown=a?:recent
        val slate=palette.header
        val now = System.currentTimeMillis()
        val state = WorkMeterHomeState.from(a != null, a?.optString("kind").orEmpty(), a?.optString("objectId").orEmpty())
        val privateMode = state == WorkMeterHomeState.PERSONAL
        root.setBackgroundColor(palette.background)
        root.setPadding(dp(16),dp(16),dp(16),dp(28))

        fun rounded(color:Int,radius:Int=18):GradientDrawable = GradientDrawable().apply {
            setColor(color); cornerRadius=dp(radius).toFloat()
        }
        fun text(value:String,size:Float,color:Int,bold:Boolean=false):TextView =
            TextView(this).apply {
                this.text=value; textSize=size; setTextColor(color)
                if(bold) setTypeface(resources.getFont(R.font.clash_grotesk),android.graphics.Typeface.BOLD)
                includeFontPadding=false
            }
        fun section(title:String) {
            root.addView(text(title,14f,palette.textPrimary,true),LinearLayout.LayoutParams(-1,-2).apply {
                topMargin=dp(20); bottomMargin=dp(10)
            })
        }
        fun action(label: String, role: WorkMeterButtonRole = WorkMeterButtonRole.SECONDARY, onClick: () -> Unit): Button =
            workMeterButton(this, label, role, onClick)
        fun actionRow(first:Pair<String,()->Unit>,second:Pair<String,()->Unit>) {
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            row.addView(action(first.first,onClick=first.second),LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=dp(5) })
            row.addView(action(second.first,onClick=second.second),LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(5) })
            root.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        }
        val hero=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(19),dp(20),dp(20))
            background=rounded(slate,22)
            elevation=dp(2).toFloat()
        }
        val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
        header.addView(text("TWÓJ DZIEŃ",12f,palette.onHeader,true),LinearLayout.LayoutParams(0,-2,1f))
        val status=when { privateMode -> "PRYWATNIE";a!=null -> "DZIEŃ OTWARTY";else -> "DZIEŃ ZAMKNIĘTY" }
        header.addView(text(status,12f,palette.onHeader,true))
        hero.addView(header)
        hero.addView(text(shown?.let {
            duration(workedMillis(it))
        } ?: "00:00",44f,palette.onHeader,true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14) })
        hero.addView(text(
            when {
                privateMode -> "Czas prywatny"
                a==null -> "Gotowy do rozpoczęcia pracy"
                a.optString("kind")=="Przejazd" -> "W drodze"
                a.optString("kind")=="Zakupy" -> "Zakupy"
                a.optString("kind")=="Biuro" -> "Biuro · "+a.optString("object")
                else -> a.optString("object").ifBlank { "Bez przypisanego obiektu" }
            },16f,palette.onHeader,true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(5) })
        hero.addView(text(
            "Rozpoczęcie: "+(shown?.let { fmt.format(Date(it.optLong("start"))) } ?: "—"),
            12f,palette.onHeader),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(7) })
        if(a==null && recent!=null) hero.addView(text(
            "Zakończenie: "+fmt.format(Date(recent.optLong("end"))),
            12f,palette.onHeader),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(2) })
        if (a != null) hero.addView(text(
            "Czas etapu: " + duration(WorkMeterHomeState.stageElapsed(now, a.optLong("start"), a.optLong("stageStart"))),
            13f,palette.onHeader,true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(9) })
        val gpsLabel = GpsServiceStatusUi.read(this)
        hero.addView(text(gpsLabel, 13f, palette.onHeader), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        root.addView(hero,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(14) })

        root.addView(action(state.actionLabel, WorkMeterButtonRole.PRIMARY) {
            when (state) {
                WorkMeterHomeState.CLOSED -> startShift()
                WorkMeterHomeState.ON_SITE, WorkMeterHomeState.TRAVEL -> toggleTravel()
                WorkMeterHomeState.SHOPPING -> toggleShopping()
                WorkMeterHomeState.PERSONAL -> togglePrivateTime()
                WorkMeterHomeState.UNASSIGNED -> switchObject()
            }
        }, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })

        if (a != null) {
            root.addView(action("■  ZAKOŃCZ PRACĘ", WorkMeterButtonRole.DESTRUCTIVE) { confirmWorkday(false) { stopShift() } },
                LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
            pendingGpsStageEventId(d,System.currentTimeMillis())?.let { eventId ->
                root.addView(action("📍  GPS · POTWIERDŹ ZMIANĘ ETAPU") {
                    reviewGpsStageProposal(eventId)
                },LinearLayout.LayoutParams(-1,dp(48)).apply { bottomMargin=dp(10) })
            }
            if (!privateMode) root.addView(action("⏸  PRYWATNIE") {
                togglePrivateTime()
            },LinearLayout.LayoutParams(-1,dp(48)).apply { bottomMargin=dp(10) })
            if (state != WorkMeterHomeState.PERSONAL) {
                val switch = "↔  ZMIEŃ OBIEKT" to { confirmAction("Zmienić obiekt?") { switchObject() } }
                val travel = "Rozpocznij przejazd" to { toggleTravel() }
                val shopping = "🛍  ZAKUPY" to { toggleShopping() }
                when (state) {
                    WorkMeterHomeState.ON_SITE, WorkMeterHomeState.TRAVEL -> actionRow(switch, shopping)
                    WorkMeterHomeState.SHOPPING -> actionRow(switch, travel)
                    WorkMeterHomeState.UNASSIGNED -> actionRow(travel, shopping)
                    else -> Unit
                }
                root.addView(action("↗  TRASA DO OBIEKTU") {
                    chooseObject(false) { obj ->
                        if (obj != null) openLink("https://www.google.com/maps/dir/?api=1&destination=" +
                            URLEncoder.encode(obj.optString("city") + " " + obj.optString("Adres"), "UTF-8"))
                    }
                }, LinearLayout.LayoutParams(-1, -2))
            }
            if(a.optString("kind")=="Zakupy") {
                root.addView(action("Dane zakupów / paragony") { shoppingRecord(a.optString("shoppingRecordId")) },
                    LinearLayout.LayoutParams(-1,dp(48)))
            }
        }
        val saveGpsReady=canSaveHomeGps(a)
        root.addView(action("Zapisz GPS obiektu") { saveHomeGps() }.apply {
            isEnabled=saveGpsReady
            alpha=if(saveGpsReady) 1f else 0.55f
        },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        section("SZYBKIE DZIAŁANIA")
        root.addView(action("Zrób zdjęcie") { openHomeRecord("links"); camera() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        actionRow("Dodaj notatkę" to { openHomeRecord("notes") },
            "Dodaj materiał" to { openHomeRecord("materials") })
        actionRow("📷  ZDJĘCIA" to { openScreen("list:links:") },
            "📦  MATERIAŁY" to { openScreen("list:materials:") })
        actionRow("📝  NOTATKI" to { openScreen("notes") },
            "🏢  OBIEKTY" to { openScreen("objects") })
        section("PRZEGLĄD")
        actionRow("◷  MÓJ CZAS" to { report() }, "▥  GRAFIKI" to { charts() })
        root.addView(action("☷  WIĘCEJ · MENU P1–P4") { menu() },LinearLayout.LayoutParams(-1,dp(50)))
    }

    private fun openHomeRecord(kind: String) {
        val day = data()
        val shift = active(day)
        val objectId = shift?.optString("objectId").orEmpty()
        val state = WorkMeterHomeState.from(shift != null, shift?.optString("kind").orEmpty(), objectId)
        val defaultObject = if (state == WorkMeterHomeState.ON_SITE && !prefs.contains("draft_" + kind)) {
            rows(day, "objects").singleOrNull { it.optString("id") == objectId }
                ?.takeUnless { it.optBoolean("archived") }
                ?.optString("id").orEmpty()
        } else ""
        newForm(kind, objectId = defaultObject)
    }

    // Native WorkMeter 4.0 menu inspired by the user's P1–P4 interactive prototype.
    // Not-yet-implemented modules are transparent informational destinations, never fake actions.
    private data class MenuModule(val id:String,val title:String,val detail:String,val phase:String)
    private val prototypeModules get() = listOf(
        MenuModule("backups","Kopie i dane","Kopie lokalne, import, eksport i Google Disk","P1"),
        MenuModule("widgets","Galeria widżetów","Wygląd, przezroczystość i podgląd","P1"),
        MenuModule("jobs","Zlecenia","Powiązanie pracy z obiektem","P2"),
        MenuModule("planner","Planer","Plan dnia i zadania","P2"),
        MenuModule("schedule","Harmonogram","Kalendarz wizyt i zadania","P2"),
        MenuModule("protocols","Protokoły","Odbiory, podpisy i PDF","P2"),
        MenuModule("quality","Jakość i gwarancje","Usterki, reklamacje i historia","P2"),
        MenuModule("company","Finanse firmy","Koszty, przychody i raporty","P3"),
        MenuModule("clients","Klienci","Lista klientów i kontakty","P3"),
        MenuModule("equipment","Sprzęt","Rejestr urządzeń i serwis","P3"),
        MenuModule("team","Zespół","Pracownicy i dyspozycyjność","P3"),
        MenuModule("service","Serwis cykliczny","Przeglądy i przypomnienia","P3"),
        MenuModule("inventory","Magazyn","Stan auta, materiały i zestawy","P3"),
        MenuModule("documents","Dokumenty","Zdjęcia, paragony i umowy","P3"),
        MenuModule("access","Role i dostęp","Prywatność i uprawnienia","P3"),
        MenuModule("clientportal","Portal klienta","Akceptacje i statusy","P3"),
        MenuModule("routes","Trasy i dojazdy","Mapa obiektów i kolejność wizyt","P4"),
        MenuModule("estimator","AI kosztorys","Roboczogodziny, materiały i marża","P4"),
        MenuModule("insights","Rekomendacje AI","Porównanie planu i rzeczywistości","P4")
    )
    private fun menu() {
        screen="menu"
        base("Menu WorkMeter")
        root.setBackgroundColor(palette.background)
        nav()
        val banner=TextView(this).apply {
            text="WORKMETER · SOLO\nWSZYSTKIE FUNKCJE · P1–P4"
            textSize=13f
            setTextColor(palette.onHeader)
            setTypeface(null,android.graphics.Typeface.BOLD)
            setPadding(dp(14),dp(12),dp(14),dp(12))
            background=GradientDrawable().apply {
                setColor(palette.header)
                cornerRadius=dp(13).toFloat()
            }
        }
        root.addView(banner,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        label("Główne działy")
        tiles(listOf(
            "⌂  Dzisiaj" to { home() },
            "◷  Mój czas" to { report() },
            "▦  Obiekty" to { objects() },
            "▧  Zdjęcia" to { openScreen("list:links:") },
            "▤  Materiały" to { openScreen("list:materials:") },
            "📝  Notatki" to { openScreen("notes") },
            "◔  Grafiki" to { charts() },
            "🔐  Portfel" to { confirmPin { walletUnlocked=true;wallet() } },
            "⚙  Ustawienia" to { settings() }
        ))
        for(phase in listOf("P1","P2","P3","P4")) {
            label("$phase · "+when(phase) {
                "P1" -> "Fundamenty";"P2" -> "Organizacja pracy"
                "P3" -> "Zespół i dokumenty";else -> "Planowanie i analityka"
            })
            prototypeModules.filter { it.phase==phase }.forEach { module ->
                root.addView(makeButton("›  "+module.title+"\n"+module.detail) { menuFeature(module.id) }.apply {
                    textSize=13f
                    gravity=Gravity.CENTER_VERTICAL or Gravity.START
                    minHeight=dp(62); minimumHeight=dp(62)
                    setTextColor(palette.textPrimary)
                    setPadding(dp(14),dp(10),dp(12),dp(10))
                    background=GradientDrawable().apply {
                        setColor(palette.surface)
                        cornerRadius=dp(13).toFloat()
                        setStroke(dp(1),palette.border)
                    }
                },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(7) })
            }
        }
    }
    private fun menuFeature(id:String) {
        when(id) {
            "backups" -> { logs(); return }
            "widgets" -> { widgetSettings(); return }
            "inventory" -> { carStock(); return }
            "insights" -> { charts(); return }
        }
        val module=prototypeModules.firstOrNull { it.id==id } ?: return menu()
        screen="menuFeature:$id"
        base(module.title)
        nav()
        label(module.phase+" · "+module.detail)
        label("Ten moduł jest częścią wizualnego prototypu P1–P4. Pełna obsługa wymaga osobnego wdrożenia. Dostępne działania poniżej korzystają z prawdziwych danych WorkMeter.")
        when(id) {
            "schedule","jobs","planner" -> {
                button("◷  Historia pracy i kalendarz") { report() }
                button("▦  Obiekty") { objects() }
            }
            "protocols","quality","service" -> {
                button("📝  Notatki i historia") { openScreen("notes") }
                button("▦  Obiekty") { objects() }
                button("📷  Zdjęcia") { openScreen("list:links:") }
            }
            "team","access","clients" -> {
                button("▦  Obiekty") { objects() }
                button("⚙  Ustawienia") { settings() }
                button("🔐  Portfel prywatny (PIN)") { confirmPin { walletUnlocked=true; wallet() } }
            }
            "equipment" -> {
                button("▦  Obiekty i dane urządzeń") { objects() }
                button("📝  Notatki serwisowe") { openScreen("notes") }
            }
            "company" -> {
                button("🔐  Rozliczenia prywatne (PIN)") { confirmPin { walletUnlocked=true;wallet() } }
                button("▤  Materiały") { openScreen("list:materials:") }
            }
            "documents" -> {
                button("📷  Zdjęcia") { openScreen("list:links:") }
                button("🧾  Paragony i zakupy") { expenseList() }
                button("☁  Kopia i import danych") { logs() }
            }
            "routes" -> {
                button("▦  Obiekty i ich adresy") { objects() }
                button("↗  Trasa do obiektu w Google Maps") {
                    chooseObject(false) { obj -> if(obj!=null) openLink("https://www.google.com/maps/dir/?api=1&destination="+URLEncoder.encode(obj.optString("city")+" "+obj.optString("Adres"),"UTF-8")) }
                }
            }
            "estimator" -> {
                button("▤  Materiały") { openScreen("list:materials:") }
                button("🔐  Stawki i rozliczenia (PIN)") { confirmPin { walletUnlocked=true;wallet() } }
            }
            "clientportal" -> {
                button("▦  Obiekty") { objects() }
                button("📝  Notatki") { openScreen("notes") }
            }
        }
        button("☷  Wróć do pełnego menu") { menu() }
    }
    private fun recordGpsArrival(loc:Location) {
        if(loc.accuracy>100f || !loc.latitude.isFinite() || !loc.longitude.isFinite()) return
        val now=System.currentTimeMillis()
        val point=GpsWorkRules.Point(loc.latitude,loc.longitude,loc.accuracy.toDouble(),now)
        val places=rows(data(),"objects").filter { !it.optBoolean("archived") }.mapNotNull { o ->
            try {
                val geo=JSONObject(prefs.getString("gps_object_"+o.optString("id"),"{}") ?: "{}")
                if(!geo.has("lat") || !geo.has("lon")) null else
                    GpsWorkRules.Place(o.optString("id"),GpsWorkRules.Kind.OBJECT,geo.getDouble("lat"),geo.getDouble("lon"),geo.optDouble("radius",100.0))
            } catch(_:Exception) { null }
        }
        val observed=GpsWorkRules.nearest(point,places)?.place?.id
        val saved=JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}")
        val state=GpsArrivalRules.State(
            confirmedId=saved.optString("confirmed").ifBlank { null },
            candidateId=saved.optString("candidate").ifBlank { null },
            candidateSinceMs=saved.optLong("since"),
            lastFixMs=saved.optLong("last")
        )
        val result=GpsArrivalRules.advance(state,observed,now)
        val next=result.state
        val stateJson=JSONObject().put("confirmed",next.confirmedId ?: "").put("candidate",next.candidateId ?: "")
            .put("since",next.candidateSinceMs).put("last",next.lastFixMs)
        val editor=prefs.edit().putString("gps_arrival_state",stateJson.toString())
        if(result.event!=null) {
            val history=try { JSONArray(prefs.getString("gps_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
            history.put(JSONObject().put("event",result.event).put("objectId",result.placeId ?: "").put("time",now))
            val recent=JSONArray()
            for(i in maxOf(0,history.length()-100) until history.length()) recent.put(history.get(i))
            editor.putString("gps_events",recent.toString())
        }
        editor.apply()
    }

    private fun gpsResumePrompt() {
        if(!prefs.getBoolean("gps_resume_pending",false)) return
        label("GPS był aktywny przed restartem telefonu. Wznów go tylko, jeśli chcesz.")
        button("Wznów GPS po restarcie") {
            prefs.edit().putBoolean(GpsWorkdaySession.CONSENT,true).putBoolean("gps_resume_pending",false).apply()
            setGpsEnabled(true)
            gpsScreen()
        }
        button("Nie wznawiaj GPS") {
            prefs.edit().putBoolean("gps_resume_pending",false).apply()
            gpsScreen()
        }
    }
    /**
     * Request Android 13+ notification access once when GPS is opted in.
     * A declined request never turns tracking on/off or edits work stages.
     */
    private fun requestGpsStageNotificationsIfNeeded() {
        val granted=Build.VERSION.SDK_INT<33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
        if(CoreGpsNotificationPermissionRules.needsRequest(
                Build.VERSION.SDK_INT,prefs.getBoolean("gps_enabled",false),granted,
                prefs.getBoolean("core_gps_notification_permission_asked",false))) {
            prefs.edit().putBoolean("core_gps_notification_permission_asked",true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),308)
        }
    }

    private fun toggleGpsConsent() {
        val allow = !GpsWorkdaySession.hasConsent(prefs)
        prefs.edit().putBoolean(GpsWorkdaySession.CONSENT,allow).apply()
        setGpsEnabled(allow)
    }

    private fun setGpsEnabled(value:Boolean) {
        if (value && (!GpsWorkdaySession.workdayOpen(prefs) || !GpsWorkdaySession.hasConsent(prefs) ||
            active(data())?.optString("kind") == PrivateTimeRules.KIND)) return
        if(!value) {
            prefs.edit().putBoolean("gps_enabled",false).putBoolean("gps_resume_pending",false).apply()
            CoreGpsStageBridge.dismiss(this)
            stopService(Intent(this,GpsTrackingService::class.java))
            GpsRecoveryMonitorWorker.cancel(this)
            return
        }
        val fine=checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!fine && !coarse) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION),307)
            return
        }
        prefs.edit().putBoolean(GpsWorkdaySession.CONSENT,true).putBoolean("gps_enabled",true)
            .putBoolean("gps_resume_pending",false).apply()
        try {
            androidx.core.content.ContextCompat.startForegroundService(
                this,Intent(this,GpsTrackingService::class.java).setAction(GpsTrackingService.ACTION_START))
        } catch(e:Exception) {
            prefs.edit().putBoolean("gps_enabled",false).apply()
            CoreGpsStageBridge.dismiss(this)
            Toast.makeText(this,"Nie można uruchomić GPS: "+e.javaClass.simpleName,Toast.LENGTH_LONG).show()
            return
        }
        // Notification permission is separate from location tracking: never
        // disable GPS if Android cannot show the optional prompt.
        try { requestGpsStageNotificationsIfNeeded() } catch (_: RuntimeException) {
            toast("Powiadomienia GPS możesz włączyć później w ustawieniach.")
        }
    }
    private fun gpsDeviceDiagnostics() {
        base("GPS · diagnostyka Android"); screen="gpsDiagnostics"; nav()
        val fine=checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val locationManager=getSystemService(LOCATION_SERVICE) as LocationManager
        val locationEnabled=locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val power=getSystemService(POWER_SERVICE) as PowerManager
        val batteryUnrestricted=power.isIgnoringBatteryOptimizations(packageName)
        val snapshot=GpsDeviceDiagnostics.Snapshot(fine || coarse,locationEnabled,batteryUnrestricted,
            prefs.getBoolean("gps_enabled",false),prefs.getBoolean("gps_service_active",false))
        label("Lokalizacja: "+if(locationEnabled) "włączona" else "wyłączona")
        label("Uprawnienie GPS: "+if(fine) "dokładne" else if(coarse) "przybliżone" else "brak")
        label("Ograniczenia baterii: "+if(batteryUnrestricted) "bez optymalizacji Android" else "możliwe ograniczenia")
        label("Usługa GPS: "+if(snapshot.serviceReportedActive) "zgłosiła uruchomienie" else "nie zgłasza uruchomienia")
        for(issue in GpsDeviceDiagnostics.issues(snapshot)) {
            label(when(issue) {
                GpsDeviceDiagnostics.Issue.LOCATION_PERMISSION -> "Wymagane pozwolenie GPS"
                GpsDeviceDiagnostics.Issue.LOCATION_SWITCH -> "Lokalizacja telefonu jest wyłączona"
                GpsDeviceDiagnostics.Issue.BATTERY_RESTRICTIONS -> "Android może ograniczać działanie GPS przy zgaszonym ekranie"
                GpsDeviceDiagnostics.Issue.SERVICE_NOT_ACTIVE -> "Usługa GPS nie zgłasza działania"
            })
        }
        val fixJournal=try { JSONArray(prefs.getString("gps_fix_times","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
        val samples=(0 until fixJournal.length()).map { fixJournal.optLong(it) }
        val quality=GpsSamplingHealth.assess(snapshot.serviceReportedActive,
            prefs.getLong("gps_session_start",0),samples,System.currentTimeMillis())
        label("Pomiary w tej sesji: "+quality.samples)
        label("Odrzucone pomiary (niska jakość): "+prefs.getInt("gps_rejected_quality_count",0))
        label("Odrzucone pomiary (powtórzone lub opóźnione): "+prefs.getInt("gps_rejected_order_count",0))
        label("Odrzucone skoki lokalizacji GPS: "+prefs.getInt("gps_rejected_jump_count",0))
        label("Liczniki dotyczą bieżącej sesji i nie wpływają na zapisane godziny.")
        val requestedMs=prefs.getLong("gps_requested_interval_ms",30_000L)
        val cadence=GpsCadenceStats.assess(samples,prefs.getLong("gps_session_start",0),
            System.currentTimeMillis(),requestedMs)
        label("Żądany odstęp pomiarów: "+(requestedMs/1000)+" s")
        label("Mediana faktycznych odstępów: "+
            (cadence.medianIntervalMs?.let { (it/1000).toString()+" s" } ?: "brak danych"))
        label("Najdłuższa przerwa: "+
            (cadence.longestGapMs?.let { (it/1000).toString()+" s" } ?: "brak danych"))
        label("Opóźnione odstępy: "+cadence.delayedIntervals)
        label("Przerwy w pomiarach GPS ponad 4 minuty: "+cadence.coverageInterruptions)
        label("Odstępy pomiarów nie oznaczają zmierzonego zużycia baterii.")
        label("Dziennik diagnostyczny GPS · zapis lokalny")
        label("Rejestruje pomiary, dokładność, odległość do obiektu, odrzucenia i decyzje o wyjeździe.")
        button("📤 Udostępnij dziennik GPS") {
            val report=GpsTraceJournal.report(prefs)
            val intent=Intent(Intent.ACTION_SEND).apply {
                type="text/plain"
                putExtra(Intent.EXTRA_SUBJECT,"WorkMeter SOLO · dziennik GPS")
                putExtra(Intent.EXTRA_TEXT,report)
            }
            startActivity(Intent.createChooser(intent,"Wyślij diagnostykę GPS"))
        }
        label("Ostatnie zdarzenia GPS (czas Unix ms):")
        for(entry in GpsTraceJournal.recent(prefs)) label(entry)
        button("Usuń dziennik diagnostyczny GPS") {
            confirmAction("Usunąć lokalne wpisy diagnostyczne GPS? Nie usuwa historii pracy ani zapisanych obiektów.") {
                prefs.edit().remove("gps_diagnostic_trace").apply()
                gpsDeviceDiagnostics()
            }
        }
        label("Jakość pomiarów w tle: "+when(quality.level) {
            GpsSamplingHealth.Level.NO_SESSION -> "brak aktywnej sesji"
            GpsSamplingHealth.Level.WAITING_FOR_FIRST_FIX -> "oczekiwanie na pierwszy pomiar"
            GpsSamplingHealth.Level.FRESH -> "aktualne pomiary"
            GpsSamplingHealth.Level.DELAYED -> "opóźnione pomiary"
            GpsSamplingHealth.Level.STALE -> "brak pomiarów ponad 15 minut"
        })
        if(quality.level==GpsSamplingHealth.Level.STALE)
            label("Sprawdź oszczędzanie energii i dostęp GPS. Nie dopisujemy brakujących godzin.")
        button("Ustawienia lokalizacji telefonu") {
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        button("Ustawienia baterii WorkMeter") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:"+packageName)))
        }
        button("Uprawnienia WorkMeter") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:"+packageName)))
        }
        label("Te informacje nie zastępują testu GPS na zablokowanym telefonie.")
        button("Odśwież diagnostykę") { gpsDeviceDiagnostics() }
    }

    private fun placeType(row: JSONObject): WorkPlaceRules.Type = WorkPlaceRules.type(
        row.optString("placeType"), prefs.getString("gps_place_kind_"+row.optString("id"),null)=="SHOPPING"
    )

    private fun workStageFor(row: JSONObject): String = WorkPlaceRules.workStage(placeType(row))

    private fun pickPlaceType(selected: (WorkPlaceRules.Type) -> Unit) {
        val types=WorkPlaceRules.Type.entries
        AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Typ miejsca")
            .setItems(types.map { it.label }.toTypedArray()) { _,i -> selected(types[i]) }
            .setNegativeButton("Anuluj",null).show()
    }

    private fun gpsPlaceKinds() {
        base("GPS · typy geostref"); screen="gpsPlaceKinds"; nav()
        label("Typ miejsca pomaga opisać obserwację GPS. Potwierdzona historia pozostaje bez zmian.")
        val objects=rows(data(),"objects").filter { !it.optBoolean("archived") }
        if(objects.isEmpty()) label("Brak obiektów. Dodaj lokalizację w sekcji Obiekty.")
        for(o in objects) {
            val id=o.optString("id")
            if(id.isBlank()) continue
            button(objectName(o)+" · "+placeType(o).label) {
                pickPlaceType { type ->
                    val d=data()
                    val row=rows(d,"objects").singleOrNull { it.optString("id")==id }
                        ?.takeUnless { it.optBoolean("archived") }
                    if(row!=null && placeType(row)!=type) {
                        row.put("placeType",type.name)
                        save(d,"OBJECT_PLACE_TYPE",id)
                    }
                    gpsPlaceKinds()
                }
            }
        }
        button("GPS · wróć") { gpsScreen() }
    }

    private fun gpsScreen() {
        base("GPS · WorkMeter"); screen="gps"; nav()
        label("GPS aktywny działa w tle z widocznym powiadomieniem. Wyłącz GPS, aby zakończyć rejestrację.")
        gpsResumePrompt()
        button("Diagnostyka GPS i baterii Android") { gpsDeviceDiagnostics() }
        button("GPS · Mój czas") { gpsTimeSummary() }
        button("Historia obserwacji GPS") {
            val events=(CoreGpsProposalStore.read(AndroidCoreGpsProposalStorage(this)) as?
                CoreGpsProposalStore.ReadResult.Success)?.events
            val entries=events.orEmpty().asReversed().take(50).map { event ->
                val kind=when(event.kind) {
                    CoreGpsProposalInbox.Kind.TRIP_START -> "Początek przejazdu"
                    CoreGpsProposalInbox.Kind.TRIP_STOP -> "Koniec przejazdu"
                    CoreGpsProposalInbox.Kind.OBJECT_VISIT -> "Wizyta w miejscu"
                    CoreGpsProposalInbox.Kind.GPS_GAP -> "Przerwa w pomiarach"
                    CoreGpsProposalInbox.Kind.ARRIVAL -> "Przyjazd"
                    CoreGpsProposalInbox.Kind.DEPARTURE -> "Wyjazd"
                }
                val status=when(event.decision) {
                    CoreGpsProposalInbox.Decision.PROVISIONAL -> "obserwacja"
                    CoreGpsProposalInbox.Decision.USER_CONFIRMED -> "potwierdzone"
                    CoreGpsProposalInbox.Decision.USER_REJECTED -> "odrzucone"
                }
                val place=event.objectId?.let(::objectById)?.let(::objectName)
                listOf(fmt.format(Date(event.timestampMs)),kind,place,status).filterNotNull().joinToString(" · ")
            }
            AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Historia obserwacji GPS")
                .setItems(entries.ifEmpty { listOf(if(events==null) "Nie można odczytać historii" else "Brak obserwacji") }
                    .toTypedArray()) { _,_ -> }
                .setPositiveButton("Zamknij",null).show()
        }
        button("Typy geostref: obiekt / sklep / biuro") { gpsPlaceKinds() }
        val adaptive=prefs.getBoolean("gps_adaptive_sampling",false)
        button("Oszczędzanie GPS z akcelerometrem: "+if(adaptive) "WŁĄCZONE" else "WYŁĄCZONE") {
            prefs.edit().putBoolean("gps_adaptive_sampling",!adaptive).apply()
            if(prefs.getBoolean("gps_enabled",false)) {
                stopService(Intent(this,GpsTrackingService::class.java))
                setGpsEnabled(true)
            }
            gpsScreen()
        }
        label("Eksperymentalne: po 5 minutach bez wykrytego ruchu GPS co 90 sekund zamiast co 30 sekund. Nie wyłącza GPS; ruch telefonu nie potwierdza przejazdu.")

        val enabled = prefs.getBoolean("gps_enabled",false)
        button(if(GpsWorkdaySession.hasConsent(prefs)) "GPS: dozwolony w pracy — wyłącz" else "GPS: wyłączony — włącz") {
            toggleGpsConsent(); gpsScreen()
        }
        if(!enabled) {
            if(GpsWorkdaySession.workdayOpen(prefs) && GpsWorkdaySession.hasConsent(prefs) &&
                !prefs.getBoolean("gps_resume_pending",false)) {
                button("Uruchom śledzenie GPS") { setGpsEnabled(true); gpsScreen() }
            }
            return
        }
        val notificationsGranted=Build.VERSION.SDK_INT<33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
        if(CoreGpsNotificationPermissionRules.showPromptButton(
                Build.VERSION.SDK_INT,enabled,notificationsGranted)) {
            label("Powiadomienia GPS o zmianie etapu są wyłączone. Sam GPS nadal działa.")
            button("Zezwól na powiadomienia o zmianie etapu GPS") {
                prefs.edit().putBoolean("core_gps_notification_permission_asked",true).apply()
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),308)
            }
        }
        val permission=checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!permission) {
            label("Wymagane pozwolenie na lokalizację.")
            button("Zezwól na GPS") { requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION),307) }
            return
        }
        val status=GpsStatusReader.read(this,pendingProposalCount=0).status
        label(when(status) {
            GpsStatusContract.Status.OFF -> "GPS wyłączony."
            GpsStatusContract.Status.ACTIVE -> "Ostatni pomiar GPS jest aktualny."
            GpsStatusContract.Status.WAITING_FOR_FIX -> "Oczekiwanie na aktualny pomiar GPS."
            GpsStatusContract.Status.UNAVAILABLE -> "Brak dostępnego źródła lokalizacji."
            GpsStatusContract.Status.PERMISSION_REQUIRED -> "Wymagane pozwolenie na dokładną lokalizację."
            GpsStatusContract.Status.ERROR -> "Sprawdź diagnostykę GPS."
        })
        button("Uruchom ponownie śledzenie GPS") {
            stopService(Intent(this,GpsTrackingService::class.java))
            setGpsEnabled(true)
            gpsScreen()
        }
        button("Sprawdź aktualną pozycję") {
            val manager=getSystemService(LOCATION_SERVICE) as LocationManager
            val provider=when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> ""
            }
            if(provider.isEmpty()) { Toast.makeText(this,"Włącz lokalizację w telefonie",Toast.LENGTH_LONG).show(); return@button }
            try {
                manager.requestSingleUpdate(provider,object:LocationListener {
                    override fun onLocationChanged(loc:Location) {
                        // Manual location checks are previews only: never overwrite the
                        // tracking service's latest fix or advance arrival/trip evidence.
                        val observedAt=System.currentTimeMillis()
                        if(GpsLocationQuality.acceptable(observedAt,loc.time,loc.accuracy.toDouble(),
                                loc.latitude,loc.longitude)) {
                            prefs.edit().putString("gps_manual_preview",JSONObject()
                                .put("lat",loc.latitude).put("lon",loc.longitude)
                                .put("accuracy",loc.accuracy.toDouble())
                                .put("time",observedAt).toString()).apply()
                        } else {
                            Toast.makeText(this@MainActivity,"Pomiar nieaktualny lub niedokładny",Toast.LENGTH_LONG).show()
                        }
                        if(screen=="gps") gpsScreen()
                    }
                },Looper.getMainLooper())
            } catch(e:SecurityException) { Toast.makeText(this,"Brak uprawnienia GPS",Toast.LENGTH_LONG).show() }
        }
        val preview=try { JSONObject(prefs.getString("gps_manual_preview","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
        val previewAge=System.currentTimeMillis()-preview.optLong("time")
        if(preview.optLong("time")>0 && previewAge in 0..120_000L)
            label("Ręczny odczyt (podgląd, bez zapisu zdarzeń): "+
                "%.5f".format(preview.optDouble("lat"))+", "+"%.5f".format(preview.optDouble("lon"))+
                " · dokładność ok. "+preview.optInt("accuracy")+" m")
        val arrivalLog=try { JSONArray(prefs.getString("gps_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
        val travelLog=try { JSONArray(prefs.getString("gps_trip_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
        val activityEvents=mutableListOf<GpsActivitySuggestionRules.Event>()
        for(i in 0 until arrivalLog.length()) {
            val event=arrivalLog.optJSONObject(i) ?: continue
            activityEvents.add(GpsActivitySuggestionRules.Event(event.optString("event"),
                event.optLong("time"),event.optString("objectId")))
        }
        for(i in 0 until travelLog.length()) {
            val event=travelLog.optJSONObject(i) ?: continue
            activityEvents.add(GpsActivitySuggestionRules.Event(event.optString("event"),event.optLong("time")))
        }
        // Only configured work objects are recognized here; an untagged location
        // must never be silently inferred to be a material supplier.
        val worksiteKinds=mutableMapOf<String,GpsActivitySuggestionRules.Kind>()
        val objectsById=rows(data(),"objects").groupBy { it.optString("id") }
        for((id,matching) in objectsById) {
            val o=matching.singleOrNull() ?: continue
            if(id.isNotBlank() && !o.optBoolean("archived"))
                worksiteKinds[id]=if(placeType(o)==WorkPlaceRules.Type.SHOP)
                    GpsActivitySuggestionRules.Kind.SHOPPING
                else GpsActivitySuggestionRules.Kind.WORKSITE
        }
        val workSuggestionsAllowed=GpsActivitySuggestionRules.workSuggestionsAllowed(
            active(data())?.optString("kind","Obiekt"))
        val hint=GpsActivitySuggestionRules.latest(
            activityEvents,worksiteKinds,System.currentTimeMillis(),workSuggestionsAllowed)
        if(hint!=null) {
            when(hint.kind) {
                GpsActivitySuggestionRules.Kind.WORKSITE -> {
                    val place=hint.placeId?.let(::objectById)
                    val caption=if(place!=null && placeType(place)==WorkPlaceRules.Type.OFFICE)
                        "GPS · możliwy przyjazd do biura: " else "GPS · możliwy przyjazd na obiekt: "
                    label(caption+(place?.let(::objectName) ?: hint.placeId.orEmpty()))
                }
                GpsActivitySuggestionRules.Kind.SHOPPING -> {
                    label("GPS · możliwy postój na zakupy materiałów")
                    val shopId=hint.placeId
                    if(!shopId.isNullOrBlank()) {
                        val visits=GpsShopVisitHistory.confirmedVisits(
                            activityEvents.map { GpsShopVisitHistory.Event(it.type,it.timeMs,it.placeId) },
                            shopId,System.currentTimeMillis())
                        if(visits>=2)
                            label("W tej lokalizacji wykryto "+visits+
                                " potwierdzone postoje w ciągu 30 dni. Sprawdź kategorię przejazdu ręcznie.")
                        val observedTrips=activityEvents.map {
                            GpsShiftEstimate.Event(it.type,it.timeMs)
                        }
                        val matched=GpsShopTripMatch.matchingTrip(
                            observedTrips,hint.atMs,true)
                        if(matched!=null) {
                            val categoryKey="gps_trip_category_"+matched.startMs
                            val category=prefs.getString(categoryKey,"UNKNOWN")
                            if(category=="UNKNOWN") {
                                button("Potwierdź kategorię tej podróży") {
                                    AlertDialog.Builder(this,dialogStyle)
                                        .setTitle("Zakupy materiałów?")
                                        .setMessage("Przejazd: "+
                                            fmt.format(Date(matched.startMs))+
                                            " – "+fmt.format(Date(matched.endMs))+
                                            ". Czy potwierdzasz zakupy materiałów? "+
                                            "Zmiana dotyczy tylko kategorii szacunku GPS, nie ewidencji pracy.")
                                        .setPositiveButton("Potwierdź") { _,_ ->
                                            val freshLog=try {
                                                JSONArray(prefs.getString("gps_trip_events","[]") ?: "[]")
                                            } catch(_:Exception) { JSONArray() }
                                            val freshEvents=mutableListOf<GpsShiftEstimate.Event>()
                                            for(i in 0 until freshLog.length()) {
                                                val e=freshLog.optJSONObject(i) ?: continue
                                                freshEvents.add(GpsShiftEstimate.Event(
                                                    e.optString("event"),e.optLong("time")))
                                            }
                                            val latest=data()
                                            val shop=rows(latest,"objects").singleOrNull { it.optString("id")==shopId }
                                            val stillEligible=GpsWorkdaySession.shouldTrack(prefs) &&
                                                GpsActivitySuggestionRules.workSuggestionsAllowed(
                                                    active(latest)?.optString("kind","Obiekt")) &&
                                                shop?.optBoolean("archived")==false &&
                                                shop?.let(::placeType)==WorkPlaceRules.Type.SHOP
                                            if(GpsShopConfirmationRules.canConfirm(
                                                freshEvents,matched.startMs,matched.endMs,stillEligible,
                                                prefs.getString(categoryKey,"UNKNOWN") ?: "UNKNOWN",
                                                System.currentTimeMillis())) {
                                                prefs.edit().putString(categoryKey,"SHOPPING").apply()
                                                toast("Zatwierdzono kategorię zakupów")
                                            } else {
                                                toast("Nie zapisano: przejazd lub oznaczenie miejsca uległo zmianie")
                                            }
                                            gpsScreen()
                                        }
                                        .setNegativeButton("Anuluj",null).show()
                                }
                            } else label("Kategoria tego przejazdu: "+category+
                                " · zmień ręcznie w kategorii przejazdów.")
                        }
                        button("Wszystkie kategorie przejazdów") { gpsTripCategories() }
                    }
                }
                GpsActivitySuggestionRules.Kind.STOP ->
                    label("GPS · wykryto dłuższy postój; jego cel jest nieznany")
            }
            label("To tylko sugestia. Potwierdź czynność ręcznie; GPS nie zmienia godzin ani wypłat.")
        }
        val current=JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}").optString("confirmed")
        if(current.isNotBlank()) label("Potwierdzona strefa: "+(objectById(current)?.let { objectName(it) } ?: current))
        val gpsEvents=try { JSONArray(prefs.getString("gps_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
        if(gpsEvents.length()>0) {
            label("Ostatnie zdarzenia GPS (tylko lokalnie):")
            for(i in gpsEvents.length()-1 downTo maxOf(0,gpsEvents.length()-5)) {
                val event=gpsEvents.optJSONObject(i) ?: continue
                val title=if(event.optString("event")=="ARRIVAL") "Przyjazd" else "Wyjazd"
                label(title+" · "+(objectById(event.optString("objectId"))?.let { objectName(it) } ?: event.optString("objectId"))+
                    " · "+fmt.format(Date(event.optLong("time"))))
            }
        }
        label("Powiadomienie o dłuższym postoju")
        val stopMinutes=prefs.getInt("gps_stop_minutes",10)
        button("Próg postoju: "+stopMinutes+" min — zmień") {
            val options=intArrayOf(5,10,15,30)
            AlertDialog.Builder(this,dialogStyle)
                .setTitle("Próg postoju")
                .setSingleChoiceItems(arrayOf("5 min","10 min","15 min","30 min"),options.indexOf(stopMinutes)) { dialog,index ->
                    prefs.edit().putInt("gps_stop_minutes",options[index]).apply()
                    dialog.dismiss(); gpsScreen()
                }.setNegativeButton("Anuluj",null).show()
        }
        val trips=try { JSONArray(prefs.getString("gps_trip_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
        if(trips.length()>0) {
            label("Ostatnie przejazdy i postoje")
            for(i in trips.length()-1 downTo maxOf(0,trips.length()-5)) {
                val event=trips.optJSONObject(i) ?: continue
                val title=when(event.optString("event")) {
                    "TRIP_START" -> "Rozpoczęto przejazd"
                    "TRIP_STOP" -> "Zakończono przejazd"
                    else -> "Dłuższy postój"
                }
                label(title+" · "+fmt.format(Date(event.optLong("time"))))
            }
        }
        val coverageGaps=try { JSONArray(prefs.getString("gps_coverage_gaps","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
        if(coverageGaps.length()>0) {
            label("Przerwy w GPS (nie uzupełniamy czasu automatycznie):")
            for(i in coverageGaps.length()-1 downTo maxOf(0,coverageGaps.length()-3)) {
                val gap=coverageGaps.optJSONObject(i) ?: continue
                label(fmt.format(Date(gap.optLong("start")))+" – "+fmt.format(Date(gap.optLong("end"))))
            }
        }
        val fix=JSONObject(prefs.getString("gps_last_fix","{}") ?: "{}")
        val time=fix.optLong("time")
        if(time>0) {
            label("Ostatni pomiar: "+fmt.format(Date(time))+" · dokładność "+fix.optDouble("accuracy").toInt()+" m")
            val point=GpsWorkRules.Point(fix.optDouble("lat"),fix.optDouble("lon"),fix.optDouble("accuracy"),time)
            val places=rows(data(),"objects").mapNotNull { o ->
                val geo=prefs.getString("gps_object_"+o.optString("id"),null) ?: return@mapNotNull null
                try {
                    val j=JSONObject(geo)
                    GpsWorkRules.Place(o.optString("id"),GpsWorkRules.Kind.OBJECT,j.getDouble("lat"),j.getDouble("lon"),j.optDouble("radius",100.0))
                } catch(_:Exception) { null }
            }
            val nearest=GpsWorkRules.nearest(point,places)
            label(if(nearest==null) "Poza zapisanymi strefami obiektów." else
                "W strefie: "+(objectById(nearest.place.id)?.let { objectName(it) } ?: nearest.place.id))
        }
    }

    private fun settings() {
        base("Ustawienia"); screen="settings"; nav()
        root.setBackgroundColor(palette.background)
        label("WSZYSTKIE USTAWIENIA · SOLO")
        label("Z poziomu głównego dostępne są ustawienia wszystkich działów.")
        // Backward-compatible entry points retained for existing flows and tests.
        button("Portfel / rozliczenia (PIN)") { confirmPin { walletUnlocked=true; wallet() } }
        button("GENERUJ RAPORT SMS") { weeklySms() }
        button("Logi / eksport / import") { logs() }
        label("Kopie i synchronizacja")
        button("Google Disk") { driveSettings() }
        button("Centrum kopii / eksport / import") { logs() }
        label("Widżet i interfejs")
        button("Motyw aplikacji · "+appearance.title) {
            val themes=WorkMeterTheme.entries
            AlertDialog.Builder(this,dialogStyle).setTitle("Motyw aplikacji")
                .setSingleChoiceItems(themes.map { it.title }.toTypedArray(),appearance.ordinal) { dialog,index ->
                    val previousScroll = (root.parent as? ScrollView)?.scrollY ?: 0
                    prefs.edit().putString(WorkMeterTheme.PREF_KEY,themes[index].key).apply()
                    GateWidget.refresh(this)
                    dialog.dismiss()
                    settings()
                    (root.parent as? ScrollView)?.let { scroll -> scroll.doOnLayout { scroll.scrollTo(0, previousScroll) } }
                }.setNegativeButton("Anuluj",null).show()
        }
        button("Wygląd widżetu") { widgetSettings() }
        button("Język aplikacji") { language() }
        label("GPS i lokalizacja")
        label("Po naciśnięciu Rozpocznij dzień aplikacja jednorazowo sprawdza lokalizację, jeśli GPS jest dozwolony. Śledzenie w tle działa tylko podczas otwartego dnia.")
        if(prefs.getBoolean("gps_resume_pending",false)) button("GPS · wznowienie po restarcie") { gpsScreen() }
        button(if(GpsWorkdaySession.hasConsent(prefs)) "GPS WorkMeter: DOZWOLONY W PRACY — wyłącz" else "GPS WorkMeter: WYŁĄCZONY — włącz") {
            toggleGpsConsent()
            settings()
        }
        button("Geostrefy i lokalizacja GPS") { gpsScreen() }
        button("GPS · zasady rozliczania czasu") { gpsPaySettings() }
        label("Ustawienia działów")
        listOf("Mój czas","Obiekty","Zdjęcia","Materiały","Grafiki","Portfel").forEach { item ->
            button("⚙  $item  ›") { sectionSettings(item) }
        }
        button("Kopie zapasowe i odzyskiwanie") { backupScreen() }
        label("Informacje")
        button("O aplikacji") { about() }
    }
    private fun sectionSettings(section:String) {
        currentSection=section
        base("Ustawienia · $section"); screen="sectionSettings"; nav()
        root.setBackgroundColor(palette.background)
        when(section) {
            "Mój czas" -> {
                label("Czas pracy i raporty")
                button("Raport SMS") { weeklySms("settings") }
                button("Historia pracy") { report() }
            }
            "Obiekty" -> {
                label("Obiekty i lokalizacje GPS")
                button("Lista obiektów") { openScreen("objects") }
                button(if(GpsWorkdaySession.hasConsent(prefs)) "Wyłącz GPS WorkMeter" else "Włącz GPS WorkMeter") {
                    toggleGpsConsent()
                    sectionSettings("Obiekty")
                }
                button("GPS i geostrefy") { gpsScreen() }
            }
            "Zdjęcia" -> {
                label("Kolejka wysyłania i Google Drive")
                button("Kolejka zdjęć") { openScreen("queue") }
                button("Google Disk") { driveSettings() }
            }
            "Materiały" -> {
                label("Katalog i historia materiałów")
                button("Otwórz materiały") { openScreen("list:materials:") }
            }
            "Grafiki" -> {
                label("Analiza czasu")
                button("Grafiki i filtry") { charts() }
            }
            "Portfel" -> {
                label("Prywatne rozliczenia chronione PIN")
                button("Otwórz Portfel") { confirmPin { walletUnlocked=true; wallet() } }
            }
        }
        label("Dodatkowe przełączniki pojawią się po podłączeniu rzeczywistej logiki Android.")
        button("Wszystkie ustawienia") { settings() }
    }
    private fun about() {
        base("O aplikacji"); screen="about"; nav()
        val info=packageManager.getPackageInfo(packageName,0)
        label("WorkMeter")
        label("Wersja: "+info.versionName)
        label("Czas pracy, obiekty, materiały, zdjęcia, notatki i rozliczenia.")
        filters("aboutDiagnostics",caption="Szczegóły techniczne") { label("Pakiet: "+packageName+"\nSHA-1: "+signingFingerprint()) }
    }
    private fun widgetSettings() {
        base("Wygląd widżetu"); screen="widgetSettings"; nav()
        val widgetManager = AppWidgetManager.getInstance(this)
        val widgetIds = widgetManager.getAppWidgetIds(ComponentName(this, GateWidget::class.java)).sorted()
        if (widgetIds.isNotEmpty()) {
            label("Zainstalowane widżety")
            val appearanceStore = WidgetAppearanceStore(this)
            widgetIds.forEach { id ->
                val appearance = appearanceStore.read(id)
                val theme = appearance.theme?.title ?: getString(R.string.widget_follow_app)
                val options = widgetManager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                val profile = WidgetLayoutProfile.forBounds(width, height).displayName
                button(getString(R.string.widget_configure_instance, id, profile, theme)) {
                    startActivity(Intent(this, WidgetAppearanceActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                }
            }
        }
        label("Domyślna przezroczystość tła widżetu")
        val value=TextView(this).apply { setTextColor(palette.textPrimary); text=prefs.getInt("widgetTransparency",0).coerceIn(0,50).toString()+"% · maks. 50%" }
        root.addView(value)
        val previewFrame=FrameLayout(this).apply { setBackgroundColor(0xFF90A4AE.toInt()); setPadding(dp(8),dp(8),dp(8),dp(8)) }
        var previewSize=300 to 180
        lateinit var preview: View
        fun drawPreview() {
            previewFrame.removeAllViews()
            preview=GateWidget.render(this,WidgetLayoutProfile.forBounds(previewSize.first,previewSize.second),
                interactive=false,widthDp=previewSize.first,heightDp=previewSize.second).apply(this,previewFrame)
            previewFrame.addView(preview,FrameLayout.LayoutParams(dp(previewSize.first),dp(previewSize.second)))
        }
        drawPreview()
        for(size in listOf(144 to 68,180 to 170,300 to 90,300 to 180,300 to 260)) button("Podgląd ${size.first} × ${size.second} dp") {
            previewSize=size; drawPreview()
        }
        val slider=SeekBar(this).apply { max=50; progress=prefs.getInt("widgetTransparency",0).coerceIn(0,50); contentDescription="Przezroczystość tła widżetu" }
        slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean) {
                value.text=progress.toString()+"% · maks. 50%"; prefs.edit().putInt("widgetTransparency",progress).apply()
                preview.findViewById<ImageView>(R.id.wbackground).imageAlpha=WidgetAppearance(transparency=progress).backgroundAlpha(0)
            }
            override fun onStartTrackingTouch(bar:SeekBar?) {}
            override fun onStopTrackingTouch(bar:SeekBar?) { GateWidget.refresh(this@MainActivity) }
        })
        root.addView(slider,LinearLayout.LayoutParams(-1,dp(44)))
        label("0% — pełne tło. Do 50% — przezroczyste tło z kontrastową podkładką tekstu.")
        root.addView(previewFrame)
        label("Rozmiar zmienisz, przytrzymując widżet na pulpicie i przeciągając jego ramkę.")
    }
    private fun walletConfig(d:JSONObject):JSONObject = d.optJSONObject("walletSettings") ?: JSONObject().put("defaultRate","30.00").put("openingCents",0).also { d.put("walletSettings",it) }
    private fun defaultRate():BigDecimal = WalletRules.rate(walletConfig(data()).optString("defaultRate","30.00")) ?: BigDecimal("30.00")
    private fun migrateWallet() {
        val d=data(); var changed=false
        if(!d.has("walletSettings")) { walletConfig(d); changed=true }
        for(sh in rows(d,"shifts")+listOfNotNull(active(d))) if(!sh.has("rate")) { sh.put("rate","30.00"); changed=true }
        if(changed) prefs.edit().putString("data",d.toString()).apply()
    }
    private fun walletShifts(d:JSONObject) = rows(d,"shifts").mapNotNull {
        val rate=WalletRules.rate(it.optString("rate","30.00")) ?: return@mapNotNull null
        WalletRules.Shift(it.optLong("start"),it.optLong("end"),rate,privateIntervals(it))
    }
    private fun walletPayments(d:JSONObject) = rows(d,"payments").map { WalletRules.Payment(it.optString("periodFrom"),it.optString("periodTo"),it.optLong("cents"),it.optString("method")) }
    private fun money(cents:Long):String = BigDecimal.valueOf(cents,2).toPlainString().replace('.',',')+" zł"
    private fun walletRange():Pair<LocalDate,LocalDate> {
        val now=LocalDate.now()
        return try { LocalDate.parse(prefs.getString("walletFrom",now.withDayOfMonth(1).toString())) to LocalDate.parse(prefs.getString("walletTo",now.withDayOfMonth(now.lengthOfMonth()).toString())) }
        catch(_:Exception) { now.withDayOfMonth(1) to now.withDayOfMonth(now.lengthOfMonth()) }
    }
    private fun wallet() {
        if(!walletUnlocked) { settings(); confirmPin { walletUnlocked=true; wallet() }; return }
        base("Portfel"); screen="wallet"; nav()
        val (from,to)=walletRange()
        label("Okres: "+from.format(displayDate)+" – "+to.format(displayDate))
        filters("wallet") {
            button("Data od: "+from.format(displayDate)) { pickReportDate(from) { prefs.edit().putString("walletFrom",it.toString()).apply(); wallet() } }
            button("Data do: "+to.format(displayDate)) { pickReportDate(to) { prefs.edit().putString("walletTo",it.toString()).apply(); wallet() } }
            button("Bieżący miesiąc") { prefs.edit().remove("walletFrom").remove("walletTo").apply(); wallet() }
        }
        if(to.isBefore(from)) { label("Data do nie może być wcześniejsza niż Data od."); return }
        val d=data(); val config=walletConfig(d); val shifts=walletShifts(d); val payments=walletPayments(d)
        val summary=WalletRules.summary(shifts,payments,from,to)
        val periodLines = buildList {
            add((if (summary.conflictingRateMillis == 0L) "Naliczone: " else "Naliczone bez spornego czasu: ") + money(summary.earned))
            add("Otrzymano — gotówka: " + money(summary.cash))
            add("Otrzymano — karta: " + money(summary.card))
            if (summary.conflictingRateMillis > 0L) {
                add("Różne stawki w tym samym czasie: " + duration(summary.conflictingRateMillis) + ". Sprawdź zmiany w Mój czas.")
            }
            if (summary.overlapping > 0) {
                add("Saldo okresu niepełne: " + summary.overlapping + " wypłat obejmuje szerszy okres. Poszerz daty lub popraw przypisanie wypłaty.")
            } else if (summary.conflictingRateMillis == 0L) {
                add((if (summary.due >= 0) "Do wypłaty: " else "Nadpłata: ") + money(kotlin.math.abs(summary.due)))
            }
        }
        financeCard("Wybrany okres", *periodLines.toTypedArray())
        val historyEarnings = WalletRules.calculateEarnings(shifts)
        val global = historyEarnings.cents + config.optLong("openingCents") - payments.sumOf { it.cents }
        val expensesDue = rows(d, "expenses").sumOf { (it.optLong("cents") - it.optLong("reimbursedCents")).coerceAtLeast(0) }
        val historyLines = buildList {
            add("Zakupy firmowe do zwrotu (cała historia): " + money(expensesDue))
            if (historyEarnings.conflictingRateMillis == 0L) {
                add((if (global >= 0) "Łącznie do wypłaty (cała historia): " else "Łączna nadpłata (cała historia): ") + money(kotlin.math.abs(global)))
                val combined = global + expensesDue
                add((if (combined >= 0) "Razem do rozliczenia (wynagrodzenie i zakupy): " else "Łączna nadpłata (wynagrodzenie i zakupy): ") +
                    money(kotlin.math.abs(combined)))
            } else {
                add("Saldo całej historii niedostępne do czasu wyjaśnienia nakładających się stawek.")
            }
        }
        financeCard("Cała historia", *historyLines.toTypedArray())
        label("Naliczanie obejmuje zakończone zmiany, także przejazdy i zakupy. Otwarty dzień zostanie rozliczony po zakończeniu.")
        button("Zakupy za własne pieniądze / paragony") { expenseList() }
        button("+ Dodaj wypłatę") { walletDraft=JSONObject().put("id",UUID.randomUUID().toString()).put("date",LocalDate.now().toString()).put("periodFrom",from.toString()).put("periodTo",to.toString()).put("method","cash"); paymentForm() }
        label("Wypłaty przypisane do tego okresu")
        val selected=rows(d,"payments").filter { it.optString("periodFrom")<=to.toString() && it.optString("periodTo")>=from.toString() }.sortedByDescending { it.optString("date") }
        if(selected.isEmpty()) label("Brak wypłat w tym okresie.")
        for(payment in selected) {
            button(dateText(payment.optString("date"))+" · "+money(payment.optLong("cents"))+" · "+if(payment.optString("method")=="cash") "Gotówka" else "Karta") {
                walletDraft=JSONObject(payment.toString()).put("amount",BigDecimal.valueOf(payment.optLong("cents"),2).toPlainString()); paymentForm()
            }
            label("Za okres: "+dateText(payment.optString("periodFrom"))+" – "+dateText(payment.optString("periodTo")))
        }
    }
    private fun walletSettings() {
        if(!walletUnlocked) { settings(); confirmPin { walletUnlocked=true; walletSettings() }; return }
        base("Ustawienia portfela"); screen="walletSettings"; nav()
        button("Domyślna stawka: "+defaultRate().toPlainString()+" zł/h") { rateDialog(false) }
        button("Zmień stawkę zmian w tym okresie") { rateDialog(true) }
        button("Saldo początkowe: "+money(walletConfig(data()).optLong("openingCents"))) { openingDialog() }
    }
    private fun objectSettings() {
        base("Ustawienia obiektów"); screen="objectSettings"; nav()
        val state=objectFilters ?: ObjectFilterUiState.State(archived=prefs.getBoolean("archived_objects",false),grouped=prefs.getBoolean("objects_grouped",true))
        button("Grupowanie według miast: "+if(state.grouped) "Tak" else "Nie") {
            objectFilters=ObjectFilterUiState.toggleGrouping(state)
            prefs.edit().putBoolean("objects_grouped",!state.grouped).apply(); objectSettings()
        }
        button("Archiwalne obiekty: "+if(state.archived) "Tak" else "Nie") {
            objectFilters=ObjectFilterUiState.toggleArchive(state)
            prefs.edit().putBoolean("archived_objects",!state.archived).apply(); objectSettings()
        }
    }
    private fun financeCard(title: String, vararg values: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = background(palette.surface).apply { setStroke(dp(1), palette.border) }
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(palette.secondaryText)
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        })
        values.forEachIndexed { index, value ->
            val emphasized = index == values.lastIndex
            card.addView(TextView(this).apply {
                text = value
                textSize = if (emphasized) 18f else 16f
                setTextColor(palette.textPrimary)
                setPadding(0, dp(8), 0, 0)
                if (emphasized) setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun expenseList() {
        if(!walletUnlocked) { settings(); confirmPin { walletUnlocked=true; expenseList() }; return }
        base("Zakupy do zwrotu"); screen="expenses"; nav()
        label("Zakupy opłacone z własnych pieniędzy. Zwroty są rozliczane oddzielnie od wynagrodzenia.")
        val list=rows(data(),"expenses").sortedByDescending { recordDate(it) }
        financeCard("Podsumowanie zakupów", "Do zwrotu: " + money(list.sumOf { (it.optLong("cents") - it.optLong("reimbursedCents")).coerceAtLeast(0) }))
        button("+ Dodaj zakup za własne pieniądze") { newForm("expenses") }
        for(item in list) button(dateText(recordDate(item))+" · "+money(item.optLong("cents"))+" · "+if(item.optLong("reimbursedCents")>=item.optLong("cents")) "Zwrócono" else "Do zwrotu") {
            recordDetails("expenses",item.optString("id"),"expenses")
        }
    }
    private fun walletInput(caption:String,key:String,numeric:Boolean=false) {
        label(caption)
        root.addView(EditText(ContextThemeWrapper(this,dialogStyle)).apply {
            hint=caption; setText(walletDraft.optString(key)); setTextColor(palette.textPrimary); setHintTextColor(palette.secondaryText)
            background=background(palette.surface).apply { setStroke(dp(1),palette.border) }; setPadding(dp(10),dp(6),dp(10),dp(6))
            inputType=if(numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { walletDraft.put(key,s.toString()) }
                override fun afterTextChanged(s:Editable?) {}
            })
        },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
    }
    private fun paymentForm() {
        if(!walletUnlocked) { settings(); confirmPin { walletUnlocked=true; paymentForm() }; return }
        base("Wypłata"); screen="payment"; nav()
        for((key,title) in listOf("date" to "Data otrzymania","periodFrom" to "Za okres od","periodTo" to "Za okres do")) {
            button(title+": "+dateText(walletDraft.optString(key))) { pickReportDate(try { LocalDate.parse(walletDraft.optString(key)) } catch(_:Exception) { LocalDate.now() }) { walletDraft.put(key,it.toString()); paymentForm() } }
        }
        walletInput("Kwota (zł)","amount",true)
        button("Sposób: "+if(walletDraft.optString("method")=="cash") "Gotówka" else "Karta") {
            AlertDialog.Builder(this,dialogStyle).setTitle("Sposób wypłaty").setItems(arrayOf("Gotówka","Karta")) { _,index -> walletDraft.put("method",if(index==0) "cash" else "card"); paymentForm() }.show()
        }
        walletInput("Notatka (opcjonalnie)","note")
        button("Zapisz") {
            val cents=WalletRules.money(walletDraft.optString("amount"))
            val from=try { LocalDate.parse(walletDraft.optString("periodFrom")) } catch(_:Exception) { null }
            val to=try { LocalDate.parse(walletDraft.optString("periodTo")) } catch(_:Exception) { null }
            if(cents==null || cents<=0 || from==null || to==null || to.isBefore(from)) { toast("Podaj poprawną kwotę i okres wypłaty"); return@button }
            walletDraft.put("cents",cents); walletDraft.remove("amount")
            val d=data(); val list=arr(d,"payments"); val id=walletDraft.optString("id")
            val index=(0 until list.length()).find { list.getJSONObject(it).optString("id")==id }
            if(index==null) list.put(walletDraft) else list.put(index,walletDraft)
            save(d,"PAYMENT_SAVE",id); wallet()
        }
        val id=walletDraft.optString("id")
        if(rows(data(),"payments").any { it.optString("id")==id }) button("Usuń wypłatę (PIN)") {
            confirmPin { AlertDialog.Builder(this,dialogStyle).setMessage("Usunąć tę wypłatę z rozliczenia?").setNegativeButton("Anuluj",null).setPositiveButton("Usuń") { _, _ ->
                val d=data(); val next=JSONArray(); rows(d,"payments").filter { it.optString("id")!=id }.forEach { next.put(it) }; d.put("payments",next); save(d,"PAYMENT_DELETE",id); wallet()
            }.show() }
        }
    }
    private fun rateDialog(existing:Boolean) {
        val input=EditText(ContextThemeWrapper(this,dialogStyle)).apply { hint="Stawka zł/h"; setText(defaultRate().toPlainString()); inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val dialog=AlertDialog.Builder(this,dialogStyle).setTitle(if(existing) "Stawka za wybrany okres" else "Stawka dla nowych zmian")
            .setMessage(if(existing) "Zmienisz stawkę zakończonych zmian rozpoczynających się w wybranym okresie." else "Zmiana dotyczy tylko nowych dni pracy. Starsze zmiany zachowają swoje stawki.")
            .setView(input).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val rate=WalletRules.rate(input.text.toString())
            if(rate==null) { input.error="Podaj stawkę większą od zera"; return@setOnClickListener }
            val d=data()
            if(existing) { val (from,to)=walletRange(); rows(d,"shifts").filter { localDate(it.optLong("start")) in from..to }.forEach { it.put("rate",rate.toPlainString()) } }
            else walletConfig(d).put("defaultRate",rate.toPlainString())
            save(d,"RATE_EDIT",if(existing) "period" else "default"); dialog.dismiss(); wallet()
        } }; dialog.show()
    }
    private fun openingDialog() {
        val field=EditText(ContextThemeWrapper(this,dialogStyle)).apply { hint="Saldo początkowe zł"; setText(BigDecimal.valueOf(walletConfig(data()).optLong("openingCents"),2).toPlainString()); inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val dialog=AlertDialog.Builder(this,dialogStyle).setTitle("Saldo sprzed zapisanej historii").setMessage("Dodatnia kwota: zaległa wypłata. Ujemna: wcześniejsza nadpłata. Uwzględniana tylko w saldzie całej historii.").setView(field).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val amount=WalletRules.money(field.text.toString(),true)
            if(amount==null) { field.error="Nieprawidłowa kwota"; return@setOnClickListener }
            val d=data(); walletConfig(d).put("openingCents",amount); save(d,"OPENING_BALANCE_EDIT",""); dialog.dismiss(); wallet()
        } }; dialog.show()
    }
    private fun gpsTripCategories() {
        base("GPS · kategorie przejazdów"); screen="gpsCategories"; nav()
        label("Wybierz kategorię przejazdu. Nie zmienia to zapisanych godzin pracy.")
        val events=try { JSONArray(prefs.getString("gps_trip_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
        val list=mutableListOf<GpsShiftEstimate.Event>()
        for(i in 0 until events.length()) {
            val event=events.optJSONObject(i) ?: continue
            list.add(GpsShiftEstimate.Event(event.optString("event"),event.optLong("time")))
        }
        val trips=GpsTripBilling.pair(list,emptyMap()).takeLast(25).asReversed()
        val categories=listOf(
            GpsTripBilling.Category.UNKNOWN to "Nieustalony",
            GpsTripBilling.Category.OUTBOUND to "Dojazd z domu",
            GpsTripBilling.Category.RETURN to "Powrót do domu",
            GpsTripBilling.Category.BETWEEN to "Między obiektami",
            GpsTripBilling.Category.SHOPPING to "Zakupy materiałów",
            GpsTripBilling.Category.PRIVATE to "Prywatny"
        )
        if(trips.isEmpty()) label("Brak zakończonych przejazdów GPS.")
        for(trip in trips) {
            val key="gps_trip_category_"+trip.startMs
            val current=categories.find { it.first.name==prefs.getString(key,"UNKNOWN") }?.second ?: "Nieustalony"
            button(fmt.format(Date(trip.startMs))+" · "+duration(trip.endMs-trip.startMs)+" · "+current) {
                AlertDialog.Builder(this,dialogStyle).setTitle("Rodzaj przejazdu")
                    .setItems(categories.map { it.second }.toTypedArray()) { _,index ->
                        prefs.edit().putString(key,categories[index].first.name).apply()
                        gpsTripCategories()
                    }.setNegativeButton("Anuluj",null).show()
            }
        }
    }

    private fun gpsPaySettings() {
        base("GPS · rozliczanie czasu"); screen="gpsPay"; nav()
        label("Ustawienia dotyczą tylko szacunków GPS. Nie zmieniają zapisanych godzin pracy.")
        button("Przypisz kategorie przejazdów GPS") { gpsTripCategories() }
        val choices=listOf(
            "gps_pay_outbound" to "Dojazd z domu",
            "gps_pay_return" to "Powrót do domu",
            "gps_pay_between" to "Przejazdy między obiektami",
            "gps_pay_shopping" to "Zakupy materiałów",
            "gps_pay_stop" to "Dłuższe postoje"
        )
        for((key,title) in choices) {
            val selected=prefs.getBoolean(key,key=="gps_pay_between" || key=="gps_pay_shopping")
            button(title+": "+if(selected) "PŁATNY" else "NIEPŁATNY") {
                prefs.edit().putBoolean(key,!selected).apply(); gpsPaySettings()
            }
        }
        label("Nieznane przejazdy są domyślnie niepłatne. Ich rodzaju nie da się pewnie ustalić wyłącznie z GPS.")
        button("Mój czas") { report() }
    }
    private fun gpsEstimate(sh:JSONObject,endMs:Long):GpsShiftEstimate.Summary {
        val events=try { JSONArray(prefs.getString("gps_trip_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
        val list=mutableListOf<GpsShiftEstimate.Event>()
        for(i in 0 until events.length()) {
            val event=events.optJSONObject(i) ?: continue
            list.add(GpsShiftEstimate.Event(event.optString("event"),event.optLong("time")))
        }
        val trips=GpsTripBilling.pair(list,emptyMap()).map { trip ->
            val category=GpsTripBilling.Category.entries.find {
                it.name==prefs.getString("gps_trip_category_"+trip.startMs,"UNKNOWN")
            } ?: GpsTripBilling.Category.UNKNOWN
            trip.copy(category=category)
        }
        val totals=GpsTripBilling.calculate(trips,GpsShiftEstimate.Window(sh.optLong("start"),endMs),
            GpsTripBilling.Rules(
                outbound=prefs.getBoolean("gps_pay_outbound",false),
                returnTrip=prefs.getBoolean("gps_pay_return",false),
                between=prefs.getBoolean("gps_pay_between",true),
                shopping=prefs.getBoolean("gps_pay_shopping",true)))
        return GpsShiftEstimate.Summary(totals.elapsedMs,totals.payableMs,totals.trips)
    }
    private fun gpsTimeSummary() {
        base("GPS · Mój czas"); screen = "gpsTimeSummary"; nav()
        val journal = data()
        val currentShift = active(journal) ?: rows(journal, "shifts").lastOrNull()
        if(currentShift!=null) {
            val end=if(currentShift.has("end")) currentShift.optLong("end") else System.currentTimeMillis()
            val estimate=gpsEstimate(currentShift,end)
            label("GPS · szacunkowy czas przejazdów: "+duration(estimate.actualTravelMs)+
                " · szacowany czas rozliczany: "+duration(estimate.payableTravelMs))
            label("Wykryto "+estimate.completeTrips+" zakończonych przejazdów. Szacunek GPS nie zmienia ewidencji pracy.")
            val tripEvents=try { JSONArray(prefs.getString("gps_trip_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
            val observed=mutableListOf<GpsShiftEstimate.Event>()
            for(i in 0 until tripEvents.length()) {
                val entry=tripEvents.optJSONObject(i) ?: continue
                observed.add(GpsShiftEstimate.Event(entry.optString("event"),entry.optLong("time")))
            }
            val fixArray=try { JSONArray(prefs.getString("gps_fix_times","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
            val fixes=(0 until fixArray.length()).map { fixArray.optLong(it) }
            val review=GpsShiftReconciliation.review(observed,
                GpsShiftReconciliation.Window(currentShift.optLong("start"),end),fixes)
            label("GPS · kontrola jakości danych: "+
                if(review.requiresReview) "wymaga sprawdzenia" else "bez wykrytych problemów")
            label("Braki pomiarów: "+review.missingLocationIntervals+
                " · niezakończone przejazdy: "+review.incompleteTripStarts+
                " · nakładające się przejazdy: "+review.overlappingTrips)
            label("Przejazdy poza zmianą: "+duration(review.travelOutsideMs)+
                ". GPS nie zmienia zapisanych godzin ani wypłat.")

        } else label("Brak zmiany do sprawdzenia.")
    }

    private fun report() {
        base("Mój czas"); screen="report"; nav()
        val from=prefs.getString("reportFrom","").orEmpty(); val to=prefs.getString("reportTo","").orEmpty()
        fun setPeriod(key:String,date:LocalDate) {
            val f=if(key=="reportFrom") date.toString() else from
            val t=if(key=="reportTo") date.toString() else to
            if(f.isNotBlank() && t.isNotBlank() && f>t) { toast("Data do nie może być wcześniejsza niż data od"); return }
            prefs.edit().putString(key,date.toString()).apply(); report()
        }
        filters("report",from.isNotBlank() || to.isNotBlank()) {
            button("Data od: "+if(from.isBlank()) "wszystkie" else dateText(from)) {
                pickReportDate(if(from.isBlank()) LocalDate.now() else LocalDate.parse(from)) { setPeriod("reportFrom",it) }
            }
            button("Data do: "+if(to.isBlank()) "wszystkie" else dateText(to)) {
                pickReportDate(if(to.isBlank()) LocalDate.now() else LocalDate.parse(to)) { setPeriod("reportTo",it) }
            }
            button("Wyczyść filtry") { prefs.edit().remove("reportFrom").remove("reportTo").apply(); report() }
            val smsRangeStart = from.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val smsRangeEnd = to.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (smsRangeStart != null && smsRangeEnd != null && !smsRangeEnd.isBefore(smsRangeStart)) {
                button("GENERUJ RAPORT SMS") {
                    smsFrom = smsRangeStart
                    smsTo = smsRangeEnd
                    weeklySms("report")
                }
            }
        }
        val journal=data()
        if(journal.optJSONObject("deletedShiftUndo")!=null) button("Przywróć usuniętą zmianę") {
            confirmAction("Przywrócić ostatnio usuniętą zmianę?") { undoDeleteShift() }
        }
        val shifts=rows(journal,"shifts")
        val entries=shifts.flatMap { shift -> HistoryRules.slices(shift.optLong("start"),shift.optLong("end"),ZoneId.systemDefault()).map { shift to it } }
            .filter { DomainRules.inRange(it.second.day,from,to) }
        val total=reportWorkedMillis(entries)
        label("Łączny czas: "+duration(total))
        if(total!=entries.sumOf { workedMillis(it.first,it.second.start,it.second.end) })
            label("Nakładający się czas uwzględniono tylko raz.")
        if(entries.isEmpty()) label("Brak zmian w wybranym okresie")
        for((day,items) in entries.groupBy { it.second.day }.toSortedMap(reverseOrder())) {
            val parent=root
            val card=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(6))
                background=background(palette.surface).apply { setStroke(dp(1), palette.border) }
            }
            parent.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(12) })
            root=card
            try {
                label(dateText(day)+" · "+LocalDate.parse(day).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL,Locale.forLanguageTag("pl")))
                label("Czas dnia: "+duration(reportWorkedMillis(items)))
                for((shift,slice) in items.sortedByDescending { it.second.start }) {
                    button(fmt.format(Date(slice.start))+" → "+fmt.format(Date(slice.end))+"  ("+duration(workedMillis(shift,slice.start,slice.end))+")") { shiftDetails(shift.optString("id")) }
                    button("Edytuj czas") { confirmAction("Edytować godziny tej zmiany?") { editShift(shift.optString("id")) } }
                    button("Usuń zmianę") { deleteShift(shift.optString("id")) }
                }
            } finally { root=parent }
        }
    }
    private fun shiftDetails(id:String) {
        val shift=rows(data(),"shifts").singleOrNull { id.isNotBlank() && it.optString("id")==id } ?: run {
            toast("Nie można jednoznacznie znaleźć zmiany"); report(); return
        }
        base("Szczegóły dnia"); screen="shift:"+id; nav()
        val start=shift.optLong("start"); val end=shift.optLong("end")
        label("Rozpoczęcie pracy: "+fmt.format(Date(start)))
        val stages=rows(shift,"stages").sortedBy { it.optLong("start") }
        if(stages.isEmpty()) financeCard("Nieprzypisane", "Brak zapisanej historii etapów dla tej zmiany. Nie można odtworzyć obiektów ani przejazdów.",fmt.format(Date(start))+" → "+fmt.format(Date(end)),"Czas: "+duration(end-start))
        var cursor=start
        for(stage in stages) {
            val from=maxOf(start,stage.optLong("start")); val to=minOf(end,stage.optLong("end"))
            if(to<=from) continue
            if(from>cursor) financeCard("Nieprzypisane",fmt.format(Date(cursor))+" → "+fmt.format(Date(from)),"Czas: "+duration(from-cursor))
            val kind=stage.optString("kind","Obiekt")
            val placeName=stage.optString("object").ifBlank {
                objectById(stage.optString("objectId"))?.let(::objectName) ?: "Miejsce bez nazwy"
            }
            val name=when(kind) { "Obiekt" -> placeName; "Biuro" -> "Biuro · "+placeName; else -> kind }

            if(kind=="Zakupy" && stage.optString("shoppingRecordId").isNotBlank()) button("Zakupy / paragony") { recordDetails("notes",stage.optString("shoppingRecordId"),screen) }
            val lines=mutableListOf(fmt.format(Date(from))+" → "+fmt.format(Date(to)),"Czas: "+duration(to-from))
            if(kind=="Przejazd") lines.add("Z: "+stage.optString("fromObject","—")+"\nDo: "+stage.optString("toObject","—"))
            if(kind==PrivateTimeRules.KIND) lines.add("Czas prywatny · poza wynagrodzeniem")
            financeCard(name.split(" · ").take(2).joinToString(" · "),*lines.toTypedArray())
            cursor=maxOf(cursor,to)
        }
        if(stages.isNotEmpty() && cursor<end) financeCard("Nieprzypisane",fmt.format(Date(cursor))+" → "+fmt.format(Date(end)),"Czas: "+duration(end-cursor))
        val diagnostics=WorkStageHistory.diagnose(start,end,stages.map {
            WorkStageHistory.Stage(it.optLong("start"),it.optLong("end"),it.optString("kind"),it.optString("objectId"))
        })
        if(diagnostics.overlapMillis>0) label("Nakładające się etapy: "+duration(diagnostics.overlapMillis))
        if(diagnostics.invalidStages>0) label("Nieprawidłowe etapy: "+diagnostics.invalidStages)
        label("Zakończenie pracy: "+fmt.format(Date(end)))
        label("Łączny czas: "+duration(workedMillis(shift,start,end)))
        button("Edytuj etapy") { selectStageEdit(id) }
        if(rows(shift,"stageEdits").isNotEmpty()) button("Cofnij zmianę etapów") {
            confirmAction("Przywrócić poprzednią historię etapów?") { undoStageEdit(id) }
        }
        button("Edytuj czas") { confirmAction { editShift(id) } }
        button("Usuń zmianę") { deleteShift(id) }
    }
    private fun stageInterval(row: JSONObject) =
        WorkShiftIntegrity.Interval(row.optLong("start", -1L), row.optLong("end", -1L))

    private fun selectStageEdit(id: String) {
        val shift = rows(data(), "shifts").singleOrNull { it.optString("id") == id } ?: return
        val stages = shift.optJSONArray("stages") ?: JSONArray()
        val indices = (0 until stages.length()).filter { stages.optJSONObject(it) != null }
        val choices = listOf("Dodaj etap") + indices.map {
            val stage = stages.getJSONObject(it)
            stage.optString("kind", "Obiekt") + " · " + fmt.format(Date(stage.optLong("start")))
        }
        AlertDialog.Builder(this, R.style.WorkMeterDialogTheme).setTitle("Etapy pracy")
            .setItems(choices.toTypedArray()) { _, which ->
                editRecordedStage(id, stageInterval(shift), stages.toString(), indices.getOrNull(which - 1))
            }.setNegativeButton("Anuluj", null).show()
    }

    private fun editRecordedStage(
        shiftId: String, bounds: WorkShiftIntegrity.Interval, snapshot: String, index: Int?
    ) {
        if(!WorkShiftIntegrity.isValid(bounds.start,bounds.end)) { toast("Najpierw popraw godziny zmiany"); return }
        val stages = JSONArray(snapshot)
        val original = index?.let { stages.optJSONObject(it) }
        val originalKind = original?.optString("kind","Obiekt")
        val kinds = (listOf("Obiekt", "Biuro", "Przejazd", "Zakupy", PrivateTimeRules.KIND, "Nieprzypisane") +
            listOfNotNull(originalKind)).distinct()
        val zone = ZoneId.systemDefault()
        val startMs = original?.optLong("start", bounds.start)?.coerceIn(bounds.start,bounds.end) ?: bounds.start
        val endMs = original?.optLong("end", bounds.end)?.coerceIn(bounds.start,bounds.end) ?: bounds.end
        var start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDateTime()
        var end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDateTime()
        var objectId = original?.optString("objectId").orEmpty()
        var objectLabel = original?.optString("object").orEmpty()
        val layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),0,dp(16),0) }
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, kinds)
            setSelection(kinds.indexOf(originalKind ?: "Obiekt"))
            contentDescription = "Rodzaj etapu"
        }
        val objectButton = Button(this)
        val startDate = Button(this)
        val startTime = Button(this)
        val endDate = Button(this)
        val endTime = Button(this)
        fun refresh() {
            objectButton.text = "Obiekt: " + objectLabel.ifBlank { "wybierz" }
            startDate.text = "Data od: " + start.toLocalDate().format(displayDate)
            startTime.text = "Godzina od: " + start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            endDate.text = "Data do: " + end.toLocalDate().format(displayDate)
            endTime.text = "Godzina do: " + end.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        objectButton.setOnClickListener {
            chooseObject(false) { obj ->
                if(obj!=null) { objectId=obj.optString("id"); objectLabel=objectName(obj); refresh() }
            }
        }
        startDate.setOnClickListener { pickReportDate(start.toLocalDate()) { start=LocalDateTime.of(it,start.toLocalTime()); refresh() } }
        endDate.setOnClickListener { pickReportDate(end.toLocalDate()) { end=LocalDateTime.of(it,end.toLocalTime()); refresh() } }
        startTime.setOnClickListener { TimePickerDialog(this,{ _,h,m -> start=start.withHour(h).withMinute(m); refresh() },start.hour,start.minute,true).show() }
        endTime.setOnClickListener { TimePickerDialog(this,{ _,h,m -> end=end.withHour(h).withMinute(m); refresh() },end.hour,end.minute,true).show() }
        listOf(category, objectButton, startDate, startTime, endDate, endTime).forEach { layout.addView(it) }
        refresh()
        val builder = AlertDialog.Builder(this,R.style.WorkMeterDialogTheme)
            .setTitle(if(index==null) "Dodaj etap" else "Edytuj etap")
            .setView(ScrollView(this).apply { addView(layout) })
            .setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",null)
        if(index!=null) builder.setNeutralButton("Usuń") { _,_ ->
            confirmAction("Usunąć ten etap z historii zmiany?") {
                stages.remove(index)
                saveStageHistory(shiftId,bounds,snapshot,stages)
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val from = WorkShiftIntegrity.resolveEditTime(start,zone,startMs)
                val to = WorkShiftIntegrity.resolveEditTime(end,zone,endMs)
                val others = (0 until stages.length()).filter { it != index }
                    .mapNotNull { stages.optJSONObject(it)?.let(::stageInterval) }
                if(from==null || to==null ||
                    !WorkShiftIntegrity.canSaveStage(bounds,WorkShiftIntegrity.Interval(from,to),others)) {
                    toast("Etap musi mieścić się w zmianie i nie może nakładać się na inne etapy")
                    return@setOnClickListener
                }
                val kind = kinds[category.selectedItemPosition]
                val previousObject = original?.takeIf { WorkPlaceRules.isOnSiteStage(originalKind) }?.optString("objectId")
                val preservesLegacyObject = originalKind == kind && objectId == previousObject
                val references = rows(data(),"objects").map {
                    ObjectIntegrityRules.ObjectRef(it.optString("id"),it.optBoolean("archived"))
                }
                if(WorkPlaceRules.isOnSiteStage(kind) && !preservesLegacyObject && !ObjectIntegrityRules.canAssign(objectId,references)) {
                    toast("Wybierz aktywny obiekt")
                    return@setOnClickListener
                }
                if(kind=="Biuro" && !preservesLegacyObject &&
                    objectById(objectId)?.let(::placeType)!=WorkPlaceRules.Type.OFFICE) {
                    toast("Wybierz miejsce typu Biuro")
                    return@setOnClickListener
                }
                val row = if(original!=null && originalKind==kind) JSONObject(original.toString()) else JSONObject()
                row.put("start",from).put("end",to).put("kind",kind)
                if(WorkPlaceRules.isOnSiteStage(kind)) row.put("objectId",objectId).put("object",objectLabel)
                if(index==null) stages.put(row) else stages.put(index,row)
                if(saveStageHistory(shiftId,bounds,snapshot,stages)) dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun saveStageHistory(
        shiftId: String, bounds: WorkShiftIntegrity.Interval, snapshot: String, stages: JSONArray
    ): Boolean {
        val d = data()
        val shift = rows(d,"shifts").singleOrNull { it.optString("id")==shiftId } ?: return false
        if(stageInterval(shift)!=bounds || (shift.optJSONArray("stages") ?: JSONArray()).toString()!=snapshot) {
            toast("Historia etapów uległa zmianie. Otwórz edycję ponownie")
            return false
        }
        arr(shift,"stageEdits").put(JSONObject().put("start",bounds.start).put("end",bounds.end)
            .put("before",JSONArray(snapshot)).put("after",stages))
        shift.put("stages",stages)
        save(d,"EDIT_STAGES",shiftId)
        shiftDetails(shiftId)
        return true
    }

    private fun undoStageEdit(shiftId: String) {
        val d = data()
        val shift = rows(d,"shifts").singleOrNull { it.optString("id")==shiftId } ?: return
        val edits = shift.optJSONArray("stageEdits") ?: return
        val edit = edits.optJSONObject(edits.length()-1) ?: return
        val before = edit.optJSONArray("before") ?: return
        if(stageInterval(edit)!=stageInterval(shift) ||
            edit.optJSONArray("after")?.toString()!=shift.optJSONArray("stages")?.toString()) {
            toast("Zmiana lub historia etapów została zmodyfikowana")
            return
        }
        edits.remove(edits.length()-1)
        shift.put("stages",before)
        save(d,"UNDO_EDIT_STAGES",shiftId)
        shiftDetails(shiftId)
    }

    private fun uniqueShiftIndex(shifts: JSONArray, id: String): Int? =
        HistoryRules.uniqueRecordIndex((0 until shifts.length()).map {
            shifts.optJSONObject(it)?.optString("id")
        },id)

    private fun deleteShift(id:String) {
        val original = data().optJSONArray("shifts") ?: return
        val originalIndex = uniqueShiftIndex(original,id) ?: run {
            toast("Nie można jednoznacznie znaleźć zmiany"); return
        }
        val expected = original.getJSONObject(originalIndex).toString()
        confirmAction("Usunąć zmianę z raportu i zestawienia godzin? Materiały, zdjęcia i notatki pozostaną.") {
            val d=data()
            val shifts=d.optJSONArray("shifts") ?: return@confirmAction
            val index=uniqueShiftIndex(shifts,id)
            if(index==null || shifts.optJSONObject(index)?.toString()!=expected) {
                toast("Zmiana została zmodyfikowana. Otwórz raport ponownie.")
                return@confirmAction
            }
            val removed=shifts.remove(index) as JSONObject
            d.put("deletedShiftUndo",JSONObject().put("index",index).put("shift",removed))
            save(d,"DELETE_SHIFT",id); report()
        }
    }

    private fun undoDeleteShift() {
        val d=data()
        val undo=d.optJSONObject("deletedShiftUndo") ?: return
        val row=undo.optJSONObject("shift") ?: return
        val id=row.optString("id")
        val shifts=arr(d,"shifts")
        val active=active(d)
        if(id.isBlank() || (0 until shifts.length()).any { shifts.optJSONObject(it)?.optString("id")==id } ||
            active?.optString("id")==id) {
            toast("Zmiana z tym identyfikatorem już istnieje")
            return
        }
        val intervals=rows(d,"shifts").map(::stageInterval).toMutableList()
        if(active!=null) intervals.add(WorkShiftIntegrity.Interval(active.optLong("start",-1L),Long.MAX_VALUE))
        if(!WorkShiftIntegrity.canSave(stageInterval(row),intervals)) {
            toast("Nie można przywrócić zmiany, ponieważ jej czas koliduje z historią")
            return
        }
        val index=undo.optInt("index",shifts.length()).coerceIn(0,shifts.length())
        val restored=JSONArray()
        for(i in 0..shifts.length()) {
            if(i==index) restored.put(row)
            if(i<shifts.length()) restored.put(shifts.get(i))
        }
        d.put("shifts",restored).remove("deletedShiftUndo")
        save(d,"UNDO_DELETE_SHIFT",id); report()
    }
    private fun editShift(id:String) {
        val d=data()
        val s=rows(d,"shifts").singleOrNull { it.optString("id")==id } ?: return
        val zone=ZoneId.systemDefault()
        val original=WorkShiftIntegrity.Interval(s.optLong("start"),s.optLong("end"))
        var start=Instant.ofEpochMilli(original.start).atZone(zone).toLocalDateTime()
        var end=Instant.ofEpochMilli(original.end).atZone(zone).toLocalDateTime()
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),0,dp(16),0) }
        val dateStart=Button(this)
        val dateEnd=Button(this)
        val timeStart=Button(this)
        val timeEnd=Button(this)
        fun refresh() {
            dateStart.text="Data rozpoczęcia: "+start.toLocalDate().format(displayDate)
            dateEnd.text="Data zakończenia: "+end.toLocalDate().format(displayDate)
            timeStart.text="Rozpoczęcie: "+start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            timeEnd.text="Zakończenie: "+end.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        dateStart.setOnClickListener { pickReportDate(start.toLocalDate()) { start=LocalDateTime.of(it,start.toLocalTime()); refresh() } }
        dateEnd.setOnClickListener { pickReportDate(end.toLocalDate()) { end=LocalDateTime.of(it,end.toLocalTime()); refresh() } }
        timeStart.setOnClickListener { TimePickerDialog(this,{ _,h,m -> start=start.withHour(h).withMinute(m); refresh() },start.hour,start.minute,true).show() }
        timeEnd.setOnClickListener { TimePickerDialog(this,{ _,h,m -> end=end.withHour(h).withMinute(m); refresh() },end.hour,end.minute,true).show() }
        listOf(dateStart,dateEnd,timeStart,timeEnd).forEach { layout.addView(it) }; refresh()
        val dialog=AlertDialog.Builder(this,dialogStyle).setTitle("Edytuj zmianę").setView(layout)
            .setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val st=WorkShiftIntegrity.resolveEditTime(start,zone,original.start)
                val en=WorkShiftIntegrity.resolveEditTime(end,zone,original.end)
                if(st==null || en==null) {
                    toast("Wybrana godzina nie istnieje w tej strefie czasowej")
                    return@setOnClickListener
                }
                if(en<=st) { toast("Koniec musi być po rozpoczęciu"); return@setOnClickListener }
                val current=data()
                val saved=rows(current,"shifts")
                val row=saved.singleOrNull { it.optString("id")==id } ?: return@setOnClickListener
                if(row.optLong("start")!=original.start || row.optLong("end")!=original.end) {
                    toast("Zmiana została zaktualizowana. Otwórz edycję ponownie")
                    return@setOnClickListener
                }
                val proposed=WorkShiftIntegrity.Interval(st,en)
                val otherShifts=saved.filter { it.optString("id")!=id }
                    .map { WorkShiftIntegrity.Interval(it.optLong("start"),it.optLong("end")) }
                    .toMutableList()
                active(current)?.let { otherShifts.add(WorkShiftIntegrity.Interval(it.optLong("start",-1L),Long.MAX_VALUE)) }
                if(!WorkShiftIntegrity.canSave(proposed,otherShifts)) {
                    toast("Zmiana nakłada się na inną zmianę lub historia zawiera nieprawidłowe daty")
                    return@setOnClickListener
                }
                val stages=rows(row,"stages").map { WorkShiftIntegrity.Interval(it.optLong("start"),it.optLong("end")) }
                if(!WorkShiftIntegrity.preservesRecordedStages(original,proposed,stages)) {
                    toast("Nowy zakres pominąłby część zapisanego etapu")
                    return@setOnClickListener
                }
                arr(row,"edits").put(JSONObject().put("at",System.currentTimeMillis()).put("start",row.optLong("start")).put("end",row.optLong("end")))
                row.put("start",st).put("end",en); save(current,"EDIT_SHIFT",id); dialog.dismiss(); report()
            }
        }
        dialog.show()
    }
    private fun logs() {
        base("Logi i kopia zapasowa"); screen = "logs"; nav()
        label("Kopia JSON zawiera dane i odnośniki. Zachowaj również zdjęcia na Google Disk; zdjęcia lokalne nie są dołączane do JSON.")
        label(prefs.getString("backupStatus","Kopia Google Disk nie została jeszcze wykonana").orEmpty())
        button("Eksportuj dziennik na Google Disk") { confirmAction("Zapisać kopię dziennika w WorkMeter / Backup na osobistym Google Disk?") { pendingUpload="backup"; authorizeDrive() } }
        button("Zapisz kopię lokalną JSON") { confirmAction { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="application/json"; putExtra(Intent.EXTRA_TITLE,"WorkMeter-backup.json") },100) } }
        button("Importuj dziennik JSON") { confirmAction { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="application/json" },101) } }
        val list=arr(data(),"logs"); for(i in list.length()-1 downTo 0) { val x=list.optJSONObject(i)?:continue; label("${x.optString("at")} | ${x.optString("type")} | ${x.optString("detail")}") }
    }
    @Deprecated("Legacy activity result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,dataIntent:Intent?) {
        super.onActivityResult(requestCode,resultCode,dataIntent)
        if(requestCode==410) {
            val callback=backupDocumentResult; backupDocumentResult=null
            callback?.invoke(if(resultCode==RESULT_OK) dataIntent?.data else null); return
        }
        if(requestCode==203) {
            if(dataIntent!=null) {
                try { authorized(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(dataIntent)) }
                catch(e:Exception) { driveFailure(e) }
            } else {
                backupDriveResult?.invoke(null); backupDriveResult=null
                pendingUpload=""; prefs.edit().putString("driveError","Połączenie anulowane przed udzieleniem dostępu. Spróbuj ponownie.").apply(); driveSettings()
            }
            return
        }
        if (requestCode == 201 || requestCode == 202) {
            if (resultCode == RESULT_OK) {
                if (requestCode == 201) {
                    val uris = mutableListOf<Uri>()
                    dataIntent?.clipData?.let { clips -> for (i in 0 until clips.itemCount) uris.add(clips.getItemAt(i).uri) }
                    if (uris.isEmpty()) dataIntent?.data?.let { uris.add(it) }
                    for (u in uris) {
                        try {
                            val folder=File(filesDir,"photos").apply { mkdirs() }
                            val ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(u).orEmpty()) ?: "jpg"
                            val local=File(folder,UUID.randomUUID().toString()+"."+ext)
                            try { contentResolver.openInputStream(u)!!.use { input -> local.outputStream().use { input.copyTo(it) } }; arr(draft,"photos").put(Uri.fromFile(local).toString()) }
                            catch(e:Exception) { local.delete(); throw e }
                        }
                        catch (_:Exception) { toast("Nie można zachować dostępu do zdjęcia. Wybierz inny plik.") }
                    }
                } else cameraPath?.let { arr(draft,"photos").put(Uri.fromFile(File(it)).toString()) }
                prefs.edit().putString("draft_$formKind",draft.toString()).apply()
            }
            cameraPath=null
            renderForm()
            return
        }
        if(resultCode!=RESULT_OK) return
        val uri=dataIntent?.data?:return
        try {
            if(requestCode==100) { contentResolver.openOutputStream(uri)?.use { it.write(exportData().toString(2).toByteArray(Charsets.UTF_8)) }; toast("Kopia zapisana") }
            if(requestCode==101) {
                if(uploading) { toast("Poczekaj na zakończenie przesyłania przed importem"); return }
                val content=contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val imported=JSONObject(content)
                validateImport(imported)
                val expectedJournal=prefs.getString("data","{}")
                AlertDialog.Builder(this,R.style.WorkMeterDialogTheme)
                    .setTitle("Przywrócić kopię?").setMessage("Obecne dane zostaną zastąpione.")
                    .setNegativeButton("Anuluj",null).setPositiveButton("Importuj") { _, _ ->
                        if(uploading || prefs.getString("data","{}")!=expectedJournal) {
                            toast("Dane zmieniły się podczas podglądu. Otwórz kopię ponownie.")
                            return@setPositiveButton
                        }
                        val consent=GpsWorkdaySession.hasConsent(prefs)
                        val transparency=imported.optJSONObject("wtSettings")
                            ?.optInt("widgetTransparency",prefs.getInt("widgetTransparency",0))
                            ?.coerceIn(0,100) ?: prefs.getInt("widgetTransparency",0)
                        val restored=prefs.edit().putString("preImportBackup",expectedJournal)
                            .putString("data",imported.toString()).putInt("widgetTransparency",transparency)
                            .putBoolean(GpsWorkdaySession.CONSENT,consent)
                            .putBoolean("gps_enabled",false).putBoolean("gps_resume_pending",false)
                            .putBoolean("gps_service_active",false).remove("gps_session_start")
                            .commit()
                        setGpsEnabled(false)
                        if(!restored) {
                            toast("Nie udało się potwierdzić zapisu kopii")
                            return@setPositiveButton
                        }
                        migrate(); migrateWallet(); GateWidget.refresh(this); logs()
                    }.show()
            }
        } catch (_:Exception) { toast("Błąd pliku JSON") }
    }

    private fun exportData():JSONObject = DriveBackupWorker.snapshot(this)
    private fun validateImport(d:JSONObject) {
        require(!d.has("active") || d.optJSONObject("active")!=null) { "Nieprawidłowa aktywna zmiana" }
        for(key in listOf("objects","shifts","links","materials","notes","payments","expenses","logs")) if(d.has(key) && d.optJSONArray(key)==null) throw IllegalArgumentException("Nieprawidłowa lista: "+key)
        if(!listOf("objects","shifts","links","materials","notes","payments").any { d.has(it) }) throw IllegalArgumentException("Brak danych WT")
        for(e in rows(d,"expenses")) {
            require(e.optLong("cents")>0 && e.optLong("reimbursedCents") in 0..e.optLong("cents"))
            require(runCatching { LocalDate.parse(e.optString("date")) }.isSuccess)
        }
        for(p in rows(d,"payments")) {
            require(p.optLong("cents")>0 && p.optString("method") in listOf("cash","card"))
            val from=LocalDate.parse(p.optString("periodFrom")); val to=LocalDate.parse(p.optString("periodTo")); LocalDate.parse(p.optString("date")); require(!to.isBefore(from))
        }
        require(validStock(d)) { "Nieprawidłowe stany materiałów" }
        d.optJSONObject("walletSettings")?.let { require(WalletRules.rate(it.optString("defaultRate","30.00"))!=null) }
    }
    private val displayDate = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private fun rows(d:JSONObject,key:String) = (0 until arr(d,key).length()).mapNotNull { arr(d,key).optJSONObject(it) }
    private fun objectName(o:JSONObject) = listOf(o.optString("TAG"),o.optString("organization"),o.optString("city"),o.optString("Adres")).filter { it.isNotBlank() }.joinToString(" · ")
    private fun objectSummary(row: JSONObject) = ObjectUiGateway.Summary(
        row.optString("id"), row.optString("TAG"), row.optString("city"), row.optString("Adres"),
        row.optString("name", row.optString("Opis")), row.optBoolean("archived")
    )
    private fun objectById(id:String) = rows(data(),"objects").singleOrNull { id.isNotBlank() && it.optString("id")==id }
    private fun localDate(ms:Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    private fun dateText(iso:String):String = try { LocalDate.parse(iso).format(displayDate) } catch (_:Exception) { "Data nieznana — starszy wpis" }
    private fun recordDate(r:JSONObject):String {
        if(r.optString("date").isNotBlank()) return r.optString("date")
        return try { LocalDate.parse(r.optString("Data (RRRR-MM-DD)")).toString() } catch (_:Exception) { "" }
    }
    private fun migrate() {
        val d=data()
        if(d.optInt("schemaVersion")>=2) return
        val objects=rows(d,"objects")
        for(o in objects) {
            if(o.optString("id").isBlank()) o.put("id",UUID.randomUUID().toString())
            o.put("legacyName",o.optString("TAG")+" — "+o.optString("Adres"))
            if(!o.has("organization")) o.put("organization",o.optString("Opis"))
        }
        fun attach(r:JSONObject,oldKey:String) {
            if(r.optString("objectId").isNotBlank()) return
            val old=r.optString(oldKey)
            val matches=objects.filter { old.isNotBlank() && (it.optString("legacyName")==old || it.optString("TAG")==old || objectName(it)==old) }
            if(matches.size==1) r.put("objectId",matches[0].optString("id"))
        }
        for(key in listOf("links","materials","notes")) for(r in rows(d,key)) {
            if(r.optString("id").isBlank()) r.put("id",UUID.randomUUID().toString())
            attach(r,"Obiekt")
            if(key=="links" && !r.has("date")) r.put("date",recordDate(r))
            if(key=="materials") {
                r.put("name",r.optString("Materiał")); r.put("quantity",r.optString("Ilość")); r.put("unit","szt.")
            }
            if(key=="notes") r.put("text",listOf(r.optString("Tytuł"),r.optString("Treść")).filter { it.isNotBlank() }.joinToString("\n"))
        }
        for(sh in rows(d,"shifts") + listOfNotNull(active(d))) {
            attach(sh,"object")
            for(stage in rows(sh,"stages")) attach(stage,"object")
        }
        d.put("schemaVersion",2)
        prefs.edit().putString("data",d.toString()).apply()
    }
    private fun chooseObject(optional:Boolean, selected:(JSONObject?)->Unit) {
        objectPicker(optional, false, selected = selected)
    }
    private fun selectableObjects(d: JSONObject = data()): List<JSONObject> {
        val saved = rows(d, "objects")
        val ids = ObjectUiGateway(saved.map(::objectSummary)).selectableObjects().map { it.id }.toSet()
        return saved.filter { it.optString("id") in ids }
    }

    private fun objectPicker(optional: Boolean, arrival: Boolean, startDay: Boolean = false, selected: (JSONObject?) -> Unit) {
        val options = selectableObjects()
        val pickerSessionId=active(data())?.optString("id").orEmpty()
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(8)) }
        val searchRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val query=EditText(ContextThemeWrapper(this,dialogStyle)).apply { hint="Szukaj"; setSingleLine(); inputType=InputType.TYPE_CLASS_TEXT; imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH }
        searchRow.addView(query,LinearLayout.LayoutParams(0,dp(48),1f))
        val results=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val dialog=AlertDialog.Builder(this,dialogStyle).setTitle(if(arrival) "Dokąd przyjechałeś?" else "Wybierz obiekt")
            .setView(layout).setNegativeButton(if(arrival) "Anuluj — przejazd trwa" else "Anuluj",null).create()
        fun renderResults() {
            results.removeAllViews()
            val matches=options.filter { obj -> ObjectSearch.matchesFields(listOf("TAG", "name", "organization", "Opis", "city", "Adres", "street", "building", "postcode").map { obj.optString(it) }, query.text.toString()) }.sortedWith { x,y -> ObjectSearch.compareTags(x.optString("TAG"),y.optString("TAG")) }
            for(obj in matches) results.addView(makeButton(objectName(obj)) {
                val current=objectById(obj.optString("id"))?.takeUnless { it.optBoolean("archived") }
                if(current==null) { toast("Obiekt nie jest już dostępny"); dialog.dismiss() }
                else { dialog.dismiss(); selected(current) }
            },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(6) })
            if(matches.isEmpty()) results.addView(TextView(this).apply { text="Brak obiektów dla tego wyszukiwania"; setTextColor(palette.textPrimary); setPadding(0,dp(12),0,dp(12)) })
        }
        query.setOnEditorActionListener { _,action,_ -> if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { renderResults(); true } else false }
        query.addTextChangedListener(object:TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {} ; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { renderResults() }; override fun afterTextChanged(s:Editable?) {} })
        run {
            layout.addView(workMeterButton(this, "＋ Dodaj obiekt", WorkMeterButtonRole.PRIMARY) {
                dialog.dismiss()
                newForm("objects")
                startAfterObjectSave = startDay
                startObjectSessionId=pickerSessionId
            })
        }
        layout.addView(searchRow)
        if(optional) layout.addView(makeButton("Bez obiektu") { dialog.dismiss(); selected(null) })
        if(arrival) {
            layout.addView(makeButton("🛒 Zakupy") { dialog.dismiss(); startShopping() })
            layout.addView(makeButton("+ Nowy obiekt") { dialog.dismiss(); screen="home"; newForm("objects") })
        }
        layout.addView(object:ScrollView(this) {
            override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
                super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(dp(240),View.MeasureSpec.AT_MOST))
            }
        }.apply { addView(results) },LinearLayout.LayoutParams(-1,-2))
        renderResults()
        if (startDay) query.requestFocus()
        dialog.show()
        dialog.window?.setSoftInputMode(if (startDay) WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            else WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    private fun openScreen(target:String) {
        screen=target
        when {
            target=="home" -> home()
            target=="menu" -> menu()
            target.startsWith("menuFeature:") -> menuFeature(target.substringAfter(":"))
            target=="settings" -> settings()
            target=="sectionSettings" -> sectionSettings(currentSection)
            target=="gps" -> gpsScreen()
            target=="gpsDiagnostics" -> gpsDeviceDiagnostics()
            target=="gpsPlaceKinds" -> gpsPlaceKinds()
            target=="gpsPay" -> gpsPaySettings()
            target=="gpsCategories" -> gpsTripCategories()
            target=="gpsTimeSummary" -> gpsTimeSummary()
            target=="language" -> language()
            target=="charts" -> charts()
            target=="queue" -> uploadQueue()
            target=="car" -> carStock()
            target.startsWith("materialGroup:") -> { val p=target.split(":"); materialGroup(p[1],p.getOrElse(2){""}) }
            target=="about" -> about()
            target=="widgetSettings" -> widgetSettings()
            target=="wallet" -> wallet()
            target=="walletSettings" -> walletSettings()
            target=="objectSettings" -> objectSettings()
            target=="payment" -> paymentForm()
            target=="expenses" -> expenseList()
            target=="sms" -> weeklySms(smsParent)
            target=="objects" -> objects()
            target.startsWith("object:") -> objectDetails(target.substringAfter(":"))
            target.startsWith("detail:") -> { val p=target.split(":"); recordDetails(p[1],p[2],detailParent) }
            target.startsWith("list:") -> { val p=target.split(":"); recordList(p[1],p.getOrElse(2){""}) }
            target=="notes" -> {
                base("Notatki"); nav()
                button("+ Dodaj notatkę") { newForm("notes") }
                button("Aktualne ("+rows(data(),"notes").count { it.optBoolean("aktualne") && !it.optBoolean("done") }+")") { openScreen("notesActive") }
                button("Historia / Logi — wszystkie wpisy") { openScreen("notesLog") }
            }
            target=="notesActive" -> recordList("notes","",true)
            target=="notesLog" -> recordList("notes","")
            target=="report" -> report()
            target.startsWith("shift:") -> shiftDetails(target.substringAfter(":"))
            target=="logs" -> logs()
            target=="backup" -> backupScreen()
            target=="form" -> renderForm()
            target=="drive" -> driveSettings()
            else -> home()
        }
    }
    private fun objects() {
        screen="objects"
        base("Obiekty")
        nav()
        val items=rows(data(),"objects").map { obj ->
            ObjectListUiModel.Item(
                id=obj.optString("id"),
                tag=obj.optString("TAG"),
                organization=obj.optString("organization",obj.optString("Opis")),
                city=obj.optString("city"),
                address=obj.optString("Adres"),
                archived=obj.optBoolean("archived"),
                name=obj.optString("name",obj.optString("Opis"))
            )
        }
        val panel=ObjectListPanel(
            this,
            onOpenObject={ id -> openScreen("object:"+id) },
            onAddObject={ newForm("objects") },
            initialFilter = objectFilters ?: ObjectFilterUiState.State(archived = prefs.getBoolean("archived_objects", false),grouped=prefs.getBoolean("objects_grouped",true)),
            initialQuery = objectSearch,
            onFilterChanged = { filters, query ->
                objectFilters = filters
                objectSearch = query
                prefs.edit().putBoolean("objects_grouped",filters.grouped).apply()
                if (prefs.getBoolean("archived_objects", false) != filters.archived) {
                    prefs.edit().putBoolean("archived_objects", filters.archived).apply()
                }
            }
        )
        root.addView(panel,LinearLayout.LayoutParams(-1,-2))
        panel.submitObjects(items)
    }
    private fun objectDetails(id:String) {
        val obj=objectById(id) ?: run { objects(); return }
        base(obj.optString("TAG")); screen="object:"+id; nav()
        val period=JSONObject()
        label("Typ miejsca: "+placeType(obj).label)
        val totals=sortedMapOf<String,Long>()
        val shifts=rows(data(),"shifts")+listOfNotNull(active(data()))
        var unassigned=0
        for(sh in shifts) {
            val stages=rows(sh,"stages").toMutableList()
            if(!sh.has("end")) stages.add(JSONObject().put("start",sh.optLong("stageStart")).put("end",System.currentTimeMillis()).put("objectId",sh.optString("objectId")).put("kind",sh.optString("kind","Obiekt")))
            if(stages.isEmpty()) { unassigned++; continue }
            for(st in stages) {
                if(st.optString("objectId")!=id || !WorkPlaceRules.isOnSiteStage(st.optString("kind","Obiekt"))) continue
                var a=maxOf(st.optLong("start"),sh.optLong("start"))
                val end=minOf(st.optLong("end"),if(sh.has("end")) sh.optLong("end") else System.currentTimeMillis())
                for((day,ms) in DomainRules.timeByDay(a,end,ZoneId.systemDefault())) {
                    if(DomainRules.inRange(day,period.optString("from"),period.optString("to"))) totals[day]=(totals[day] ?: 0L)+ms
                }
            }
        }
        val details=ObjectDetailsUiModel.Content(
            tag=obj.optString("TAG"),
            name=obj.optString("name",obj.optString("Opis")),
            city=obj.optString("city"),
            address=obj.optString("Adres"),
            organization=obj.optString("organization"),
            notes=obj.optString("notes",obj.optString("Opis")),
            archived=obj.optBoolean("archived"),
            duration=duration(totals.values.sum()),
            days=totals.toSortedMap(reverseOrder()).map { (day,ms) ->
                ObjectDetailsUiModel.Day(dateText(day),duration(ms))
            },
            hasUnassignedLegacyShifts=unassigned>0
        )
        val detailsPanel=ObjectDetailsPanel(
            this,details,
            onEdit={ newForm("objects",id) },
            onMaps={
                val link=obj.optString("maps")
                openLink(if(link.isNotBlank()) link else "https://www.google.com/maps/search/?api=1&query="+URLEncoder.encode(obj.optString("city")+" "+obj.optString("Adres"),"UTF-8"))
            },
            onPhotos={ openScreen("list:links:"+id) },
            onMaterials={ openScreen("list:materials:"+id) },
            onNotes={ openScreen("list:notes:"+id) },
            counts=ObjectDetailsTabs.Counts(
                photos=rows(data(),"links").count { it.optString("objectId")==id },
                materials=rows(data(),"materials").count {
                    it.optString("objectId")==id && it.optString("storage")!="car"
                },
                notes=rows(data(),"notes").count { it.optString("objectId")==id }
            )
        )
        root.addView(detailsPanel,LinearLayout.LayoutParams(-1,-2))
        val archiveObject = !obj.optBoolean("archived")
        button(if(archiveObject) "Usuń z listy aktywnych" else "Przywróć obiekt") {
            confirmAction {
                val d=data()
                val saved=rows(d,"objects")
                val change=ObjectUiGateway(saved.map(::objectSummary)).setArchived(id,archiveObject)
                    ?: return@confirmAction
                val row=saved.singleOrNull { it.optString("id")==change.id } ?: return@confirmAction
                if(row.optBoolean("archived")!=change.archived) {
                    row.put("archived",change.archived)
                    save(d,"OBJECT_ARCHIVE",id)
                }
                objects()
            }
        }
    }
    private fun recordList(key:String,objectId:String="",onlyActive:Boolean=false) {
        val target=screen
        base(when(key) { "links" -> "Zdjęcia"; "materials" -> "Materiały"; else -> if(onlyActive) "Aktualne notatki" else "Historia notatek" })
        screen=target; nav()
        val filterKey="filter_"+target
        val f=try { JSONObject(prefs.getString(filterKey,if(objectId.isBlank()) "{}" else prefs.getString("objectPeriod_"+objectId,"{}"))!!) } catch (_:Exception) { JSONObject() }
        fun refresh() { prefs.edit().putString(filterKey,f.toString()).apply(); openScreen(target) }
        filters(target, f.optString("from").isNotBlank() || f.optString("to").isNotBlank() || f.optString("objectId").isNotBlank() || f.optBoolean("nameSort") || f.optBoolean("shoppingOnly")) {
            button("Data od: "+f.optString("from").let { if(it.isBlank()) "wszystkie" else dateText(it) }) {
                pickReportDate(LocalDate.now()) { f.put("from",it.toString()); refresh() }
            }
            button("Data do: "+f.optString("to").let { if(it.isBlank()) "wszystkie" else dateText(it) }) {
                pickReportDate(LocalDate.now()) { f.put("to",it.toString()); refresh() }
            }
            if(objectId.isBlank()) button("Obiekt: "+(objectById(f.optString("objectId"))?.let { objectName(it) } ?: "wszystkie")) {
                chooseObject(true) { f.put("objectId",it?.optString("id") ?: ""); refresh() }
            }
            if(key=="notes") button(if(f.optBoolean("shoppingOnly")) "Zakupy / paragony: tylko zakupy" else "Zakupy / paragony: wszystkie notatki") {
                f.put("shoppingOnly",!f.optBoolean("shoppingOnly")); refresh()
            }
            button("Wyczyść filtry") { prefs.edit().remove(filterKey).apply(); openScreen(target) }
            button(if(f.optBoolean("nameSort")) "Sortowanie: nazwa" else "Sortowanie: data, najnowsze") { f.put("nameSort",!f.optBoolean("nameSort")); refresh() }
        }
        button("+ Dodaj") { newForm(key,objectId=objectId) }
        if(key=="links") button("WYŚLIJ / KOLEJKA") { sendQueue() }
        if(key=="materials" && objectId.isBlank()) button("W SAMOCHODZIE") { carStock() }
        if(f.optString("from").isNotBlank() && f.optString("to").isNotBlank() && f.optString("from")>f.optString("to")) { label("Data do musi być po dacie od."); return }
        val selected=objectId.ifBlank { f.optString("objectId") }
        val list=rows(data(),key).filter {
            val day=recordDate(it)
            (selected.isBlank() || it.optString("objectId")==selected) &&
            (!onlyActive || (it.optBoolean("aktualne") && !it.optBoolean("done"))) &&
            (!f.optBoolean("shoppingOnly") || it.optBoolean("shopping")) &&
            DomainRules.inRange(day,f.optString("from"),f.optString("to"))
        }.let { list -> if(f.optBoolean("nameSort")) list.sortedBy { it.optString("name",it.optString("text")).lowercase(Locale.ROOT) } else list.sortedByDescending { recordDate(it)+it.optString("created") } }
        val listKind=WorkRecordListUi.kind(key)
        if(listKind!=null) {
            val filtered=f.optString("from").isNotBlank() || f.optString("to").isNotBlank() ||
                f.optString("objectId").isNotBlank() || f.optBoolean("shoppingOnly")
            val header=WorkRecordListUi.header(listKind,list.size,filtered)
            label(header.heading)
            label(header.description)
            label(header.countLabel)
            if(list.isEmpty()) label(header.emptyMessage)
        } else if(list.isEmpty()) label("Brak wpisów dla wybranych filtrów.")
        if(key=="materials") {
            val used=list.filter { it.optString("storage")!="car" }
            label("Podsumowanie materiałów dla wybranego zakresu")
            val sums=linkedMapOf<Pair<String,String>,BigDecimal>()
            for(r in used) {
                val q=r.optString("quantity").replace(",",".").toBigDecimalOrNull() ?: continue
                val group=r.optString("name") to r.optString("unit","szt.")
                sums[group]=(sums[group] ?: BigDecimal.ZERO)+q
            }
            sums.forEach { (group,q) -> label(group.first+" — "+q.stripTrailingZeros().toPlainString()+" "+group.second) }
            for((group,items) in used.groupBy { recordDate(it) to it.optString("objectId") }) {
                label("── "+dateText(group.first)+" ──")
                val obj=objectById(group.second)?.let { objectName(it) } ?: "Bez obiektu"
                val card=WorkRecordListUi.materialsGroup(dateText(group.first),obj,items.size)
                root.addView(WorkRecordListCard(this,card) {
                    materialGroup(group.first,group.second)
                },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
            }
            return
        }
        var lastDate=""
        for(r in list) {
            val date=dateText(recordDate(r))
            if(lastDate!=date) { label("── "+date+" ──"); lastDate=date }
            val obj=objectById(r.optString("objectId"))?.let { objectName(it) } ?: r.optString("Obiekt").ifBlank { "Bez obiektu" }
            val summary=when(key) {
                "materials" -> r.optString("name")+" · "+r.optString("quantity")+" "+r.optString("unit","szt.")
                "notes" -> (if(r.optBoolean("shopping")) "🛒 " else "")+(if(r.optBoolean("done")) "✓ " else if(r.optBoolean("aktualne")) "● " else "")+r.optString("text").ifBlank { "Notatka ze zdjęciem" }.take(100)
                else -> "Zdjęcia: "+arr(r,"photos").length()+" · "+r.optString("Link Google Drive")
            }
            if(key=="links") {
                val status=if(arr(r,"photos").length()>0) PhotoQueue.status(this,key,r).caption else ""
                val card=WorkRecordListUi.photos(arr(r,"photos").length(),
                    r.optString("Link Google Drive"),obj,status)
                root.addView(WorkRecordListCard(this,card) {
                    recordDetails(key,r.optString("id"),target)
                },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
            } else button(summary+"\\n"+obj) { recordDetails(key,r.optString("id"),target) }
        }
    }
    private var detailParent="home"
    private fun recordDetails(key:String,id:String,parent:String) {
        if(key=="expenses" && !walletUnlocked) { settings(); confirmPin { walletUnlocked=true; recordDetails(key,id,parent) }; return }
        val r=rows(data(),key).find { it.optString("id")==id } ?: return
        base("Szczegóły"); screen="detail:"+key+":"+id; detailParent=parent
        button("← Wstecz") { openScreen(parent) }
        label(dateText(recordDate(r)))
        label(objectById(r.optString("objectId"))?.let { objectName(it) } ?: r.optString("Obiekt").ifBlank { "Bez obiektu" })
        if(key=="expenses") {
            label(r.optString("text")); label("Kwota zakupu: "+money(r.optLong("cents")))
            label("Do zwrotu: "+money((r.optLong("cents")-r.optLong("reimbursedCents")).coerceAtLeast(0)))
            if(r.optLong("reimbursedCents")>0) label("Zwrot: "+r.optString("reimbursedDate")+" · "+r.optString("reimbursedMethod"))
            button(if(r.optLong("reimbursedCents")>=r.optLong("cents")) "Cofnij oznaczenie zwrotu" else "Oznacz jako zwrócone") {
                val paid=r.optLong("reimbursedCents")>=r.optLong("cents")
                if(paid) confirmAction("Cofnąć oznaczenie zwrotu? Kwota znów będzie należna.") {
                    val d=data(); val current=rows(d,key).find { it.optString("id")==id } ?: return@confirmAction
                    current.put("reimbursedCents",0).remove("reimbursedDate"); current.remove("reimbursedMethod"); save(d,"EXPENSE_REOPEN",id); recordDetails(key,id,parent)
                } else AlertDialog.Builder(this,dialogStyle).setTitle("Jak otrzymałeś zwrot?")
                    .setItems(arrayOf("Gotówka","Karta")) { _,which -> confirmAction("Potwierdzić zwrot kosztu zakupu? Nie zmienia to rozliczenia wynagrodzenia.") {
                        val d=data(); val current=rows(d,key).find { it.optString("id")==id } ?: return@confirmAction
                        current.put("reimbursedCents",current.optLong("cents")).put("reimbursedDate",fmt.format(Date())).put("reimbursedMethod",if(which==0) "Gotówka" else "Karta")
                        save(d,"EXPENSE_REIMBURSE",id); recordDetails(key,id,parent)
                    } }.setNegativeButton("Anuluj",null).show()
            }
        }
        if(key=="materials") label(materialLine(r))
        if(key=="notes") {
            if(r.optString("store").isNotBlank()) label("Sklep: "+r.optString("store"))
            if(r.optBoolean("shopping")) label("🛒 Zakupy / paragon")
            label(r.optString("text"))
            label(if(r.optBoolean("done")) "Wykonano: "+r.optString("completedAt") else if(r.optBoolean("aktualne")) "Aktualna" else "Zwykła notatka")
            button(if(r.optBoolean("done")) "Oznacz jako aktualną" else "Wykonano") {
                val d=data(); val row=rows(d,key).find { it.optString("id")==id } ?: return@button
                val done=!row.optBoolean("done")
                row.put("done",done).put("aktualne",!done).put("completedAt",if(done) fmt.format(Date()) else "")
                save(d,"NOTE_STATUS",id); openScreen(parent)
            }
        }
        showPhotos(r)
        if(r.optString("Link Google Drive").isNotBlank()) button("Otwórz folder Google Disk") { openLink(r.optString("Link Google Drive")) }
        if(arr(r,"photos").length()>0) button(if(uploading) "Trwa przesyłanie…" else "Wyślij zdjęcia na Google Disk / ponów") {
            if(!uploading) { pendingUpload=key+":"+id; authorizeDrive() } else toast("Przesyłanie trwa lub czeka na internet. Postęp jest w powiadomieniu.")
        }
        if(key=="links") button("Udostępnij folder przez link") {
            confirmAction("Każda osoba mająca link będzie mogła przeglądać wszystkie pliki w tym folderze. Otworzyć dostęp?") {
                prefs.edit().putBoolean("shareConfirmed_"+id,true).apply(); pendingUpload="share:"+id; authorizeDrive()
            }
        }
        button("Edytuj") { screen=parent; newForm(key,id) }
        button("Usuń wpis") {
            confirmAction("Usunąć wpis z aplikacji? Pliki na Google Disk pozostaną.") {
                val d=data(); val newer=JSONArray()
                rows(d,key).filter { it.optString("id")!=id }.forEach { newer.put(it) }
                d.put(key,newer)
                if(key=="materials" && !validStock(d)) { toast("Najpierw usuń lub popraw powiązane przeniesienia z samochodu"); return@confirmAction }
                save(d,"DELETE_"+key,id); openScreen(parent)
            }
        }
    }
    private fun openLink(link:String) {
        val u=Uri.parse(link)
        if(u.scheme!="https") { toast("Wymagany link https://"); return }
        try { startActivity(Intent(Intent.ACTION_VIEW,u)) } catch (_:Exception) { toast("Nie można otworzyć linku") }
    }
    private fun showPhotos(r: JSONObject) {
        val photos = arr(r, "photos")
        val count = photos.length()
        if (count == 0) return
        label(PhotoPreviewUi.summary(count))
        val uploaded = PhotoQueue.uploadedFiles(r)
        val sent = (0 until count).count { photos.optString(it) in uploaded }
        if (sent > 0) label("Wysłano na Google Drive: $sent z $count")
        val visible = (0 until count).mapNotNull { index ->
            val uri = photos.optString(index)
            val remoteId = uploaded[uri]
            val original = try {
                PhotoPreview.decode(this, Uri.parse(uri), 320)
            } catch (_: Exception) {
                null
            }
            val preview = original ?: remoteId?.let { PhotoPreview.cachedThumbnail(this, it) }
            if (preview == null && remoteId != null) null
            else PhotoTile(index, uri, preview, original != null, remoteId)
        }
        val columns = PhotoPreviewUi.columns(visible.size, resources.configuration.screenWidthDp)
        for (group in visible.chunked(columns)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (column in 0 until columns) {
                val view = group.getOrNull(column)?.let { photoTile(it, count, columns) } ?: View(this)
                row.addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (column > 0) marginStart = dp(8)
                })
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private data class PhotoTile(val index: Int, val uri: String, val bitmap: Bitmap?, val local: Boolean, val remoteId: String?)

    private fun photoTile(photo: PhotoTile, count: Int, columns: Int): View {
        val bitmap = photo.bitmap
        val state = PhotoPreviewUi.state(photo.index, count, bitmap != null)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = background(palette.surface).apply { setStroke(dp(1), palette.border) }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            if (bitmap != null) {
                addView(ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(bitmap)
                    contentDescription = if (photo.local) state.accessibilityLabel else state.heading + " · Otwórz w Google Drive"
                    isFocusable = true
                    setOnClickListener {
                        if (photo.local) openPhoto(photo.uri)
                        else openLink("https://drive.google.com/open?id=" + Uri.encode(photo.remoteId.orEmpty()))
                    }
                }, LinearLayout.LayoutParams(-1, dp(if (columns == 2) 120 else 180)))
            } else {
                addView(TextView(this@MainActivity).apply {
                    text = state.message
                    contentDescription = state.accessibilityLabel
                    setTextColor(palette.textPrimary)
                    gravity = Gravity.CENTER
                    minimumHeight = dp(if (columns == 2) 120 else 180)
                }, LinearLayout.LayoutParams(-1, -2))
            }
            addView(TextView(this@MainActivity).apply {
                text = state.heading
                textSize = 12f
                setTextColor(palette.secondaryText)
                setPadding(dp(4), dp(6), dp(4), dp(2))
            })
        }
    }

    private fun newForm(key:String,id:String="",objectId:String="",sendAfterSave:Boolean=false) {
        quickPhotoUpload=sendAfterSave
        startAfterObjectSave = false
        formReturn=screen; formKind=key
        draft=if(id.isNotBlank()) JSONObject(rows(data(),key).find { it.optString("id")==id }?.toString() ?: "{}")
            else if(key=="objects") {
                // Adding an object must never restore a draft from editing another object.
                prefs.edit().remove("draft_objects").apply()
                JSONObject()
            } else try { JSONObject(prefs.getString("draft_"+key,"{}")!!) } catch (_:Exception) { JSONObject() }
        if(!draft.has("id")) draft.put("id",UUID.randomUUID().toString())
        if(!draft.has("created")) draft.put("created",fmt.format(Date()))
        if(key!="objects" && !draft.has("date")) draft.put("date",active(data())?.let { localDate(it.optLong("start")).toString() } ?: if(key=="materials") "" else LocalDate.now().toString())
        if(objectId.isNotBlank()) draft.put("objectId",objectId)
        renderForm()
    }
    private fun field(caption:String,key:String,multiline:Boolean=false,numeric:Boolean=false) {
        label(caption)
        val e=EditText(ContextThemeWrapper(this,dialogStyle)).apply {
            setText(draft.optString(key)); textSize=17f; minHeight=dp(48)
            inputType=if(numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or (if(multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
            setSingleLine(!multiline); setPadding(dp(10),dp(8),dp(10),dp(8)); setTextColor(palette.textPrimary); setHintTextColor(palette.secondaryText); background=background(palette.surface).apply { setStroke(dp(1),palette.border) }
        }
        e.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                draft.put(key,s.toString()); prefs.edit().putString("draft_"+formKind,draft.toString()).apply()
            }
            override fun afterTextChanged(e:Editable?) {}
        })
        root.addView(e,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }
    private fun currentObjectFix(objectId: String, received: (GpsObjectLocationRules.Capture) -> Unit) {
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            toast("Zezwól aplikacji na dokładną lokalizację w ustawieniach Android"); return
        }
        val manager=getSystemService(LocationManager::class.java) ?: return
        val provider=try { listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) } } catch (_:RuntimeException) { null }
        if(provider==null) { toast("Włącz lokalizację w telefonie"); return }
        cancelStartAttempt?.invoke()
        val signal=android.os.CancellationSignal()
        val timeout=Runnable { if(!signal.isCanceled) { signal.cancel(); toast("Brak pomiaru GPS. Spróbuj ponownie.") } }
        cancelStartAttempt={ signal.cancel(); ticker.removeCallbacks(timeout) }
        ticker.postDelayed(timeout,12_000)
        toast("Pobieranie aktualnej lokalizacji GPS…")
        try {
            androidx.core.location.LocationManagerCompat.getCurrentLocation(manager,provider,signal,
                androidx.core.content.ContextCompat.getMainExecutor(this)) { loc ->
                if(!signal.isCanceled) {
                    signal.cancel(); ticker.removeCallbacks(timeout); cancelStartAttempt=null
                    val capture=loc?.let { GpsObjectLocationRules.Capture(objectId,it.latitude,it.longitude,it.accuracy.toDouble(),it.time) }
                    if(capture==null || !GpsObjectLocationRules.valid(capture,System.currentTimeMillis()))
                        toast("Pomiar GPS niedokładny lub nieaktualny. Spróbuj ponownie.")
                    else received(capture)
                }
            }
        } catch (_:RuntimeException) { signal.cancel(); ticker.removeCallbacks(timeout); toast("Nie udało się pobrać lokalizacji GPS") }
    }
    private fun canSaveHomeGps(a: JSONObject?): Boolean = a!=null &&
        WorkPlaceRules.isOnSiteStage(a.optString("kind")) &&
        selectableObjects().count { it.optString("id")==a.optString("objectId") }==1

    private fun saveHomeGps() {
        val running=active(data()) ?: return
        if(!canSaveHomeGps(running)) return
        val objectId=running.optString("objectId")
        val sessionId=running.optString("id")
        currentObjectFix(objectId) { capture ->
            fun persist() {
                val now=active(data())
                if(!canSaveHomeGps(now) || now?.optString("id")!=sessionId || now.optString("objectId")!=objectId ||
                    !GpsObjectLocationRules.valid(capture,System.currentTimeMillis())) {
                    toast("Etap lub pomiar zmienił się. Pobierz GPS ponownie."); return
                }
                if(prefs.edit().putString("gps_object_"+objectId,JSONObject()
                    .put("lat",capture.latitude).put("lon",capture.longitude).put("accuracy",capture.accuracyM)
                    .put("time",capture.fixTimeMs).put("radius",GpsObjectLocationRules.RADIUS_M).toString()).commit()) {
                    toast("Zapisano GPS obiektu"); home()
                } else toast("Nie udało się zapisać GPS")
            }
            if(prefs.contains("gps_object_"+objectId)) AlertDialog.Builder(this,dialogStyle)
                .setTitle("Zastąpić GPS obiektu?").setNegativeButton("Nie",null)
                .setPositiveButton("Tak") { _, _ -> persist() }.show()
            else persist()
        }
    }
    private fun captureObjectGps() {
        if(formKind!="objects") return
        val targetId=draft.optString("id")
        currentObjectFix(targetId) { capture ->
            if(formKind=="objects" && draft.optString("id")==targetId && screen=="form") {
                draft.put("_gps_capture",JSONObject().put("objectId",targetId).put("lat",capture.latitude)
                    .put("lon",capture.longitude).put("accuracy",capture.accuracyM).put("time",capture.fixTimeMs))
                prefs.edit().putString("draft_objects",draft.toString()).apply()
                toast("Lokalizacja pobrana. Naciśnij Zapisz, aby przypisać ją do obiektu."); renderForm()
            }
        }
    }
    private fun renderForm() {
        if(formKind=="expenses" && !walletUnlocked) { settings(); confirmPin { walletUnlocked=true; renderForm() }; return }
        base(when(formKind) { "objects" -> "Obiekt"; "expenses" -> "Zakup za własne pieniądze"; "materials" -> "Zużyty materiał"; "links" -> "Dodaj zdjęcia"; else -> "Notatka" })
        screen="form"; nav()
        if(formKind=="objects") {
            val editing=rows(data(),"objects").any { it.optString("id")==draft.optString("id") }
            label(ObjectFormUiModel.title(editing))
            label(ObjectFormUiModel.requiredHint())
            label(ObjectFormUiModel.tagHint(draft.optString("TAG")))
            val location=ObjectFormUiModel.sections[0]
            label(location.title)
            field(location.fields[0].caption,location.fields[0].key)
            if(draft.optString("TAG").isBlank()) field("Tag (opcjonalnie)","tagPrefix")
            else label(ObjectFormUiModel.tagHint(draft.optString("TAG")))
            field(location.fields[1].caption,location.fields[1].key)
            button("Typ miejsca: "+placeType(draft).label) {
                pickPlaceType { type -> draft.put("placeType",type.name); renderForm() }
            }
            val information=ObjectFormUiModel.sections[1]
            label(information.title)
            for(spec in information.fields) field(spec.caption,spec.key,spec.multiline)
            label(ObjectFormUiModel.mapHint())
            val savedGeo=prefs.getString("gps_object_"+draft.optString("id"),null)
            val pendingGeo=draft.optJSONObject("_gps_capture")
            label(when {
                pendingGeo!=null -> "📍 Lokalizacja GPS pobrana — zapisze się razem z obiektem."
                savedGeo!=null -> "📍 Lokalizacja GPS przypisana do tego obiektu."
                else -> "📍 Lokalizacja GPS: nie przypisano (opcjonalnie)."
            })
            button("📍 Zapisz lokalizację GPS") { captureObjectGps() }
            label(ObjectFormUiModel.footer(editing))
        } else {
            val uiKind=WorkRecordFormUi.kind(formKind)
            if(uiKind!=null) {
                label(WorkRecordFormUi.title(uiKind))
                val assignment=WorkRecordFormUi.assignment(uiKind)
                label(assignment.title)
                label(assignment.hint)
            }
            button("Data: "+recordDate(draft).let { if(it.isBlank()) "Wybierz datę" else dateText(it) }) {
                pickReportDate(try { LocalDate.parse(recordDate(draft)) } catch (_:Exception) { LocalDate.now() }) {
                    draft.put("date",it.toString()); renderForm()
                }
            }
            if(formKind=="materials" && draft.optString("sourceId").isBlank()) button("Miejsce: "+if(draft.optString("storage")=="car") "W samochodzie" else "Na obiekcie") {
                AlertDialog.Builder(this,dialogStyle).setTitle("Miejsce").setItems(arrayOf("Na obiekcie","W samochodzie")) { _,i -> draft.put("storage",if(i==1) "car" else "object"); renderForm() }.show()
            }
            button("Obiekt: "+(objectById(draft.optString("objectId"))?.let { objectName(it) } ?: if(formKind in listOf("notes","expenses")) "Bez obiektu" else "Wybierz")) {
                chooseObject(formKind in listOf("notes","expenses") || (formKind=="materials" && draft.optString("storage")=="car")) { draft.put("objectId",it?.optString("id") ?: ""); draft.remove("Obiekt"); renderForm() }
            }
            if(uiKind!=null) {
                val details=WorkRecordFormUi.details(uiKind)
                label(details.title)
                label(details.hint)
            }
            if(formKind=="expenses") {
                if(!draft.has("amount") && draft.has("cents")) draft.put("amount",BigDecimal.valueOf(draft.optLong("cents"),2).toPlainString())
                field("Kwota zakupu (zł)","amount",numeric=true)
                field("Co kupiłeś? (opcjonalnie)","text",true)
            }
            if(formKind=="materials") {
                materialNameField()
                button("Dodaj zdjęcia z galerii") { pickPhotos() }; button("Zrób zdjęcie") { camera() }
                field("Ilość","quantity",numeric=true)
                button("Jednostka: "+draft.optString("unit","szt.")) {
                    AlertDialog.Builder(this,dialogStyle).setTitle("Jednostka").setItems(arrayOf("szt.","m")) { _,which ->
                        draft.put("unit",if(which==0) "szt." else "m"); renderForm()
                    }.show()
                }
            } else {
                if(formKind=="notes") {
                    if(draft.optBoolean("shopping")) field("Sklep (opcjonalnie)","store")
                    field("Treść (opcjonalnie, gdy dodajesz zdjęcie)","text",true)
                    root.addView(CheckBox(this).apply {
                        text="Zakupy / paragon"; setTextColor(palette.textPrimary)
                        buttonTintList=android.content.res.ColorStateList.valueOf(palette.accentText)
                        isChecked=draft.optBoolean("shopping")
                        setOnCheckedChangeListener { _,checked -> draft.put("shopping",checked) }
                    })
                    root.addView(CheckBox(this).apply {
                        setTextColor(palette.textPrimary); buttonTintList=android.content.res.ColorStateList.valueOf(palette.accentText)
                        text="Aktualna"; isChecked=draft.optBoolean("aktualne")
                        setOnCheckedChangeListener { _,checked -> draft.put("aktualne",checked); if(checked) draft.put("done",false) }
                    })
                }
                button("Dodaj zdjęcia z galerii") { pickPhotos() }; button("Zrób zdjęcie") { camera() }
                if(formKind=="links") field("Link Google Disk (opcjonalnie)","Link Google Drive")
            }
            if(uiKind!=null) label(WorkRecordFormUi.attachments(arr(draft,"photos").length()))
            showPhotos(draft)
            if(arr(draft,"photos").length()>0) button("Usuń zdjęcie ze szkicu") {
                val photos=arr(draft,"photos")
                AlertDialog.Builder(this,dialogStyle).setTitle("Wybierz zdjęcie").setItems((0 until photos.length()).map { "Zdjęcie "+(it+1) }.toTypedArray()) { _,index ->
                    photos.remove(index); renderForm()
                }.show()
            }
        }
        if(formKind=="materials" && rows(data(),"materials").none { it.optString("id")==draft.optString("id") }) button("+ DODAJ MATERIAŁ") { addBatchLine() }
        button("Zapisz") { saveForm() }
        if(formKind=="materials") label(WorkRecordFormUi.pendingBatch(rows(draft,"batch").size))
        if(formKind=="materials") for((i,row) in rows(draft,"batch").withIndex()) {
            label(materialLine(row))
            button("EDYTUJ · "+row.optString("name")) {
                if(draft.optString("name").isNotBlank() || draft.optString("quantity").isNotBlank()) { toast("Najpierw dodaj bieżący materiał do listy"); return@button }
                listOf("name","quantity","unit","photos").forEach { k -> if(row.has(k)) draft.put(k,row.get(k)) }
                arr(draft,"batch").remove(i); renderForm()
            }
        }
    }
    private fun saveForm() {
        if(formKind=="materials") { saveMaterials(); return }
        val d=data()
        if(formKind=="objects") {
            val city=draft.optString("city").trim()
            val existingObjects=rows(d,"objects")
            val catalog=ObjectUiGateway(existingObjects.map(::objectSummary))
            val draftObject=objectSummary(draft)
            val previousId=existingObjects.firstOrNull { it.optString("id")==draftObject.id }?.optString("id")
            val error=catalog.validateObjectDraft(draftObject,previousId,draft.optString("tagPrefix"))
            if(error!=null) {
                toast(when(error) {
                    ObjectUiGateway.DraftError.MISSING_CITY,
                    ObjectUiGateway.DraftError.MISSING_ADDRESS -> "Podaj miasto i adres"
                    ObjectUiGateway.DraftError.INVALID_TAG_PREFIX -> "Tag musi zawierać litery lub cyfry"
                    else -> "Nieprawidłowy identyfikator obiektu"
                })
                return
            }
            if(draft.optString("TAG").isBlank()) {
                val prefix=if(draft.optString("tagPrefix").isBlank()) DomainRules.cityPrefix(city) else DomainRules.customPrefix(draft.optString("tagPrefix"))
                if(prefix.isBlank()) { toast("Tag musi zawierać litery lub cyfry"); return }
                val counters=d.optJSONObject("tagCounters") ?: JSONObject().also { d.put("tagCounters",it) }
                val (tag,n)=DomainRules.nextPrefixTag(prefix,counters.optInt(prefix),rows(d,"objects").map { it.optString("TAG") })
                draft.put("TAG",tag); counters.put(prefix,n)
            }
        } else {
            if(recordDate(draft).isBlank()) { toast("Wybierz datę"); return }
            if(formKind !in listOf("notes","expenses") && draft.optString("objectId").isBlank()) { toast("Wybierz obiekt"); return }
            if(formKind=="materials") {
                val qty=DomainRules.quantity(draft.optString("quantity"),draft.optString("unit","szt."))
                if(draft.optString("name").isBlank() || qty==null || qty<=BigDecimal.ZERO) { toast("Podaj nazwę i ilość większą od zera"); return }
                if(draft.optString("unit","szt.")=="szt." && qty.stripTrailingZeros().scale()>0) { toast("Liczba sztuk musi być całkowita"); return }
                draft.put("quantity",qty.stripTrailingZeros().toPlainString())
                if(!draft.has("unit")) draft.put("unit","szt.")
            }
            if(formKind=="expenses") {
                val cents=WalletRules.money(draft.optString("amount"))
                if(cents==null || cents<=0 || cents<draft.optLong("reimbursedCents")) { toast("Podaj poprawną kwotę, nie mniejszą niż zapisany zwrot"); return }
                draft.put("cents",cents); draft.remove("amount")
            }
            if(formKind=="notes" && !DomainRules.validNote(draft.optString("text"),arr(draft,"photos").length())) { toast("Dodaj tekst lub zdjęcie"); return }
            if(formKind=="links" && arr(draft,"photos").length()==0 && draft.optString("Link Google Drive").isBlank()) { toast("Dodaj zdjęcie lub link"); return }
        }
        val maps=draft.optString("maps"); val link=draft.optString("Link Google Drive")
        if((maps.isNotBlank() && !maps.startsWith("https://")) || (link.isNotBlank() && !link.startsWith("https://"))) { toast("Link musi zaczynać się od https://"); return }
        // Validate object references at the persistence boundary, including stale drafts.
        val objectRefs=rows(d,"objects").map { ObjectIntegrityRules.ObjectRef(it.optString("id"),it.optBoolean("archived")) }
        if(formKind!="objects") {
            val assigned=draft.optString("objectId")
            if(assigned.isNotBlank()) {
                val old=rows(d,formKind).find { it.optString("id")==draft.optString("id") }
                if(!ObjectIntegrityRules.canSaveAssignment(assigned,old?.optString("objectId"),objectRefs)) {
                    toast("Wybierz aktywny obiekt"); return
                }
            }
        }
        if(formKind=="links") {
            val previous=rows(d,"links").find { it.optString("id")==draft.optString("id") }
            if(!ObjectIntegrityRules.canSavePhotoEntry(
                arr(draft,"photos").length(),draft.optString("Link Google Drive"),
                draft.optString("objectId"),previous?.optString("objectId"),objectRefs
            )) { toast("Zdjęcia wymagają aktywnego obiektu i zdjęcia lub linku"); return }
        }
        val capturedGeo=if(formKind=="objects") draft.optJSONObject("_gps_capture") else null
        val objectId=draft.optString("id")
        val geoCapture=if(capturedGeo!=null) GpsObjectLocationRules.Capture(
            capturedGeo.optString("objectId"),capturedGeo.optDouble("lat"),
            capturedGeo.optDouble("lon"),capturedGeo.optDouble("accuracy"),
            capturedGeo.optLong("time")) else null
        if(geoCapture!=null && !GpsObjectLocationRules.belongsTo(geoCapture,objectId,geoCapture.fixTimeMs)) {
            toast("Lokalizacja GPS nie pasuje do obiektu. Pobierz GPS ponownie.")
            return
        }
        draft.remove("_gps_capture")
        val list=arr(d,formKind); val index=(0 until list.length()).find { list.optJSONObject(it)?.optString("id")==draft.optString("id") }
        if(index==null) list.put(draft) else list.put(index,draft)
        save(d,"SAVE_"+formKind,draft.optString("id"))
        if(geoCapture!=null) {
            prefs.edit().putString("gps_object_"+objectId,JSONObject()
                .put("lat",geoCapture.latitude).put("lon",geoCapture.longitude).put("accuracy",geoCapture.accuracyM).put("time",geoCapture.fixTimeMs)
                .put("radius",GpsObjectLocationRules.RADIUS_M).toString()).commit()
        }
        PhotoQueue.markPending(this,formKind,draft)
        quickPhotoUpload=false
        prefs.edit().remove("draft_"+formKind).apply()
        if (formKind == "objects" && startAfterObjectSave) {
            startAfterObjectSave = false
            home()
            startOnObject(draft,startObjectSessionId)
            return
        }
        openScreen(formReturn)
    }
    private fun pickPhotos() {
        prefs.edit().putString("draft_"+formKind,draft.toString()).apply()
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type="image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        },201)
    }
    private fun camera() {
        val folder=File(filesDir,"photos").apply { mkdirs() }
        val file=File(folder,UUID.randomUUID().toString()+".jpg"); cameraPath=file.absolutePath
        val uri=FileProvider.getUriForFile(this,packageName+".photos",file)
        try {
            startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri); clipData=ClipData.newRawUri("photo",uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },202)
        } catch (_:Exception) { cameraPath=null; toast("Aparat niedostępny. Wybierz zdjęcie z galerii.") }
    }

    private val driveScope="https://www.googleapis.com/auth/drive.file"
    private fun driveSettings() {
        base("Google Disk"); screen="drive"; nav()
        label(if(prefs.getBoolean("driveConnected",false)) "Połączono: "+prefs.getString("driveEmail","Google Disk") else "Disk nie jest połączony")
        prefs.getString("driveError",null)?.takeIf { it.isNotBlank() }?.let { label(it) }
        label("Zdjęcia będą zapisane w folderze aplikacji, z podziałem na obiekty i daty. Dotychczasowy folder zostanie zachowany.")
        label("Kopie dziennika: WorkMeter / Backup")
        label(prefs.getString("backupStatus","Automatyczna kopia raz dziennie po połączeniu Google Disk").orEmpty())
        button("Kopia dzienna: "+if(prefs.getBoolean("dailyBackupEnabled",true)) "włączona" else "wyłączona") {
            val enabled=!prefs.getBoolean("dailyBackupEnabled",true)
            prefs.edit().putBoolean("dailyBackupEnabled",enabled).apply()
            if(enabled) DriveBackupWorker.schedule(this) else androidx.work.WorkManager.getInstance(this).cancelUniqueWork("workmeter-daily-backup")
            driveSettings()
        }
        button("Skrót zdjęć w powiadomieniach") {
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),205)
            else { UploadNotifications.shortcut(this); toast("Skrót dodany do powiadomień") }
        }
        button("Połącz Google Disk") { pendingUpload=""; authorizeDrive() }
        if(prefs.getBoolean("driveConnected",false)) button("Odłącz Google Disk") {
            if(uploading) { toast("Poczekaj na zakończenie przesyłania"); return@button }
            val request=com.google.android.gms.auth.api.identity.RevokeAccessRequest.builder().setScopes(listOf(Scope(driveScope))).build()
            Identity.getAuthorizationClient(this).revokeAccess(request).addOnSuccessListener {
                androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag("driveBackup")
                driveToken=null; prefs.edit().remove("driveEmail").remove("driveError").putBoolean("driveConnected",false).apply(); driveSettings()
            }.addOnFailureListener { driveFailure(it) }
        }
        filters("driveDiagnostics",caption="Diagnostyka") {
            label("Dane do konfiguracji autoryzacji Google")
            label("Pakiet: "+packageName+"\nSHA-1: "+signingFingerprint())
        }
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==307) {
            if(grantResults.any { it==PackageManager.PERMISSION_GRANTED }) setGpsEnabled(true)
            else toast("Bez pozwolenia na lokalizację GPS pozostanie wyłączony.")
            if(screen=="gps") gpsScreen()
        }
        if(requestCode==308) {
            if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) {
                CoreGpsStageBridge.refresh(this)
                toast("Powiadomienia GPS włączone")
            } else toast("Powiadomienia GPS wyłączone. Włączysz je później w ustawieniach telefonu.")
            if(screen=="gps") gpsScreen()
        }
        if(requestCode in listOf(204,205)) {
            if(grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED) UploadNotifications.shortcut(this)
            else toast("Powiadomienia są wyłączone. Możesz włączyć je w ustawieniach telefonu.")
        }
    }
    private fun driveFailure(e:Exception) {
        backupDriveResult?.invoke(null); backupDriveResult=null
        pendingUpload=""
        val code=(e as? com.google.android.gms.common.api.ApiException)?.statusCode
        val message=when(code) {
            10 -> "Google odrzucił konfigurację aplikacji (kod 10). Sprawdź zgodność podpisu APK z klientem Android w Google Cloud."
            16 -> "Autoryzacja nie została zakończona (kod 16). Sprawdź konto testowe Google i spróbuj ponownie."
            7 -> "Brak połączenia z Google. Sprawdź internet i spróbuj ponownie."
            else -> "Nie udało się połączyć z Google Disk"+(code?.let { " (kod "+it+")" } ?: "")+". Sprawdź konto, dostęp i konfigurację Google."
        }
        prefs.edit().putString("driveError",message).apply(); toast(message)
        if(screen=="drive") driveSettings()
    }
    private fun signingFingerprint():String = try {
        @Suppress("DEPRECATION")
        val signature=packageManager.getPackageInfo(packageName,android.content.pm.PackageManager.GET_SIGNATURES).signatures!!.first()
        MessageDigest.getInstance("SHA-1").digest(signature.toByteArray()).joinToString(":") { "%02X".format(it) }
    } catch (_:Exception) { "niedostępny" }
    private fun authorizeDrive() {
        val request=AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(driveScope))).build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if(result.hasResolution()) {
                    try { startIntentSenderForResult(result.pendingIntent!!.intentSender,203,null,0,0,0) }
                    catch (e:Exception) { driveFailure(e) }
                } else authorized(result)
            }.addOnFailureListener { driveFailure(it) }
    }
    private fun authorized(result:com.google.android.gms.auth.api.identity.AuthorizationResult) {
        val token=result.accessToken
        if(token.isNullOrBlank() || driveScope !in result.grantedScopes) {
            backupDriveResult?.invoke(null); backupDriveResult=null; pendingUpload=""; toast("Nie udzielono dostępu do Google Disk"); return
        }
        driveToken=token
        val upload=pendingUpload; pendingUpload=""
        io.execute {
            try {
                val about=driveJson("GET","https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)",token)
                runOnUiThread {
                    prefs.edit().remove("driveError").putBoolean("driveConnected",true).putString("driveEmail",about.optJSONObject("user")?.optString("emailAddress") ?: "Google Drive").apply()
                    DriveBackupWorker.schedule(this)
                    val backupCallback=backupDriveResult; backupDriveResult=null
                    if(backupCallback!=null) { backupCallback(token); return@runOnUiThread }
                    if(upload=="queue") { PhotoQueue.enqueueAll(this); uploadQueue() } else if(upload.isBlank()) driveSettings() else if(upload=="backup") { DriveBackupWorker.manual(this); toast("Kopia w kolejce: WorkMeter / Backup"); logs() } else if(upload.startsWith("share:")) shareFolder(upload.substringAfter(":"),token) else uploadPhotos(upload,token)
                }
            } catch (_:Exception) { runOnUiThread { backupDriveResult?.invoke(null); backupDriveResult=null; prefs.edit().putString("driveError","Google Disk nie odpowiada. Sprawdź, czy Drive API jest włączone i konto ma dostęp.").apply(); toast("Nie udało się połączyć z Google Disk"); } }
        }
    }
    private fun driveJson(method:String,url:String,token:String,body:JSONObject?=null):JSONObject {
        val c=URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod=method; c.connectTimeout=20000; c.readTimeout=30000
            c.setRequestProperty("Authorization","Bearer "+token)
            if(body!=null) {
                c.doOutput=true; c.setRequestProperty("Content-Type","application/json; charset=UTF-8")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            if(c.responseCode !in 200..299) throw java.io.IOException("Drive HTTP "+c.responseCode)
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() }.ifBlank { "{}" })
        } finally { c.disconnect() }
    }
    private fun uploadPhotos(reference:String,token:String) {
        if(token.isBlank()) return
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),204)
        DriveUploadWorker.enqueue(this,reference,prefs.getString("driveEmail","").orEmpty())
        toast("Zdjęcia w kolejce. Możesz zwinąć aplikację; postęp będzie w powiadomieniu.")
    }
    private fun language() {
        base("Język aplikacji"); screen="language"; nav()
        button("Polski ✓") { prefs.edit().putString("appLanguage","pl").apply(); toast("Wybrano język polski") }
    }
    private fun charts() {
        base("Grafiki");screen="charts";nav()
        val today=LocalDate.now()
        val period=TimeAnalytics.restorePeriod(prefs.getString("chartPeriod",null))
        fun datePreference(key: String): LocalDate =
            runCatching { LocalDate.parse(prefs.getString(key, today.toString())) }.getOrDefault(today)
        val custom=datePreference("chartFrom") to datePreference("chartTo")
        val range=TimeAnalytics.dateRange(period,today,custom)
        filters("charts") {
            val periods=listOf(
                TimeAnalytics.Period.TODAY to "Dzisiaj",
                TimeAnalytics.Period.CURRENT_WEEK to "Bieżący tydzień",
                TimeAnalytics.Period.PREVIOUS_WEEK to "Ostatni tydzień",
                TimeAnalytics.Period.CURRENT_MONTH to "Bieżący miesiąc",
                TimeAnalytics.Period.PREVIOUS_MONTH to "Ostatni miesiąc",
                TimeAnalytics.Period.CUSTOM to "Własny okres"
            )
            for((choice,caption) in periods) button(caption) {
                prefs.edit().putString("chartPeriod",choice.name).apply()
                charts()
            }
            if(period==TimeAnalytics.Period.CUSTOM) {
                button("Data od: "+dateText(custom.first.toString())) {
                    pickReportDate(custom.first) { prefs.edit().putString("chartFrom",it.toString()).apply(); charts() }
                }
                button("Data do: "+dateText(custom.second.toString())) {
                    pickReportDate(custom.second) { prefs.edit().putString("chartTo",it.toString()).apply(); charts() }
                }
            }
        }
        if(range==null) { label("Data do nie może być wcześniejsza niż Data od."); return }
        label("Wybrany okres: "+dateText(range.first.toString())+" — "+dateText(range.second.toString()))
        val shifts=TimeAnalyticsSnapshot.shifts(data(),System.currentTimeMillis())
        val result=TimeAnalytics.calculate(shifts,range.first,range.second,ZoneId.systemDefault())
        label("Czas według godzin")
        TimeChartCategory.entries.take(TimeAnalytics.WORK_CATEGORY_COUNT).forEach { category ->
            if(result.totals[category.ordinal]>0 || category in listOf(TimeChartCategory.ON_SITE,TimeChartCategory.TRAVEL,TimeChartCategory.SHOPPING)) {
                val legend=TextView(this).apply { text="● "+category.label; setTextColor(palette.textPrimary); textSize=14f; setPadding(0,dp(4),0,dp(4)) }
                val colored=android.text.SpannableString(legend.text)
                colored.setSpan(android.text.style.ForegroundColorSpan(category.color),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                legend.text=colored; root.addView(legend)
            }
        }
        root.addView(TimeChart(this,result,false) { hour ->
            val title="%02d:00–%02d:00".format(hour,hour+1)
            val lines=result.hours[hour].mapIndexed { index,ms -> TimeChartCategory.entries[index].label+": "+(ms/60_000)+" min" }
            AlertDialog.Builder(this,dialogStyle).setTitle(title).setMessage(lines.joinToString("\n"))
                .setPositiveButton("Zamknij",null).show()
        },LinearLayout.LayoutParams(-1,dp(230)))
        label("Suma czasu w każdej godzinie doby, ze wszystkich dni wybranego okresu.")
        filters("chartHours",caption="Szczegóły godzinowe") {
            result.hours.forEachIndexed { hour,values -> if(values.sum()>0) label("%02d:00".format(hour)+" · "+values.mapIndexed { i,n -> TimeChartCategory.entries[i].label+": "+duration(n) }.joinToString(" · ")) }
        }
        label("Podział czasu pracy")
        root.addView(TimeChart(this,result,true),LinearLayout.LayoutParams(-1,dp(240)))
        val sum=result.workMillis
        result.totals.forEachIndexed { i, elapsed ->
            val category = TimeChartCategory.entries[i]
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(View(this).apply {
                background = background(category.color)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(8) })
            val percentage = result.sharePercent(i)
            row.addView(TextView(this).apply {
                text = category.label + " — " + duration(elapsed) + " · " + "%.1f%%".format(Locale("pl"), percentage)
                textSize = 16f
                setTextColor(palette.textPrimary)
                setPadding(0, dp(7), 0, dp(7))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        if(sum==0L) label("Brak czasu pracy w wybranym okresie.")
        if(result.personalMillis>0) label("Czas prywatny: "+duration(result.personalMillis)+" · poza czasem pracy")
        filters("chartComparison",caption="Porównanie okresów") {
            val previous=TimeAnalytics.comparisonRange(period,range)
            val comparison=TimeAnalytics.calculate(shifts,previous.first,previous.second,ZoneId.systemDefault())
            label("Poprzedni okres: "+dateText(previous.first.toString())+" — "+dateText(previous.second.toString()))
            label("Czas pracy: "+duration(sum)+" · wcześniej: "+duration(comparison.workMillis))
            result.totals.forEachIndexed { category, millis ->
                label(TimeChartCategory.entries[category].label+": "+duration(millis)+" · wcześniej: "+
                    duration(comparison.totals[category]))
            }
        }
    }
    private fun toggleShopping() {
        if(active(data())?.optString("kind")==PrivateTimeRules.KIND) { toast("Najpierw wznów pracę"); return }
        val a=active(data()) ?: run { toast("Najpierw rozpocznij pracę");return }
        if(a.optString("kind")=="Zakupy") {
            confirmAction("Zakończyć zakupy? Wybierz obiekt lub rozpocznij przejazd.") {
                AlertDialog.Builder(this,dialogStyle).setTitle("Co dalej?").setItems(arrayOf("Na obiekt","Przejazd","Bez przypisania")) { _,i ->
                    when(i) { 0 -> switchObject();1 -> beginStage("Przejazd");else -> beginStage("Nieprzypisane") }
                }.setNegativeButton("Anuluj",null).show()
            }
        } else startShopping()
    }
    private fun startShopping() {
        if(active(data())?.optString("kind")==PrivateTimeRules.KIND) { toast("Najpierw wznów pracę"); return }
        val d=data();val a=active(d) ?: return
        val now=System.currentTimeMillis()
        if(!canCloseCurrentStage(a,now)) return
        val id=UUID.randomUUID().toString()
        if(a.optString("kind")=="Przejazd") a.optJSONObject("stageMeta")?.put("toObject","Zakupy")
        closeStage(a,now)
        val note=JSONObject().put("id",id).put("date",localDate(now).toString()).put("created",fmt.format(Date(now))).put("shopping",true).put("text","").put("photos",JSONArray()).put("objectId",a.optString("objectId"))
        arr(d,"notes").put(note)
        a.put("stageStart",now).put("kind","Zakupy").put("object","").put("objectId","").put("shoppingRecordId",id).put("stageMeta",JSONObject().put("shoppingRecordId",id))
        save(d,"SHOPPING_START",id);home();shoppingRecord(id)
    }
    private fun shoppingRecord(id:String) { if(id.isNotBlank()) newForm("notes",id) }
    private fun openPhoto(uri:String) {
        try {
            val bitmap=PhotoPreview.decode(this,Uri.parse(uri),4096)
            val dialog=Dialog(this,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            val frame=FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
            frame.addView(ZoomPhoto(this,bitmap),FrameLayout.LayoutParams(-1,-1))
            frame.addView(makeButton("Zamknij") { dialog.dismiss() },FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP or Gravity.END))
            dialog.setContentView(frame);dialog.show()
        } catch(_:Exception) { toast("Nie można otworzyć zdjęcia") }
    }
    private fun uploadQueue() {
        base("Kolejka wysyłania");screen="queue";nav()
        button("PONÓW WYSYŁANIE") { sendQueue() }
        if(!prefs.getBoolean("driveConnected",false)) button("Połącz Google Drive") { pendingUpload="queue"; authorizeDrive() }
        val statuses=listOf("links","materials","notes","expenses").flatMap { key -> rows(data(),key).map { PhotoQueue.status(this,key,it) } }
        label("Wysłano: ${statuses.sumOf { it.sent }} · Do wysłania: ${statuses.sumOf { it.pending }} · Błędy: ${statuses.sumOf { it.failed }}")
        val network=getSystemService(android.net.ConnectivityManager::class.java)
        val internet=network?.getNetworkCapabilities(network.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
        label(if(internet) "Internet dostępny" else "Brak potwierdzonego dostępu do internetu — zdjęcia pozostają lokalnie.")
        var count=0
        for(key in listOf("links","materials","notes","expenses")) for(row in rows(data(),key)) {
            if(arr(row,"photos").length()==0) continue
            val status=PhotoQueue.status(this,key,row)
            if(status.pending==0) continue
            count++
            label(dateText(recordDate(row))+" · "+status.caption)
            button(if(key=="expenses") "Paragon — otwórz portfel" else "Otwórz wpis") { recordDetails(key,row.optString("id"),"queue") }
        }
        if(count==0) label("Brak zdjęć oczekujących na wysłanie.")
        label("Zdjęcia pozostają na telefonie. Kolejka jest wznawiana po odzyskaniu internetu.")
    }
    private fun sendQueue() {
        if(prefs.getBoolean("driveConnected",false)) PhotoQueue.enqueueAll(this)
        uploadQueue()
        if(!prefs.getBoolean("driveConnected",false)) toast("Połącz Google Drive, aby wysłać zdjęcia")
    }
    private fun shareFolder(id:String,token:String) {
        if(!prefs.getBoolean("shareConfirmed_"+id,false)) return
        prefs.edit().remove("shareConfirmed_"+id).apply()
        val row=rows(data(),"links").find { it.optString("id")==id } ?: return
        val folder=PhotoQueue.folderId(row.optString("Link Google Drive"))
        if(folder==null) { toast("Najpierw wyślij zdjęcia lub wpisz poprawny link folderu Google Disk");return }
        io.execute {
            try {
                driveJson("POST","https://www.googleapis.com/drive/v3/files/"+folder+"/permissions",token,JSONObject().put("type","anyone").put("role","reader"))
                runOnUiThread { toast("Folder: każda osoba mająca link — przeglądający") }
            } catch(_:Exception) { runOnUiThread { toast("Nie można udostępnić folderu. Sprawdź uprawnienia Google Disk.") } }
        }
    }
    private fun materialLine(r:JSONObject)=r.optString("name")+" — "+r.optString("quantity")+" "+r.optString("unit","szt.")
    private fun stockEntries(d:JSONObject):List<MaterialStock.Entry> = rows(d,"materials").map { MaterialStock.Entry(it.optString("id"),it.optString("name"),it.optString("unit","szt."),it.optString("quantity").replace(",",".").toBigDecimalOrNull() ?: BigDecimal.ZERO,it.optString("storage")=="car",it.optString("sourceId")) }
    private fun validStock(d:JSONObject):Boolean {
        // Legacy unstructured materials remain untouched; only stock-linked entries require the new invariants.
        val entries=stockEntries(d)
        return MaterialStock.valid(entries.filter { it.car || it.sourceId.isNotBlank() })
    }
    private fun rememberMaterial(d:JSONObject,row:JSONObject) {
        val catalog=arr(d,"materialCatalog");val name=row.optString("name").trim()
        val old=rows(d,"materialCatalog").find { it.optString("name").trim().equals(name,true) }
        if(old==null) catalog.put(JSONObject().put("name",name).put("unit",row.optString("unit","szt.")))
        else old.put("unit",row.optString("unit","szt."))
    }
    private fun materialNameField() {
        label("Nazwa materiału")
        val choices=(rows(data(),"materialCatalog")+rows(data(),"materials")).filter { it.optString("name").isNotBlank() }.distinctBy { it.optString("name").trim().lowercase(Locale.ROOT) }
        val input=AutoCompleteTextView(ContextThemeWrapper(this,dialogStyle)).apply {
            setText(draft.optString("name"));threshold=1;textSize=17f;setSingleLine();setPadding(dp(10),dp(8),dp(10),dp(8));background=background(palette.surface).apply { setStroke(dp(1),palette.border) }
        }
        val names=choices.map { it.optString("name") }
        val adapter=object:ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,ArrayList(names)) {
            override fun getFilter():Filter=object:Filter() {
                override fun performFiltering(query:CharSequence?):FilterResults {
                    val found=names.filter { it.contains(query?.toString().orEmpty(),true) }
                    return FilterResults().apply { values=found;count=found.size }
                }
                override fun publishResults(query:CharSequence?,result:FilterResults?) {
                    setNotifyOnChange(false);clear()
                    @Suppress("UNCHECKED_CAST") val values=result?.values as? List<String> ?: emptyList()
                    addAll(values);notifyDataSetChanged()
                }
            }
        }
        input.setAdapter(adapter)
        input.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun afterTextChanged(s:Editable?) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { draft.put("name",s.toString()) }
        })
        input.setOnItemClickListener { parent,_,position,_ ->
            val name=parent.getItemAtPosition(position).toString()
            draft.put("name",name);choices.find { it.optString("name")==name }?.let { draft.put("unit",it.optString("unit","szt.")) };renderForm()
        }
        if(draft.optString("sourceId").isNotBlank()) input.isEnabled=false
        root.addView(input,LinearLayout.LayoutParams(-1,dp(48)))
    }
    private fun pendingMaterial():JSONObject? {
        return validatedMaterialLine(draft) ?: run {
            toast("Podaj nazwę i poprawną ilość większą od zera")
            null
        }
    }

    private fun validatedMaterialLine(line: JSONObject): JSONObject? {
        val fields=MaterialStock.validateDraft(
            line.optString("name"),line.optString("quantity"),line.optString("unit","szt.")) ?: return null
        val photos=line.optJSONArray("photos")
        if(line.has("photos") && photos==null) return null
        return JSONObject().put("name",fields.name).put("quantity",fields.quantity)
            .put("unit",fields.unit).put("photos",JSONArray(photos?.toString() ?: "[]"))
    }
    private fun addBatchLine() {
        val row=pendingMaterial() ?: return
        arr(draft,"batch").put(row)
        listOf("name","quantity","unit","photos").forEach { draft.remove(it) }
        prefs.edit().putString("draft_materials",draft.toString()).apply();renderForm()
    }
    private fun saveMaterials() {
        if(recordDate(draft).isBlank()) { toast("Wybierz datę");return }
        if(draft.optString("storage")!="car" && draft.optString("objectId").isBlank()) { toast("Wybierz obiekt");return }
        val storedBatch=draft.optJSONArray("batch")
        if(draft.has("batch") && storedBatch==null) { toast("Nieprawidłowa lista materiałów"); return }
        val batch=mutableListOf<JSONObject>()
        if(storedBatch!=null) for(i in 0 until storedBatch.length()) {
            val line=storedBatch.optJSONObject(i)?.let(::validatedMaterialLine)
            if(line==null) { toast("Sprawdź zapisane pozycje materiałów"); return }
            batch.add(line)
        }
        val d=data();val existing=rows(d,"materials").find { it.optString("id")==draft.optString("id") }
        val objectRefs=rows(d,"objects").map { ObjectIntegrityRules.ObjectRef(it.optString("id"),it.optBoolean("archived")) }
        val destination=draft.optString("objectId")
        if(!ObjectIntegrityRules.canSaveMaterialAssignment(draft.optString("storage"),destination,existing?.optString("objectId"),objectRefs)) {
            toast("Wybierz aktywny obiekt dla materiałów");return
        }
        if(draft.optString("name").isNotBlank() || draft.optString("quantity").isNotBlank() || batch.isEmpty()) batch.add(pendingMaterial() ?: return)
        if(existing!=null && batch.size!=1) { toast("Edytuj jeden zapis naraz");return }
        val list=arr(d,"materials");val saved=mutableListOf<JSONObject>()
        for((i,line) in batch.withIndex()) {
            val row=JSONObject(draft.toString());row.remove("batch")
            listOf("name","quantity","unit","photos").forEach { row.put(it,line.get(it)) }
            row.put("id",if(i==0) draft.optString("id") else UUID.randomUUID().toString())
            if(row.optString("sourceId").isNotBlank()) row.put("storage","object")
            val index=(0 until list.length()).find { list.getJSONObject(it).optString("id")==row.optString("id") }
            if(index==null) list.put(row) else list.put(index,row)
            saved.add(row);rememberMaterial(d,row)
        }
        if(!validStock(d)) { toast("Niewystarczający stan w samochodzie lub zmiana powiązanego materiału");return }
        save(d,"MATERIALS_SAVE",saved.joinToString { it.toString() })
        saved.forEach { PhotoQueue.markPending(this,"materials",it) }
        prefs.edit().remove("draft_materials").apply();openScreen(formReturn)
    }
    private fun materialGroup(date:String,objectId:String) {
        base("Materiały obiektu");screen="materialGroup:"+date+":"+objectId;nav()
        label(dateText(date));label(objectById(objectId)?.let { objectName(it) } ?: "Bez obiektu")
        button("+ Dodaj materiał") { newForm("materials",objectId=objectId);draft.put("date",date);renderForm() }
        rows(data(),"materials").filter { recordDate(it)==date && it.optString("objectId")==objectId && it.optString("storage")!="car" }.forEach { r ->
            button(materialLine(r)) { recordDetails("materials",r.optString("id"),screen) }
        }
    }
    private fun carStock() {
        base("Materiały w samochodzie");screen="car";nav()
        button("+ Dodaj materiał") { newForm("materials");draft.put("storage","car");renderForm() }
        val entries=stockEntries(data())
        for(source in entries.filter { it.car }) {
            label(source.name+" — "+MaterialStock.remaining(source,entries).stripTrailingZeros().toPlainString()+" "+source.unit)
            button("PRZENIEŚ NA OBIEKT · "+source.name) { transferMaterial(source.id) }
            button("Historia / edytuj · "+source.name) {
                val rows=rows(data(),"materials").filter { it.optString("id")==source.id || it.optString("sourceId")==source.id }
                AlertDialog.Builder(this,dialogStyle).setTitle("Poprawienia / przeniesienia").setItems(rows.map { dateText(recordDate(it))+" · "+materialLine(it) }.toTypedArray()) { _,i -> recordDetails("materials",rows[i].optString("id"),"car") }.setNegativeButton("Zamknij",null).show()
            }
        }
        if(entries.none { it.car }) label("Brak materiałów w samochodzie.")
    }
    private fun transferMaterial(sourceId:String) {
        chooseObject(false) { obj ->
            if(obj==null) return@chooseObject
            val source=rows(data(),"materials").find { it.optString("id")==sourceId } ?: return@chooseObject
            newForm("materials",objectId=obj.optString("id"))
            draft=JSONObject().put("id",UUID.randomUUID().toString()).put("sourceId",sourceId).put("storage","object").put("objectId",obj.optString("id")).put("date",LocalDate.now().toString()).put("name",source.optString("name")).put("unit",source.optString("unit","szt."))
            renderForm()
        }
    }

}
