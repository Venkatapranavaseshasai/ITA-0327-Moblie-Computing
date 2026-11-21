package dev.netstream.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import dev.netstream.app.R
import dev.netstream.app.viewmodel.CatalogueState
import dev.netstream.domain.*

@Composable
fun CatalogueScreen(
    state: CatalogueState,
    cloud: Boolean,
    onPlay: (VideoItem) -> Unit,
    onRetry: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var about by rememberSaveable { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Ink).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            Modifier.widthIn(max = 800.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        ) {
            item("masthead") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Brand()
                    TextButton(
                        onClick = { about = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Muted),
                    ) {
                        StatusDot(if (cloud) Teal else Lime)
                        Spacer(Modifier.width(7.dp))
                        Eyebrow(if (cloud) "Cloud library" else "Demo library")
                        Spacer(Modifier.width(5.dp))
                        Mark(Glyph.INFO, Modifier.size(15.dp), Muted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Edge)
                Spacer(Modifier.height(25.dp))
                Eyebrow("01   /   WATCHSPACE", color = Lime)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Stream library",
                    color = Paper,
                    fontSize = 39.sp,
                    lineHeight = 44.sp,
                    letterSpacing = (-1.8).sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Text("Open films. Adaptive playback.", color = Muted, fontSize = 15.sp)
                Spacer(Modifier.height(21.dp))
            }
            when (state) {
                CatalogueState.Loading ->
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 44.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = Lime,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Loading streams…", color = Muted)
                        }
                    }
                is CatalogueState.Failed ->
                    item { Notice("Catalogue unavailable", state.message, "Try again", onRetry) }
                is CatalogueState.Ready -> {
                    val videos = state.items
                    if (videos.isEmpty())
                        item {
                            Notice(
                                "Your library is empty",
                                if (cloud)
                                    "Add a document to the videos collection, or run the included catalogue seed script."
                                else "No streams are available.",
                                "Refresh",
                                onRetry,
                            )
                        }
                    else {
                        item("featured") { Featured(videos.first(), onPlay) }
                        item("filter") {
                            Spacer(Modifier.height(26.dp))
                            SectionTitle("Browse streams") {
                                Eyebrow("${videos.size.toString().padStart(2,'0')} titles")
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (name in listOf("All", "HLS", "DASH")) {
                                    val n =
                                        if (name == "All") videos.size
                                        else videos.count { it.protocol.name == name }
                                    val selected = filter == name
                                    Box(
                                        Modifier.clip(RoundedCornerShape(20.dp))
                                            .background(if (selected) Lime else Panel)
                                            .border(
                                                1.dp,
                                                if (selected) Lime else Edge,
                                                RoundedCornerShape(20.dp),
                                            )
                                            .selectable(
                                                selected = selected,
                                                role = Role.Tab,
                                                onClick = { filter = name },
                                            )
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            "$name  $n",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) Ink else Muted,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                        }
                        val filtered =
                            videos.filter { filter == "All" || it.protocol.name == filter }
                        if (filtered.isEmpty())
                            item {
                                Text(
                                    "No $filter streams in this catalogue.",
                                    Modifier.padding(vertical = 22.dp),
                                    color = Muted,
                                )
                            }
                        items(filtered, key = { "film_${it.id}" }) { video ->
                            FilmRow(video, onPlay)
                            HorizontalDivider(color = Edge.copy(alpha = .7f))
                        }
                        item("footer") {
                            Spacer(Modifier.height(20.dp))
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Panel)
                                    .clickable { about = true }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Mark(Glyph.PULSE, Modifier.size(22.dp), Lime)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Context-aware engine",
                                        color = Paper,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        "How quality adapts as you watch",
                                        color = Muted,
                                        fontSize = 12.sp,
                                    )
                                }
                                Mark(Glyph.CHEVRON, Modifier.size(17.dp), Muted)
                            }
                        }
                    }
                }
            }
        }
    }
    if (about) AboutSheet(cloud) { about = false }
}

