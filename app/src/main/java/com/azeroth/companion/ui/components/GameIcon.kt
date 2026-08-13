package com.azeroth.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azeroth.companion.ui.theme.Line
import com.azeroth.companion.ui.theme.SurfaceHigh

/**
 * Un icono del juego, con su marco.
 *
 * World of Warcraft es un lenguaje de iconos: hechizos, objetos, afijos,
 * mazmorras, todo tiene el suyo y el jugador los reconoce antes de leer el
 * nombre. La app no usaba ninguno, y por eso pantallas como la de afijos se
 * leían como párrafos de una enciclopedia en vez de como algo del juego.
 *
 * Los iconos del juego son SIEMPRE cuadrados y con un borde oscuro; el borde
 * existe porque el arte llega a sangre hasta el píxel del borde y sin él los
 * iconos claros se funden con el fondo. Redondearlos, que es lo que haría una
 * plantilla, los convierte en avatares de aplicación.
 */
@Composable
fun GameIcon(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    /**
     * Borde de color: se usa con la calidad del objeto, igual que en el juego.
     * Sin color, un borde neutro que solo separa del fondo.
     */
    border: Color = Line,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(size)
            .background(SurfaceHigh)
            .border(1.dp, border),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
