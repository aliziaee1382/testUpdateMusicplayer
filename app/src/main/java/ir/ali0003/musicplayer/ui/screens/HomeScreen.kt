package ir.ali0003.musicplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import ir.ali0003.musicplayer.model.GlassTheme
import ir.ali0003.musicplayer.model.ListItemSize
import ir.ali0003.musicplayer.model.Playlist
import ir.ali0003.musicplayer.model.Track
import ir.ali0003.musicplayer.model.TrackSortCriterion
import ir.ali0003.musicplayer.model.TrackSortOrder
import ir.ali0003.musicplayer.ui.glass.*
import androidx.compose.ui.res.painterResource
import ir.ali0003.musicplayer.R
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tracks: List<Track>,
    recentlyPlayed: List<Track>,
    selectedCategory: String,
    searchQuery: String,
    currentTrack: Track?,
    isPlaying: Boolean,
    theme: GlassTheme,
    listItemSize: ListItemSize = ListItemSize.SMALL,
    hasAudioPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    sortCriterion: TrackSortCriterion = TrackSortCriterion.DATE_ADDED,
    sortOrder: TrackSortOrder = TrackSortOrder.DESCENDING,
    allPlaylists: List<Playlist> = emptyList(),
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onSelectCategory: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortCriterionChange: (TrackSortCriterion) -> Unit = {},
    onSortOrderChange: (TrackSortOrder) -> Unit = {},
    onPlayTrack: (Track, List<Track>?) -> Unit,
    onShufflePlay: ((List<Track>?) -> Unit)? = null,
    onToggleFavorite: (Track) -> Unit,
    onOpenThemeSelector: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenAddToPlaylist: ((Track) -> Unit)? = null,
    onScanLocalMusic: (() -> Unit)? = null,
    onHideTracks: ((List<Long>) -> Unit)? = null,
    onHideFolder: ((String) -> Unit)? = null,
    onPlayNextTracks: ((List<Track>) -> Unit)? = null,
    onAddTracksToPlaylist: ((Long, List<Long>) -> Unit)? = null,
    onCreatePlaylist: ((String) -> Unit)? = null,
    isNowPlayingExpanded: Boolean = false,
    scrollToTopTrigger: Int = 0
) {
    var showSortDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var folderToHide by remember { mutableStateOf<String?>(null) }
    var selectedTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showMultiAddToPlaylistDialog by remember { mutableStateOf(false) }

    if (folderToHide != null) {
        HideFolderConfirmationDialog(
            folderName = folderToHide!!,
            onConfirm = {
                val target = folderToHide!!
                folderToHide = null
                if (selectedCategory.equals(target, ignoreCase = true)) {
                    onSelectCategory("All")
                }
                onHideFolder?.invoke(target)
            },
            onDismiss = { folderToHide = null },
            theme = theme
        )
    }

    BackHandler(enabled = selectedTrackIds.isNotEmpty()) {
        selectedTrackIds = emptySet()
    }

    if (showMultiAddToPlaylistDialog) {
        MultiAddToPlaylistDialog(
            playlists = allPlaylists,
            selectedCount = selectedTrackIds.size,
            onAddToPlaylist = { playlistId ->
                onAddTracksToPlaylist?.invoke(playlistId, selectedTrackIds.toList())
                selectedTrackIds = emptySet()
                showMultiAddToPlaylistDialog = false
            },
            onCreateNewPlaylist = {
                showMultiAddToPlaylistDialog = false
                onCreatePlaylist?.invoke("New Playlist")
            },
            onDismiss = { showMultiAddToPlaylistDialog = false },
            theme = theme
        )
    }

    if (showSortDialog) {
        SortTracksDialog(
            selectedCriterion = sortCriterion,
            selectedOrder = sortOrder,
            onSelectCriterion = { onSortCriterionChange(it) },
            onSelectOrder = { onSortOrderChange(it) },
            onDismiss = { showSortDialog = false },
            theme = theme
        )
    }

    if (showSearchDialog) {
        SearchTracksDialog(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            tracks = tracks,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onPlayTrack = onPlayTrack,
            onToggleFavorite = onToggleFavorite,
            onDismiss = { showSearchDialog = false },
            theme = theme
        )
    }

    val categories = remember(tracks) {
        val detectedFolders = tracks.map { it.folderName }
            .filter { it.isNotBlank() }
            .distinct()
        listOf("All") + detectedFolders
    }

    val filteredTracks = remember(tracks, selectedCategory, searchQuery) {
        tracks.filter { track ->
            val matchesCategory = if (selectedCategory == "All" || selectedCategory.isBlank()) {
                true
            } else {
                track.folderName.equals(selectedCategory, ignoreCase = true) || track.category.equals(selectedCategory, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    track.title.contains(searchQuery, ignoreCase = true) ||
                    track.artist.contains(searchQuery, ignoreCase = true) ||
                    track.album.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val sortedTracks = remember(filteredTracks, sortCriterion, sortOrder) {
        val comparator = when (sortCriterion) {
            TrackSortCriterion.DATE_ADDED -> compareBy<Track> { it.dateAddedTimestamp }
            TrackSortCriterion.FILE_DATE -> compareBy<Track> { it.dateModifiedTimestamp }
            TrackSortCriterion.TITLE -> compareBy<Track> { it.title.lowercase() }
            TrackSortCriterion.DURATION -> compareBy<Track> { it.durationSeconds }
        }
        if (sortOrder == TrackSortOrder.ASCENDING) {
            filteredTracks.sortedWith(comparator)
        } else {
            filteredTracks.sortedWith(comparator.reversed())
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    val isMiniPlayerVisible = currentTrack != null && !isNowPlayingExpanded
    val bottomOffset = if (isMiniPlayerVisible) 130.dp else 65.dp

    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = if (theme.isLight) Color(0xFFF3F4F6) else Color(0xFF1E293B),
                color = theme.accentColor
            )
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomOffset)
                .clipToBounds()
                .testTag("home_screen_column"),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
        // Top User Header
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0003",
                        color = theme.textColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("home_header_title")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Permission Denied State or Music Content
        if (!hasAudioPermission) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(
                        onClick = { onRequestPermission() },
                        theme = theme,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "permission_denied_card"
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(theme.accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Permission Required",
                                    tint = theme.accentColor,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "Audio Permission Required",
                                color = theme.textColor,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("permission_denied_title")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "To display and play your local music, 0003 Player needs permission to access audio files on your device.",
                                color = theme.subtextColor,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("permission_denied_desc")
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            GlassButton(
                                text = "Grant Permission",
                                onClick = { onRequestPermission() },
                                theme = theme,
                                testTag = "grant_permission_button"
                            )
                        }
                    }
                }
            }
        } else {
            // Scan Prompt if no songs exist
            if (tracks.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            GlassCard(
                                onClick = { onScanLocalMusic?.invoke() },
                                theme = theme,
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "empty_offline_card"
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderSpecial,
                                        contentDescription = "Scan Storage",
                                        tint = theme.accentColor,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No Local Music Found",
                                        color = theme.textColor,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap below to scan phone storage for MP3, FLAC, M4A & WAV audio files.",
                                        color = theme.subtextColor,
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    GlassButton(
                                        text = "Scan Storage",
                                        onClick = { onScanLocalMusic?.invoke() },
                                        theme = theme,
                                        testTag = "home_scan_empty_cta"
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Pop-out Categories / Folders Chips
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "DETECTED FOLDERS & CATEGORIES",
                        color = theme.subtextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        items(categories, key = { it }, contentType = { "category_chip" }) { category ->
                            val isAll = category == "All"
                            GlassChip(
                                text = category,
                                isSelected = selectedCategory == category,
                                onClick = { onSelectCategory(category) },
                                onLongClick = if (!isAll) {
                                    { folderToHide = category }
                                } else null,
                                theme = theme,
                                testTag = "category_chip_$category"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Music Tracks Header with Sort Icon Button
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "All Songs",
                                color = theme.textColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${sortedTracks.size} tracks • ${sortCriterion.labelEn} (${sortOrder.labelEn})",
                                color = theme.accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassIconButton(
                                icon = Icons.Default.Shuffle,
                                contentDescription = "Quick Shuffle Play",
                                onClick = { onShufflePlay?.invoke(sortedTracks) },
                                theme = theme,
                                size = 40.dp,
                                testTag = "quick_shuffle_button"
                            )

                            GlassIconButton(
                                icon = Icons.Default.Search,
                                contentDescription = "Search Songs & Artists",
                                onClick = { showSearchDialog = true },
                                isActive = searchQuery.isNotEmpty(),
                                theme = theme,
                                size = 40.dp,
                                testTag = "open_search_dialog_button"
                            )

                            GlassIconButton(
                                icon = Icons.Default.Sort,
                                contentDescription = "Sort Songs",
                                onClick = { showSortDialog = true },
                                theme = theme,
                                size = 40.dp,
                                testTag = "open_sort_dialog_button"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Lazy recycling items for high-performance compact track list
            itemsIndexed(
                items = sortedTracks,
                key = { _, track -> track.id },
                contentType = { _, _ -> "track_item" }
            ) { index, track ->
                val isCurrent = currentTrack?.id == track.id
                val isFirst = index == 0
                val isLast = index == sortedTracks.lastIndex
                val isSelected = selectedTrackIds.contains(track.id)
                val itemShape = remember(isFirst, isLast) {
                    when {
                        isFirst && isLast -> RoundedCornerShape(16.dp)
                        isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        isLast -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                        else -> RectangleShape
                    }
                }

                val onItemClick = remember(track.id, sortedTracks, selectedTrackIds) {
                    {
                        if (selectedTrackIds.isNotEmpty()) {
                            selectedTrackIds = if (selectedTrackIds.contains(track.id)) {
                                selectedTrackIds - track.id
                            } else {
                                selectedTrackIds + track.id
                            }
                        } else {
                            onPlayTrack(track, sortedTracks)
                        }
                    }
                }

                val onItemLongClick = remember(track.id, selectedTrackIds) {
                    {
                        selectedTrackIds = if (selectedTrackIds.contains(track.id)) {
                            selectedTrackIds - track.id
                        } else {
                            selectedTrackIds + track.id
                        }
                    }
                }

                val onFavoriteClick = remember(track) { { onToggleFavorite(track) } }

                Box(modifier = Modifier.padding(start = 16.dp, end = 30.dp)) {
                    TrackListItem(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying,
                        listItemSize = listItemSize,
                        theme = theme,
                        itemShape = itemShape,
                        isLastInGroup = isLast,
                        showDivider = !isLast,
                        onClick = onItemClick,
                        onLongClick = onItemLongClick,
                        isSelected = isSelected,
                        onToggleFavorite = onFavoriteClick,
                        onOpenAddToPlaylist = null,
                        testTag = "home_track_item_${track.id}"
                    )
                }
            }
        }
    }

        AnimatedVisibility(
            visible = selectedTrackIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        ) {
            MultiSelectTopActionBar(
                selectedCount = selectedTrackIds.size,
                onClose = { selectedTrackIds = emptySet() },
                onPlayNext = {
                    val selectedList = sortedTracks.filter { it.id in selectedTrackIds }
                    onPlayNextTracks?.invoke(selectedList)
                    selectedTrackIds = emptySet()
                },
                onAddToPlaylist = {
                    showMultiAddToPlaylistDialog = true
                },
                onHide = {
                    onHideTracks?.invoke(selectedTrackIds.toList())
                    selectedTrackIds = emptySet()
                },
                theme = theme
            )
        }

        if (hasAudioPermission && sortedTracks.isNotEmpty()) {
            DotsFastScrollOverlay(
                listState = listState,
                sortedTracks = sortedTracks,
                sortCriterion = sortCriterion,
                headerCount = 3,
                theme = theme,
                bottomPadding = bottomOffset,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
fun MultiSelectTopActionBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onHide: () -> Unit,
    theme: GlassTheme,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            theme.glassFill.copy(alpha = 0.95f),
                            theme.glassFill.copy(alpha = 0.85f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.4f),
                            theme.glassBorder,
                            Color.White.copy(alpha = 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Cancel Selection",
                        onClick = onClose,
                        theme = theme,
                        size = 36.dp,
                        testTag = "multi_select_close_button"
                    )
                    Text(
                        text = "$selectedCount",
                        color = theme.textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("multi_select_count_text")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassButton(
                        text = "Play Next",
                        icon = Icons.Default.SkipNext,
                        onClick = onPlayNext,
                        theme = theme,
                        testTag = "multi_select_play_next_button"
                    )

                    GlassIconButton(
                        icon = Icons.Default.PlaylistAdd,
                        contentDescription = "Add to Playlist",
                        onClick = onAddToPlaylist,
                        theme = theme,
                        size = 36.dp,
                        testTag = "multi_select_add_playlist_button"
                    )

                    GlassIconButton(
                        icon = Icons.Default.VisibilityOff,
                        contentDescription = "Hide Tracks",
                        onClick = onHide,
                        tint = Color(0xFFEF4444),
                        theme = theme,
                        size = 36.dp,
                        testTag = "multi_select_hide_button"
                    )
                }
            }
        }
    }
}
