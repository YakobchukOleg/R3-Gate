package com.r3gate.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Reads only GPS's durable evidence journals, proposes changes, and alerts the user. */
object CoreGpsStageBridge {
    private const val CHANNEL = "core-gps-stage-review"
    const val INTENT_EVENT = "core_gps_stage_event_id"

    /**
     * Remove a superseded lock-screen prompt, without forgetting that the
     * underlying event was previously delivered. Replays stay deduplicated.
     */
    fun dismiss(context: Context) {
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val tag = prefs.getString("core_gps_active_notification", null) ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(tag, 4502)
        prefs.edit().remove("core_gps_active_notification").apply()
    }

    private fun hasPreciseTracking(context: Context, prefs: android.content.SharedPreferences): Boolean =
        GpsWorkdaySession.shouldTrack(prefs) &&
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Recheck GPS coverage at notification time and immediately before approval. */
    fun hasContinuousCoverage(context: Context, eventMs: Long, nowMs: Long): Boolean {
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        if (!hasPreciseTracking(context, prefs) || !prefs.getBoolean("gps_service_active", false))
            return false
        val lastFix = try {
            JSONObject(prefs.getString("gps_last_fix", "{}") ?: "{}").optLong("time")
        } catch (_: Exception) { return false }
        val sessionStart = prefs.getLong("gps_session_start", 0L)
        if (!CoreGpsFixFreshness.isFromActiveSession(sessionStart, eventMs, lastFix, nowMs)) return false
        val events = try { JSONArray(prefs.getString("gps_trip_events", "[]") ?: "[]") }
            catch (_: Exception) { return false }
        val gaps = try { JSONArray(prefs.getString("gps_coverage_gaps", "[]") ?: "[]") }
            catch (_: Exception) { return false }
        val boundaries = (0 until events.length()).map { i ->
            events.optJSONObject(i)?.let {
                CoreGpsCoverageJournal.Boundary(
                    it.optString("event"),
                    if (it.has("time") && !it.isNull("time") && it.opt("time") is Number)
                        it.optLong("time") else null
                )
            }
        }
        val intervals = (0 until gaps.length()).map { i ->
            gaps.optJSONObject(i)?.let {
                CoreGpsCoverageJournal.Interval(
                    if (it.has("start") && !it.isNull("start") && it.opt("start") is Number)
                        it.optLong("start") else null,
                    if (it.has("end") && !it.isNull("end") && it.opt("end") is Number)
                        it.optLong("end") else null
                )
            }
        }
        return CoreGpsCoverageJournal.trustworthy(eventMs, nowMs, boundaries, intervals)
    }

    /**
     * The producer journal is one integrity boundary: a broken row must never
     * be skipped while a different row authorizes a work-stage transition.
     */
    private fun readJournal(context: Context, travel: Boolean = false): List<CoreGpsJournalAdapter.Raw>? {
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val key = if (travel) "gps_trip_events" else "gps_events"
        val journal = try { JSONArray(prefs.getString(key, "[]") ?: "[]") }
            catch (_: Exception) { return null }
        val rows = ArrayList<CoreGpsJournalAdapter.Raw>(journal.length())
        for (i in 0 until journal.length()) {
            val item = journal.optJSONObject(i) ?: return null
            val event = item.opt("event") as? String ?: return null
            val objectId = if (travel && !item.has("objectId")) null else
                item.opt("objectId") as? String ?: return null
            val eventId = when {
                !item.has("eventId") || item.isNull("eventId") -> null
                else -> item.opt("eventId") as? String ?: return null
            }
            val time = CoreGpsJournalIntegrity.exactTime(item.opt("time") as? Number)
                ?: return null
            val metadata = if (listOf("timestampMs", "source", "confirmation", "confidence",
                    "evidenceStartMs", "evidenceEndMs", "tripId").any(item::has)) {
                val timestamp = CoreGpsJournalIntegrity.exactTime(item.opt("timestampMs") as? Number)
                    ?: return null
                val source = item.opt("source") as? String ?: return null
                val confirmation = item.opt("confirmation") as? String ?: return null
                val confidence = CoreGpsProposalInbox.Confidence.entries.find {
                    it.name == item.opt("confidence")
                } ?: return null
                val start = if (item.has("evidenceStartMs"))
                    CoreGpsJournalIntegrity.exactTime(item.opt("evidenceStartMs") as? Number) ?: return null else null
                val end = if (item.has("evidenceEndMs"))
                    CoreGpsJournalIntegrity.exactTime(item.opt("evidenceEndMs") as? Number) ?: return null else null
                val tripId = if (item.has("tripId")) item.opt("tripId") as? String ?: return null else null
                CoreGpsJournalAdapter.Metadata(timestamp, source, confirmation, confidence, start, end, tripId)
            } else null
            rows.add(CoreGpsJournalAdapter.Raw(event, time, objectId, eventId, metadata))
        }
        return if (travel) rows else CoreGpsJournalIntegrity.validated(rows)
    }

