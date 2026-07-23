package com.azeroth.companion.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarmas exactas para los avisos de evento (§5.3). Si el usuario deniega
 * SCHEDULE_EXACT_ALARM, degrada a setWindow y [isExact] queda en false para
 * que la UI lo avise — degradación explícita, nunca silenciosa.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val isExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(id: NotificationId, at: Instant, title: String, body: String, channel: String) {
        if (!at.isAfter(Instant.now())) return
        val pi = pendingIntent(id, title, body, channel)
        if (isExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        } else {
            val window = Duration.ofMinutes(2).toMillis()
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli() - window, window, pi)
        }
    }

    fun cancel(id: NotificationId) {
        alarmManager.cancel(pendingIntent(id, "", "", NotificationChannels.EVENTS))
    }

    private fun pendingIntent(id: NotificationId, title: String, body: String, channel: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_TITLE, title)
            .putExtra(AlarmReceiver.EXTRA_BODY, body)
            .putExtra(AlarmReceiver.EXTRA_CHANNEL, channel)
            .putExtra(AlarmReceiver.EXTRA_EVENT_ID, id.eventId)
        return PendingIntent.getBroadcast(
            context,
            id.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Identidad estable de cada alarma: tipo + evento → requestCode determinista. */
data class NotificationId(val type: String, val eventId: String) {
    val requestCode: Int get() = (type + eventId).hashCode()
}
