package com.r3gate.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Reads only GPS's durable evidence journals, proposes changes, and alerts the user. */
object CoreGpsStageBridge {
    private const val CHANNEL = "core-gps-stage-review"
    const val INTENT_EVENT = "core_gps_stage_event_id"

    fun refresh(context: Context) {
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val data = try { JSONObject(prefs.getString("data", "{}") ?: "{}") } catch (_: Exception) { return }
        val active = data.optJSONObject("active") ?: return
        val nowMs = System.currentTimeMillis()
        val shiftStart = active.optLong("start")
        val stageStart = maxOf(shiftStart, active.optLong("stageStart", shiftStart))
        val objects = data.optJSONArray("objects") ?: JSONArray()
        val activeIds = (0 until objects.length()).mapNotNull { i ->
            objects.optJSONObject(i)?.takeIf { !it.optBoolean("archived") }
                ?.optString("id")?.takeIf { it.isNotBlank() }
        }.toSet()
        val shift = CoreGpsStageRules.Shift(true, active.optString("kind", "Obiekt"), active.optString("objectId"))
        val incoming = mutableListOf<CoreGpsProposalInbox.Event>()
        val journal = try { JSONArray(prefs.getString("gps_events", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        for (i in 0 until journal.length()) {
            val item = journal.optJSONObject(i) ?: continue
            val kind = when (item.optString("event")) {
                "ARRIVAL" -> CoreGpsProposalInbox.Kind.ARRIVAL
                "DEPARTURE" -> CoreGpsProposalInbox.Kind.DEPARTURE
                else -> continue
            }
            val id = item.optString("objectId").takeIf { it.isNotBlank() } ?: continue
            val whenMs = item.optLong("time")
            if (whenMs < stageStart || whenMs <= 0) continue
            val eventId = item.optString("eventId").ifBlank {
                "gps-v1:${kind.name.length}:${kind.name}:$whenMs:${id.length}:$id"
            }
            val candidate = CoreGpsProposalInbox.Event(eventId, kind, whenMs, id,
                confidence = CoreGpsProposalInbox.Confidence.HIGH)
            if (CoreGpsStageRules.shouldNotify(candidate, shift, activeIds, stageStart, nowMs)) incoming.add(candidate)
        }
        if (incoming.isEmpty()) return
        val storage = AndroidCoreGpsProposalStorage(context)
        val updated = CoreGpsProposalStore.import(storage, incoming, activeIds)
        if (updated.state !is CoreGpsProposalStore.ReadResult.Success) return
        val notifier = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        notifier.createNotificationChannel(NotificationChannel(CHANNEL, "WorkMeter · etapy GPS", NotificationManager.IMPORTANCE_HIGH))
        // Only the newest unambiguous suggestion can be shown for this stage.
        val choice = CoreGpsStageReviewQueue.choose(
            updated.state.events, shift, activeIds, stageStart, nowMs
        )
        val event = choice.event ?: return
        val flagged = prefs.getStringSet("core_gps_notified", emptySet())?.toSet() ?: emptySet()
        if (event.eventId in flagged) return
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(INTENT_EVENT, event.eventId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val unique = event.eventId.hashCode()
            val pending = PendingIntent.getActivity(context, unique, intent,
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
            notifier.notify(unique, notification)
        // Durable dedup even if the app is restarted while the screen is locked.
        prefs.edit().putStringSet("core_gps_notified", flagged + event.eventId).commit()
    }
}
