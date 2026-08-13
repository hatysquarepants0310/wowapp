package com.azeroth.companion.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.ui.theme.Positive
import com.azeroth.companion.ui.theme.TextLow
import com.azeroth.companion.ui.theme.Warning
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * Badge de confianza (§2.4): todo dato inferido se marca visualmente. Antes era
 * un rectángulo de color sólido que gritaba más que el propio dato; ahora es
 * una etiqueta discreta, porque su papel es matizar, no competir.
 */
@Composable
fun ConfidenceBadge(confidence: Confidence, modifier: Modifier = Modifier) {
    val (label, color) = when (confidence) {
        Confidence.CONFIRMED -> "Confirmado" to Positive
        Confidence.ESTIMATED -> "Estimado" to Warning
        Confidence.PREDICTED -> "Predicho" to TextLow
    }
    Pill(label, modifier, color = color)
}

/**
 * Cuenta regresiva que se refresca cada segundo (§9.1).
 *
 * Ojo con [whenPast]: antes daba por hecho que un objetivo ya pasado significaba
 * "en curso", y eso era una suposición del componente de dibujo sobre algo que
 * solo sabe quien le pasa la fecha. En la lista de eventos hacía que los que ya
 * habían TERMINADO se rotularan como activos. Ahora lo decide quien llama.
 */
@Composable
fun CountdownText(
    target: Instant,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    whenPast: String = "en curso",
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(target) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }
    val remaining = Duration.between(now, target)
    val text = if (remaining.isNegative) whenPast else formatDuration(remaining)
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

/**
 * Bloque de sección.
 *
 * Ya no dibuja una tarjeta: el título va como etiqueta y el contenido respira
 * sobre el fondo de la pantalla. Apilar diez tarjetas idénticas hacía que todo
 * pesara lo mismo y no hubiera dónde mirar primero; con la etiqueta y el
 * espacio, la jerarquía la marca el contenido. Quien necesite fondo propio usa
 * [Panel] explícitamente.
 */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title)
        content()
        Spacer(Modifier.height(Spacing.sm))
    }
}
