package com.azeroth.companion.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val EDGE = 1.dp

/**
 * Superficie de la app. Por defecto es un panel con radio amplio: se acabó
 * la pila de rectángulos vivos que se leía como una plantilla de 2022.
 * Las barras de chrome (arriba/abajo) pasan `corner = 0.dp`.
 */
fun Modifier.metal(
    base: Color,
    corner: Dp = 20.dp,
    seated: Boolean = true,
): Modifier {
    val shape = RoundedCornerShape(corner)
    val fill = Brush.verticalGradient(
        listOf(base.lighten(0.10f), base, base.darken(0.12f)),
    )
    return this
        .then(if (seated && corner < 1.dp) Modifier.seated() else Modifier)
        .clip(shape)
        .background(fill, shape)
        .then(
            if (corner < 1.dp) {
                Modifier.drawWithContent {
                    drawContent()
                    val edge = EDGE.toPx()
                    drawRect(Color.White.copy(alpha = 0.16f), size = Size(size.width, edge))
                    drawRect(Color.White.copy(alpha = 0.07f), size = Size(edge, size.height))
                    drawRect(
                        Color.Black.copy(alpha = 0.55f),
                        topLeft = Offset(0f, size.height - edge),
                        size = Size(size.width, edge),
                    )
                    drawRect(
                        Color.Black.copy(alpha = 0.35f),
                        topLeft = Offset(size.width - edge, 0f),
                        size = Size(edge, size.height),
                    )
                }
            } else {
                Modifier
            },
        )
}

fun Modifier.seated(color: Color = Color.Black.copy(alpha = 0.45f), depth: Dp = 2.dp): Modifier =
    this.drawBehind {
        drawRect(
            color,
            topLeft = Offset(0f, depth.toPx()),
            size = Size(size.width, size.height),
        )
    }

fun Modifier.inset(base: Color, corner: Dp = 10.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(base.darken(0.18f), shape)
}

internal fun Color.lighten(amount: Float): Color = Color(
    red = (red + amount).coerceAtMost(1f),
    green = (green + amount).coerceAtMost(1f),
    blue = (blue + amount).coerceAtMost(1f),
    alpha = alpha,
)

internal fun Color.darken(amount: Float): Color = Color(
    red = (red - amount).coerceAtLeast(0f),
    green = (green - amount).coerceAtLeast(0f),
    blue = (blue - amount).coerceAtLeast(0f),
    alpha = alpha,
)
