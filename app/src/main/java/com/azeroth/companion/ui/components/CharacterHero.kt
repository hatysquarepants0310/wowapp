package com.azeroth.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azeroth.companion.ui.theme.Base
import com.azeroth.companion.ui.theme.BigNumberStyle
import com.azeroth.companion.ui.theme.LocalAccent
import com.azeroth.companion.ui.theme.TextHigh
import com.azeroth.companion.ui.theme.TextMid
import com.azeroth.companion.ui.theme.readableOn

/** Alto del banner. Ocupa casi un tercio de la pantalla, y debe hacerlo. */
private val HERO_HEIGHT = 300.dp

/**
 * El retrato del personaje, que preside la app.
 *
 * Es la pieza que da identidad a todo lo demás. Blizzard publica un render de
 * cuerpo entero de CADA personaje (`character-media` → `main-raw`), con fondo
 * transparente: no es un icono genérico, es tu gnomo guerrero con su equipo
 * puesto. Ninguna plantilla de dashboard tiene eso, y por eso la interfaz que
 * lo rodea puede permitirse estar callada.
 *
 * El degradado del fondo sale del color de la CLASE, así que un druida ve su
 * retrato sobre naranja y un brujo sobre morado, sin tocar ningún ajuste.
 */
@Composable
fun CharacterHero(
    name: String,
    modifier: Modifier = Modifier,
    realm: String? = null,
    className: String? = null,
    spec: String? = null,
    itemLevel: Int = 0,
    renderUrl: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    Box(
        modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .background(Base)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        // Halo del color de la clase detrás del personaje. Es lo que hace que el
        // render se despegue del fondo negro en vez de flotar recortado, y de
        // paso tiñe la pantalla entera del color de TU clase sin pintar nada.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.75f)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    ),
                ),
        )

        if (renderUrl != null) {
            // ENTERO y grande, apoyado en el borde inferior.
            //
            // Antes iba recortado dentro de una caja de 168dp a la derecha, y en
            // un móvil real se veía del tamaño de un sello. Es el mejor recurso
            // que tiene la app —Blizzard publica un render de cuerpo entero de
            // TU personaje con su equipo puesto, con fondo transparente— y
            // estaba desperdiciado. `Fit` en vez de `Crop` para no cortarle la
            // cabeza ni los pies.
            AsyncImage(
                model = renderUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxHeight(0.96f)
                    .fillMaxWidth(0.60f),
            )
        }

        // Velo por la izquierda: el nombre tiene que leerse aunque el personaje
        // lleve una armadura clarísima justo detrás.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Base,
                        0.42f to Base.copy(alpha = 0.82f),
                        0.72f to Color.Transparent,
                    ),
                ),
        )
        // Y un fundido a negro abajo, para que la primera tarjeta de la pantalla
        // salga de la imagen en vez de chocar contra ella.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Base)),
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.lg)
                .fillMaxWidth(0.66f),
        ) {
            if (realm != null) {
                Text(
                    realm.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.readableOn(Base),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                name,
                style = MaterialTheme.typography.displaySmall,
                color = TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line = listOfNotNull(spec, className).joinToString(" ")
            if (line.isNotBlank()) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (itemLevel > 0) {
                Spacer(Modifier.height(Spacing.md))
                // El ilvl en una placa, no suelto: es la cifra que el jugador
                // mira primero y con la que se compara con los demás.
                Row(
                    Modifier
                        .background(accent.copy(alpha = 0.16f))
                        .border(Spacing.hairline, accent.copy(alpha = 0.55f))
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        itemLevel.toString(),
                        style = BigNumberStyle,
                        color = TextHigh,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ILVL",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent.readableOn(Base),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.height(Spacing.sm))
                trailing()
            }
        }
    }
}

/** Cara del personaje para listas: el roster deja de ser texto plano. */
@Composable
fun CharacterAvatar(
    avatarUrl: String?,
    className: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val accent = com.azeroth.companion.ui.theme.ClassColors.forClassName(className)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}
