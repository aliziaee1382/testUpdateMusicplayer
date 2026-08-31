package ir.ali0003.musicplayer.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.ali0003.musicplayer.model.GlassTheme
import ir.ali0003.musicplayer.model.Track
import ir.ali0003.musicplayer.model.TrackSortCriterion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class FastScrollGroup(
    val label: String,
    val firstTrackIndex: Int,
    val itemIndex: Int
)

private val PERSIAN_ALPHABET = listOf(
    "آ", "ا", "ب", "پ", "ت", "ث", "ج", "چ", "ح", "خ",
    "د", "ذ", "ر", "ز", "ژ", "س", "ش", "ص", "ض", "ط",
    "ظ", "ع", "غ", "ف", "ق", "ک", "گ", "ل", "م", "ن",
    "و", "ه", "ی"
)

fun getTrackGroupLabel(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "#"
    val firstChar = trimmed.first()

    val normalized = when (firstChar) {
        'أ', 'إ', 'ٱ' -> 'ا'
        'ك' -> 'ک'
        'ي' -> 'ی'
        else -> firstChar
    }

    if (normalized in 'A'..'Z' || normalized in 'a'..'z') {
        return normalized.uppercaseChar().toString()
    }

    val str = normalized.toString()
    if (PERSIAN_ALPHABET.contains(str)) {
        return str
    }

    if (normalized in '\u0600'..'\u06FF' || normalized in '\u0750'..'\u077F' || normalized in '\uFB50'..'\uFDFF') {
        return str
    }

    return "#"
}

private fun compareGroupLabels(a: String, b: String): Int {
    if (a == b) return 0
    if (a == "#") return -1
    if (b == "#") return 1

    val aIsEnglish = a.length == 1 && a[0] in 'A'..'Z'
    val bIsEnglish = b.length == 1 && b[0] in 'A'..'Z'

    if (aIsEnglish && bIsEnglish) {
        return a.compareTo(b)
    }
    if (aIsEnglish && !bIsEnglish) {
        return -1
    }
    if (!aIsEnglish && bIsEnglish) {
        return 1
    }

    val aIndex = PERSIAN_ALPHABET.indexOf(a)
    val bIndex = PERSIAN_ALPHABET.indexOf(b)
    return if (aIndex != -1 && bIndex != -1) {
        aIndex.compareTo(bIndex)
    } else {
        a.compareTo(b)
    }
}

