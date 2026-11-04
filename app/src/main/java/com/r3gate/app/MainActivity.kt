package com.r3gate.app

import android.app.*
import android.location.LocationManager
import android.location.LocationListener
import android.location.Location
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Color
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
    private val expandedFilters = mutableSetOf<String>()
    private var formKind = ""
    private var formReturn = "home"
    private var draft = JSONObject()
    private var quickPhotoUpload=false
    private var cameraPath: String? = null
    private val io = Executors.newSingleThreadExecutor()
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
        if (!GpsWorkdaySession.workdayOpen(prefs) &&
            (prefs.getBoolean("gps_enabled",false) || prefs.getBoolean("gps_resume_pending",false))) {
            if (GpsWorkdaySession.hasConsent(prefs)) prefs.edit().putBoolean(GpsWorkdaySession.CONSENT,true).apply()
            setGpsEnabled(false)
        }
        migrateWallet()
        DriveBackupWorker.schedule(this)
        expandedFilters.addAll(savedInstanceState?.getStringArrayList("expandedFilters") ?: emptyList())
        currentSection=savedInstanceState?.getString("currentSection") ?: "Obiekty"
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
    override fun onStop() { walletUnlocked=false; super.onStop() }
    override fun onRestart() {
        super.onRestart()
        // Keep the existing view (including its scroll position) when returning
        // from another app. Only lock sensitive finance screens.
        if(GpsNavigationRestoreRules.lockOnRestart(screen)) settings()
    }
    private fun hash(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun setupPin() {
        val field = EditText(ContextThemeWrapper(this,R.style.WorkMeterDialogTheme)).apply { hint="PIN (4–8 cyfr)"; inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Ustaw PIN").setMessage("Kod chroni rozpoczęcie i zakończenie pracy.").setView(field).setCancelable(false).setPositiveButton("Zapisz") { _, _ ->
            val p=field.text.toString(); if (p.length in 4..8) {
                val salt=UUID.randomUUID().toString(); prefs.edit().putString("salt",salt).putString("pin",hash(salt+p)).apply(); home()
            } else { toast("PIN musi mieć 4–8 cyfr"); setupPin() }
        }.show()
    }
    private fun confirmPin(action: () -> Unit) {
        val field=EditText(ContextThemeWrapper(this,R.style.WorkMeterDialogTheme)).apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint="PIN" }
        AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Potwierdź PIN").setView(field).setNegativeButton("Anuluj",null).setPositiveButton("Potwierdź") { _, _ ->
            if (hash(prefs.getString("salt","")+field.text.toString()) == prefs.getString("pin","")) action() else toast("Nieprawidłowy PIN")
        }.show()
    }
    private fun confirmAction(message:String="Wykonać tę czynność?",action:()->Unit) {
        AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Potwierdzenie").setMessage(message)
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
            "start" -> confirmPin { startShift() }
            "stop" -> confirmPin { stopShift() }
            "switch" -> confirmAction("Zmienić obiekt?") { switchObject() }
            "travel" -> toggleTravel()
            "shopping" -> toggleShopping()
        }
    }
    /** Recovery entry point when a lock-screen notification was dismissed. */
    private fun pendingGpsStageEventId(d: JSONObject, nowMs: Long): String? {
        val running=active(d) ?: return null
        val saved=(CoreGpsProposalStore.read(AndroidCoreGpsProposalStorage(this)) as?
            CoreGpsProposalStore.ReadResult.Success)?.events ?: return null
        val activeIds=rows(d,"objects").filter { !it.optBoolean("archived") }
            .map { it.optString("id") }.toSet()
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
        val activeIds=rows(d,"objects").filter { !it.optBoolean("archived") }
            .map { it.optString("id") }.toSet()
        val shift=CoreGpsStageRules.Shift(true,running.optString("kind","Obiekt"),running.optString("objectId"))
        val stageStart=maxOf(running.optLong("start"),running.optLong("stageStart",running.optLong("start")))
        if(!CoreGpsStageReviewQueue.isCurrent(eventId,current.events,shift,activeIds,
                stageStart,System.currentTimeMillis()) ||
            (!CoreGpsStageBridge.hasContinuousCoverage(this,event.timestampMs,System.currentTimeMillis()) ||
                !CoreGpsStageBridge.hasJournalEvidence(this,event))) return
        val obj=rows(d,"objects").find { it.optString("id")==event.objectId } ?: return
        val stage=CoreGpsStageRules.proposal(event.kind,event.objectId,shift,activeIds) ?: return
        val title=if(stage==CoreGpsStageRules.Proposal.START_TRAVEL)
            "Rozpocząć przejazd?" else "Potwierdzić przyjazd?"
        val message=if(stage==CoreGpsStageRules.Proposal.START_TRAVEL)
            "GPS wykrył wyjazd z: "+objectName(obj)+"\\nZmienimy etap od chwili potwierdzenia."
        else "GPS wykrył przyjazd: "+objectName(obj)+"\\nZmienimy etap od chwili potwierdzenia."
        AlertDialog.Builder(this,R.style.WorkMeterDialogTheme)
            .setTitle(title).setMessage(message).setNegativeButton("Odrzuć") { _,_ ->
                CoreGpsProposalStore.decide(storage,eventId,CoreGpsProposalInbox.Decision.USER_REJECTED)
                CoreGpsStageBridge.dismiss(this)
            }.setNeutralButton("Później", null).setPositiveButton("Potwierdź") { _,_ ->
                val latest=data(); val nowActive=active(latest) ?: return@setPositiveButton
                val available=rows(latest,"objects").filter { !it.optBoolean("archived") }
                    .map { it.optString("id") }.toSet()
                val currentShift=CoreGpsStageRules.Shift(true,nowActive.optString("kind","Obiekt"),
                    nowActive.optString("objectId"))
                val latestEvents=(CoreGpsProposalStore.read(storage) as?
                    CoreGpsProposalStore.ReadResult.Success)?.events ?: return@setPositiveButton
                val stageStart=maxOf(nowActive.optLong("start"),
                    nowActive.optLong("stageStart",nowActive.optLong("start")))
                if(!CoreGpsStageReviewQueue.isCurrent(eventId,latestEvents,currentShift,
                        available,stageStart,System.currentTimeMillis()) ||
                    (!CoreGpsStageBridge.hasContinuousCoverage(this,event.timestampMs,System.currentTimeMillis()) ||
                        !CoreGpsStageBridge.hasJournalEvidence(this,event))) {
                    toast("Propozycja jest już nieaktualna"); return@setPositiveButton
                }
                val selected=rows(latest,"objects").find { it.optString("id")==event.objectId &&
                    !it.optBoolean("archived") } ?: return@setPositiveButton
                val now=System.currentTimeMillis()
                if(now<=nowActive.optLong("stageStart",nowActive.optLong("start"))) return@setPositiveButton
                if(stage==CoreGpsStageRules.Proposal.START_TRAVEL) {
                    closeStage(nowActive,now)
                    val origin=nowActive.optString("object")
                    nowActive.put("stageStart",now).put("kind","Przejazd")
                        .put("object","").put("objectId","")
                        .put("stageMeta",JSONObject().put("fromObject",origin)
                            .put("fromObjectId",event.objectId))
                } else {
                    if(nowActive.optString("kind")=="Przejazd")
                        nowActive.optJSONObject("stageMeta")?.put("toObject",objectName(selected))
                            ?.put("toObjectId",event.objectId)
                    closeStage(nowActive,now)
                    nowActive.remove("stageMeta")
                    nowActive.put("stageStart",now).put("kind",workStageFor(selected))
                        .put("object",objectName(selected)).put("objectId",event.objectId)
                }
                // Commit the confirmed stage and reviewed proposal in the same durable write.
                // A process death cannot leave the work stage applied but the event pending.
                val reviewed=(CoreGpsProposalStore.read(storage) as? CoreGpsProposalStore.ReadResult.Success)
                    ?.events ?: return@setPositiveButton
                val marked=CoreGpsProposalInbox.decide(reviewed,eventId,
                    CoreGpsProposalInbox.Decision.USER_CONFIRMED)
                arr(latest,"logs").put(JSONObject().put("at",fmt.format(Date()))
                    .put("type","GPS_STAGE_CONFIRMED").put("detail",eventId))
                if(!prefs.edit().putString("data",latest.toString())
                    .putString(CoreGpsProposalStore.KEY,CoreGpsProposalStore.encode(marked))
                    .commit()) {
                    toast("Nie zapisano zmiany etapu, spróbuj ponownie")
                    return@setPositiveButton
                }
                GateWidget.refresh(this)
                CoreGpsStageBridge.dismiss(this)
                home()
            }.show()
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

    private fun togglePrivateTime() {
        val d = data()
        val shift = active(d) ?: return
        val now = System.currentTimeMillis()
        if (now <= shift.optLong("stageStart", shift.optLong("start"))) {
            toast("Sprawdź godzinę urządzenia")
            return
        }
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

    private fun startShift() {
        if (active(data()) != null) { toast("Dzień pracy już rozpoczęty"); return }
        chooseObject(false) { obj ->
            startOnObject(obj ?: return@chooseObject)
        }
    }
    private fun startOnObject(obj: JSONObject) {
        val d=data(); if (active(d)!=null) { toast("Dzień pracy już rozpoczęty"); home(); return }
        val t=System.currentTimeMillis(); d.put("active",JSONObject().put("id",UUID.randomUUID().toString()).put("start",t).put("object",objectName(obj)).put("objectId",obj.optString("id")).put("kind",workStageFor(obj)).put("stageStart",t).put("stages",JSONArray()).put("rate",defaultRate().toPlainString())); save(d,"START",fmt.format(Date(t)))
        if (GpsWorkdaySession.hasConsent(prefs)) setGpsEnabled(true)
        home()
    }
    private fun stopShift() {
        val d=data(); val a=active(d); if(a==null) { toast("Brak rozpoczętej pracy"); home(); return }
        val end=System.currentTimeMillis(); closeStage(a,end); a.put("end",end); arr(d,"shifts").put(a); d.remove("active"); save(d,"STOP",fmt.format(Date(end)))
        if (GpsWorkdaySession.hasConsent(prefs)) prefs.edit().putBoolean(GpsWorkdaySession.CONSENT,true).apply()
        setGpsEnabled(false)
        PhotoQueue.enqueueAll(this); home()
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
        objectPicker(false,true) { obj ->
            if(obj!=null) {
                val d=data(); val current=active(d) ?: return@objectPicker
                if(current.optString("kind")!="Przejazd") return@objectPicker
                val now=System.currentTimeMillis()
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
        val list = arr(data(), "shifts")
        val pl = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela")
        val shifts = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
            .filter {
                val date = Instant.ofEpochMilli(it.optLong("start")).atZone(ZoneId.systemDefault()).toLocalDate()
                !date.isBefore(from) && !date.isAfter(to) && it.optLong("end") > it.optLong("start")
            }.sortedBy { it.optLong("start") }
        if (shifts.isEmpty()) {
            label("Brak zakończonych zmian w wybranym okresie.")
            return
        }
        var sum = 0L
        val lines = shifts.map {
            val start = it.optLong("start")
            val end = it.optLong("end")
            val date = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
            sum += workedMillis(it, start, end)
            "${date.format(df)} ${pl[date.dayOfWeek.value - 1]} ${clock.format(Date(start))}–${clock.format(Date(end))}"
        }
        val minutes = sum / 60000
        val sms = lines.joinToString("\n\n") + "\n\nRazem\n${minutes / 60} godz. ${minutes % 60} min"
        label(sms)
        button("KOPIUJ RAPORT") {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("WorkMeter raport", sms))
            toast("Raport skopiowany")
        }
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(n:Int) = (n * resources.displayMetrics.density).toInt()
    private fun background(color:Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(8).toFloat()
    }
    private fun base(title:String) {
        homeVisible=false
        val frame=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val top=View(this); frame.addView(top,LinearLayout.LayoutParams(-1,0))
        val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),0,dp(8),0); setBackgroundColor(0xFF37474F.toInt()) }
        header.addView(TextView(this).apply { text=title; textSize=27f; gravity=Gravity.CENTER_VERTICAL; setTextColor(Color.WHITE); setTypeface(resources.getFont(R.font.clash_grotesk),android.graphics.Typeface.BOLD) },LinearLayout.LayoutParams(0,dp(56),1f).apply { gravity=Gravity.CENTER_VERTICAL })
        if(title=="WorkMeter") header.addView(TextView(this).apply {
            text="SOLO"
            textSize=11f
            setTextColor(0xFFAFE3CE.toInt())
            setPadding(dp(7),dp(5),dp(7),dp(5))
            background=background(0xFF28443F.toInt())
        },LinearLayout.LayoutParams(-2,-2).apply { rightMargin=dp(7) })
        if(title=="WorkMeter") header.addView(Button(this).apply {
            text="⚙"; textSize=27f; contentDescription="Ustawienia"; setTextColor(Color.WHITE)
            background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT); backgroundTintList=null; setPadding(0,0,0,0)
            setOnClickListener { settings() }
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        frame.addView(header,LinearLayout.LayoutParams(-1,dp(56)))
        val scroll=ScrollView(this).apply { setBackgroundColor(Color.WHITE); isFillViewport=true }
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(24)); isFocusableInTouchMode=true; descendantFocusability=ViewGroup.FOCUS_BEFORE_DESCENDANTS }
        scroll.addView(root,FrameLayout.LayoutParams(-1,-2))
        frame.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        // Persistent six-item navigation mirrors the supplied WorkMeter 4.0 prototype.
        // It lives outside the screen-specific content, so forms and history remain intact.
        val navigation=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(3),dp(4),dp(3),dp(3))
            setBackgroundColor(Color.WHITE)
            elevation=dp(4).toFloat()
        }
        val destinations=listOf(
            Triple("⌂","Dzisiaj","home"),
            Triple("◷","Mój czas","report"),
            Triple("▦","Obiekty","objects"),
            Triple("▧","Zdjęcia","list:links:"),
            Triple("▤","Materiały","list:materials:"),
            Triple("☷","Więcej","menu")
        )
        destinations.forEach { (symbol,name,target) ->
            navigation.addView(Button(this).apply {
                text="$symbol\n$name"
                contentDescription=name
                textSize=10f
                isAllCaps=false
                minHeight=dp(52); minimumHeight=dp(52)
                setPadding(0,0,0,0)
                setTextColor(if(screen==target || (target=="menu" && screen.startsWith("menu"))) 0xFF32916F.toInt() else 0xFF667E87.toInt())
                background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                backgroundTintList=null
                setOnClickListener {
                    when(target) {
                        "report" -> report()
                        else -> openScreen(target)
                    }
                }
            },LinearLayout.LayoutParams(0,dp(55),1f))
        }
        frame.addView(navigation,LinearLayout.LayoutParams(-1,dp(62)))
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
        text=displayLegacyDates(s); textSize=16f; setTextColor(0xFF37474F.toInt()); setPadding(dp(2),dp(8),dp(2),dp(8))
    }) }
    private fun makeButton(s:String, f:()->Unit):Button {
        val accent=s.contains("ZAKOŃCZ") || s.startsWith("Usuń") || ((s.startsWith("🚗") || s=="DOTARŁEM") && active(data())?.optString("kind")=="Przejazd")
        val primary=s=="Zapisz" || s.contains("ROZPOCZNIJ") || s.startsWith("Wyślij zdjęcia")
        val fill=when { accent -> 0xFFFF5C5C.toInt(); primary -> 0xFF37474F.toInt(); else -> 0xFFECEFF1.toInt() }
        return Button(this).apply {
            text=s; textSize=14f; isAllCaps=false; minHeight=dp(28); minimumHeight=dp(28)
            setTextColor(if(accent || primary) Color.WHITE else 0xFF37474F.toInt())
            background=background(fill); backgroundTintList=null; elevation=0f
            setPadding(dp(12),dp(6),dp(12),dp(6)); setOnClickListener { f() }
        }
    }
    private fun button(s:String, f:()->Unit) {
        root.addView(makeButton(s,f),LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
    }
    private fun tiles(items:List<Pair<String,()->Unit>>) {
        items.chunked(2).forEach { pair ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i,item -> row.addView(makeButton(item.first,item.second).apply { minHeight=dp(45); minimumHeight=dp(45) },LinearLayout.LayoutParams(0,-2,1f).apply { if(i==0) rightMargin=dp(8) }) }
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
            screen=="sectionSettings" -> settings()
            screen=="gps" -> sectionSettings("Obiekty")
            screen=="gpsPlaceKinds" -> gpsScreen()
            screen=="gpsCategories" -> report()
            screen=="gpsDiagnostics" -> gpsScreen()
            screen=="gpsPay" -> settings()
            screen.startsWith("menuFeature:") -> menu()
            screen in listOf("drive","logs","about","widgetSettings","wallet","language") -> settings()
            screen == "payment" || screen=="expenses" -> wallet()
            screen=="car" || screen.startsWith("materialGroup:") -> openScreen("list:materials:")
            screen=="queue" -> openScreen("list:links:")
            screen == "form" -> {
                AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setMessage("Zachować szkic i wrócić?")
                    .setNegativeButton("Zostań",null).setPositiveButton("Wróć") { _, _ ->
                        prefs.edit().putString("draft_$formKind",draft.toString()).apply()
                        openScreen(formReturn)
                    }.show()
            }
            screen.startsWith("detail:") -> openScreen(detailParent)
            screen.startsWith("shift:") -> report()
            screen.startsWith("object:") -> openScreen("objects")
            screen.startsWith("list:") && screen.split(":").getOrNull(2)?.isNotBlank() == true ->
                openScreen("object:" + screen.split(":")[2])
            screen == "notesActive" || screen == "notesLog" -> openScreen("notes")
            else -> home()
        }
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
        val slate=0xFF304953.toInt()
        val muted=0xFF6D7C84.toInt()
        val coral=0xFFFF5C5C.toInt()
        val surface=0xFFF4F7F8.toInt()
        val privateMode=a?.optString("kind")=="Prywatnie"
        root.setBackgroundColor(Color.WHITE)
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
            root.addView(text(title,14f,slate,true),LinearLayout.LayoutParams(-1,-2).apply {
                topMargin=dp(20); bottomMargin=dp(10)
            })
        }
        fun action(label:String,primary:Boolean=false,onClick:()->Unit):Button =
            makeButton(label,onClick).apply {
                textSize=13f; minHeight=dp(48); minimumHeight=dp(48)
                setTextColor(if(primary) Color.WHITE else slate)
                background=rounded(if(primary) slate else surface,14)
                setPadding(dp(8),dp(10),dp(8),dp(10))
                isAllCaps=false
            }
        fun actionRow(first:Pair<String,()->Unit>,second:Pair<String,()->Unit>) {
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            row.addView(action(first.first,onClick=first.second),LinearLayout.LayoutParams(0,dp(52),1f).apply { rightMargin=dp(5) })
            row.addView(action(second.first,onClick=second.second),LinearLayout.LayoutParams(0,dp(52),1f).apply { leftMargin=dp(5) })
            root.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        }
        val hero=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(19),dp(20),dp(20))
            background=rounded(slate,22)
            elevation=dp(2).toFloat()
        }
        val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
        header.addView(text("TWÓJ DZIEŃ",12f,0xFFD4E0E4.toInt(),true),LinearLayout.LayoutParams(0,-2,1f))
        val status=when { privateMode -> "● PRYWATNIE";a!=null -> "● ONLINE";else -> "● OFFLINE" }
        val statusColor=when { privateMode -> 0xFFFFC88F.toInt();a!=null -> 0xFF6EE0AD.toInt();else -> 0xFFD3DBDF.toInt() }
        header.addView(text(status,12f,statusColor,true))
        hero.addView(header)
        hero.addView(text(shown?.let {
            duration(workedMillis(it))
        } ?: "00:00",44f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14) })
        hero.addView(text(
            when {
                privateMode -> "Czas prywatny"
                a==null -> "Gotowy do rozpoczęcia pracy"
                a.optString("kind")=="Przejazd" -> "W drodze"
                a.optString("kind")=="Zakupy" -> "Zakupy"
                a.optString("kind")=="Biuro" -> "Biuro · "+a.optString("object")
                else -> a.optString("object").ifBlank { "Bez przypisanego obiektu" }
            },16f,Color.WHITE,true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(5) })
        hero.addView(text(
            "Rozpoczęcie: "+(shown?.let { fmt.format(Date(it.optLong("start"))) } ?: "—"),
            12f,0xFFD5E1E5.toInt()),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(7) })
        if(a==null && recent!=null) hero.addView(text(
            "Zakończenie: "+fmt.format(Date(recent.optLong("end"))),
            12f,0xFFD5E1E5.toInt()),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(2) })
        if(a?.optString("kind")=="Przejazd") hero.addView(text(
            "Czas przejazdu: "+duration(System.currentTimeMillis()-a.optLong("stageStart")),
            13f,0xFFFFC5BD.toInt(),true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(9) })
        root.addView(hero,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(14) })

        if(a==null) {
            root.addView(action("▶  ROZPOCZNIJ PRACĘ",true) { confirmPin { startShift() } },
                LinearLayout.LayoutParams(-1,dp(52)))
        } else {
            root.addView(action("■  ZAKOŃCZ PRACĘ",true) { confirmPin { stopShift() } }.apply {
                background=rounded(coral,14)
            },LinearLayout.LayoutParams(-1,dp(52)).apply { bottomMargin=dp(10) })
            pendingGpsStageEventId(d,System.currentTimeMillis())?.let { eventId ->
                root.addView(action("📍  GPS · POTWIERDŹ ZMIANĘ ETAPU") {
                    reviewGpsStageProposal(eventId)
                },LinearLayout.LayoutParams(-1,dp(48)).apply { bottomMargin=dp(10) })
            }
            root.addView(action(if(privateMode) "▶  WZNÓW PRACĘ" else "⏸  PRYWATNIE") {
                togglePrivateTime()
            },LinearLayout.LayoutParams(-1,dp(48)).apply { bottomMargin=dp(10) })
            if(!privateMode) {
                actionRow("↔  ZMIEŃ OBIEKT" to { confirmAction("Zmienić obiekt?") { switchObject() } },
                    (if(a.optString("kind")=="Przejazd") "✓  DOTARŁEM" else "🚗  PRZEJAZD") to { toggleTravel() })
                actionRow((if(a.optString("kind")=="Zakupy") "✓  ZAKOŃCZ ZAKUPY" else "🛍  ZAKUPY") to { toggleShopping() },
                    "↗  TRASA DO OBIEKTU" to { chooseObject(false) { obj ->
                        if(obj!=null) openLink("https://www.google.com/maps/dir/?api=1&destination="+
                            URLEncoder.encode(obj.optString("city")+" "+obj.optString("Adres"),"UTF-8"))
                    } })
                if(a.optString("kind")=="Zakupy") {
                    root.addView(action("Dane zakupów / paragony") { shoppingRecord(a.optString("shoppingRecordId")) },
                        LinearLayout.LayoutParams(-1,dp(48)))
                }
            }
        }
        // This is an optional manual action, never an automatic shift transition.
        // Only a recent confirmed geofence matching the latest fix enables it.
        val arrivalReady=gpsHomeArrivalReady(a)
        root.addView(action(if(arrivalReady) "📍  GPS · WYBIERZ OBIEKT" else
            "📍  GPS · OCZEKIWANIE NA OBIEKT") {
            if(!gpsHomeArrivalReady(active(data()))) {
                toast("Czekam na potwierdzoną lokalizację GPS przy obiekcie")
                home()
            } else if(active(data())?.optString("kind")=="Przejazd") {
                chooseArrival()
            } else {
                switchObject()
            }
        }.apply {
            isEnabled=arrivalReady
            alpha=if(arrivalReady) 1f else 0.55f
            background=rounded(if(arrivalReady) 0xFF257E61.toInt() else surface,14)
            setTextColor(if(arrivalReady) Color.WHITE else muted)
        },LinearLayout.LayoutParams(-1,dp(52)).apply { bottomMargin=dp(10) })
        section("SZYBKIE DZIAŁANIA")
        actionRow("📷  ZDJĘCIA" to { openScreen("list:links:") },
            "📦  MATERIAŁY" to { openScreen("list:materials:") })
        actionRow("📝  NOTATKI" to { openScreen("notes") },
            "🏢  OBIEKTY" to { openScreen("objects") })
        section("PRZEGLĄD")
        actionRow("◷  MÓJ CZAS" to { report() }, "▥  GRAFIKI" to { charts() })
        root.addView(action("☷  WIĘCEJ · MENU P1–P4") { menu() },LinearLayout.LayoutParams(-1,dp(50)))
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
        root.setBackgroundColor(0xFFF4F7F8.toInt())
        nav()
        val banner=TextView(this).apply {
            text="WORKMETER · SOLO\nWSZYSTKIE FUNKCJE · P1–P4"
            textSize=13f
            setTextColor(0xFF2E725D.toInt())
            setTypeface(null,android.graphics.Typeface.BOLD)
            setPadding(dp(14),dp(12),dp(14),dp(12))
            background=GradientDrawable().apply {
                setColor(0xFFE1F2EB.toInt())
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
                    setTextColor(0xFF344953.toInt())
                    setPadding(dp(14),dp(10),dp(12),dp(10))
                    background=GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius=dp(13).toFloat()
                        setStroke(dp(1),0xFFE2E9EC.toInt())
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
                                    AlertDialog.Builder(this,R.style.WorkMeterDialogTheme)
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
            AlertDialog.Builder(this,R.style.WorkMeterDialogTheme)
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
        root.setBackgroundColor(0xFFF4F7F8.toInt())
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
        button("Wygląd widżetu") { widgetSettings() }
        button("Język aplikacji") { language() }
        label("GPS i lokalizacja")
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
        label("Informacje")
        button("O aplikacji") { about() }
    }
    private fun sectionSettings(section:String) {
        currentSection=section
        base("Ustawienia · $section"); screen="sectionSettings"; nav()
        root.setBackgroundColor(0xFFF4F7F8.toInt())
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
        label("Przezroczystość tła widżetu")
        val value=TextView(this).apply { setTextColor(0xFF37474F.toInt()); text=prefs.getInt("widgetTransparency",0).toString()+"%" }
        root.addView(value)
        val previewFrame=FrameLayout(this).apply { setBackgroundColor(0xFF90A4AE.toInt()); setPadding(dp(8),dp(8),dp(8),dp(8)) }
        val preview=GateWidget.render(this).apply(this,previewFrame)
        val slider=SeekBar(this).apply { max=100; progress=prefs.getInt("widgetTransparency",0); contentDescription="Przezroczystość tła widżetu" }
        slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean) {
                value.text=progress.toString()+"%"; prefs.edit().putInt("widgetTransparency",progress).apply()
                preview.findViewById<ImageView>(R.id.wbackground).imageAlpha=255*(100-progress)/100
            }
            override fun onStartTrackingTouch(bar:SeekBar?) {}
            override fun onStopTrackingTouch(bar:SeekBar?) { GateWidget.refresh(this@MainActivity) }
        })
        root.addView(slider,LinearLayout.LayoutParams(-1,dp(44)))
        label("0% — pełne tło. 100% — przezroczyste tło. Tekst i przyciski pozostają widoczne.")
        previewFrame.addView(preview,FrameLayout.LayoutParams(-1,-2)); root.addView(previewFrame)
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
        label("Naliczone: "+money(summary.earned))
        label("Otrzymano — gotówka: "+money(summary.cash))
        label("Otrzymano — karta: "+money(summary.card))
        if(summary.overlapping==0) label((if(summary.due>=0) "Do wypłaty: " else "Nadpłata: ")+money(kotlin.math.abs(summary.due)))
        else label("Saldo okresu niepełne: "+summary.overlapping+" wypłat obejmuje szerszy okres. Poszerz daty lub popraw przypisanie wypłaty.")
        val global=WalletRules.earned(shifts)+config.optLong("openingCents")-payments.sumOf { it.cents }
        label((if(global>=0) "Łącznie do wypłaty (cała historia): " else "Łączna nadpłata (cała historia): ")+money(kotlin.math.abs(global)))
        label("Naliczanie obejmuje zakończone zmiany, także przejazdy i zakupy. Otwarty dzień zostanie rozliczony po zakończeniu.")
        val expenses=rows(d,"expenses")
        val expensesDue=expenses.sumOf { (it.optLong("cents")-it.optLong("reimbursedCents")).coerceAtLeast(0) }
        label("Zakupy firmowe do zwrotu (cała historia): "+money(expensesDue))
        label((if(global+expensesDue>=0) "Razem do rozliczenia (wynagrodzenie i zakupy): " else "Łączna nadpłata (wynagrodzenie i zakupy): ")+money(kotlin.math.abs(global+expensesDue)))
        button("Zakupy za własne pieniądze / paragony") { expenseList() }
        button("+ Dodaj wypłatę") { walletDraft=JSONObject().put("id",UUID.randomUUID().toString()).put("date",LocalDate.now().toString()).put("periodFrom",from.toString()).put("periodTo",to.toString()).put("method","cash"); paymentForm() }
        filters("walletRates",caption="Stawka i saldo początkowe") {
            button("Domyślna stawka: "+defaultRate().toPlainString()+" zł/h") { rateDialog(false) }
            button("Zmień stawkę zmian w tym okresie") { rateDialog(true) }
            button("Saldo początkowe: "+money(config.optLong("openingCents"))) { openingDialog() }
        }
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
    private fun expenseList() {
        if(!walletUnlocked) { settings(); confirmPin { walletUnlocked=true; expenseList() }; return }
        base("Zakupy do zwrotu"); screen="expenses"; nav()
        label("Zakupy opłacone z własnych pieniędzy. Zwroty są rozliczane oddzielnie od wynagrodzenia.")
        val list=rows(data(),"expenses").sortedByDescending { recordDate(it) }
        label("Do zwrotu: "+money(list.sumOf { (it.optLong("cents")-it.optLong("reimbursedCents")).coerceAtLeast(0) }))
        button("+ Dodaj zakup za własne pieniądze") { newForm("expenses") }
        for(item in list) button(dateText(recordDate(item))+" · "+money(item.optLong("cents"))+" · "+if(item.optLong("reimbursedCents")>=item.optLong("cents")) "Zwrócono" else "Do zwrotu") {
            recordDetails("expenses",item.optString("id"),"expenses")
        }
    }
    private fun walletInput(caption:String,key:String,numeric:Boolean=false) {
        label(caption)
        root.addView(EditText(ContextThemeWrapper(this,R.style.WorkMeterDialogTheme)).apply {
            hint=caption; setText(walletDraft.optString(key)); setTextColor(0xFF37474F.toInt()); setHintTextColor(0xFF78909C.toInt())
            background=background(0xFFECEFF1.toInt()); setPadding(dp(10),dp(6),dp(10),dp(6))
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
            AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Sposób wypłaty").setItems(arrayOf("Gotówka","Karta")) { _,index -> walletDraft.put("method",if(index==0) "cash" else "card"); paymentForm() }.show()
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
            confirmPin { AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setMessage("Usunąć tę wypłatę z rozliczenia?").setNegativeButton("Anuluj",null).setPositiveButton("Usuń") { _, _ ->
                val d=data(); val next=JSONArray(); rows(d,"payments").filter { it.optString("id")!=id }.forEach { next.put(it) }; d.put("payments",next); save(d,"PAYMENT_DELETE",id); wallet()
            }.show() }
        }
    }
    private fun rateDialog(existing:Boolean) {
        val input=EditText(ContextThemeWrapper(this,R.style.WorkMeterDialogTheme)).apply { hint="Stawka zł/h"; setText(defaultRate().toPlainString()); inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val dialog=AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle(if(existing) "Stawka za wybrany okres" else "Stawka dla nowych zmian")
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
        val field=EditText(ContextThemeWrapper(this,R.style.WorkMeterDialogTheme)).apply { hint="Saldo początkowe zł"; setText(BigDecimal.valueOf(walletConfig(data()).optLong("openingCents"),2).toPlainString()); inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val dialog=AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Saldo sprzed zapisanej historii").setMessage("Dodatnia kwota: zaległa wypłata. Ujemna: wcześniejsza nadpłata. Uwzględniana tylko w saldzie całej historii.").setView(field).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",null).create()
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
                AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Rodzaj przejazdu")
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
    private fun report() {
        base("Mój czas"); screen="report"; nav()
        button("GENERUJ RAPORT SMS") { weeklySms("report") }
        button("GPS · zasady rozliczania") { gpsPaySettings() }
        button("GPS · kategorie przejazdów") { gpsTripCategories() }
        val currentShift=active(data()) ?: rows(data(),"shifts").lastOrNull()
        if(currentShift!=null) {
            val end=if(currentShift.has("end")) currentShift.optLong("end") else System.currentTimeMillis()
            val estimate=gpsEstimate(currentShift,end)
            label("GPS · szacunkowy czas przejazdów: "+duration(estimate.actualTravelMs)+
                " · zatwierdzone do wypłaty: "+duration(estimate.payableTravelMs))
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

        }

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
        }
        val shifts=rows(data(),"shifts")
        val entries=shifts.flatMap { shift -> HistoryRules.slices(shift.optLong("start"),shift.optLong("end"),ZoneId.systemDefault()).map { shift to it } }
            .filter { DomainRules.inRange(it.second.day,from,to) }
        label("Łączny czas: "+duration(entries.sumOf { workedMillis(it.first,it.second.start,it.second.end) }))
        if(entries.isEmpty()) label("Brak zmian w wybranym okresie")
        for((day,items) in entries.groupBy { it.second.day }.toSortedMap(reverseOrder())) {
            val parent=root
            val card=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(6))
                background=background(0xFFF5F7F8.toInt()).apply { setStroke(dp(1),0xFFCFD8DC.toInt()) }
            }
            parent.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(12) })
            root=card
            try {
                label(dateText(day)+" · "+LocalDate.parse(day).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL,Locale.forLanguageTag("pl")))
                label("Czas dnia: "+duration(items.sumOf { workedMillis(it.first,it.second.start,it.second.end) }))
                for((shift,slice) in items.sortedByDescending { it.second.start }) {
                    button(fmt.format(Date(slice.start))+" → "+fmt.format(Date(slice.end))+"  ("+duration(workedMillis(shift,slice.start,slice.end))+")") { shiftDetails(shift.optString("id")) }
                    button("Edytuj czas") { confirmAction("Edytować godziny tej zmiany?") { editShift(shift.optString("id")) } }
                    button("Usuń zmianę") { deleteShift(shift.optString("id")) }
                }
            } finally { root=parent }
        }
    }
    private fun shiftDetails(id:String) {
        val shift=rows(data(),"shifts").find { it.optString("id")==id } ?: run { report(); return }
        base("Szczegóły dnia"); screen="shift:"+id; nav()
        val start=shift.optLong("start"); val end=shift.optLong("end")
        label("Rozpoczęcie pracy: "+fmt.format(Date(start)))
        val stages=rows(shift,"stages").sortedBy { it.optLong("start") }
        if(stages.isEmpty()) label("Brak zapisanej historii etapów dla tej zmiany. Nie można odtworzyć obiektów ani przejazdów.")
        var cursor=start
        for(stage in stages) {
            val from=maxOf(start,stage.optLong("start")); val to=minOf(end,stage.optLong("end"))
            if(to<=from) continue
            if(from>cursor) label("Brak przypisanego etapu: "+fmt.format(Date(cursor))+" → "+fmt.format(Date(from))+" · "+duration(from-cursor))
            val kind=stage.optString("kind","Obiekt")
            val placeName=stage.optString("object").ifBlank {
                objectById(stage.optString("objectId"))?.let(::objectName) ?: "Miejsce bez nazwy"
            }
            val name=when(kind) { "Obiekt" -> placeName; "Biuro" -> "Biuro · "+placeName; else -> kind }
            if(kind=="Przejazd") label("Z: "+stage.optString("fromObject","—")+"\nDo: "+stage.optString("toObject","—"))
            if(kind=="Zakupy" && stage.optString("shoppingRecordId").isNotBlank()) button("Zakupy / paragony") { recordDetails("notes",stage.optString("shoppingRecordId"),screen) }
            label(name+"\n"+fmt.format(Date(from))+" → "+fmt.format(Date(to))+"\nCzas: "+duration(to-from))
            cursor=maxOf(cursor,to)
        }
        if(stages.isNotEmpty() && cursor<end) label("Brak przypisanego etapu: "+fmt.format(Date(cursor))+" → "+fmt.format(Date(end))+" · "+duration(end-cursor))
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

    private fun deleteShift(id:String) {
        confirmAction("Usunąć zmianę z raportu i zestawienia godzin? Materiały, zdjęcia i notatki pozostaną.") {
            val d=data(); val remaining=JSONArray()
            rows(d,"shifts").filter { it.optString("id")!=id }.forEach { remaining.put(it) }
            d.put("shifts",remaining); save(d,"DELETE_SHIFT",id); report()
        }
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
        val dialog=AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Edytuj zmianę").setView(layout)
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
        if(requestCode==203) {
            if(dataIntent!=null) {
                try { authorized(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(dataIntent)) }
                catch(e:Exception) { driveFailure(e) }
            } else {
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
                val content=contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }; val imported=JSONObject(content); validateImport(imported)
                AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Przywrócić kopię?").setMessage("Obecne dane zostaną zastąpione.").setNegativeButton("Anuluj",null).setPositiveButton("Importuj") { _, _ ->
                    prefs.edit().putString("preImportBackup",data().toString()).putString("data",imported.toString()).putInt("widgetTransparency",imported.optJSONObject("wtSettings")?.optInt("widgetTransparency",prefs.getInt("widgetTransparency",0))?.coerceIn(0,100) ?: prefs.getInt("widgetTransparency",0)).apply(); migrate(); migrateWallet(); GateWidget.refresh(this); logs()
                }.show()
            }
        } catch (_:Exception) { toast("Błąd pliku JSON") }
    }

    private fun exportData():JSONObject = DriveBackupWorker.snapshot(this)
    private fun validateImport(d:JSONObject) {
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
        objectPicker(optional,false,selected)
    }
    private fun objectPicker(optional:Boolean, arrival:Boolean, selected:(JSONObject?)->Unit) {
        val savedObjects=rows(data(),"objects")
        val catalog=ObjectUiGateway(savedObjects.map(::objectSummary))
        val selectableIds=catalog.selectableObjects().map { it.id }.toSet()
        val options=savedObjects.filter { it.optString("id") in selectableIds }
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(8)) }
        val searchRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val query=EditText(ContextThemeWrapper(this,R.style.WorkMeterDialogTheme)).apply { hint="Szukaj miasta"; setSingleLine(); inputType=InputType.TYPE_CLASS_TEXT; imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH }
        searchRow.addView(query,LinearLayout.LayoutParams(0,dp(48),1f))
        val results=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val dialog=AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle(if(arrival) "Dokąd przyjechałeś?" else "Wybierz obiekt")
            .setView(layout).setNegativeButton(if(arrival) "Anuluj — przejazd trwa" else "Anuluj",null).create()
        fun renderResults() {
            results.removeAllViews()
            val matches=options.filter { ObjectSearch.matches(it.optString("city"),query.text.toString()) }.sortedWith { x,y -> ObjectSearch.compareTags(x.optString("TAG"),y.optString("TAG")) }
            for(obj in matches) results.addView(makeButton(objectName(obj)) {
                val current=objectById(obj.optString("id"))?.takeUnless { it.optBoolean("archived") }
                if(current==null) { toast("Obiekt nie jest już dostępny"); dialog.dismiss() }
                else { dialog.dismiss(); selected(current) }
            },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(6) })
            if(matches.isEmpty()) results.addView(TextView(this).apply { text="Brak obiektów dla tego miasta"; setTextColor(0xFF37474F.toInt()); setPadding(0,dp(12),0,dp(12)) })
        }
        searchRow.addView(makeButton("⌕") { renderResults(); (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(query.windowToken,0) }.apply { contentDescription="Szukaj"; textSize=24f; background=background(0xFF37474F.toInt()); setTextColor(Color.WHITE) },LinearLayout.LayoutParams(dp(48),dp(48)))
        query.setOnEditorActionListener { _,action,_ -> if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { renderResults(); true } else false }
        query.addTextChangedListener(object:TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {} ; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { renderResults() }; override fun afterTextChanged(s:Editable?) {} })
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
        renderResults(); dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
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
            target=="language" -> language()
            target=="charts" -> charts()
            target=="queue" -> uploadQueue()
            target=="car" -> carStock()
            target.startsWith("materialGroup:") -> { val p=target.split(":"); materialGroup(p[1],p.getOrElse(2){""}) }
            target=="about" -> about()
            target=="widgetSettings" -> widgetSettings()
            target=="wallet" -> wallet()
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
            target=="form" -> renderForm()
            target=="drive" -> driveSettings()
            else -> home()
        }
    }
    private fun objects() {
        base("Obiekty"); screen="objects"; nav()
        val all=rows(data(),"objects")
        val showArchived=prefs.getBoolean("archived_objects",false)
        button(if(showArchived) "Pokaż aktywne" else "Archiwum obiektów") {
            prefs.edit().putBoolean("archived_objects",!showArchived).apply(); objects()
        }
        button("+ Dodaj obiekt") { newForm("objects") }
        for(o in all.filter { it.optBoolean("archived")==showArchived }.sortedBy { objectName(it) }) {
            button(objectName(o)) { openScreen("object:"+o.optString("id")) }
        }
        if(all.isEmpty()) label("Dodaj pierwszy obiekt, aby przypisywać pracę, materiały i zdjęcia.")
    }
    private fun objectDetails(id:String) {
        val obj=objectById(id) ?: run { objects(); return }
        base(obj.optString("TAG")); screen="object:"+id; nav()
        val period=JSONObject()
        label(objectName(obj)); label(obj.optString("notes",obj.optString("Opis")))
        label("Typ miejsca: "+placeType(obj).label)
        button("Edytuj obiekt") { newForm("objects",id) }
        button("Otwórz w Google Maps") {
            val link=obj.optString("maps")
            openLink(if(link.isNotBlank()) link else "https://www.google.com/maps/search/?api=1&query="+URLEncoder.encode(obj.optString("city")+" "+obj.optString("Adres"),"UTF-8"))
        }
        label("Czas na obiekcie")
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
        label("Razem: "+duration(totals.values.sum()))
        for((day,ms) in totals.toSortedMap(reverseOrder())) label(dateText(day)+" · "+duration(ms))
        if(unassigned>0) label("Starsze zmiany bez etapów nie są przypisywane automatycznie do obiektu.")
        button("Materiały") { openScreen("list:materials:"+id) }
        button("Zdjęcia") { openScreen("list:links:"+id) }
        button("Notatki obiektu") { openScreen("list:notes:"+id) }
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
        label("Usunięcie z listy archiwizuje obiekt. Historia pracy i wpisy pozostają dostępne.")
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
        if(key=="links") { button("KOLEJKA WYSYŁANIA") { uploadQueue() }; button("WYŚLIJ TERAZ") { sendQueue() } }
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
        if(list.isEmpty()) label("Brak wpisów dla wybranych filtrów.")
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
                button(obj+"\n"+items.joinToString("\n") { materialLine(it) }) { materialGroup(group.first,group.second) }
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
            if(key=="links" && arr(r,"photos").length()>0) {
                val status=PhotoQueue.status(this,key,r)
                root.addView(makeButton(summary+"\n"+obj+"\n"+status.caption) { recordDetails(key,r.optString("id"),target) }.apply { background=background(status.color) },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
            } else button(summary+"\n"+obj) { recordDetails(key,r.optString("id"),target) }
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
                } else AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Jak otrzymałeś zwrot?")
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
    private fun showPhotos(r:JSONObject) {
        for(i in 0 until arr(r,"photos").length()) {
            val uri=arr(r,"photos").optString(i)
            val card=FrameLayout(this).apply {
                background=background(0xFFECEFF1.toInt()); clipToOutline=true; elevation=dp(2).toFloat()
            }
            val thumb=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; contentDescription="Zdjęcie "+(i+1); adjustViewBounds=true }
            try {
                val bitmap=PhotoPreview.decode(this,Uri.parse(uri))
                thumb.scaleType=ImageView.ScaleType.FIT_CENTER
                thumb.setImageBitmap(bitmap)
                val width=resources.displayMetrics.widthPixels-dp(32)
                card.addView(thumb,FrameLayout.LayoutParams(-1,width*9/16))
                thumb.setOnClickListener { openPhoto(uri) }
                root.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(16); topMargin=dp(4) })
            } catch(_:Exception) {
                val placeholder=ImageView(this).apply { setImageResource(android.R.drawable.ic_menu_gallery); contentDescription="Zdjęcie niedostępne"; setPadding(dp(32),dp(32),dp(32),dp(32)); setOnClickListener { toast("Oryginał zdjęcia nie jest dostępny na tym urządzeniu") } }
                card.addView(placeholder,FrameLayout.LayoutParams(-1,dp(120)))
                root.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(16) })
            }
        }
    }
    private fun newForm(key:String,id:String="",objectId:String="",sendAfterSave:Boolean=false) {
        quickPhotoUpload=sendAfterSave
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
        val e=EditText(ContextThemeWrapper(this,R.style.WorkMeterDialogTheme)).apply {
            setText(draft.optString(key)); textSize=17f; minHeight=dp(48)
            inputType=if(numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or (if(multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
            setSingleLine(!multiline); setPadding(dp(10),dp(8),dp(10),dp(8)); setTextColor(0xFF37474F.toInt()); setHintTextColor(0xFF78909C.toInt()); background=background(0xFFECEFF1.toInt())
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
    private fun captureObjectGps() {
        if(formKind!="objects") return
        val targetId=draft.optString("id")
        val manager=getSystemService(LOCATION_SERVICE) as LocationManager
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            toast("Zezwól aplikacji na lokalizację w ustawieniach Android")
            return
        }
        try {
            val provider=when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if(provider==null) { toast("Włącz lokalizację w telefonie");return }
            toast("Pobieranie aktualnej lokalizacji GPS…")
            manager.requestSingleUpdate(provider,object:LocationListener {
                override fun onLocationChanged(loc:Location) {
                    if(formKind!="objects" || draft.optString("id")!=targetId || screen!="form") return
                    val now=System.currentTimeMillis()
                    val capture=GpsObjectLocationRules.Capture(targetId,loc.latitude,loc.longitude,
                        loc.accuracy.toDouble(),loc.time)
                    if(!GpsObjectLocationRules.valid(capture,now)) {
                        toast("Pomiar GPS niedokładny lub nieaktualny. Spróbuj ponownie.")
                        return
                    }
                    draft.put("_gps_capture",JSONObject()
                        .put("objectId",targetId).put("lat",loc.latitude).put("lon",loc.longitude)
                        .put("accuracy",loc.accuracy.toDouble()).put("time",loc.time))
                    prefs.edit().putString("draft_objects",draft.toString()).apply()
                    toast("Lokalizacja pobrana. Naciśnij Zapisz, aby przypisać ją do obiektu.")
                    renderForm()
                }
            },Looper.getMainLooper())
        } catch(_:SecurityException) { toast("Brak uprawnienia do lokalizacji GPS") }
        catch(_:RuntimeException) { toast("Nie udało się pobrać lokalizacji GPS") }
    }
    private fun renderForm() {
        if(formKind=="expenses" && !walletUnlocked) { settings(); confirmPin { walletUnlocked=true; renderForm() }; return }
        base(when(formKind) { "objects" -> "Obiekt"; "expenses" -> "Zakup za własne pieniądze"; "materials" -> "Zużyty materiał"; "links" -> "Dodaj zdjęcia"; else -> "Notatka" })
        screen="form"; nav()
        if(formKind=="objects") {
            label("TAG: "+draft.optString("TAG").ifBlank { "automatycznie po zapisaniu miasta" })
            field("Miasto","city")
            if(draft.optString("TAG").isBlank()) field("Tag (opcjonalnie)","tagPrefix") else label("Tag (stały): "+draft.optString("TAG"))
            field("Adres","Adres"); field("Nazwa organizacji","organization")
            button("Typ miejsca: "+placeType(draft).label) {
                pickPlaceType { type -> draft.put("placeType",type.name); renderForm() }
            }
            field("Notatki / kontakt","notes",true); field("Link Google Maps (opcjonalnie)","maps")
            val savedGeo=prefs.getString("gps_object_"+draft.optString("id"),null)
            val pendingGeo=draft.optJSONObject("_gps_capture")
            label(when {
                pendingGeo!=null -> "📍 Lokalizacja GPS pobrana — zapisze się razem z obiektem."
                savedGeo!=null -> "📍 Lokalizacja GPS przypisana do tego obiektu."
                else -> "📍 Lokalizacja GPS: nie przypisano (opcjonalnie)."
            })
            button("📍 Zapisz lokalizację GPS") { captureObjectGps() }
        } else {
            button("Data: "+recordDate(draft).let { if(it.isBlank()) "Wybierz datę" else dateText(it) }) {
                pickReportDate(try { LocalDate.parse(recordDate(draft)) } catch (_:Exception) { LocalDate.now() }) {
                    draft.put("date",it.toString()); renderForm()
                }
            }
            if(formKind=="materials" && draft.optString("sourceId").isBlank()) button("Miejsce: "+if(draft.optString("storage")=="car") "W samochodzie" else "Na obiekcie") {
                AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Miejsce").setItems(arrayOf("Na obiekcie","W samochodzie")) { _,i -> draft.put("storage",if(i==1) "car" else "object"); renderForm() }.show()
            }
            button("Obiekt: "+(objectById(draft.optString("objectId"))?.let { objectName(it) } ?: if(formKind in listOf("notes","expenses")) "Bez obiektu" else "Wybierz")) {
                chooseObject(formKind in listOf("notes","expenses") || (formKind=="materials" && draft.optString("storage")=="car")) { draft.put("objectId",it?.optString("id") ?: ""); draft.remove("Obiekt"); renderForm() }
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
                    AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Jednostka").setItems(arrayOf("szt.","m")) { _,which ->
                        draft.put("unit",if(which==0) "szt." else "m"); renderForm()
                    }.show()
                }
            } else {
                if(formKind=="notes") {
                    if(draft.optBoolean("shopping")) field("Sklep (opcjonalnie)","store")
                    field("Treść (opcjonalnie, gdy dodajesz zdjęcie)","text",true)
                    root.addView(CheckBox(this).apply {
                        text="Zakupy / paragon"; setTextColor(0xFF37474F.toInt())
                        buttonTintList=android.content.res.ColorStateList.valueOf(0xFFFF5C5C.toInt())
                        isChecked=draft.optBoolean("shopping")
                        setOnCheckedChangeListener { _,checked -> draft.put("shopping",checked) }
                    })
                    root.addView(CheckBox(this).apply {
                        setTextColor(0xFF37474F.toInt()); buttonTintList=android.content.res.ColorStateList.valueOf(0xFFFF5C5C.toInt())
                        text="Aktualna"; isChecked=draft.optBoolean("aktualne")
                        setOnCheckedChangeListener { _,checked -> draft.put("aktualne",checked); if(checked) draft.put("done",false) }
                    })
                }
                button("Dodaj zdjęcia z galerii") { pickPhotos() }; button("Zrób zdjęcie") { camera() }
                if(formKind=="links") field("Link Google Disk (opcjonalnie)","Link Google Drive")
            }
            showPhotos(draft)
            if(arr(draft,"photos").length()>0) button("Usuń zdjęcie ze szkicu") {
                val photos=arr(draft,"photos")
                AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Wybierz zdjęcie").setItems((0 until photos.length()).map { "Zdjęcie "+(it+1) }.toTypedArray()) { _,index ->
                    photos.remove(index); renderForm()
                }.show()
            }
        }
        if(formKind=="materials" && rows(data(),"materials").none { it.optString("id")==draft.optString("id") }) button("+ DODAJ MATERIAŁ") { addBatchLine() }
        button(if(quickPhotoUpload) "Zapisz i wyślij zdjęcia" else "Zapisz") { saveForm() }
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
                .put("lat",geoCapture.latitude).put("lon",geoCapture.longitude)
                .put("radius",GpsObjectLocationRules.RADIUS_M).toString()).commit()
        }
        PhotoQueue.markPending(this,formKind,draft)
        val send=quickPhotoUpload && formKind=="links" && arr(draft,"photos").length()>0
        val reference=formKind+":"+draft.optString("id")
        quickPhotoUpload=false
        prefs.edit().remove("draft_"+formKind).apply(); openScreen(formReturn)
        if(send) { pendingUpload=reference; authorizeDrive() }
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
            pendingUpload=""; toast("Nie udzielono dostępu do Google Disk"); return
        }
        driveToken=token
        val upload=pendingUpload; pendingUpload=""
        io.execute {
            try {
                val about=driveJson("GET","https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)",token)
                runOnUiThread {
                    prefs.edit().remove("driveError").putBoolean("driveConnected",true).putString("driveEmail",about.optJSONObject("user")?.optString("emailAddress") ?: "Google Drive").apply()
                    DriveBackupWorker.schedule(this)
                    if(upload=="queue") { PhotoQueue.enqueueAll(this); uploadQueue() } else if(upload.isBlank()) driveSettings() else if(upload=="backup") { DriveBackupWorker.manual(this); toast("Kopia w kolejce: WorkMeter / Backup"); logs() } else if(upload.startsWith("share:")) shareFolder(upload.substringAfter(":"),token) else uploadPhotos(upload,token)
                }
            } catch (_:Exception) { runOnUiThread { prefs.edit().putString("driveError","Google Disk nie odpowiada. Sprawdź, czy Drive API jest włączone i konto ma dostęp.").apply(); toast("Nie udało się połączyć z Google Disk"); } }
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
        val shifts=(rows(data(),"shifts")+listOfNotNull(active(data()))).map { sh ->
            val end=sh.optLong("end",System.currentTimeMillis())
            val stages=rows(sh,"stages").toMutableList()
            if(!sh.has("end")) stages.add(JSONObject().put("start",sh.optLong("stageStart",sh.optLong("start"))).put("end",end).put("kind",sh.optString("kind","Obiekt")).put("objectId",sh.optString("objectId")))
            TimeAnalytics.Shift(sh.optLong("start"),end,stages.map { st -> TimeAnalytics.Stage(st.optLong("start"),st.optLong("end"),when(st.optString("kind")) { PrivateTimeRules.KIND -> TimeAnalytics.PERSONAL; "Biuro" -> TimeAnalytics.OFFICE; "Obiekt" -> if(st.optString("objectId").isNotBlank() || st.optString("object").isNotBlank()) 0 else 3; "Zakupy" -> 1; "Przejazd" -> 2; else -> 3 }) })
        }
        val result=TimeAnalytics.calculate(shifts,range.first,range.second,ZoneId.systemDefault())
        label("Statystyka czasu pracy")
        root.addView(TimeChart(this,result,false),LinearLayout.LayoutParams(-1,dp(230)))
        label("Suma czasu w każdej godzinie doby, ze wszystkich dni wybranego okresu.")
        filters("chartHours",caption="Szczegóły godzinowe") {
            result.hours.forEachIndexed { hour,values -> if(values.sum()>0) label("%02d:00".format(hour)+" · "+values.mapIndexed { i,n -> TimeChart.names[i]+": "+duration(n) }.joinToString(" · ")) }
        }
        label("Podział czasu pracy")
        root.addView(TimeChart(this,result,true),LinearLayout.LayoutParams(-1,dp(240)))
        val sum=result.workMillis
        result.totals.forEachIndexed { i,n ->
            root.addView(TextView(this).apply { text="● "+TimeChart.names[i]+" — "+duration(n)+" · "+"%.1f%%".format(Locale("pl"),result.sharePercent(i));textSize=16f;setTextColor(0xFF37474F.toInt());setPadding(0,dp(7),0,dp(7)) })
        }
        if(sum==0L) label("Brak czasu pracy w wybranym okresie.")
        if(result.personalMillis>0) label("Czas prywatny: "+duration(result.personalMillis)+" · poza czasem pracy")
        filters("chartComparison",caption="Porównanie okresów") {
            val previous=TimeAnalytics.comparisonRange(period,range)
            val comparison=TimeAnalytics.calculate(shifts,previous.first,previous.second,ZoneId.systemDefault())
            label("Poprzedni okres: "+dateText(previous.first.toString())+" — "+dateText(previous.second.toString()))
            label("Czas pracy: "+duration(sum)+" · wcześniej: "+duration(comparison.workMillis))
            result.totals.forEachIndexed { category, millis ->
                label(TimeChart.names[category]+": "+duration(millis)+" · wcześniej: "+
                    duration(comparison.totals[category]))
            }
        }
    }
    private fun toggleShopping() {
        if(active(data())?.optString("kind")==PrivateTimeRules.KIND) { toast("Najpierw wznów pracę"); return }
        val a=active(data()) ?: run { toast("Najpierw rozpocznij pracę");return }
        if(a.optString("kind")=="Zakupy") {
            confirmAction("Zakończyć zakupy? Wybierz obiekt lub rozpocznij przejazd.") {
                AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Co dalej?").setItems(arrayOf("Na obiekt","Przejazd","Bez przypisania")) { _,i ->
                    when(i) { 0 -> switchObject();1 -> beginStage("Przejazd");else -> beginStage("Nieprzypisane") }
                }.setNegativeButton("Anuluj",null).show()
            }
        } else startShopping()
    }
    private fun startShopping() {
        if(active(data())?.optString("kind")==PrivateTimeRules.KIND) { toast("Najpierw wznów pracę"); return }
        val d=data();val a=active(d) ?: return
        val now=System.currentTimeMillis();val id=UUID.randomUUID().toString()
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
        button("WYŚLIJ TERAZ") { sendQueue() }
        button("PONÓW WYSYŁANIE") { sendQueue() }
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
        if(prefs.getBoolean("driveConnected",false)) { PhotoQueue.enqueueAll(this);uploadQueue() }
        else { pendingUpload="queue";authorizeDrive() }
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
        val input=AutoCompleteTextView(ContextThemeWrapper(this,R.style.WorkMeterDialogTheme)).apply {
            setText(draft.optString("name"));threshold=1;textSize=17f;setSingleLine();setPadding(dp(10),dp(8),dp(10),dp(8));background=background(0xFFECEFF1.toInt())
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
                AlertDialog.Builder(this,R.style.WorkMeterDialogTheme).setTitle("Poprawienia / przeniesienia").setItems(rows.map { dateText(recordDate(it))+" · "+materialLine(it) }.toTypedArray()) { _,i -> recordDetails("materials",rows[i].optString("id"),"car") }.setNegativeButton("Zamknij",null).show()
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
