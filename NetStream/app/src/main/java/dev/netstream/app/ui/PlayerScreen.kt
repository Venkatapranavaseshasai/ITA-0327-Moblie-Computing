package dev.netstream.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.netstream.app.viewmodel.PlayerViewModel
import dev.netstream.domain.*
import dev.netstream.playback.StreamingSession

@Composable
fun PlayerScreen(video: VideoItem, vm: PlayerViewModel, activity: Activity, cloud: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(video.id, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.start()
                Lifecycle.Event.ON_STOP -> vm.stop("background")
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.stop("screen_disposed")
        }
    }
    var full by rememberSaveable(video.id) { mutableStateOf(false) }
    BackHandler { if (full) full = false else vm.close() }
    DisposableEffect(activity, full) {
        val prior = activity.requestedOrientation
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val behavior = bars.systemBarsBehavior
        if (full) {
            bars.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (full) {
                activity.requestedOrientation = prior
                bars.show(WindowInsetsCompat.Type.systemBars())
                bars.systemBarsBehavior = behavior
            }
        }
    }
    val session by vm.session.collectAsStateWithLifecycle()
    val active = session
    if (active == null)
        Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Lime)
        }
    else key(active) { PlayerContent(video, active, full, cloud, vm::close) { full = !full } }
}

@Composable
private fun PlayerContent(
    video: VideoItem,
    session: StreamingSession,
    full: Boolean,
    cloud: Boolean,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val s by session.contextState.collectAsStateWithLifecycle()
    val decision by session.policyState.collectAsStateWithLifecycle()
    val error by session.error.collectAsStateWithLifecycle()
    val history by session.history.collectAsStateWithLifecycle()
    val warning by session.loggingWarning.collectAsStateWithLifecycle()
    var hud by rememberSaveable { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    LaunchedEffect(full) { controls = true }
    val view = LocalView.current
    val keep = s.playbackRequested && s.phase != PlaybackPhase.ENDED && error == null
    DisposableEffect(view, keep) {
        val prior = view.keepScreenOn
        view.keepScreenOn = keep
        onDispose { view.keepScreenOn = prior }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(if (full) Color.Black else Ink)
            .then(if (full) Modifier else Modifier.safeDrawingPadding()),
        contentAlignment = Alignment.TopCenter,
    ) {
        val contentWidth = if (full) maxWidth else minOf(maxWidth, 900.dp)
        val frameHeight = if (full) maxHeight else minOf(contentWidth * 9f / 16f, maxHeight * .45f)
        Column(Modifier.width(contentWidth).fillMaxHeight()) {
            if (!full)
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Mark(Glyph.BACK, label = "Back to library") }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Watchspace   /   02", color = Lime)
                        Text(
                            "Adaptive player",
                            color = Paper,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Tag(
                        video.protocol.name,
                        if (video.protocol == StreamProtocol.HLS) Teal else Lilac,
                    )
                    Spacer(Modifier.width(12.dp))
                }
            Box(Modifier.fillMaxWidth().height(frameHeight).background(Color.Black)) {
                MediaSurface(session, Modifier.fillMaxSize())
                if (full)
                    Box(
                        Modifier.matchParentSize().clickable(
                            role = Role.Button,
                            onClickLabel = if (controls) "Hide controls" else "Show controls",
                        ) {
                            controls = !controls
                        }
                    )
                if (error == null && hud)
                    ContextHud(s, Modifier.align(Alignment.TopStart).padding(10.dp))
                if (error == null && (!full || controls)) {
                    IconButton(
                        onClick = { hud = !hud },
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                    ) {
                        Box(
                            Modifier.size(32.dp).background(Ink.copy(alpha = .8f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Mark(
                                Glyph.PULSE,
                                Modifier.size(19.dp),
                                if (hud) Lime else Muted,
                                if (hud) "Hide context HUD" else "Show context HUD",
                            )
                        }
                    }
                }
                if (error != null) {
                    Column(
                        Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Stream interrupted",
                            color = Paper,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            error.orEmpty(),
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = session::retry) { Text("Retry stream") }
                    }
                } else if (s.phase == PlaybackPhase.BUFFERING || s.phase == PlaybackPhase.IDLE) {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center).size(28.dp),
                        color = Lime,
                        strokeWidth = 2.dp,
                    )
                }
                if (full && controls)
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = .94f))
                                )
                            )
                            .navigationBarsPadding()
                            .padding(top = 12.dp)
                    ) {
                        Transport(s, session, true, onFullscreen)
                    }
            }
            if (!full) {
                Transport(s, session, false, onFullscreen)
                HorizontalDivider(color = Edge)
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding =
                        PaddingValues(start = 20.dp, end = 20.dp, top = 21.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(17.dp),
                ) {
                    item("film") {
                        Eyebrow(
                            listOfNotNull(video.genre, video.year?.toString())
                                .joinToString("   /   "),
                            color = Lime,
                        )
                        Text(
                            video.title,
                            color = Paper,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 32.sp,
                            letterSpacing = (-1.3).sp,
                            lineHeight = 38.sp,
                        )
                        Text(video.creator, color = Muted, fontSize = 13.sp)
                    }
                    item("telemetry") { ContextPanel(s) }
                    item("policy") { EnginePanel(decision) }
                    item("qoe") { SessionPanel(s, cloud, warning) }
                    item("events-heading") {
                        SectionTitle("Switch history") { Eyebrow("Latest ${history.size} / 32") }
                    }
                    if (history.isEmpty())
                        item {
                            Text(
                                "No switches yet. Policy and picture changes appear here as they happen.",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = Muted,
                            )
                        }
                    items(history, key = { it.eventId }) { EventRow(it) }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MediaSurface(session: StreamingSession, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setEnableComposeSurfaceSyncWorkaround(true)
            }
        },
        update = { if (it.player !== session.player) it.player = session.player },
        onRelease = { it.player = null },
    )
}

