package com.azeroth.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.azeroth.companion.core.model.Confidence
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** Badge de confianza (§2.4): todo dato inferido se marca visualmente. */
@Composable
fun ConfidenceBadge(confidence: Confidence, modifier: Modifier = Modifier) {
    val (label, color) = when (confidence) {
        Confidence.CONFIRMED -> "CONFIRMADO" to Color(0xFF2EA043)
        Confidence.ESTIMATED -> "ESTIMADO" to Color(0xFFF0B429)
        Confidence.PREDICTED -> "PREDICHO" to Color(0xFF8B949E)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.Black,
        modifier = modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Cuenta regresiva que se refresca cada segundo (§9.1). */
@Composable
fun CountdownText(target: Instant, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displaySmall) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(target) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }
    val remaining = Duration.between(now, target)
    val text = if (remaining.isNegative) "en curso" else formatDuration(remaining)
    Text(text = text, style = style, modifier = modifier)
}

fun formatDuration(d: Duration): String {
    val days = d.toDays()
    val h = d.toHours() % 24
    val m = d.toMinutes() % 60
    val s = d.seconds % 60
    return when {
        days > 0 -> "%dd %02dh %02dm".format(days, h, m)
        h > 0 -> "%dh %02dm %02ds".format(h, m, s)
        else -> "%02dm %02ds".format(m, s)
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
