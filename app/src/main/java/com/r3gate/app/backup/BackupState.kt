package com.r3gate.app.backup

/** Text states shared by backup settings and any contextual error indicator. */
enum class BackupState(val label: String, val needsAttention: Boolean = false) {
    LOCAL_SAVED("Zapisano lokalnie"),
    WAITING_TO_SEND("Oczekuje na wysłanie"),
    CREATING("Trwa tworzenie kopii"),
    SENDING("Wysyłanie do Google Drive"),
    DRIVE_READY("Kopia w Google Drive gotowa"),
    FAILED("Błąd kopii zapasowej", true),
    ACTION_REQUIRED("Wymagane działanie", true),
    CANCELLED("Anulowano"),
    SCHEDULED("Zaplanowano kopię");

    internal enum class Job { WAITING, RUNNING, FINISHED, FAILED, CANCELLED }

    internal companion object {
        fun resolve(job: Job, drive: Boolean, periodic: Boolean, attempts: Int,
            sending: Boolean, actionRequired: Boolean): BackupState = when (job) {
            Job.WAITING -> when {
                periodic && attempts == 0 -> SCHEDULED
                drive -> WAITING_TO_SEND
                else -> SCHEDULED
            }
            Job.RUNNING -> if (drive && sending) SENDING else CREATING
            Job.FINISHED -> if (drive) DRIVE_READY else LOCAL_SAVED
            Job.FAILED -> if (actionRequired) ACTION_REQUIRED else FAILED
            Job.CANCELLED -> CANCELLED
        }
    }
}
