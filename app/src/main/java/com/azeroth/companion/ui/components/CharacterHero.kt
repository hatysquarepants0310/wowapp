package com.azeroth.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.azeroth.companion.ui.theme.LocalAccent

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
            .height(168.dp)
            .clip(RoundedCornerShape(Radius.none))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        if (renderUrl != null) {
            // El render va a la derecha y recortado por arriba: así se ve la
            // cara y el torso, que es lo que identifica al personaje, en vez de
            // los pies.
            AsyncImage(
                model = renderUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxSize()
                    .padding(start = 120.dp),
            )
            // Velo por la izquierda para que el texto se lea siempre, aunque el
            // personaje lleve una armadura clarísima.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to MaterialTheme.colorScheme.surface,
                            0.55f to MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }

        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(Spacing.lg)
                .fillMaxWidth(0.62f),
        ) {
            Text(
                name,
                style = MaterialTheme.typography.headlineLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line = listOfNotNull(className, spec).joinToString(" · ")
            if (line.isNotBlank()) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (realm != null) {
                Text(
                    realm,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (itemLevel > 0) {
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        itemLevel.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "ilvl",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
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
