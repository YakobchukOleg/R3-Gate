package com.r3gate.app.backup

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

internal object BackupCompanions {
    const val DIAGNOSTICS="diagnostics/workmeter-diagnostics.jsonl"
    const val OBJECTS="objects/workmeter-objects.json"
    val paths=listOf(DIAGNOSTICS,OBJECTS)
    fun read(open:()->InputStream,path:String):ByteArray? {
        require(path in paths)
        val item=open().use { WorkMeterArchive.inspect(it) }.files.singleOrNull { it.path==path } ?: return null
        require(item.size<=20*1024*1024)
        val bytes=open().use { input -> ZipInputStream(input).use { zip ->
            var found:ByteArray?=null
            while(true) {
                val entry=zip.nextEntry ?: break
                if(entry.name==path) { val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) { val n=zip.read(buffer); if(n<0) break; require(out.size()+n<=20*1024*1024);out.write(buffer,0,n) }
                    found=out.toByteArray();break
                }
            }
            requireNotNull(found)
        } }
        require(bytes.size.toLong()==item.size)
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(hash==item.sha256) { "Załącznik kopii zmienił się" }
        return bytes
    }
    fun suffix(path:String)=if(path==OBJECTS) ".objects.json" else ".diagnostics.jsonl"
    fun file(archive:File,path:String)=File(archive.parentFile,archive.name.removeSuffix(".wmb.zip")+suffix(path))
    fun publish(archive:File,snapshot:Map<String,ByteArray>?=null) {
        for(path in paths) {
            if(snapshot==null && file(archive,path).isFile) continue
            val bytes=if(snapshot!=null) snapshot[path] else read({ archive.inputStream() },path)
            if(bytes==null) continue
            val destination=file(archive,path);val temp=File(destination.path+".tmp")
            try { temp.writeBytes(bytes);java.nio.file.Files.move(temp.toPath(),destination.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            finally { temp.delete() }
        }
        archive.parentFile?.listFiles().orEmpty().filter { f ->
            val suffix=paths.map(::suffix).firstOrNull { f.name.endsWith(it) }
            val archiveName=suffix?.let { f.name.removeSuffix(it)+".wmb.zip" }
            archiveName!=null && LocalBackupStore.isBackupName(archiveName) && !File(f.parentFile,archiveName).exists()
        }.forEach { it.delete() }
    }
}
