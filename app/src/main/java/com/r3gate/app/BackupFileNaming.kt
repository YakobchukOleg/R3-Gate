package com.r3gate.app

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared filenames for local and Drive backups, including the export timestamp. */
object BackupFileNaming {
    private val formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss",Locale.US)
    fun fileName(at:LocalDateTime,version:String="2.4"):String {
        require(version.matches(Regex("[0-9]+(\\.[0-9]+)*")))
        return "WorkMeter-SOLO-v${version}-backup-${at.format(formatter)}.json"
    }
}