@Composable
fun DotsFastScrollOverlay(
    listState: LazyListState,
    sortedTracks: List<Track>,
    sortCriterion: TrackSortCriterion,
    headerCount: Int,
    theme: GlassTheme,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    if (sortedTracks.size <= 3) return

    val isAlphabetMode = sortCriterion == TrackSortCriterion.TITLE
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val groups = remember(sortedTracks, sortCriterion, headerCount) {
        if (!isAlphabetMode || sortedTracks.isEmpty()) {
            emptyList()
        } else {
            val groupMap = mutableMapOf<String, Int>()
            sortedTracks.forEachIndexed { index, track ->
                val label = getTrackGroupLabel(track.title)
                if (!groupMap.containsKey(label)) {
                    groupMap[label] = index
                }
            }

            val sortedLabels = groupMap.keys.sortedWith { a, b -> compareGroupLabels(a, b) }
            sortedLabels.map { label ->
                val trackIdx = groupMap[label]!!
                FastScrollGroup(
                    label = label,
                    firstTrackIndex = trackIdx,
                    itemIndex = headerCount + trackIdx
                )
            }
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var touchYPx by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.2f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "thumb_scale"
    )

    val firstVisibleItemIndex = listState.firstVisibleItemIndex

    val activeGroupIndex = remember(touchYPx, trackHeightPx, groups) {
        if (groups.isEmpty()) -1
        else {
            val fraction = (touchYPx / trackHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
            (fraction * groups.size).toInt().coerceIn(0, groups.lastIndex)
        }
    }

    val activeGroup = if (isAlphabetMode && groups.isNotEmpty() && activeGroupIndex in groups.indices) {
        groups[activeGroupIndex]
    } else null

    var lastHapticGroupLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeGroup?.label, isDragging) {
        if (isDragging && activeGroup != null && activeGroup.label != lastHapticGroupLabel) {
            lastHapticGroupLabel = activeGroup.label
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val thumbFraction: Float = if (isDragging) {
        (touchYPx / trackHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    } else {
        val currentTrackIdx = (firstVisibleItemIndex - headerCount).coerceAtLeast(0)
        val totalTracks = sortedTracks.size
        if (totalTracks > 1) {
            (currentTrackIdx.toFloat() / (totalTracks - 1)).coerceIn(0f, 1f)
        } else 0f
    }

    val thumbYPx = thumbFraction * trackHeightPx
    val thumbYDp = with(density) { thumbYPx.toDp() }

    Box(
        modifier = modifier
            .padding(top = 180.dp, bottom = bottomPadding + 12.dp)
            .fillMaxHeight()
            .width(30.dp)
            .onGloballyPositioned { coordinates ->
                trackHeightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(groups, sortedTracks, isAlphabetMode, headerCount) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    touchYPx = down.position.y

                    val initialFraction = (touchYPx / trackHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    if (isAlphabetMode && groups.isNotEmpty()) {
                        val gIdx = (initialFraction * groups.size).toInt().coerceIn(0, groups.lastIndex)
                        val targetItem = groups[gIdx].itemIndex
                        coroutineScope.launch { listState.scrollToItem(targetItem) }
                    } else {
                        val targetTrackIdx = (initialFraction * (sortedTracks.size - 1)).toInt().coerceIn(0, sortedTracks.lastIndex)
                        coroutineScope.launch { listState.scrollToItem(headerCount + targetTrackIdx) }
                    }

                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val dragEvent = event.changes.firstOrNull { it.id == pointerId }
                        if (dragEvent == null || !dragEvent.pressed) {
                            isDragging = false
                            break
                        }
                        dragEvent.consume()
                        touchYPx = dragEvent.position.y
                        val currentFraction = (touchYPx / trackHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

                        if (isAlphabetMode && groups.isNotEmpty()) {
                            val gIdx = (currentFraction * groups.size).toInt().coerceIn(0, groups.lastIndex)
                            val targetItem = groups[gIdx].itemIndex
                            coroutineScope.launch { listState.scrollToItem(targetItem) }
                        } else {
                            val targetTrackIdx = (currentFraction * (sortedTracks.size - 1)).toInt().coerceIn(0, sortedTracks.lastIndex)
                            coroutineScope.launch { listState.scrollToItem(headerCount + targetTrackIdx) }
                        }
                    }
                }
            }
    ) {
        if (isAlphabetMode && groups.isNotEmpty()) {
            // Dots Column on the right edge
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                groups.forEachIndexed { idx, group ->
                    val isActive = if (isDragging) idx == activeGroupIndex else {
                        // Current group derived from current scroll position
                        val currentTrackIdx = (firstVisibleItemIndex - headerCount).coerceAtLeast(0)
                        val activeTrackGroupIdx = groups.indexOfLast { it.firstTrackIndex <= currentTrackIdx }
                        (activeTrackGroupIdx.coerceAtLeast(0)) == idx
                    }

                    Box(
                        modifier = Modifier
                            .size(if (isActive) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) theme.accentColor
                                else theme.subtextColor.copy(alpha = 0.45f)
                            )
                    )
                }
            }

            // Glassmorphic Indicator Bubble next to finger when dragging
            if (isDragging && activeGroup != null) {
                val clampedBubbleY = (thumbYDp - 26.dp).coerceIn(0.dp, with(density) { (trackHeightPx - 52f).coerceAtLeast(0f).toDp() })
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-28).dp, y = clampedBubbleY)
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    theme.glassFill.copy(alpha = 0.92f),
                                    theme.glassFill.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .border(1.dp, theme.glassBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeGroup.label,
                        color = theme.textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Standard Scroll Mode (No dots, no bubble, clean glassmorphic thumb)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(6.dp)
                    .padding(end = 5.dp)
                    .background(theme.subtextColor.copy(alpha = 0.15f), CircleShape)
            )

            val clampedThumbY = (thumbYDp - 18.dp).coerceIn(0.dp, with(density) { (trackHeightPx - 36f).coerceAtLeast(0f).toDp() })
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = clampedThumbY)
                    .padding(end = 4.dp)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .size(width = 8.dp, height = 38.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(theme.accentColor, theme.glowColor)
                        )
                    )
                    .border(0.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            )
        }
    }
}
