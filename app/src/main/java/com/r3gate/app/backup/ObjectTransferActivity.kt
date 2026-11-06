package com.r3gate.app.backup

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.r3gate.app.*
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.Executors

/** Shareable catalog and diagnostic export, separate from destructive whole-journal restore. */
class ObjectTransferActivity:Activity() {
    private val io=Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("r3_gate",0) }
    private lateinit var status:TextView
    private var busy=false
    private var pending=0
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val palette=WorkMeterAppearance.theme(this).palette
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,32,24,24);setBackgroundColor(palette.background) }
        fun text(value:String) { content.addView(TextView(this).apply { text=value;textSize=16f;setTextColor(palette.textPrimary);setPadding(0,12,0,12) }) }
        fun button(label:String,action:()->Unit) { content.addView(workMeterButton(this,label,WorkMeterButtonRole.SECONDARY) { if(!busy) action() }) }
        text("Pliki obiektów i diagnostyka")
        text("Obiekty: TAG, miasto, adres, organizacja, typ i GPS. Bez zdjęć, materiałów, notatek, historii pracy i finansów.")
        button("Eksportuj tylko obiekty z GPS") { choose(1,Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/json";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"WorkMeter-Objects-${LocalDate.now()}.json") }) }
        button("Importuj obiekty z GPS") { choose(2,Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/json";addCategory(Intent.CATEGORY_OPENABLE) }) }
        text("Diagnostyka zawiera lokalizacje, jakość GPS, sygnały ruchu i przyciski. Przekazuj ją tylko świadomie. Zachowujemy do 3 dni; limity i utracone wpisy są oznaczone w pliku.")
        button("Eksportuj diagnostykę JSONL") { choose(3,Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/x-ndjson";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"WorkMeter-Diagnostics-${LocalDate.now()}.jsonl") }) }
        text("Codziennie wieczorem kopia zawiera ZIP oraz osobne pliki obiektów i diagnostyki. Aby zachować pliki po odinstalowaniu, wybierz folder poza aplikacją.")
        button("Wybierz folder codziennych kopii na telefonie") { choose(4,Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) }
        val current=getSharedPreferences(LocalBackupWorker.PREFERENCES,0)
        text("Godzina kopii: około ${current.getInt(EveningBackup.HOUR,21)}:00 · Europe/Warsaw. Android może opóźnić wykonanie. Harmonogram i Google Drive zmienisz w ustawieniach kopii.")
        status=TextView(this).apply { setTextColor(palette.textPrimary);textSize=16f };content.addView(status)
        button("← Wstecz") { finish() }
        val scroll=ScrollView(this).apply { addView(content) };setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view,insets -> view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets }
        if(savedInstanceState!=null) status.text="Wybór pliku został przerwany. Ponów operację."
    }
    private fun choose(code:Int,intent:Intent) { pending=code;startActivityForResult(intent,code) }
    private fun runTask(task:()->String) {
        busy=true;status.text="Przetwarzanie…"
        io.execute { val result=runCatching(task).getOrElse { "Błąd: "+(it.message ?: "Nie zapisano danych") }
            runOnUiThread { if(!isDestroyed) { busy=false;status.text=result } }
        }
    }
    @Deprecated("Legacy result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK || data?.data==null || requestCode!=pending) return
        pending=0;val uri=data.data!!
        when(requestCode) {
            1 -> runTask {
                val locations=prefs.all.filterKeys { it.startsWith("gps_object_") }.mapNotNull { (k,v) -> (v as? String)?.let { k.removePrefix("gps_object_") to it } }.toMap()
                val catalog=ObjectCatalog.export(prefs.getString("data","{}")!!,locations)
                BackupFilePicker.write(contentResolver,uri) { it.write(catalog.toByteArray()) };"Zapisano plik obiektów."
            }
            2 -> runTask {
                val incoming=contentResolver.openInputStream(uri)?.use { source ->
                    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) { val n=source.read(buffer);if(n<0) break;require(out.size()+n<=ObjectCatalog.MAX_BYTES);out.write(buffer,0,n) };out.toString("UTF-8")
                } ?: error("Nie odczytano pliku")
                val before=prefs.getString("data","{}")!!;val preview=ObjectCatalog.merge(before,incoming)
                runOnUiThread { if(!isDestroyed) AlertDialog.Builder(this)
                    .setTitle("Import obiektów")
                    .setMessage("Nowe: ${preview.added}. Istniejące pominięte: ${preview.skipped}. Obecne obiekty i historia pozostaną zachowane.")
                    .setNegativeButton("Anuluj",null).setPositiveButton("Importuj") { _,_ -> runTask {
                        check(prefs.getString("data","{}")==before) { "Dane zmieniły się. Ponów podgląd." }
                        val editor=prefs.edit().putString("data",preview.journal)
                        preview.locations.forEach { (id,gps) -> editor.putString("gps_object_"+id,gps) }
                        check(editor.commit());GateWidget.refresh(this);"Dodano ${preview.added} obiektów."
                    } }.show() }
                "Sprawdź podgląd importu."
            }
            3 -> runTask { val report=WorkMeterDiagnostics.snapshot(this);BackupFilePicker.write(contentResolver,uri) { it.write(report.toByteArray()) };"Zapisano diagnostykę." }
            4 -> runTask {
                contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                check(getSharedPreferences(LocalBackupWorker.PREFERENCES,0).edit().putString(BackupDirectoryExport.KEY,uri.toString()).commit())
                LocalBackups.createNow(this);"Folder zapisany. Utworzono pierwszą pełną kopię i osobne pliki."
            }
        }
    }
    override fun onDestroy() { io.shutdown();super.onDestroy() }
}