@Composable
private fun ContextHud(s: ContextState, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xDF101511))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            StatusDot(if (s.isStalling) Amber else Lime)
            Eyebrow(if (s.isStalling) "HUD  /  REBUFFER" else "CONTEXT HUD", color = Paper)
        }
        Row {
            HudValue(Glyph.SCREEN, s.videoHeight?.let { "${it}p" } ?: "—", Lime, 69)
            HudValue(
                Glyph.SIGNAL,
                s.bandwidthMbps?.let { "${decimal(it,2)} Mbps" } ?: "— Mbps",
                Teal,
                91,
            )
        }
        Row {
            HudValue(Glyph.BUFFER, "${decimal(s.bufferedDurationMs/1000.0)} s", Lilac, 69)
            HudValue(
                Glyph.BATTERY,
                (s.batteryPercent?.let { "$it%" } ?: "—") + if (s.isCharging) " +" else "",
                Amber,
                91,
            )
        }
    }
}

@Composable
private fun HudValue(icon: Glyph, value: String, color: Color, width: Int) {
    Row(
        Modifier.width(width.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Mark(icon, Modifier.size(12.dp), color)
        Text(
            value,
            fontFamily = Mono,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = Paper,
            maxLines = 1,
        )
    }
}

@Composable
private fun Transport(
    s: ContextState,
    session: StreamingSession,
    full: Boolean,
    onFullscreen: () -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val duration = s.durationMs
    val canSeek = s.isSeekable && duration != null && duration > 0
    val progress =
        drag ?: (duration?.let { (s.positionMs.toDouble() / it).toFloat().coerceIn(0f, 1f) } ?: 0f)
    val playing = session.player.playWhenReady && s.phase != PlaybackPhase.ENDED
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = session::togglePlay) {
            Box(
                Modifier.size(35.dp).background(Lime, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Mark(
                    if (s.phase == PlaybackPhase.ENDED) Glyph.REPLAY
                    else if (playing) Glyph.PAUSE else Glyph.PLAY,
                    Modifier.size(16.dp),
                    Ink,
                    if (s.phase == PlaybackPhase.ENDED) "Replay"
                    else if (playing) "Pause" else "Play",
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    clockTime(
                        if (drag != null && duration != null) (progress * duration).toLong()
                        else s.positionMs
                    ),
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    color = Paper,
                )
                Text(
                    if (s.isLive) "LIVE" else duration?.let(::clockTime) ?: "—:—",
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    color = Muted,
                )
            }
            Slider(
                value = progress,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    val v = drag
                    if (v != null && duration != null) session.seekTo((duration * v).toLong())
                    drag = null
                },
                enabled = canSeek,
                modifier =
                    Modifier.fillMaxWidth().height(25.dp).semantics {
                        contentDescription = "Seek playback"
                    },
                colors =
                    SliderDefaults.colors(
                        thumbColor = Lime,
                        activeTrackColor = Lime,
                        inactiveTrackColor = Edge,
                    ),
            )
        }
        IconButton(onClick = onFullscreen) {
            Mark(
                if (full) Glyph.COLLAPSE else Glyph.EXPAND,
                Modifier.size(20.dp),
                Paper,
                if (full) "Exit fullscreen" else "Enter fullscreen",
            )
        }
    }
}