    /** Never approve a restored proposal without matching current producer evidence. */
    fun hasJournalEvidence(context: Context, event: CoreGpsProposalInbox.Event): Boolean {
        val rows = readJournal(context) ?: return false
        return CoreGpsProposalEvidenceGuard.matches(event, rows)
    }

    fun refresh(context: Context) {
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val data = try { JSONObject(prefs.getString("data", "{}") ?: "{}") } catch (_: Exception) { return }
        val active = data.optJSONObject("active") ?: run {
            WorkMeterDiagnostics.record(context,"PROPOSAL_BLOCKED_NO_DAY",throttleMs=60_000);dismiss(context); return
        }
        val nowMs = System.currentTimeMillis()
        val shiftStart = active.optLong("start")
        val stageStart = maxOf(shiftStart, active.optLong("stageStart", shiftStart))
        val session = CoreGpsSessionGate.Snapshot(
            hasPreciseTracking(context, prefs), true,
            active.optString("kind", "Obiekt"), shiftStart, stageStart, nowMs
        )
        if (!session.gpsEnabled || !GpsActivitySuggestionRules.workSuggestionsAllowed(session.shiftKind)) {
            dismiss(context)
            return
        }
        val canOfferStage = CoreGpsSessionGate.isEligible(session)
        WorkMeterDiagnostics.record(context,"PROPOSAL_SESSION",JSONObject().put("eligible",canOfferStage)
            .put("stage",active.optString("kind")).put("stageStart",stageStart).put("now",nowMs),30_000)
        val objects = data.optJSONArray("objects") ?: JSONArray()
        val activeIds = ObjectIntegrityRules.assignableIds((0 until objects.length()).mapNotNull { i ->
            objects.optJSONObject(i)?.let {
                ObjectIntegrityRules.ObjectRef(it.optString("id"), it.optBoolean("archived"))
            }
        })
        val shift = CoreGpsStageRules.Shift(true, active.optString("kind", "Obiekt"), active.optString("objectId"))
        val raw = readJournal(context) ?: run { dismiss(context); return }
        val travel = readJournal(context, travel = true).orEmpty()
        val travelIds = travel.mapNotNull { it.eventId }.toSet()
        val incoming = if (canOfferStage)
            CoreGpsJournalAdapter.convert(raw,shift,activeIds,stageStart,nowMs)
                .filter { it.eventId !in travelIds && hasContinuousCoverage(context,it.timestampMs,nowMs) }
        else emptyList()
        WorkMeterDiagnostics.record(context,"PROPOSAL_FILTER",JSONObject().put("raw",raw.size).put("travel",travel.size)
            .put("eligibleIncoming",incoming.size).put("eligibleObjects",activeIds.size),30_000)
        val evidence = raw + travel
        val observations = CoreGpsJournalAdapter.observations(evidence,shiftStart,nowMs).toMutableList()
        val visit = CoreGpsJournalAdapter.visitFromEvidence(evidence,activeIds,shiftStart,nowMs)
        if (visit != null && observations.none { it.eventId == visit.eventId }) observations.add(visit)
        val storage = AndroidCoreGpsProposalStorage(context)
        val updated = CoreGpsProposalStore.import(storage, incoming + observations, activeIds)
        if (!canOfferStage || updated.state !is CoreGpsProposalStore.ReadResult.Success) {
            // Do not leave a previously delivered notification visible when
            // the durable proposal journal is unreadable or cannot be updated.
            dismiss(context)
            return
        }
        // First reconcile the durable inbox and dismiss stale prompts, even if
        // notification permission or a user-disabled channel blocks new alerts.
        val choice = CoreGpsStageReviewQueue.choose(
            updated.state.events, shift, activeIds, stageStart, nowMs
        )
        val event = choice.event ?: run {
            WorkMeterDiagnostics.record(context,"PROPOSAL_QUEUE_NO_CANDIDATE",throttleMs=30_000);dismiss(context); return
        }
        if (!hasContinuousCoverage(context,event.timestampMs,nowMs) ||
            !CoreGpsProposalEvidenceGuard.matches(event, raw)) {
            dismiss(context)
            return
        }
        val notifier = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            WorkMeterDiagnostics.record(context,"PROPOSAL_BLOCKED_NOTIFICATION_PERMISSION",throttleMs=30_000)
            dismiss(context)
            return
        }
        notifier.createNotificationChannel(NotificationChannel(CHANNEL, "WorkMeter · etapy GPS", NotificationManager.IMPORTANCE_HIGH))
        if (!notifier.areNotificationsEnabled() ||
            notifier.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            WorkMeterDiagnostics.record(context,"PROPOSAL_BLOCKED_NOTIFICATION_CHANNEL",throttleMs=30_000)
            dismiss(context)
            return
        }
        val previousFlags = prefs.getStringSet("core_gps_notified", emptySet())?.toSet() ?: emptySet()
        val flagged = CoreGpsNotificationLedger.reconcile(previousFlags, updated.state.events)
        if (flagged != previousFlags)
            prefs.edit().putStringSet("core_gps_notified", flagged).commit()
        if (!CoreGpsNotificationLedger.shouldNotify(event.eventId, flagged, updated.state.events)) {
            // Never leave an older, now-inapplicable notification visible.
            val previous = prefs.getString("core_gps_active_notification", null)
            if (previous != null && previous != event.eventId) dismiss(context)
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(INTENT_EVENT, event.eventId)
            .setData(Uri.parse("workmeter://gps-stage/" + Uri.encode(event.eventId)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (event.kind == CoreGpsProposalInbox.Kind.DEPARTURE)
            "GPS: rozpocząć przejazd?" else "GPS: przyjazd na obiekt"
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText("Dotknij, aby potwierdzić zmianę etapu pracy.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()
        // Stable tag replaces the same event even after a process restart.
        val previous = prefs.getString("core_gps_active_notification", null)
        if (previous != null && previous != event.eventId)
            notifier.cancel(previous, 4502)
        try {
            notifier.notify(event.eventId, 4502, notification)
            WorkMeterDiagnostics.record(context,"GPS_PROPOSAL_NOTIFICATION",JSONObject().put("eventId",event.eventId)
                .put("eventTime",event.timestampMs).put("notifiedAt",System.currentTimeMillis()))
        } catch (_: RuntimeException) {
            // Don't mark as delivered: a later refresh may retry.
            return
        }
        // Don't lose user review state if a notification was blocked.
        prefs.edit().putStringSet("core_gps_notified",
            CoreGpsNotificationLedger.markDelivered(flagged, event.eventId, updated.state.events))
            .putString("core_gps_active_notification", event.eventId).commit()
    }
}
