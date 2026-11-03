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

    /** Recheck GPS coverage at notification time and immediately before approval. */
    fun hasContinuousCoverage(context: Context, eventMs: Long, nowMs: Long): Boolean {
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("gps_enabled", false)) return false
        val lastFix = try {
            JSONObject(prefs.getString("gps_last_fix", "{}") ?: "{}").optLong("time")
        } catch (_: Exception) { return false }
        if (!CoreGpsFixFreshness.isFresh(eventMs, lastFix, nowMs)) return false
        val events = try { JSONArray(prefs.getString("gps_trip_events", "[]") ?: "[]") }
            catch (_: Exception) { return false }
        val gaps = try { JSONArray(prefs.getString("gps_coverage_gaps", "[]") ?: "[]") }
            catch (_: Exception) { return false }
        val outages = (0 until events.length()).mapNotNull { i ->
            events.optJSONObject(i)?.takeIf { it.optString("event") == "GPS_GAP" }
                ?.optLong("time")
        }
        val intervals = (0 until gaps.length()).mapNotNull { i ->
            gaps.optJSONObject(i)?.let {
                CoreGpsCoverageRules.Gap(it.optLong("start"), it.optLong("end"))
            }
        }
        return CoreGpsCoverageRules.trustworthy(eventMs, nowMs, outages, intervals)
    }

    fun refresh(context: Context) {
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val data = try { JSONObject(prefs.getString("data", "{}") ?: "{}") } catch (_: Exception) { return }
        val active = data.optJSONObject("active") ?: run { dismiss(context); return }
        val nowMs = System.currentTimeMillis()
        val shiftStart = active.optLong("start")
        val stageStart = maxOf(shiftStart, active.optLong("stageStart", shiftStart))
        val session = CoreGpsSessionGate.Snapshot(
            prefs.getBoolean("gps_enabled", false), true,
            active.optString("kind", "Obiekt"), shiftStart, stageStart, nowMs
        )
        if (!CoreGpsSessionGate.isEligible(session)) {
            dismiss(context)
            return
        }
        val objects = data.optJSONArray("objects") ?: JSONArray()
        val activeIds = (0 until objects.length()).mapNotNull { i ->
            objects.optJSONObject(i)?.takeIf { !it.optBoolean("archived") }
                ?.optString("id")?.takeIf { it.isNotBlank() }
        }.toSet()
        val shift = CoreGpsStageRules.Shift(true, active.optString("kind", "Obiekt"), active.optString("objectId"))
        val journal = try { JSONArray(prefs.getString("gps_events", "[]") ?: "[]") }
            catch (_: Exception) { JSONArray() }
        val raw = (0 until journal.length()).mapNotNull { i ->
            journal.optJSONObject(i)?.let { item ->
                CoreGpsJournalAdapter.Raw(
                    item.optString("event"), item.optLong("time"),
                    item.optString("objectId").takeIf { it.isNotBlank() },
                    item.optString("eventId").takeIf { it.isNotBlank() }
                )
            }
        }
        val incoming = CoreGpsJournalAdapter.convert(raw,shift,activeIds,stageStart,nowMs)
            .filter { hasContinuousCoverage(context,it.timestampMs,nowMs) }
        val storage = AndroidCoreGpsProposalStorage(context)
        val updated = CoreGpsProposalStore.import(storage, incoming, activeIds)
        if (updated.state !is CoreGpsProposalStore.ReadResult.Success) return
        // First reconcile the durable inbox and dismiss stale prompts, even if
        // notification permission or a user-disabled channel blocks new alerts.
        val choice = CoreGpsStageReviewQueue.choose(
            updated.state.events, shift, activeIds, stageStart, nowMs
        )
        val event = choice.event ?: run { dismiss(context); return }
        if (!hasContinuousCoverage(context,event.timestampMs,nowMs)) {
            dismiss(context)
            return
        }
        val notifier = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            dismiss(context)
            return
        }
        notifier.createNotificationChannel(NotificationChannel(CHANNEL, "WorkMeter · etapy GPS", NotificationManager.IMPORTANCE_HIGH))
        if (!notifier.areNotificationsEnabled() ||
            notifier.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
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
            .build()
        // Stable tag replaces the same event even after a process restart.
        val previous = prefs.getString("core_gps_active_notification", null)
        if (previous != null && previous != event.eventId)
            notifier.cancel(previous, 4502)
        try {
            notifier.notify(event.eventId, 4502, notification)
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