@Composable
private fun Poster(item: VideoItem, modifier: Modifier = Modifier) {
    val resource =
        when (item.posterKey) {
            "sintel" -> R.drawable.poster_sintel
            "steel" -> R.drawable.poster_steel
            "bunny" -> R.drawable.poster_bunny
            else -> null
        }
    Box(
        modifier.background(
            Brush.linearGradient(listOf(Color(0xFF354439), Color(0xFF1B2826), Ink))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Mark(Glyph.LAYERS, Modifier.size(32.dp), Lime.copy(alpha = .55f))
        val local = resource?.let { painterResource(it) }
        when {
            item.posterUrl != null ->
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = local,
                    error = local,
                    contentScale = ContentScale.Crop,
                )
            local != null ->
                Image(local, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun Featured(item: VideoItem, onPlay: (VideoItem) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, Edge, shape).clickable(
            role = Role.Button,
            onClickLabel = "Play ${item.title}",
        ) {
            onPlay(item)
        }
    ) {
        val height = (maxWidth * .69f).coerceIn(230.dp, 340.dp)
        Box(Modifier.fillMaxWidth().height(height)) {
            Poster(item, Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = .05f),
                            .37f to Color.Transparent,
                            .75f to Color.Black.copy(alpha = .42f),
                            1f to Color.Black.copy(alpha = .91f),
                        )
                    )
            )
            Row(
                Modifier.fillMaxWidth().padding(15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(5.dp))
                        .background(Ink.copy(alpha = .79f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot()
                    Spacer(Modifier.width(6.dp))
                    Eyebrow("Featured stream", color = Paper)
                }
                Tag(item.protocol.name, Paper, Ink.copy(alpha = .75f))
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        letterSpacing = (-1.8).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        listOfNotNull(
                                item.year?.toString(),
                                item.genre,
                                item.durationSeconds?.let { clockTime(it * 1000L) },
                            )
                            .joinToString("   ·   "),
                        color = Paper,
                        fontSize = 12.sp,
                    )
                }
                Box(
                    Modifier.size(48.dp).background(Lime, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Mark(Glyph.PLAY, Modifier.size(21.dp), Ink)
                }
            }
        }
    }
}

@Composable
private fun FilmRow(item: VideoItem, onPlay: (VideoItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Play ${item.title}") { onPlay(item) }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(100.dp, 69.dp).clip(RoundedCornerShape(9.dp))) {
            Poster(item, Modifier.matchParentSize())
            item.durationSeconds?.let {
                Text(
                    clockTime(it * 1000L),
                    Modifier.align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = .78f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = Paper,
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = Paper,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-.35).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Tag(item.protocol.name, if (item.protocol == StreamProtocol.HLS) Teal else Lilac)
                Text(
                    item.genre,
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Mark(Glyph.CHEVRON, Modifier.size(16.dp), Muted)
    }
}

@Composable
internal fun Notice(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(20.dp)
    ) {
        Text(title, color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onClick) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(cloud: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        contentColor = Paper,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)
        ) {
            Eyebrow("Engine notes   /   01", color = Lime)
            Spacer(Modifier.height(7.dp))
            SectionTitle("How NetStream adapts")
            Spacer(Modifier.height(10.dp))
            Text(
                "Media3 chooses a track within a moving resolution cap. The actual picture may be smaller than the cap.",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(12.dp))
            listOf(
                    "1080p" to "Above 5 Mbps",
                    "720p" to "2–5 Mbps",
                    "480p" to "1–2 Mbps, low reserve or a rebuffer",
                    "360p" to "Below 1 Mbps or low battery",
                )
                .forEach { (q, description) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            q,
                            Modifier.width(64.dp),
                            fontFamily = Mono,
                            color = Lime,
                            fontSize = 13.sp,
                        )
                        Text(description, color = Paper, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = Edge)
                }
            Spacer(Modifier.height(12.dp))
            Text(
                "Upgrades need 12 seconds of stable playback. Downgrades apply immediately. An unplugged battery below 15% takes priority; recent stalls hold the cap for 10 seconds.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            Eyebrow(if (cloud) "Firebase mode" else "On-device demo", color = Teal)
            Spacer(Modifier.height(5.dp))
            Text(
                if (cloud)
                    "The catalogue comes from Firestore. Switch events and session summaries are stored under your anonymous Firebase user and reported to Analytics. A private local copy is also retained."
                else
                    "The catalogue is bundled; video still streams over the internet. Diagnostics stay in this app’s private storage. No Firebase setup is required.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(15.dp))
            Text(
                "Demo films and stills: © Blender Foundation, CC BY 3.0.",
                color = Muted,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Back to library")
            }
        }
    }
}
