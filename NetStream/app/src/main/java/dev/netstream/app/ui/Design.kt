package dev.netstream.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.util.Locale

internal val Ink = Color(0xFF0E1110)
internal val Panel = Color(0xFF191D1A)
internal val Raised = Color(0xFF232823)
internal val Edge = Color(0xFF333A33)
internal val Paper = Color(0xFFF1F4EB)
internal val Muted = Color(0xFFAAB3A7)
internal val Lime = Color(0xFFCDF78C)
internal val Teal = Color(0xFF8EDBCB)
internal val Amber = Color(0xFFEFC28A)
internal val Lilac = Color(0xFFBFB2E9)
internal val Alert = Color(0xFFFFB5A8)
internal val Mono = FontFamily.Monospace

@Composable
fun NetStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Lime,
                onPrimary = Ink,
                background = Ink,
                onBackground = Paper,
                surface = Panel,
                onSurface = Paper,
                surfaceVariant = Raised,
                onSurfaceVariant = Muted,
                outline = Edge,
                secondary = Teal,
                tertiary = Lilac,
                error = Alert,
            ),
        content = content,
    )
}

internal enum class Glyph {
    PLAY,
    PAUSE,
    BACK,
    CHEVRON,
    INFO,
    EXPAND,
    COLLAPSE,
    SIGNAL,
    BUFFER,
    BATTERY,
    SCREEN,
    PULSE,
    LAYERS,
    CLOSE,
    REPLAY,
}

/** Small optical line icons, drawn locally so the HUD has no font or network dependency. */
@Composable
internal fun Mark(
    icon: Glyph,
    modifier: Modifier = Modifier.size(20.dp),
    color: Color = Paper,
    label: String? = null,
) {
    Canvas(if (label != null) modifier.semantics { contentDescription = label } else modifier) {
        val u = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x: Int, y: Int, a: Int, b: Int, w: Float = 1.8f) =
            drawLine(
                color,
                p(x.toFloat(), y.toFloat()),
                p(a.toFloat(), b.toFloat()),
                w * u,
                StrokeCap.Round,
            )
        fun outline(x: Int, y: Int, w: Int, h: Int, r: Float = 2f) =
            drawRoundRect(
                color,
                p(x.toFloat(), y.toFloat()),
                Size(w * u, h * u),
                CornerRadius(r * u),
                style = Stroke(1.7f * u),
            )
        when (icon) {
            Glyph.PLAY ->
                drawPath(
                    Path().apply {
                        moveTo(7 * u, 4 * u)
                        lineTo(20 * u, 12 * u)
                        lineTo(7 * u, 20 * u)
                        close()
                    },
                    color,
                )
            Glyph.PAUSE -> {
                drawRoundRect(color, p(6f, 4f), Size(4 * u, 16 * u), CornerRadius(u))
                drawRoundRect(color, p(14f, 4f), Size(4 * u, 16 * u), CornerRadius(u))
            }
            Glyph.BACK -> {
                line(19, 12, 5, 12)
                line(5, 12, 12, 5)
                line(5, 12, 12, 19)
            }
            Glyph.CHEVRON -> {
                line(9, 6, 15, 12)
                line(15, 12, 9, 18)
            }
            Glyph.INFO -> {
                drawCircle(color, 8.5f * u, p(12f, 12f), style = Stroke(1.6f * u))
                line(12, 11, 12, 16)
                drawCircle(color, 1.1f * u, p(12f, 7.5f))
            }
            Glyph.EXPAND -> {
                line(4, 9, 4, 4)
                line(4, 4, 9, 4)
                line(15, 4, 20, 4)
                line(20, 4, 20, 9)
                line(20, 15, 20, 20)
                line(20, 20, 15, 20)
                line(9, 20, 4, 20)
                line(4, 20, 4, 15)
            }
            Glyph.COLLAPSE -> {
                line(4, 9, 9, 9)
                line(9, 9, 9, 4)
                line(15, 4, 15, 9)
                line(15, 9, 20, 9)
                line(20, 15, 15, 15)
                line(15, 15, 15, 20)
                line(9, 20, 9, 15)
                line(9, 15, 4, 15)
            }
            Glyph.SIGNAL -> {
                for (i in 0..3) line(4 + 5 * i, 19, 4 + 5 * i, 16 - 4 * i, 2.6f)
            }
            Glyph.BUFFER -> {
                outline(3, 5, 18, 14)
                for (i in 0..2) line(7 + 4 * i, 10, 7 + 4 * i, 14)
            }
            Glyph.BATTERY -> {
                outline(2, 7, 18, 10)
                line(22, 10, 22, 14)
                drawRoundRect(color, p(5f, 10f), Size(10 * u, 4 * u), CornerRadius(.5f * u))
            }
            Glyph.SCREEN -> {
                outline(3, 4, 18, 14)
                line(8, 21, 16, 21)
            }
            Glyph.PULSE -> {
                line(2, 13, 7, 13)
                line(7, 13, 10, 5)
                line(10, 5, 14, 19)
                line(14, 19, 17, 11)
                line(17, 11, 22, 11)
            }
            Glyph.LAYERS -> {
                line(3, 8, 12, 3)
                line(12, 3, 21, 8)
                line(21, 8, 12, 13)
                line(12, 13, 3, 8)
                line(3, 13, 12, 18)
                line(12, 18, 21, 13)
                line(5, 18, 12, 22)
                line(12, 22, 19, 18)
            }
            Glyph.CLOSE -> {
                line(6, 6, 18, 18)
                line(18, 6, 6, 18)
            }
            Glyph.REPLAY -> {
                drawArc(
                    color,
                    -140f,
                    295f,
                    false,
                    p(4f, 4f),
                    Size(16 * u, 16 * u),
                    style = Stroke(1.8f * u),
                )
                line(4, 3, 4, 8)
                line(4, 8, 9, 8)
            }
        }
    }
}

@Composable
internal fun Brand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Lime),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) {
                val u = size.width / 24f
                drawPath(
                    Path().apply {
                        moveTo(1 * u, 21 * u)
                        lineTo(6 * u, 3 * u)
                        lineTo(10 * u, 3 * u)
                        lineTo(16 * u, 14 * u)
                        lineTo(19 * u, 3 * u)
                        lineTo(24 * u, 3 * u)
                        lineTo(19 * u, 21 * u)
                        lineTo(15 * u, 21 * u)
                        lineTo(9 * u, 10 * u)
                        lineTo(6 * u, 21 * u)
                        close()
                    },
                    Ink,
                )
            }
        }
        Text(
            "NetStream",
            color = Paper,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.3).sp,
        )
    }
}

@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(
        text.uppercase(Locale.ROOT),
        modifier,
        color = color,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontFamily = Mono,
        letterSpacing = 1.15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun Tag(text: String, color: Color = Lime, background: Color = color.copy(alpha = .11f)) {
    Box(
        Modifier.clip(RoundedCornerShape(5.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = color,
            fontFamily = Mono,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = .45.sp,
        )
    }
}

@Composable
internal fun StatusDot(color: Color = Lime) {
    Box(Modifier.size(6.dp).background(color, CircleShape))
}

@Composable
internal fun SectionTitle(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            color = Paper,
            fontSize = 21.sp,
            letterSpacing = (-.65).sp,
            fontWeight = FontWeight.SemiBold,
        )
        trailing()
    }
}

internal fun clockTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

internal fun decimal(n: Double, places: Int = 1) = String.format(Locale.ROOT, "%.${places}f", n)
