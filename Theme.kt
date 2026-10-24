package com.ayham.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0B0714)
val Panel = Color(0xFF160E2B)
val Purple = Color(0xFF8B5CF6)
val Neon = Color(0xFFA855F7)
val Lilac = Color(0xFFC4B5FD)
val Magenta = Color(0xFFD946EF)
val Cyan = Color(0xFF22D3EE)
val Muted = Color(0xFFA29BB8)

object Lang {
    var ar by mutableStateOf(true)
}

fun tr(a: String, e: String): String = if (Lang.ar) a else e

@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Purple.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(size.width * 0.12f, -size.height * 0.05f),
                        radius = size.width * 0.65f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Magenta.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 1.02f),
                        radius = size.width * 0.55f
                    )
                )
            }
    ) { content() }
}

/** Focusable container with neon glow + scale when focused (D-pad friendly). */
@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    focusScale: Float = 1.07f,
    content: @Composable BoxScope.(Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")
    val borderBrush: Brush =
        if (focused) Brush.linearGradient(listOf(Lilac, Magenta)) else SolidColor(Color(0x22FFFFFF))
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (focused) 18.dp else 0.dp, shape, ambientColor = Neon, spotColor = Neon)
            .border(if (focused) 3.dp else 1.dp, borderBrush, shape)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) { content(focused) }
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val fm = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    FocusBox(onClick, modifier.then(fm), RoundedCornerShape(50)) { f ->
        val bg: Brush = when {
            f -> Brush.horizontalGradient(listOf(Purple, Magenta))
            primary -> SolidColor(Purple.copy(alpha = 0.55f))
            else -> SolidColor(Color(0x33FFFFFF))
        }
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
            modifier = Modifier
                .background(bg)
                .padding(horizontal = 22.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusBox(onClick, modifier, RoundedCornerShape(50), focusScale = 1.05f) { f ->
        val bg: Brush = when {
            f -> Brush.horizontalGradient(listOf(Purple, Magenta))
            selected -> SolidColor(Purple.copy(alpha = 0.6f))
            else -> SolidColor(Color(0x22FFFFFF))
        }
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier
                .background(bg)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun Brand(size: Int = 34, modifier: Modifier = Modifier) {
    Text(
        "AYHAM PLAYER",
        modifier = modifier,
        style = TextStyle(
            brush = Brush.horizontalGradient(listOf(Lilac, Neon, Magenta)),
            fontSize = size.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp
        )
    )
}
