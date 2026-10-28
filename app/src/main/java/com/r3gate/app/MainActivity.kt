package com.r3gate.app

import android.app.*
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
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val clock = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var earningsVisible = false
    private val ticker = Handler(Looper.getMainLooper())
    private var homeVisible = false
    private var smsFrom: LocalDate? = null
    private var smsTo: LocalDate? = null
    private val tick = object : Runnable { override fun run() { if (::root.isInitialized && homeVisible) home(); ticker.postDelayed(this, 60000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(24,33,46)
        window.navigationBarColor = android.graphics.Color.rgb(24,33,46)
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
        val d=data(); if (active(d)!=null) { toast("Dzień pracy już rozpoczęty"); home(); return }
        val t=System.currentTimeMillis(); d.put("active",JSONObject().put("id",UUID.randomUUID().toString()).put("start",t).put("object","").put("stageStart",t).put("stages",JSONArray())); save(d,"START",fmt.format(Date(t))); home()
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
                .put("kind",a.optString("kind","Obiekt")).put("object",a.optString("object","")))
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
        val d=data(); if(active(d)==null) { toast("Najpierw rozpocznij pracę"); return }
        val objs=arr(d,"objects")
        val options=mutableListOf<String>()
        for(i in 0 until objs.length()) {
            val o=objs.optJSONObject(i)?:continue
            options.add(o.optString("TAG")+" — "+o.optString("Adres"))
        }
        if(options.isEmpty()) { toast("Dodaj obiekt w sekcji OBIEKTY"); return }
        AlertDialog.Builder(this).setTitle("Zmień obiekt")
            .setItems(options.toTypedArray()) { _, which ->
                beginStage("Obiekt",options[which])
            }.setNegativeButton("Anuluj",null).show()
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
        base("Raport SMS"); nav()
        button("Data od: ${from.format(df)}") {
            pickReportDate(from) { smsFrom = it; smsReport() }
        }
        button("Data do: ${to.format(df)}") {
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
    private fun base(title:String) {
        homeVisible = false
        val scroll=ScrollView(this); root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(22,24,22,28); setBackgroundColor(0xFFF5F7FA.toInt()) }
        scroll.addView(root); setContentView(scroll)
        val header=TextView(this).apply { text=title; textSize=25f; setTextColor(0xFF18212E.toInt()); setPadding(0,0,0,16); setTypeface(null,android.graphics.Typeface.BOLD) }; root.addView(header)
    }
    private fun label(s:String) { root.addView(TextView(this).apply { text=s; textSize=17f; setTextColor(0xFF253446.toInt()); setPadding(2,8,2,8) }) }
    private fun button(s:String, f:()->Unit) { root.addView(Button(this).apply { text=s; isAllCaps=false; setOnClickListener { f() } },LinearLayout.LayoutParams(-1,-2)) }
    private fun nav() { button("← Menu główne") { home() } }
    private fun duration(ms:Long):String { val m=(ms.coerceAtLeast(0)/60000); return "%02d:%02d".format(m/60,m%60) }
    private fun home() {
        base("R3 Gate")
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
        button("🔗 LINKI DO ZDJĘĆ") { entries("links","Linki Google Drive",listOf("Data (RRRR-MM-DD)","Obiekt", "Link Google Drive")) }
        button("📦 MATERIAŁY") { entries("materials","Materiały",listOf("Materiał","Ilość","Obiekt")) }
        button("📝 NOTATKA") { entries("notes","Notatki",listOf("Tytuł","Treść")) }
        button("🏢 OBIEKTY") { entries("objects","Obiekty",listOf("TAG","Adres","Opis")) }
        button("📊 MÓJ RAPORT") { report() }
        button("📁 LOGI / KOPIA") { logs() }
    }
    private fun entries(key:String,title:String,fields:List<String>) {
        base(title); nav(); val d=data(); val list=arr(d,key)
        button("+ Dodaj") {
            val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,0,20,0) }
            val inputs=fields.map { field -> EditText(this).apply { hint=field; setSingleLine(field!="Treść"); layout.addView(this) } }
            AlertDialog.Builder(this).setTitle("Dodaj: $title").setView(layout).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz") { _, _ ->
                val row=JSONObject().put("id",UUID.randomUUID().toString()).put("created",fmt.format(Date()))
                fields.forEachIndexed { i,f -> row.put(f,inputs[i].text.toString()) }
                val next=data(); arr(next,key).put(row); save(next,"ADD_$key",row.toString()); entries(key,title,fields)
            }.show()
        }
        for(i in list.length()-1 downTo 0) {
            val item=list.optJSONObject(i)?:continue
            label(fields.joinToString(" · ") { "${it}: ${item.optString(it)}" })
            if(key=="links") button("Otwórz link") {
                val url=item.optString("Link Google Drive"); if(url.startsWith("https://")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))) } catch (_:Exception) { toast("Nie można otworzyć linku") }
                } else toast("Link musi zaczynać się od https://")
            }
            button("Usuń wpis") { AlertDialog.Builder(this).setMessage("Usunąć wpis?").setNegativeButton("Anuluj",null).setPositiveButton("Usuń") { _, _ ->
                val next=data(); val old=arr(next,key); val newer=JSONArray(); for(j in 0 until old.length()) if(old.optJSONObject(j)?.optString("id")!=item.optString("id")) newer.put(old.opt(j))
                next.put(key,newer); save(next,"DELETE_$key",item.toString()); entries(key,title,fields)
            }.show() }
        }
    }
    private fun report() {
        base("Mój raport"); nav(); button("📋 GENERUJ RAPORT SMS") { weeklySms() }; val d=data(); val list=arr(d,"shifts")
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
        val start=EditText(this).apply { setText(fmt.format(Date(s.optLong("start")))); hint="Start: yyyy-MM-dd HH:mm"; layout.addView(this) }
        val end=EditText(this).apply { setText(fmt.format(Date(s.optLong("end")))); hint="Koniec: yyyy-MM-dd HH:mm"; layout.addView(this) }
        AlertDialog.Builder(this).setTitle("Edytuj zmianę").setView(layout).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz") { _, _ ->
            try {
                fmt.isLenient=false; val st=fmt.parse(start.text.toString())!!.time; val en=fmt.parse(end.text.toString())!!.time
                if(en<=st) { toast("Koniec musi być po rozpoczęciu"); return@setPositiveButton }
                val old="${fmt.format(Date(s.optLong("start")))} → ${fmt.format(Date(s.optLong("end")))}"
                s.put("start",st).put("end",en); save(d,"EDIT_SHIFT","$old => ${start.text} → ${end.text}"); report()
            } catch (_:Exception) { toast("Format: yyyy-MM-dd HH:mm") }
        }.show()
    }
    private fun logs() {
        base("Logi i kopia zapasowa"); nav()
        button("Eksportuj dziennik JSON") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="application/json"; putExtra(Intent.EXTRA_TITLE,"R3-Gate-backup.json") },100) }
        button("Importuj dziennik JSON") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="application/json" },101) }
        val list=arr(data(),"logs"); for(i in list.length()-1 downTo 0) { val x=list.optJSONObject(i)?:continue; label("${x.optString("at")} | ${x.optString("type")} | ${x.optString("detail")}") }
    }
    @Deprecated("Legacy activity result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,dataIntent:Intent?) {
        super.onActivityResult(requestCode,resultCode,dataIntent)
        if(resultCode!=RESULT_OK) return
        val uri=dataIntent?.data?:return
        try {
            if(requestCode==100) { contentResolver.openOutputStream(uri)?.use { it.write(data().toString(2).toByteArray(Charsets.UTF_8)) }; toast("Kopia zapisana") }
            if(requestCode==101) {
                val content=contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }; val imported=JSONObject(content)
                AlertDialog.Builder(this).setTitle("Przywrócić kopię?").setMessage("Obecne dane zostaną zastąpione.").setNegativeButton("Anuluj",null).setPositiveButton("Importuj") { _, _ ->
                    prefs.edit().putString("data",imported.toString()).apply(); GateWidget.refresh(this); logs()
                }.show()
            }
        } catch (_:Exception) { toast("Błąd pliku JSON") }
    }
}
