package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test
class WidgetCloudEvidenceTest {
    private val at="2026-11-06T12:00:00Z"
    private val now=java.time.Instant.parse("2026-11-06T13:00:00Z").toEpochMilli()
    @Test fun matchingVerifiedAccountCanShowCheckmark() { assertTrue(WidgetCloudEvidence.verified(true,"me@test","ME@test",at,now)) }
    @Test fun merelyConnectedAccountIsNotVerified() { assertFalse(WidgetCloudEvidence.verified(true,"me@test","me@test",null,now)) }
    @Test fun differentAccountCannotInheritCloudCheckmark() { assertFalse(WidgetCloudEvidence.verified(true,"other@test","me@test",at,now)) }
    @Test fun disconnectedAccountCannotShowVerifiedStatus() { assertFalse(WidgetCloudEvidence.verified(false,"me@test","me@test",at,now)) }
    @Test fun malformedOrFutureVerificationIsRejected() {
        assertFalse(WidgetCloudEvidence.verified(true,"me@test","me@test","bad",now))
        assertFalse(WidgetCloudEvidence.verified(true,"me@test","me@test",at,1))
    }
}
