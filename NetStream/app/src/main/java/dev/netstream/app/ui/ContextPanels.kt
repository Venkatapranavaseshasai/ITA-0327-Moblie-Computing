package dev.netstream.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.netstream.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ContextPanel(s: ContextState) {
    Column {
        SectionTitle("Context monitor") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusDot()
                Eyebrow("Live", color = Lime)
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Metric(
                Modifier.weight(1f),
                Glyph.SCREEN,
                "Video output",
                s.videoHeight?.toString() ?: "—",
                "p",
                Lime,
            )
            Metric(
                Modifier.weight(1f),
                Glyph.SIGNAL,
                "Bandwidth",
                s.bandwidthMbps?.let { decimal(it, 2) } ?: "—",
                "Mbps",
                Teal,
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Metric(
                Modifier.weight(1f),
                Glyph.BUFFER,
                "Buffer reserve",
                decimal(s.bufferedDurationMs / 1000.0),
                "s",
                Lilac,
            )
            Metric(
                Modifier.weight(1f),
                Glyph.BATTERY,
                if (s.isCharging) "Charging" else "Battery",
                s.batteryPercent?.toString() ?: "—",
                "%",
                Amber,
            )
        }
    }
}

@Composable
private fun Metric(
    modifier: Modifier,
    glyph: Glyph,
    label: String,
    value: String,
    unit: String,
    color: Color,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .border(1.dp, Edge.copy(alpha = .72f), RoundedCornerShape(12.dp))
            .padding(13.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Mark(glyph, Modifier.size(14.dp), color)
            Eyebrow(label, color = Muted)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = Paper,
                fontFamily = Mono,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                letterSpacing = (-1.1).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                unit,
                Modifier.padding(bottom = 2.dp),
                color = color,
                fontFamily = Mono,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun EnginePanel(decision: AdaptiveQualityPolicy.Decision) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1F281E))
            .border(1.dp, Color(0xFF3B4935), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mark(Glyph.LAYERS, Modifier.size(17.dp), Lime)
            Spacer(Modifier.width(8.dp))
            Text(
                "Adaptive engine",
                Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                color = Paper,
                fontSize = 16.sp,
            )
            Tag("AUTO", Lime)
        }
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Quality.entries.forEach { quality ->
                val active = quality.height <= decision.cap.height
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(4.dp)
                            .background(if (active) Lime else Edge, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        quality.label,
                        color = if (quality == decision.cap) Lime else Muted,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        Text(
            "${decision.cap.label} cap  ·  ${decision.reason}",
            fontSize = 13.sp,
            color = Paper,
            lineHeight = 18.sp,
        )
        decision.pendingUpgrade?.let {
            Text(
                "Stabilizing for ${it.label} · ${(decision.stabilizationRemainingMs+999)/1000}s remaining",
                fontSize = 12.sp,
                color = Lime,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
internal fun SessionPanel(s: ContextState, cloud: Boolean, warning: String?) {
    Column {
        SectionTitle("Session QoE") { Eyebrow(if (cloud) "Firebase" else "On device") }
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(15.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Rebuffers")
                Text("${s.totalStallCount}", color = Paper, fontFamily = Mono, fontSize = 21.sp)
            }
            Column(Modifier.weight(1f)) {
                Eyebrow("Time stalled")
                Text(
                    "${decimal(s.totalStallDurationMs/1000.0)} s",
                    color = if (s.isStalling) Amber else Paper,
                    fontFamily = Mono,
                    fontSize = 21.sp,
                )
            }
        }
        warning?.let {
            Spacer(Modifier.height(7.dp))
            Text(it, color = Alert, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun EventRow(log: QualitySwitchLog) {
    val cap = log.kind == SwitchKind.POLICY_CAP
    val time =
        remember(log.timestampMs) {
            SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(log.timestampMs))
        }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(if (cap) "CAP" else "OUTPUT", if (cap) Lime else Teal)
            Spacer(Modifier.width(8.dp))
            Text(
                "${log.oldHeight}p → ${log.newHeight}p",
                Modifier.weight(1f),
                color = Paper,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(time, color = Muted, fontSize = 10.sp, fontFamily = Mono)
        }
        Spacer(Modifier.height(5.dp))
        Text(log.triggerReason, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Edge)
    }
}
