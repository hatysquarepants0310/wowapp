package com.azeroth.companion.core.notifications

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.azeroth.companion.MainActivity
import com.azeroth.companion.R

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33
        if (!granted) return

        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        // Acción rápida (§5.4): abrir la checklist previa del evento.
        val tapIntent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_EVENT_ID, eventId)
        val contentPi = PendingIntent.getActivity(
            context, (eventId ?: "").hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(
            context,
            intent.getStringExtra(EXTRA_CHANNEL) ?: NotificationChannels.EVENTS,
        )
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(intent.getStringExtra(EXTRA_BODY))
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify((intent.getStringExtra(EXTRA_TITLE) ?: "").hashCode(), notification)
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_EVENT_ID = "event_id"
    }
}
