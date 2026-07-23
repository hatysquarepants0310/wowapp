package com.azeroth.companion.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.azeroth.companion.sync.RescheduleAlarmsWorker

/**
 * Reprograma todas las alarmas tras reinicio, actualización de la app o cambio
 * de zona horaria (§5.3): AlarmManager pierde las alarmas en esos casos.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<RescheduleAlarmsWorker>().build())
        }
    }
}
