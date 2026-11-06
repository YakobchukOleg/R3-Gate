package com.r3gate.app

import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/** Bounded JSONL files, independent of active GPS preferences and journal restores. */
internal class DiagnosticLogStore(private val root:File, private val maxBytes:Int=8*1024*1024, private val days:Int=3) {
    @Synchronized fun append(now:Long,line:String) {
        require(now>0 && !line.contains('\n') && line.toByteArray().size<=16_384)
        check(root.isDirectory || root.mkdirs())
        val day=Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().toString()
        val file=File(root,"$day.jsonl")
        if(file.length()+line.toByteArray().size+1>maxBytes) {
            val all=file.readLines(); val tail=all.takeLast(all.size/2)
            file.writeText("{\"kind\":\"DIAGNOSTICS_TRUNCATED\"}\n"+tail.joinToString("\n",postfix="\n"))
        }
        file.appendText(line+"\n")
        daily().filter { it.name<day }.dropLast((days-1).coerceAtLeast(0)).forEach { it.delete() }
    }
    private fun daily()=root.listFiles().orEmpty().filter { it.name.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}\\.jsonl")) }.sortedBy { it.name }
    @Synchronized fun snapshot():String=buildString {
        File(root,"imported.jsonl").takeIf { it.isFile }?.let { append(it.readText()); if(!endsWith("\n")) append('\n') }
        daily().forEach { append(it.readText()) }
    }.let { content ->
        val bytes=content.toByteArray()
        if(bytes.size<=16*1024*1024) content else "{\"kind\":\"DIAGNOSTICS_TRUNCATED\"}\n"+
            String(bytes.copyOfRange(bytes.size-16*1024*1024,bytes.size),Charsets.UTF_8).substringAfter('\n',"")
    }
    @Synchronized fun importHistory(bytes:ByteArray) {
        require(bytes.size<=20*1024*1024)
        check(root.isDirectory || root.mkdirs())
        val tmp=File(root,"imported.tmp"); tmp.writeBytes(bytes)
        java.nio.file.Files.move(tmp.toPath(),File(root,"imported.jsonl").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
