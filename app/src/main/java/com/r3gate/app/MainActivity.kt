package com.r3gate.app

import android.app.*
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
        migrateWallet()
        DriveBackupWorker.schedule(this)
        expandedFilters.addAll(savedInstanceState?.getStringArrayList("expandedFilters") ?: emptyList())
        walletDraft = JSONObject(savedInstanceState?.getString("walletDraft") ?: "{}")
        quickPhotoUpload=savedInstanceState?.getBoolean("quickPhotoUpload") ?: false
        cameraPath = savedInstanceState?.getString("cameraPath")
        pendingUpload = savedInstanceState?.getString("pendingUpload") ?: ""
        if (savedInstanceState != null && prefs.contains("pin") && intent?.getStringExtra("widget_action")==null) {
            smsParent = savedInstanceState.getString("smsParent") ?: "settings"
            smsFrom = savedInstanceState.getString("smsFrom")?.let { LocalDate.parse(it) }
            smsTo = savedInstanceState.getString("smsTo")?.let { LocalDate.parse(it) }
            detailParent = savedInstanceState.getString("detailParent") ?: "home"
            formKind = savedInstanceState.getString("formKind") ?: ""
            formReturn = savedInstanceState.getString("formReturn") ?: "home"
            draft = JSONObject(savedInstanceState.getString("draft") ?: "{}")
            openScreen(savedInstanceState.getString("screen") ?: "home")
            return
        }
        if (prefs.getString("pin", null) == null) setupPin() else handleLaunch()
    }
    override fun onResume() { super.onResume(); prefs.registerOnSharedPreferenceChangeListener(uploadChanges); ticker.removeCallbacks(tick); ticker.postDelayed(tick, 60000) }
    override fun onPause() { prefs.unregisterOnSharedPreferenceChangeListener(uploadChanges); ticker.removeCallbacks(refreshUploads); ticker.removeCallbacks(tick); super.onPause() }
    override fun onStop() { walletUnlocked=false; super.onStop() }
    override fun onRestart() { super.onRestart(); if(screen=="wallet" || screen=="payment" || screen=="expenses" || screen.startsWith("detail:expenses:")) settings() else if(screen!="form") openScreen(screen) }
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
        home()
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
    private fun data(): JSONObject = try { JSONObject(prefs.getString("data","{}")!!) } catch (_: Exception) { JSONObject() }
    private fun arr(d:JSONObject,key:String):JSONArray = d.optJSONArray(key) ?: JSONArray().also { d.put(key,it) }
    private fun save(d:JSONObject, kind:String, detail:String) {
        val log=arr(d,"logs"); log.put(JSONObject().put("at",fmt.format(Date())).put("type",kind).put("detail",detail)); d.put("logs",log)
        prefs.edit().putString("data",d.toString()).apply(); GateWidget.refresh(this)
    }
    private fun active(d:JSONObject):JSONObject? = d.optJSONObject("active")
    private fun startShift() {
        if (active(data()) != null) { toast("Dzień pracy już rozpoczęty"); return }
        chooseObject(false) { obj ->
            startOnObject(obj ?: return@chooseObject)
        }
    }
    private fun startOnObject(obj: JSONObject) {
        val d=data(); if (active(d)!=null) { toast("Dzień pracy już rozpoczęty"); home(); return }
        val t=System.currentTimeMillis(); d.put("active",JSONObject().put("id",UUID.randomUUID().toString()).put("start",t).put("object",objectName(obj)).put("objectId",obj.optString("id")).put("stageStart",t).put("stages",JSONArray()).put("rate",defaultRate().toPlainString())); save(d,"START",fmt.format(Date(t))); home()
    }
    private fun stopShift() {
        val d=data(); val a=active(d); if(a==null) { toast("Brak rozpoczętej pracy"); home(); return }
        val end=System.currentTimeMillis(); closeStage(a,end); a.put("end",end); arr(d,"shifts").put(a); d.remove("active"); save(d,"STOP",fmt.format(Date(end))); PhotoQueue.enqueueAll(this); home()
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
        if (active(data()) == null) { toast("Najpierw rozpocznij pracę"); return }
        chooseObject(false) { obj ->
            if (obj != null) {
                val d = data(); val a = active(d) ?: return@chooseObject
                val now = System.currentTimeMillis()
                if(a.optString("kind")=="Przejazd") a.optJSONObject("stageMeta")?.put("toObject",objectName(obj))?.put("toObjectId",obj.optString("id"))
                closeStage(a,now)
                a.remove("stageMeta")
                a.put("stageStart",now).put("kind","Obiekt")
                    .put("object",objectName(obj)).put("objectId",obj.optString("id"))
                save(d,"STAGE",objectName(obj)); home()
            }
        }
    }
    private fun toggleTravel() {
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
                current.put("stageStart",now).put("kind","Obiekt").put("object",objectName(obj)).put("objectId",obj.optString("id"))
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
            sum += end - start
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
        out.putStringArrayList("expandedFilters",ArrayList(expandedFilters))
        out.putString("draft",draft.toString()); out.putString("cameraPath",cameraPath); out.putString("pendingUpload",pendingUpload)
        super.onSaveInstanceState(out)
    }
    private fun duration(ms:Long):String { val m=(ms.coerceAtLeast(0)/60000); return "%02d:%02d".format(m/60,m%60) }
    private fun home() {
        base("WorkMeter")
        screen="home"; homeVisible=true
        val d=data(); val a=active(d)
        val recent=rows(d,"shifts").lastOrNull(); val shown=a?:recent
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(12))
            background=background(0xFF37474F.toInt())
        }
        fun line(text:String,size:Float,color:Int) { card.addView(TextView(this).apply {
            this.text=text; textSize=size; setTextColor(color); setPadding(0,dp(4),0,dp(4))
        }) }
        line(if(a!=null) "● ONLINE" else "● OFFLINE",16f,if(a!=null) 0xFF42D77D.toInt() else 0xFFCFD8DC.toInt())
        line(shown?.let { duration((if(a!=null) System.currentTimeMillis() else it.optLong("end"))-it.optLong("start")) } ?: "00:00",32f,Color.WHITE)
        line("Rozpoczęcie: "+(shown?.let { fmt.format(Date(it.optLong("start"))) } ?: "—"),14f,0xFFDBEAF7.toInt())
        if(a==null) line("Zakończenie: "+(recent?.let { fmt.format(Date(it.optLong("end"))) } ?: "—"),14f,0xFFDBEAF7.toInt())
        if(a!=null) {
            val kind=a.optString("kind","Obiekt")
            line(if(kind=="Obiekt") a.optString("object") else kind,16f,Color.WHITE)
            if(kind=="Przejazd") line("W drodze: "+duration(System.currentTimeMillis()-a.optLong("stageStart")),18f,0xFFFF9898.toInt())
        }
        root.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(16) })
        if(a==null) button("🟢 ROZPOCZNIJ PRACĘ") { confirmPin { startShift() } }
        else {
            button("🔴 ZAKOŃCZ PRACĘ") { confirmPin { stopShift() } }
            button("🔄 ZMIEŃ OBIEKT") { confirmAction("Zmienić obiekt?") { switchObject() } }
            button(if(a.optString("kind")=="Przejazd") "DOTARŁEM" else "🚗 PRZEJAZD") { toggleTravel() }
            button(if(a.optString("kind")=="Zakupy") "ZAKOŃCZ ZAKUPY" else "ZAKUPY") { toggleShopping() }
            button("TRASA DO OBIEKTU") { chooseObject(false) { obj -> if(obj!=null) openLink("https://www.google.com/maps/dir/?api=1&destination="+URLEncoder.encode(obj.optString("city")+" "+obj.optString("Adres"),"UTF-8")) } }
            if(a.optString("kind")=="Zakupy") button("Dane zakupów / paragony") { shoppingRecord(a.optString("shoppingRecordId")) }
        }
        label("TWÓJ DZIEŃ")
        tiles(listOf(
            "ZDJĘCIA" to { openScreen("list:links:") },
            "MATERIAŁY" to { openScreen("list:materials:") },
            "NOTATKI" to { openScreen("notes") },
            "OBIEKTY" to { openScreen("objects") },
            "MÓJ CZAS" to { report() },
            "GRAFIKI" to { charts() }
        ))
    }
    private fun settings() {
        base("Ustawienia"); screen="settings"; nav()
        button("Portfel / rozliczenia (PIN)") { confirmPin { walletUnlocked=true; wallet() } }
        button("GENERUJ RAPORT SMS") { weeklySms() }
        button("Google Disk") { driveSettings() }
        button("Logi / eksport / import") { logs() }
        button("Wygląd widżetu") { widgetSettings() }
        button("Język aplikacji") { language() }
        button("O aplikacji") { about() }
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
        WalletRules.Shift(it.optLong("start"),it.optLong("end"),rate)
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
    private fun report() {
        base("Mój czas"); screen="report"; nav()
        button("GENERUJ RAPORT SMS") { weeklySms("report") }
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
        label("Łączny czas: "+duration(entries.sumOf { it.second.end-it.second.start }))
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
                label("Czas dnia: "+duration(items.sumOf { it.second.end-it.second.start }))
                for((shift,slice) in items.sortedByDescending { it.second.start }) {
                    button(fmt.format(Date(slice.start))+" → "+fmt.format(Date(slice.end))+"  ("+duration(slice.end-slice.start)+")") { shiftDetails(shift.optString("id")) }
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
            val name=if(kind=="Obiekt") stage.optString("object").ifBlank { objectById(stage.optString("objectId"))?.let { objectName(it) } ?: "Obiekt bez nazwy" } else kind
            if(kind=="Przejazd") label("Z: "+stage.optString("fromObject","—")+"\nDo: "+stage.optString("toObject","—"))
            if(kind=="Zakupy" && stage.optString("shoppingRecordId").isNotBlank()) button("Zakupy / paragony") { recordDetails("notes",stage.optString("shoppingRecordId"),screen) }
            label(name+"\n"+fmt.format(Date(from))+" → "+fmt.format(Date(to))+"\nCzas: "+duration(to-from))
            cursor=maxOf(cursor,to)
        }
        if(stages.isNotEmpty() && cursor<end) label("Brak przypisanego etapu: "+fmt.format(Date(cursor))+" → "+fmt.format(Date(end))+" · "+duration(end-cursor))
        label("Zakończenie pracy: "+fmt.format(Date(end)))
        label("Łączny czas: "+duration(end-start))
        button("Edytuj czas") { confirmAction { editShift(id) } }
        button("Usuń zmianę") { deleteShift(id) }
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
        val s=rows(d,"shifts").find { it.optString("id")==id } ?: return
        var start=Instant.ofEpochMilli(s.optLong("start")).atZone(ZoneId.systemDefault()).toLocalDateTime()
        var end=Instant.ofEpochMilli(s.optLong("end")).atZone(ZoneId.systemDefault()).toLocalDateTime()
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
                val st=start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val en=end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if(en<=st) { toast("Koniec musi być po rozpoczęciu"); return@setOnClickListener }
                val current=data()
                val row=rows(current,"shifts").find { it.optString("id")==id } ?: return@setOnClickListener
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
    private fun objectById(id:String) = rows(data(),"objects").find { it.optString("id")==id }
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
        val options=rows(data(),"objects").filter { !it.optBoolean("archived") }
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
            for(obj in matches) results.addView(makeButton(objectName(obj)) { dialog.dismiss(); selected(obj) },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(6) })
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
            target=="settings" -> settings()
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
                if(st.optString("objectId")!=id || st.optString("kind","Obiekt")!="Obiekt") continue
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
        button(if(obj.optBoolean("archived")) "Przywróć obiekt" else "Usuń z listy aktywnych") {
            confirmAction {
                val d=data(); val o=rows(d,"objects").find { it.optString("id")==id } ?: return@confirmAction
                o.put("archived",!o.optBoolean("archived"))
                save(d,"OBJECT_ARCHIVE",id); objects()
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
            else try { JSONObject(prefs.getString("draft_"+key,"{}")!!) } catch (_:Exception) { JSONObject() }
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
    private fun renderForm() {
        if(formKind=="expenses" && !walletUnlocked) { settings(); confirmPin { walletUnlocked=true; renderForm() }; return }
        base(when(formKind) { "objects" -> "Obiekt"; "expenses" -> "Zakup za własne pieniądze"; "materials" -> "Zużyty materiał"; "links" -> "Dodaj zdjęcia"; else -> "Notatka" })
        screen="form"; nav()
        if(formKind=="objects") {
            label("TAG: "+draft.optString("TAG").ifBlank { "automatycznie po zapisaniu miasta" })
            field("Miasto","city")
            if(draft.optString("TAG").isBlank()) field("Tag (opcjonalnie)","tagPrefix") else label("Tag (stały): "+draft.optString("TAG"))
            field("Adres","Adres"); field("Nazwa organizacji","organization")
            field("Notatki / kontakt","notes",true); field("Link Google Maps (opcjonalnie)","maps")
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
            if(city.isBlank() || draft.optString("Adres").isBlank()) { toast("Podaj miasto i adres"); return }
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
        val list=arr(d,formKind); val index=(0 until list.length()).find { list.optJSONObject(it)?.optString("id")==draft.optString("id") }
        if(index==null) list.put(draft) else list.put(index,draft)
        save(d,"SAVE_"+formKind,draft.optString("id"))
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
        val range=if(prefs.getString("chartPeriod","week")=="month") TimeAnalytics.previousMonth(LocalDate.now()) else TimeAnalytics.previousWeek(LocalDate.now())
        filters("charts") {
            button("Ostatni tydzień") { prefs.edit().putString("chartPeriod","week").apply();charts() }
            button("Ostatni miesiąc") { prefs.edit().putString("chartPeriod","month").apply();charts() }
        }
        label("Wybrany okres: "+dateText(range.first.toString())+" — "+dateText(range.second.toString()))
        val shifts=(rows(data(),"shifts")+listOfNotNull(active(data()))).map { sh ->
            val end=sh.optLong("end",System.currentTimeMillis())
            val stages=rows(sh,"stages").toMutableList()
            if(!sh.has("end")) stages.add(JSONObject().put("start",sh.optLong("stageStart",sh.optLong("start"))).put("end",end).put("kind",sh.optString("kind","Obiekt")).put("objectId",sh.optString("objectId")))
            TimeAnalytics.Shift(sh.optLong("start"),end,stages.map { st -> TimeAnalytics.Stage(st.optLong("start"),st.optLong("end"),when(st.optString("kind")) { "Obiekt" -> if(st.optString("objectId").isNotBlank() || st.optString("object").isNotBlank()) 0 else 3; "Zakupy" -> 1; "Przejazd" -> 2; else -> 3 }) })
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
        val sum=result.totals.sum()
        result.totals.forEachIndexed { i,n ->
            root.addView(TextView(this).apply { text="● "+TimeChart.names[i]+" — "+duration(n)+" · "+"%.1f%%".format(Locale("pl"),if(sum==0L) 0.0 else n*100.0/sum);textSize=16f;setTextColor(0xFF37474F.toInt());setPadding(0,dp(7),0,dp(7)) })
        }
        if(sum==0L) label("Brak czasu pracy w wybranym okresie.")
        label("Podział nie zmienia czasu płatnego. Cała zmiana jest czasem pracy.")
    }
    private fun toggleShopping() {
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
        val qty=DomainRules.quantity(draft.optString("quantity"),draft.optString("unit","szt."))
        if(draft.optString("name").isBlank() || qty==null) { toast("Podaj nazwę i poprawną ilość większą od zera");return null }
        return JSONObject().put("name",draft.optString("name").trim()).put("quantity",qty.toPlainString()).put("unit",draft.optString("unit","szt.")).put("photos",JSONArray(arr(draft,"photos").toString()))
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
        val batch=rows(draft,"batch").map { JSONObject(it.toString()) }.toMutableList()
        val d=data();val existing=rows(d,"materials").find { it.optString("id")==draft.optString("id") }
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
