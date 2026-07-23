package com.azeroth.companion.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.azeroth.companion.R

/**
 * Canales separados por tipo (§5.3): el usuario puede silenciar categorías
 * completas sin perder las críticas.
 */
object NotificationChannels {
    const val EVENTS = "events"
    const val RESETS = "resets"
    const val VAULT = "vault"
    const val SEASONAL = "seasonal"
    const val PROGRESSION = "progression"

    fun register(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(EVENTS, context.getString(R.string.channel_events), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(RESETS, context.getString(R.string.channel_resets), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(VAULT, context.getString(R.string.channel_vault), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(SEASONAL, context.getString(R.string.channel_seasonal), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(PROGRESSION, context.getString(R.string.channel_progression), NotificationManager.IMPORTANCE_LOW),
        )
        channels.forEach(nm::createNotificationChannel)
    }
}
