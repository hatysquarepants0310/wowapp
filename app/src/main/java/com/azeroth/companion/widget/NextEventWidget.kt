package com.azeroth.companion.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.azeroth.companion.data.EventsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Widget pequeño (§9.8): próximo evento y su hora de inicio. Muestra hora
 * absoluta (siempre correcta sin ticks); se refresca con cada sync/boot y por
 * updatePeriodMillis del provider.
 */
class NextEventWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun eventsRepository(): EventsRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .eventsRepository()

        val (title, subtitle) = runCatching {
            repository.refreshCalibrations()
            val next = repository.nextOccurrence(Instant.now())
            if (next != null) {
                val (def, occ) = next
                val name = def.name["es_MX"] ?: def.name.values.firstOrNull() ?: def.id
                val local = occ.startsAt.atZone(ZoneId.systemDefault())
                name to "a las ${local.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${def.zone}"
            } else {
                "Sin eventos" to "Catálogo sin próximas ocurrencias"
            }
        }.getOrDefault("Azeroth Companion" to "Abre la app para actualizar")

        provideContent {
            WidgetContent(title, subtitle)
        }
    }

    @Composable
    private fun WidgetContent(title: String, subtitle: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
                .padding(12.dp),
        ) {
            Text(
                "PRÓXIMO EVENTO",
                style = TextStyle(color = ColorProvider(Color(0xFF9BA4B0)), fontSize = 10.sp),
            )
            Text(
                title,
                style = TextStyle(color = ColorProvider(Color(0xFFC9B8FF)), fontSize = 16.sp),
            )
            Text(
                subtitle,
                style = TextStyle(color = ColorProvider(Color(0xFFE6EDF3)), fontSize = 12.sp),
            )
        }
    }
}

class NextEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextEventWidget()
}
