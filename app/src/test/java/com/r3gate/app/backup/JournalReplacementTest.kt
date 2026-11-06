package com.r3gate.app.backup
import org.junit.Assert.*
import org.junit.Test

class JournalReplacementTest {
    private val old="old journal"
    private val incoming="restored journal"
    private val edited="new manual edit"
    private fun failure(action: () -> Unit) {
        try { action(); fail("Failure was reported as success") } catch (_:IllegalStateException) { }
    }
    @Test fun successfulWriteDoesNotReadRecoveryOrWriteTwice() {
        var current=old; var writes=0
        JournalReplacement.commit(old,incoming,{current},{current=it;writes++;true},{error("No rollback needed")})
        assertEquals(incoming,current); assertEquals(1,writes)
    }
    @Test fun failedWriteLeavingOriginalDoesNotRewriteIt() {
        var writes=0
        failure { JournalReplacement.commit(old,incoming,{old},{writes++;false},{error("No rollback needed")}) }
        assertEquals(1,writes)
    }
    @Test fun failedCommitWithUpdatedMemoryRestoresVerifiedOriginal() {
        var current=old; var writes=0
        failure { JournalReplacement.commit(old,incoming,{current},{current=it;++writes>1},{old}) }
        assertEquals(old,current); assertEquals(2,writes)
    }
    @Test fun concurrentEditAfterSuccessfulCommitIsPreserved() {
        var current=old; var writes=0
        failure { JournalReplacement.commit(old,incoming,{current},{current=edited;writes++;true},{old}) }
        assertEquals(edited,current); assertEquals(1,writes)
    }
    @Test fun concurrentEditAfterFailedCommitIsPreserved() {
        var current=old; var writes=0
        failure { JournalReplacement.commit(old,incoming,{current},{current=edited;writes++;false},{old}) }
        assertEquals(edited,current); assertEquals(1,writes)
    }
    @Test fun editBeforeCommitRejectsTheReplacement() {
        var writes=0
        failure { JournalReplacement.commit(old,incoming,{edited},{writes++;true},{old}) }
        assertEquals(0,writes)
    }
    @Test fun changedRecoveryIsNeverWritten() {
        var current=old; var writes=0
        failure { JournalReplacement.commit(old,incoming,{current},{current=it;writes++;false},{"different checkpoint"}) }
        assertEquals(incoming,current); assertEquals(1,writes)
    }
    @Test fun failedRollbackIsNotReportedAsCompleted() {
        var current=old; var writes=0
        failure { JournalReplacement.commit(old,incoming,{current},{current=it;writes++;false},{old}) }
        assertEquals(2,writes)
    }
    @Test fun editDuringRecoveryVerificationIsPreserved() {
        var current=old; var writes=0
        failure { JournalReplacement.commit(old,incoming,{current},{current=it;writes++;false},{current=edited;old}) }
        assertEquals(edited,current); assertEquals(1,writes)
    }
    @Test fun damagedRecoveryDoesNotCauseAnUnverifiedWrite() {
        var current=old; var writes=0
        failure { JournalReplacement.commit(old,incoming,{current},{current=it;writes++;false},{error("Checksum mismatch")}) }
        assertEquals(incoming,current); assertEquals(1,writes)
    }
    @Test fun exceptionBeforeMemoryChangesDoesNotRewriteOriginal() {
        var writes=0
        failure { JournalReplacement.commit(old,incoming,{old},{writes++;throw java.io.IOException("Disk failed")},{old}) }
        assertEquals(1,writes)
    }
    @Test fun exceptionAfterMemoryChangesStillRollsBackToVerifiedOriginal() {
        var current=old; var writes=0
        failure { JournalReplacement.commit(old,incoming,{current},{ value ->
            current=value
            if(++writes==1) throw java.io.IOException("Disk failed")
            true
        },{old}) }
        assertEquals(old,current); assertEquals(2,writes)
    }

}
