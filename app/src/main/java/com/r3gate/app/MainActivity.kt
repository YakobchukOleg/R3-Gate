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
    private var earningsVisible = false
    private val ticker = Handler(Looper.getMainLooper())
    private var screen = "home"
    private var formKind = ""
    private var formReturn = "home"
    private var draft = JSONObject()
    private var cameraPath: String? = null
    private val io = Executors.newSingleThreadExecutor()
    private var driveToken: String? = null
    private var pendingUpload = ""
    companion object { private val uploadBusy = java.util.concurrent.atomic.AtomicBoolean(false) }
    private var uploading: Boolean
        get() = uploadBusy.get()
        set(value) { uploadBusy.set(value) }
    private var homeVisible = false
    private var smsFrom: LocalDate? = null
    private var smsTo: LocalDate? = null
    private val tick = object : Runnable { override fun run() { if (::root.isInitialized && homeVisible) home(); ticker.postDelayed(this, 60000) } }

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
        cameraPath = savedInstanceState?.getString("cameraPath")
        pendingUpload = savedInstanceState?.getString("pendingUpload") ?: ""
        if (savedInstanceState != null && prefs.contains("pin")) {
            detailParent = savedInstanceState.getString("detailParent") ?: "home"
            formKind = savedInstanceState.getString("formKind") ?: ""
            formReturn = savedInstanceState.getString("formReturn") ?: "home"
            draft = JSONObject(savedInstanceState.getString("draft") ?: "{}")
            openScreen(savedInstanceState.getString("screen") ?: "home")
            return
        }
        if (prefs.getString("pin", null) == null) setupPin() else handleLaunch()
    }
    override fun onResume() { super.onResume(); ticker.removeCallbacks(tick); ticker.postDelayed(tick, 60000) }
    override fun onPause() { ticker.removeCallbacks(tick); super.onPause() }
    private fun hash(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun setupPin() {
        val field = EditText(this).apply { hint="PIN (4–8 cyfr)"; inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle("Ustaw PIN").setMessage("Kod chroni rozpoczęcie i zakończenie pracy.").setView(field).setCancelable(false).setPositiveButton("Zapisz") { _, _ ->
            val p=field.text.toString(); if (p.length in 4..8) {
                val salt=UUID.randomUUID().toString(); prefs.edit().putString("salt",salt).putString("pin",hash(salt+p)).apply(); home()
            } else { toast("PIN musi mieć 4–8 cyfr"); setupPin() }
        }.show()
    }
    private fun confirmPin(action: () -> Unit) {
        val field=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint="PIN" }
        AlertDialog.Builder(this).setTitle("Potwierdź PIN").setView(field).setNegativeButton("Anuluj",null).setPositiveButton("Potwierdź") { _, _ ->
            if (hash(prefs.getString("salt","")+field.text.toString()) == prefs.getString("pin","")) action() else toast("Nieprawidłowy PIN")
        }.show()
    }
    private fun handleLaunch() {
        val action=intent?.getStringExtra("widget_action")
        intent?.removeExtra("widget_action")
        if (action == "start") confirmPin { startShift() }
        else if (action == "stop") confirmPin { stopShift() }
        else if (action == "switch") switchObject()
        else if (action == "travel") beginStage("Przejazd")
        else if (action == "shopping") beginStage("Zakupy")
        home()
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
        val t=System.currentTimeMillis(); d.put("active",JSONObject().put("id",UUID.randomUUID().toString()).put("start",t).put("object",objectName(obj)).put("objectId",obj.optString("id")).put("stageStart",t).put("stages",JSONArray())); save(d,"START",fmt.format(Date(t))); home()
    }
    private fun stopShift() {
        val d=data(); val a=active(d); if(a==null) { toast("Brak rozpoczętej pracy"); home(); return }
        val end=System.currentTimeMillis(); closeStage(a,end); a.put("end",end); arr(d,"shifts").put(a); d.remove("active"); save(d,"STOP",fmt.format(Date(end))); home()
    }
    private fun closeStage(a:JSONObject, end:Long) {
        val st=a.optLong("stageStart",a.optLong("start"))
        if(end>st) {
            val segments=arr(a,"stages")
            segments.put(JSONObject().put("start",st).put("end",end)
                .put("kind",a.optString("kind","Obiekt")).put("object",a.optString("object","")).put("objectId",a.optString("objectId")))
            a.put("stages",segments)
        }
    }
    private fun beginStage(kind:String, target:String="") {
        val d=data(); val a=active(d)
        if(a==null) { toast("Najpierw rozpocznij pracę"); return }
        val now=System.currentTimeMillis()
        closeStage(a,now)
        a.put("stageStart",now).put("kind",kind)
        if(target.isNotEmpty()) a.put("object",target)
        save(d,"STAGE","$kind: "+a.optString("object")); home()
    }
    private fun switchObject() {
        if (active(data()) == null) { toast("Najpierw rozpocznij pracę"); return }
        chooseObject(false) { obj ->
            if (obj != null) {
                val d = data(); val a = active(d) ?: return@chooseObject
                val now = System.currentTimeMillis()
                closeStage(a,now)
                a.put("stageStart",now).put("kind","Obiekt")
                    .put("object",objectName(obj)).put("objectId",obj.optString("id"))
                save(d,"STAGE",objectName(obj)); home()
            }
        }
    }
    private fun weeklySms() {
        val today = LocalDate.now()
        if (smsFrom == null) smsFrom = today.minusDays(((today.dayOfWeek.value + 4) % 7).toLong())
        if (smsTo == null) smsTo = smsFrom!!.plusDays(6)
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
        label("Okres: ${from.format(df)} – ${to.format(df)} (włącznie)")
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
                .setPrimaryClip(ClipData.newPlainText("R3 Gate raport", sms))
            toast("Raport skopiowany")
        }
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(n:Int) = (n * resources.displayMetrics.density).toInt()
    private fun background(color:Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(14).toFloat()
    }
    private fun base(title:String) {
        homeVisible = false
        val frame = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val top = View(this).apply { setBackgroundColor(Color.BLACK) }
        frame.addView(top,LinearLayout.LayoutParams(-1,0))
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFF3F5F8.toInt()); isFillViewport = true }
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(18),dp(16),dp(24)) }
        scroll.addView(root)
        frame.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val bottom = View(this); frame.addView(bottom,LinearLayout.LayoutParams(-1,0))
        frame.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                topInset = bars.top; bottomInset = maxOf(bars.bottom,ime.bottom)
                scroll.setPadding(bars.left,0,bars.right,0)
            } else {
                topInset = insets.systemWindowInsetTop; bottomInset = insets.systemWindowInsetBottom
                scroll.setPadding(insets.systemWindowInsetLeft,0,insets.systemWindowInsetRight,0)
            }
            top.layoutParams = top.layoutParams.apply { height = topInset }
            bottom.layoutParams = bottom.layoutParams.apply { height = bottomInset }
            insets
        }
        setContentView(frame); frame.requestApplyInsets()
        root.addView(TextView(this).apply {
            text=title; textSize=27f; setTextColor(0xFF17253A.toInt())
            setPadding(0,0,0,dp(16)); setTypeface(null,android.graphics.Typeface.BOLD)
        })
    }
    private fun label(s:String) { root.addView(TextView(this).apply {
        text=s; textSize=16f; setTextColor(0xFF35445A.toInt()); setPadding(dp(2),dp(8),dp(2),dp(8))
    }) }
    private fun button(s:String, f:()->Unit) {
        root.addView(Button(this).apply {
            text=s; textSize=15f; isAllCaps=false; minHeight=dp(52)
            setTextColor(Color.WHITE); background=background(0xFF244C70.toInt())
            setPadding(dp(12),dp(10),dp(12),dp(10)); setOnClickListener { f() }
        },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }
    private fun nav() { button("← Wstecz") { back() } }
    private fun back() {
        when {
            screen == "home" -> finish()
            screen == "sms" -> report()
            screen == "form" -> {
                AlertDialog.Builder(this).setMessage("Zachować szkic i wrócić?")
                    .setNegativeButton("Zostań",null).setPositiveButton("Wróć") { _, _ ->
                        prefs.edit().putString("draft_$formKind",draft.toString()).apply()
                        openScreen(formReturn)
                    }.show()
            }
            screen.startsWith("detail:") -> openScreen(detailParent)
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
        out.putString("detailParent",detailParent); out.putString("screen",screen); out.putString("formKind",formKind); out.putString("formReturn",formReturn)
        out.putString("draft",draft.toString()); out.putString("cameraPath",cameraPath); out.putString("pendingUpload",pendingUpload)
        super.onSaveInstanceState(out)
    }
    private fun duration(ms:Long):String { val m=(ms.coerceAtLeast(0)/60000); return "%02d:%02d".format(m/60,m%60) }
    private fun home() {
        base("R3 Gate")
        screen = "home"
        homeVisible = true
        val d=data(); val a=active(d)
        label(if(a!=null) "🟢 ONLINE" else "⚪ OFFLINE")
        val last=arr(d,"shifts"); val recent=if(last.length()>0) last.optJSONObject(last.length()-1) else null
        val shown=a?:recent
        label("Rozpoczęcie: "+(shown?.let { fmt.format(Date(it.optLong("start"))) }?:"—"))
        label("Zakończenie: "+(if(a!=null) "— Offline" else recent?.let { fmt.format(Date(it.optLong("end"))) }?:"—"))
        label("Czas pracy: "+(shown?.let { duration((if(a!=null) System.currentTimeMillis() else it.optLong("end"))-it.optLong("start")) }?:"00:00"))
        button("🟢 ROZPOCZNIJ PRACĘ") { confirmPin { startShift() } }
        button("🔴 ZAKOŃCZ PRACĘ") { confirmPin { stopShift() } }
        if(a!=null) {
            label("Aktualny etap: "+a.optString("kind","Obiekt")+" — "+a.optString("object",""))
            button("🔄 ZMIEŃ OBIEKT") { switchObject() }
            button("🚗 PRZEJAZD") { beginStage("Przejazd") }
            button("🛒 ZAKUPY") { beginStage("Zakupy") }
        }
        button("ZDJĘCIA") { openScreen("list:links:") }
        button("MATERIAŁY") { openScreen("list:materials:") }
        button("NOTATKI") { openScreen("notes") }
        button("OBIEKTY") { openScreen("objects") }
        button("GOOGLE DRIVE") { openScreen("drive") }
        button("📊 MÓJ RAPORT") { report() }
        button("📁 LOGI / KOPIA") { logs() }
    }
    private fun report() {
        base("Mój raport"); screen = "report"; nav(); button("📋 GENERUJ RAPORT SMS") { weeklySms() }; val d=data(); val list=arr(d,"shifts")
        var total=0L; for(i in 0 until list.length()) { val x=list.optJSONObject(i)?:continue; total+=(x.optLong("end")-x.optLong("start")).coerceAtLeast(0) }
        label("Łączny czas: ${duration(total)}")
        button(if(earningsVisible) "Ukryj zarobek" else "ZAROBEK (pokaż)") {
            if(earningsVisible) { earningsVisible=false; report() } else confirmPin { earningsVisible=true; report() }
        }
        if(earningsVisible) label("Zarobek (30 zł/h): %.2f zł".format(Locale.US,total/3600000.0*30))
        for(i in list.length()-1 downTo 0) {
            val s=list.optJSONObject(i)?:continue
            label("${fmt.format(Date(s.optLong("start")))} → ${fmt.format(Date(s.optLong("end")))}  (${duration(s.optLong("end")-s.optLong("start"))})")
            button("Edytuj czas") { confirmPin { editShift(s.optString("id")) } }
        }
    }
    private fun editShift(id:String) {
        val d=data(); val shifts=arr(d,"shifts"); var shift:JSONObject?=null
        for(i in 0 until shifts.length()) if(shifts.optJSONObject(i)?.optString("id")==id) shift=shifts.optJSONObject(i)
        val s=shift?:return
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,0,20,0) }
        val start=EditText(this).apply { setText(fmt.format(Date(s.optLong("start")))); hint="Start: dd-MM-yyyy HH:mm"; layout.addView(this) }
        val end=EditText(this).apply { setText(fmt.format(Date(s.optLong("end")))); hint="Koniec: dd-MM-yyyy HH:mm"; layout.addView(this) }
        AlertDialog.Builder(this).setTitle("Edytuj zmianę").setView(layout).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz") { _, _ ->
            try {
                fmt.isLenient=false; val st=fmt.parse(start.text.toString())!!.time; val en=fmt.parse(end.text.toString())!!.time
                if(en<=st) { toast("Koniec musi być po rozpoczęciu"); return@setPositiveButton }
                val old="${fmt.format(Date(s.optLong("start")))} → ${fmt.format(Date(s.optLong("end")))}"
                s.put("start",st).put("end",en); save(d,"EDIT_SHIFT","$old => ${start.text} → ${end.text}"); report()
            } catch (_:Exception) { toast("Format: dd-MM-yyyy HH:mm") }
        }.show()
    }
    private fun logs() {
        base("Logi i kopia zapasowa"); screen = "logs"; nav()
        label("Kopia JSON zawiera dane i odnośniki. Zachowaj również zdjęcia na Drive; zdjęcia lokalne nie są dołączane do JSON.")
        button("Eksportuj dziennik JSON") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="application/json"; putExtra(Intent.EXTRA_TITLE,"R3-Gate-backup.json") },100) }
        button("Importuj dziennik JSON") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="application/json" },101) }
        val list=arr(data(),"logs"); for(i in list.length()-1 downTo 0) { val x=list.optJSONObject(i)?:continue; label("${x.optString("at")} | ${x.optString("type")} | ${x.optString("detail")}") }
    }
    @Deprecated("Legacy activity result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,dataIntent:Intent?) {
        super.onActivityResult(requestCode,resultCode,dataIntent)
        if (requestCode == 203) {
            if (resultCode == RESULT_OK && dataIntent != null) {
                try { authorized(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(dataIntent)) }
                catch (_:Exception) { toast("Autoryzacja nieudana. Sprawdź konfigurację Google Cloud."); pendingUpload="" }
            } else { pendingUpload=""; toast("Anulowano połączenie z Google Drive") }
            return
        }
        if (requestCode == 201 || requestCode == 202) {
            if (resultCode == RESULT_OK) {
                if (requestCode == 201) {
                    val uris = mutableListOf<Uri>()
                    dataIntent?.clipData?.let { clips -> for (i in 0 until clips.itemCount) uris.add(clips.getItemAt(i).uri) }
                    if (uris.isEmpty()) dataIntent?.data?.let { uris.add(it) }
                    for (u in uris) {
                        try { contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION); arr(draft,"photos").put(u.toString()) }
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
            if(requestCode==100) { contentResolver.openOutputStream(uri)?.use { it.write(data().toString(2).toByteArray(Charsets.UTF_8)) }; toast("Kopia zapisana") }
            if(requestCode==101) {
                val content=contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }; val imported=JSONObject(content)
                AlertDialog.Builder(this).setTitle("Przywrócić kopię?").setMessage("Obecne dane zostaną zastąpione.").setNegativeButton("Anuluj",null).setPositiveButton("Importuj") { _, _ ->
                    prefs.edit().putString("data",imported.toString()).apply(); migrate(); GateWidget.refresh(this); logs()
                }.show()
            }
        } catch (_:Exception) { toast("Błąd pliku JSON") }
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
        val options=rows(data(),"objects").filter { !it.optBoolean("archived") }.sortedBy { objectName(it) }
        if(options.isEmpty() && !optional) { toast("Najpierw dodaj obiekt w sekcji OBIEKTY"); return }
        val names=(if(optional) listOf("Bez obiektu") else emptyList()) + options.map { objectName(it) }
        AlertDialog.Builder(this).setTitle("Wybierz obiekt").setItems(names.toTypedArray()) { _,index ->
            selected(if(optional && index==0) null else options[index-(if(optional) 1 else 0)])
        }.setNegativeButton("Anuluj",null).show()
    }
    private fun openScreen(target:String) {
        screen=target
        when {
            target=="home" -> home()
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
            target=="sms" -> weeklySms()
            target=="logs" -> logs()
            target=="form" -> renderForm()
            target=="drive" -> driveSettings()
            else -> home()
        }
    }
    private fun objects() {
        base("Obiekty"); screen="objects"; nav()
        button("+ Dodaj obiekt") { newForm("objects") }
        val all=rows(data(),"objects")
        val showArchived=prefs.getBoolean("archived_objects",false)
        button(if(showArchived) "Pokaż aktywne" else "Pokaż archiwalne") {
            prefs.edit().putBoolean("archived_objects",!showArchived).apply(); objects()
        }
        for(o in all.filter { it.optBoolean("archived")==showArchived }.sortedBy { objectName(it) }) {
            button(objectName(o)) { openScreen("object:"+o.optString("id")) }
        }
        if(all.isEmpty()) label("Dodaj pierwszy obiekt, aby przypisywać pracę, materiały i zdjęcia.")
    }
    private fun objectDetails(id:String) {
        val obj=objectById(id) ?: run { objects(); return }
        base(obj.optString("TAG")); screen="object:"+id; nav()
        label(objectName(obj)); label(obj.optString("notes",obj.optString("Opis")))
        button("Edytuj obiekt") { newForm("objects",id) }
        button("Otwórz w Google Maps") {
            val link=obj.optString("maps")
            openLink(if(link.isNotBlank()) link else "https://www.google.com/maps/search/?api=1&query="+URLEncoder.encode(obj.optString("city")+" "+obj.optString("Adres"),"UTF-8"))
        }
        label("Czas na obiekcie (bez przejazdów i zakupów)")
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
                    totals[day]=(totals[day] ?: 0L)+ms
                }
            }
        }
        label("Razem: "+duration(totals.values.sum()))
        for((day,ms) in totals.toSortedMap(reverseOrder())) label(dateText(day)+" · "+duration(ms))
        if(unassigned>0) label("Starsze zmiany bez etapów nie są przypisywane automatycznie do obiektu.")
        button("Materiały — ilości i daty") { openScreen("list:materials:"+id) }
        button("Zdjęcia według dat") { openScreen("list:links:"+id) }
        button("Notatki obiektu") { openScreen("list:notes:"+id) }
        button(if(obj.optBoolean("archived")) "Przywróć obiekt (PIN)" else "Usuń z listy aktywnych (PIN)") {
            confirmPin {
                val d=data(); val o=rows(d,"objects").find { it.optString("id")==id } ?: return@confirmPin
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
        val f=try { JSONObject(prefs.getString(filterKey,"{}")!!) } catch (_:Exception) { JSONObject() }
        fun refresh() { prefs.edit().putString(filterKey,f.toString()).apply(); openScreen(target) }
        button("Data od: "+f.optString("from").let { if(it.isBlank()) "wszystkie" else dateText(it) }) {
            pickReportDate(LocalDate.now()) { f.put("from",it.toString()); refresh() }
        }
        button("Data do: "+f.optString("to").let { if(it.isBlank()) "wszystkie" else dateText(it) }) {
            pickReportDate(LocalDate.now()) { f.put("to",it.toString()); refresh() }
        }
        if(objectId.isBlank()) button("Obiekt: "+(objectById(f.optString("objectId"))?.let { objectName(it) } ?: "wszystkie")) {
            chooseObject(true) { f.put("objectId",it?.optString("id") ?: ""); refresh() }
        }
        button("Wyczyść filtry") { prefs.edit().remove(filterKey).apply(); openScreen(target) }
        button(if(f.optBoolean("nameSort")) "Sortowanie: nazwa" else "Sortowanie: data, najnowsze") { f.put("nameSort",!f.optBoolean("nameSort")); refresh() }
        button("+ Dodaj") { newForm(key,objectId=objectId) }
        if(f.optString("from").isNotBlank() && f.optString("to").isNotBlank() && f.optString("from")>f.optString("to")) { label("Data do musi być po dacie od."); return }
        val selected=objectId.ifBlank { f.optString("objectId") }
        val list=rows(data(),key).filter {
            val day=recordDate(it)
            (selected.isBlank() || it.optString("objectId")==selected) &&
            (!onlyActive || (it.optBoolean("aktualne") && !it.optBoolean("done"))) &&
            DomainRules.inRange(day,f.optString("from"),f.optString("to"))
        }.let { list -> if(f.optBoolean("nameSort")) list.sortedBy { it.optString("name",it.optString("text")).lowercase(Locale.ROOT) } else list.sortedByDescending { recordDate(it)+it.optString("created") } }
        if(list.isEmpty()) label("Brak wpisów dla wybranych filtrów.")
        if(key=="materials") {
            label("Podsumowanie materiałów dla wybranego zakresu")
            val sums=linkedMapOf<String,BigDecimal>()
            for(r in list) {
                val q=r.optString("quantity").replace(",",".").toBigDecimalOrNull() ?: continue
                val group=r.optString("name")+" · "+r.optString("unit","szt.")
                sums[group]=(sums[group] ?: BigDecimal.ZERO)+q
            }
            sums.forEach { (name,q) -> label(name+": "+q.stripTrailingZeros().toPlainString()) }
        }
        var lastDate=""
        for(r in list) {
            val date=dateText(recordDate(r))
            if(lastDate!=date) { label("── "+date+" ──"); lastDate=date }
            val obj=objectById(r.optString("objectId"))?.let { objectName(it) } ?: r.optString("Obiekt").ifBlank { "Bez obiektu" }
            val summary=when(key) {
                "materials" -> r.optString("name")+" · "+r.optString("quantity")+" "+r.optString("unit","szt.")
                "notes" -> (if(r.optBoolean("done")) "✓ " else if(r.optBoolean("aktualne")) "● " else "")+r.optString("text").ifBlank { "Notatka ze zdjęciem" }.take(100)
                else -> "Zdjęcia: "+arr(r,"photos").length()+" · "+r.optString("Link Google Drive")
            }
            button(summary+"\n"+obj) { recordDetails(key,r.optString("id"),target) }
        }
    }
    private var detailParent="home"
    private fun recordDetails(key:String,id:String,parent:String) {
        val r=rows(data(),key).find { it.optString("id")==id } ?: return
        base("Szczegóły"); screen="detail:"+key+":"+id; detailParent=parent
        button("← Wstecz") { openScreen(parent) }
        label(dateText(recordDate(r)))
        label(objectById(r.optString("objectId"))?.let { objectName(it) } ?: r.optString("Obiekt").ifBlank { "Bez obiektu" })
        if(key=="materials") label(r.optString("name")+" · "+r.optString("quantity")+" "+r.optString("unit","szt."))
        if(key=="notes") {
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
        if(r.optString("Link Google Drive").isNotBlank()) button("Otwórz folder Drive") { openLink(r.optString("Link Google Drive")) }
        if(arr(r,"photos").length()>0) button(if(uploading) "Trwa przesyłanie…" else "Wyślij zdjęcia na Google Drive / ponów") {
            if(!uploading) { pendingUpload=key+":"+id; authorizeDrive() }
        }
        button("Edytuj") { screen=parent; newForm(key,id) }
        button("Usuń wpis (PIN)") {
            confirmPin {
                AlertDialog.Builder(this).setMessage("Usunąć ten wpis z aplikacji? Pliki na Drive pozostaną.")
                    .setNegativeButton("Anuluj",null).setPositiveButton("Usuń") { _, _ ->
                        val d=data(); val newer=JSONArray()
                        rows(d,key).filter { it.optString("id")!=id }.forEach { newer.put(it) }
                        d.put(key,newer); save(d,"DELETE_"+key,id); openScreen(parent)
                    }.show()
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
            val thumb=ImageView(this).apply { adjustViewBounds=true; maxHeight=dp(200); contentDescription="Zdjęcie "+(i+1) }
            try {
                val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                contentResolver.openInputStream(Uri.parse(uri))?.use { android.graphics.BitmapFactory.decodeStream(it,null,bounds) }
                var sample=1
                while(bounds.outWidth/sample>900 || bounds.outHeight/sample>900) sample*=2
                val options=android.graphics.BitmapFactory.Options().apply { inSampleSize=sample }
                contentResolver.openInputStream(Uri.parse(uri))?.use { thumb.setImageBitmap(android.graphics.BitmapFactory.decodeStream(it,null,options)) }
                root.addView(thumb,LinearLayout.LayoutParams(-1,dp(200)))
            } catch (_:Exception) { label("Zdjęcie "+(i+1)+": plik niedostępny na tym urządzeniu") }
            val driveId=r.optJSONObject("driveFiles")?.optString(hash(prefs.getString("driveEmail","") ?: "").take(24)+"|"+uri).orEmpty()
            label("Zdjęcie "+(i+1)+if(driveId.isBlank()) " · lokalne" else " · zapisane na Drive")
        }
    }
    private fun newForm(key:String,id:String="",objectId:String="") {
        formReturn=screen; formKind=key
        draft=if(id.isNotBlank()) JSONObject(rows(data(),key).find { it.optString("id")==id }?.toString() ?: "{}")
            else try { JSONObject(prefs.getString("draft_"+key,"{}")!!) } catch (_:Exception) { JSONObject() }
        if(!draft.has("id")) draft.put("id",UUID.randomUUID().toString())
        if(!draft.has("created")) draft.put("created",fmt.format(Date()))
        if(key!="objects" && !draft.has("date")) draft.put("date",active(data())?.let { localDate(it.optLong("start")).toString() } ?: LocalDate.now().toString())
        if(objectId.isNotBlank()) draft.put("objectId",objectId)
        renderForm()
    }
    private fun field(caption:String,key:String,multiline:Boolean=false,numeric:Boolean=false) {
        label(caption)
        val e=EditText(this).apply {
            setText(draft.optString(key)); textSize=17f; minHeight=dp(48)
            inputType=if(numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or (if(multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
            setSingleLine(!multiline); setPadding(dp(10),dp(8),dp(10),dp(8)); background=background(Color.WHITE)
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
        base(when(formKind) { "objects" -> "Obiekt"; "materials" -> "Zużyty materiał"; "links" -> "Dodaj zdjęcia"; else -> "Notatka" })
        screen="form"; nav()
        if(formKind=="objects") {
            label("TAG: "+draft.optString("TAG").ifBlank { "automatycznie po zapisaniu miasta" })
            field("Miasto","city"); field("Adres","Adres"); field("Nazwa organizacji","organization")
            field("Notatki / kontakt","notes",true); field("Link Google Maps (opcjonalnie)","maps")
        } else {
            button("Data: "+dateText(recordDate(draft))) {
                pickReportDate(try { LocalDate.parse(recordDate(draft)) } catch (_:Exception) { LocalDate.now() }) {
                    draft.put("date",it.toString()); renderForm()
                }
            }
            button("Obiekt: "+(objectById(draft.optString("objectId"))?.let { objectName(it) } ?: if(formKind=="notes") "Bez obiektu" else "Wybierz")) {
                chooseObject(formKind=="notes") { draft.put("objectId",it?.optString("id") ?: ""); draft.remove("Obiekt"); renderForm() }
            }
            if(formKind=="materials") {
                field("Nazwa materiału","name")
                button("Dodaj zdjęcia z galerii") { pickPhotos() }; button("Zrób zdjęcie") { camera() }
                field("Ilość","quantity",numeric=true)
                button("Jednostka: "+draft.optString("unit","szt.")) {
                    AlertDialog.Builder(this).setTitle("Jednostka").setItems(arrayOf("szt.","m")) { _,which ->
                        draft.put("unit",if(which==0) "szt." else "m"); renderForm()
                    }.show()
                }
            } else {
                if(formKind=="notes") {
                    field("Treść (opcjonalnie, gdy dodajesz zdjęcie)","text",true)
                    root.addView(CheckBox(this).apply {
                        text="Aktualna"; isChecked=draft.optBoolean("aktualne")
                        setOnCheckedChangeListener { _,checked -> draft.put("aktualne",checked); if(checked) draft.put("done",false) }
                    })
                }
                button("Dodaj zdjęcia z galerii") { pickPhotos() }; button("Zrób zdjęcie") { camera() }
                if(formKind=="links") field("Link Google Drive (opcjonalnie)","Link Google Drive")
            }
            showPhotos(draft)
            if(arr(draft,"photos").length()>0) button("Usuń zdjęcie ze szkicu") {
                val photos=arr(draft,"photos")
                AlertDialog.Builder(this).setTitle("Wybierz zdjęcie").setItems((0 until photos.length()).map { "Zdjęcie "+(it+1) }.toTypedArray()) { _,index ->
                    photos.remove(index); renderForm()
                }.show()
            }
        }
        button("Zapisz") { saveForm() }
    }
    private fun saveForm() {
        val d=data()
        if(formKind=="objects") {
            val city=draft.optString("city").trim()
            if(city.isBlank() || draft.optString("Adres").isBlank()) { toast("Podaj miasto i adres"); return }
            if(draft.optString("TAG").isBlank()) {
                val prefix=DomainRules.cityPrefix(city)
                val counters=d.optJSONObject("tagCounters") ?: JSONObject().also { d.put("tagCounters",it) }
                val (tag,n)=DomainRules.nextTag(city,counters.optInt(prefix),rows(d,"objects").map { it.optString("TAG") })
                draft.put("TAG",tag); counters.put(prefix,n)
            }
        } else {
            if(recordDate(draft).isBlank()) { toast("Wybierz datę"); return }
            if(formKind!="notes" && draft.optString("objectId").isBlank()) { toast("Wybierz obiekt"); return }
            if(formKind=="materials") {
                val qty=DomainRules.quantity(draft.optString("quantity"),draft.optString("unit","szt."))
                if(draft.optString("name").isBlank() || qty==null || qty<=BigDecimal.ZERO) { toast("Podaj nazwę i ilość większą od zera"); return }
                if(draft.optString("unit","szt.")=="szt." && qty.stripTrailingZeros().scale()>0) { toast("Liczba sztuk musi być całkowita"); return }
                draft.put("quantity",qty.stripTrailingZeros().toPlainString())
                if(!draft.has("unit")) draft.put("unit","szt.")
            }
            if(formKind=="notes" && !DomainRules.validNote(draft.optString("text"),arr(draft,"photos").length())) { toast("Dodaj tekst lub zdjęcie"); return }
            if(formKind=="links" && arr(draft,"photos").length()==0 && draft.optString("Link Google Drive").isBlank()) { toast("Dodaj zdjęcie lub link"); return }
        }
        val maps=draft.optString("maps"); val link=draft.optString("Link Google Drive")
        if((maps.isNotBlank() && !maps.startsWith("https://")) || (link.isNotBlank() && !link.startsWith("https://"))) { toast("Link musi zaczynać się od https://"); return }
        val list=arr(d,formKind); val index=(0 until list.length()).find { list.optJSONObject(it)?.optString("id")==draft.optString("id") }
        if(index==null) list.put(draft) else list.put(index,draft)
        save(d,"SAVE_"+formKind,draft.optString("id"))
        prefs.edit().remove("draft_"+formKind).apply(); openScreen(formReturn)
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
        base("Google Drive"); screen="drive"; nav()
        label(if(prefs.getBoolean("driveConnected",false)) "Połączono: "+prefs.getString("driveEmail","Google Drive") else "Dysk nie jest połączony")
        label("Zdjęcia będą zapisywane w: R3 Gate / obiekt / data. Aplikacja prosi tylko o dostęp do plików używanych przez R3 Gate.")
        button("Połącz Google Drive / autoryzuj") { pendingUpload=""; authorizeDrive() }
        if(prefs.getBoolean("driveConnected",false)) button("Odłącz Google Drive") {
            if(uploading) { toast("Poczekaj na zakończenie przesyłania"); return@button }
            val request=com.google.android.gms.auth.api.identity.RevokeAccessRequest.builder()
                .setScopes(listOf(Scope(driveScope))).build()
            Identity.getAuthorizationClient(this).revokeAccess(request)
                .addOnSuccessListener {
                    driveToken=null; prefs.edit().remove("driveEmail").putBoolean("driveConnected",false).apply()
                    driveSettings()
                }.addOnFailureListener { toast("Nie udało się odłączyć. Spróbuj ponownie w połączeniu z internetem.") }
        }
        label("Jeśli Google odrzuca autoryzację, projekt Google Cloud musi mieć włączone Drive API oraz klienta Android dla poniższego pakietu i podpisu.")
        label("Pakiet: "+packageName+"\nSHA-1: "+signingFingerprint())
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
                    catch (_:Exception) { pendingUpload=""; toast("Nie można otworzyć autoryzacji Google") }
                } else authorized(result)
            }.addOnFailureListener {
                pendingUpload=""
                toast("Google Drive: autoryzacja nieudana. Sprawdź internet, Usługi Google Play i klienta OAuth Android w Google Cloud.")
            }
    }
    private fun authorized(result:com.google.android.gms.auth.api.identity.AuthorizationResult) {
        val token=result.accessToken
        if(token.isNullOrBlank() || driveScope !in result.grantedScopes) {
            pendingUpload=""; toast("Nie udzielono dostępu do Drive"); return
        }
        driveToken=token
        val upload=pendingUpload; pendingUpload=""
        io.execute {
            try {
                val about=driveJson("GET","https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)",token)
                runOnUiThread {
                    prefs.edit().putBoolean("driveConnected",true).putString("driveEmail",about.optJSONObject("user")?.optString("emailAddress") ?: "Google Drive").apply()
                    if(upload.isBlank()) driveSettings() else uploadPhotos(upload,token)
                }
            } catch (_:Exception) { runOnUiThread { toast("Nie udało się połączyć z Drive. Sprawdź, czy Drive API jest włączone."); } }
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
    private fun driveFolder(name:String,parent:String?,token:String):String {
        val safeName=name.replace("\\","\\\\").replace("'","\\'")
        val q="trashed = false and mimeType = 'application/vnd.google-apps.folder' and name = '"+safeName+"' and '"+(parent ?: "root")+"' in parents"
        val found=driveJson("GET","https://www.googleapis.com/drive/v3/files?q="+URLEncoder.encode(q,"UTF-8")+"&fields=files(id)&pageSize=1",token).optJSONArray("files")
        if(found!=null && found.length()>0) return found.getJSONObject(0).getString("id")
        val meta=JSONObject().put("name",name).put("mimeType","application/vnd.google-apps.folder")
        if(parent!=null) meta.put("parents",JSONArray().put(parent))
        return driveJson("POST","https://www.googleapis.com/drive/v3/files?fields=id",token,meta).getString("id")
    }
    private fun uploadPhotos(reference:String,token:String) {
        if(uploading) return
        val parts=reference.split(":"); if(parts.size!=2) return
        val key=parts[0]; val id=parts[1]
        val row=rows(data(),key).find { it.optString("id")==id } ?: return
        if(key!="notes" && row.optString("objectId").isBlank()) { toast("Przed wysłaniem wybierz obiekt w edycji wpisu."); return }
        val obj=objectById(row.optString("objectId"))
        val photoList=(0 until arr(row,"photos").length()).map { arr(row,"photos").getString(it) }
        val accountKey=hash(prefs.getString("driveEmail","") ?: "").take(24)+"|"
        if(!uploadBusy.compareAndSet(false,true)) return
        toast("Przesyłanie zdjęć. Pozostaw aplikację otwartą.")
        io.execute {
            try {
                val rootId=driveFolder("R3 Gate",null,token)
                val objId=driveFolder(if(obj==null) "Bez obiektu" else obj.optString("TAG")+" "+obj.optString("organization"),rootId,token)
                val dayId=driveFolder(dateText(recordDate(row)),objId,token)
                for((index,photo) in photoList.withIndex()) {
                    val already=runOnUiThreadValue { rows(data(),key).find { it.optString("id")==id }?.optJSONObject("driveFiles")?.optString(accountKey+photo).orEmpty() }
                    if(already.isNotBlank()) continue
                    // appProperties makes retries idempotent, even if a response was lost after upload.
                    val uploadKey=id+"-"+hash(photo).take(24)
                    val q="trashed = false and '"+dayId+"' in parents and appProperties has { key='r3Upload' and value='"+uploadKey+"' }"
                    val matches=driveJson("GET","https://www.googleapis.com/drive/v3/files?q="+URLEncoder.encode(q,"UTF-8")+"&fields=files(id)&pageSize=1",token).optJSONArray("files")
                    val remoteId=if(matches!=null && matches.length()>0) matches.getJSONObject(0).getString("id") else {
                        val mime=contentResolver.getType(Uri.parse(photo)) ?: "image/jpeg"
                        val ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
                        val meta=JSONObject().put("name",key+"-"+id+"-"+(index+1)+"."+ext)
                            .put("parents",JSONArray().put(dayId)).put("appProperties",JSONObject().put("r3Upload",uploadKey))
                        val c=URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id").openConnection() as HttpURLConnection
                        val location:String
                        try {
                            c.requestMethod="POST"; c.connectTimeout=20000; c.readTimeout=30000; c.doOutput=true
                            c.setRequestProperty("Authorization","Bearer "+token)
                            c.setRequestProperty("Content-Type","application/json; charset=UTF-8")
                            c.setRequestProperty("X-Upload-Content-Type",mime)
                            c.outputStream.use { it.write(meta.toString().toByteArray(Charsets.UTF_8)) }
                            if(c.responseCode !in 200..299) throw java.io.IOException("Drive HTTP "+c.responseCode)
                            location=c.getHeaderField("Location") ?: throw java.io.IOException("Brak sesji przesyłania")
                        } finally { c.disconnect() }
                        val u=URL(location)
                        if(u.protocol!="https" || !(u.host=="www.googleapis.com" || u.host.endsWith(".googleapis.com"))) throw java.io.IOException("Nieprawidłowa sesja Drive")
                        val upload=u.openConnection() as HttpURLConnection
                        try {
                            upload.requestMethod="PUT"; upload.doOutput=true; upload.connectTimeout=20000; upload.readTimeout=120000
                            upload.setRequestProperty("Authorization","Bearer "+token); upload.setRequestProperty("Content-Type",mime)
                            upload.setChunkedStreamingMode(256*1024)
                            val input=contentResolver.openInputStream(Uri.parse(photo)) ?: throw java.io.IOException("Zdjęcie niedostępne")
                            input.use { source -> upload.outputStream.use { target -> source.copyTo(target) } }
                            if(upload.responseCode !in 200..299) throw java.io.IOException("Drive HTTP "+upload.responseCode)
                            JSONObject(upload.inputStream.bufferedReader().use { it.readText() }).getString("id")
                        } finally { upload.disconnect() }
                    }
                    runOnUiThreadValue {
                        val d=data(); val current=rows(d,key).find { it.optString("id")==id }
                        if(current!=null) {
                            val files=current.optJSONObject("driveFiles") ?: JSONObject().also { current.put("driveFiles",it) }
                            files.put(accountKey+photo,remoteId); current.put("Link Google Drive","https://drive.google.com/drive/folders/"+dayId)
                            save(d,"DRIVE_UPLOAD",id)
                        }
                        toast("Zdjęcie "+(index+1)+" / "+photoList.size+" zapisane")
                    }
                }
                runOnUiThread { uploading=false; toast("Zdjęcia zapisane na Google Drive"); if(!isDestroyed) recordDetails(key,id,detailParent) }
            } catch (_:Exception) {
                runOnUiThread { uploading=false; toast("Przesyłanie przerwane. Sprawdź internet i ponów — zapisane zdjęcia nie będą wysyłane ponownie.") }
            }
        }
    }
    private fun <T> runOnUiThreadValue(block:()->T):T {
        val task=java.util.concurrent.FutureTask<T> { block() }
        runOnUiThread(task)
        return task.get()
    }

}
