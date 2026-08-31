package ir.ali0003.musicplayer.ui.glass

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.*
import ir.ali0003.musicplayer.R
import ir.ali0003.musicplayer.model.GlassTheme
import ir.ali0003.musicplayer.viewmodel.DEFAULT_SEARCH_RESULTS
import ir.ali0003.musicplayer.viewmodel.DownloaderSearchResult
import ir.ali0003.musicplayer.viewmodel.DownloaderUiState
import ir.ali0003.musicplayer.viewmodel.DownloaderViewModel

@Composable
fun DownloaderScreen(
    theme: GlassTheme,
    modifier: Modifier = Modifier,
    viewModel: DownloaderViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top Glass SearchBar
            GlassSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        focusManager.clearFocus()
                        viewModel.onSearchInitiated()
                    }
                },
                placeholder = "music name Or Link",
                theme = theme,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Dynamic State Content Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f) togetherWith
                                fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.95f)
                    },
                    label = "downloader_ui_state_transition"
                ) { targetState ->
                    when (targetState) {
                        DownloaderUiState.INITIAL -> {
                            DownloaderInitialStateView(
                                theme = theme,
                                onSampleQueryClick = { query ->
                                    viewModel.updateSearchQuery(query)
                                    viewModel.onSearchInitiated()
                                }
                            )
                        }

                        DownloaderUiState.CONFIRM_INPUT -> {
                            DownloaderConfirmInputView(
                                query = searchQuery,
                                theme = theme,
                                onCancel = { viewModel.onCancelInput() },
                                onOk = { viewModel.onConfirmInputOk() }
                            )
                        }

                        DownloaderUiState.SEARCHING_LOADING -> {
                            DownloaderSearchingView(theme = theme)
                        }

                        DownloaderUiState.SEARCH_RESULTS -> {
                            DownloaderSearchResultsView(
                                results = searchResults,
                                selectedItem = selectedItem,
                                theme = theme,
                                onSelectItem = { item -> viewModel.onSelectItem(item) }
                            )
                        }

                        DownloaderUiState.DOWNLOADING_ITEM -> {
                            DownloaderDownloadingItemView(
                                item = selectedItem ?: DEFAULT_SEARCH_RESULTS.first(),
                                progress = downloadProgress,
                                theme = theme
                            )
                        }

                        DownloaderUiState.COMPLETE -> {
                            DownloaderCompleteView(
                                item = selectedItem ?: DEFAULT_SEARCH_RESULTS.first(),
                                theme = theme,
                                onDownloadAnother = { viewModel.onDownloadAnother() }
                            )
                        }

                        DownloaderUiState.ERROR_NETWORK -> {
                            DownloaderErrorNetworkView(
                                theme = theme,
                                onRetry = { viewModel.onRetryNetwork() }
                            )
                        }

                        DownloaderUiState.ERROR_OUTDATED_ENGINE -> {
                            DownloaderOutdatedEngineView(
                                theme = theme,
                                onUpdateEngine = { viewModel.onUpdateDownloaderEngine() }
                            )
                        }

                        DownloaderUiState.ENGINE_UPDATING -> {
                            DownloaderEngineUpdatingView(theme = theme)
                        }
                    }
                }
            }
        }

        // Frosted Glass Under Development Overlay
        DownloaderUnderDevelopmentOverlay(
            theme = theme,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    theme: GlassTheme,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(theme.glassFill)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.4f),
                        theme.glassBorder,
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = theme.accentColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = theme.subtextColor.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = theme.textColor,
                    unfocusedTextColor = theme.textColor,
                    cursorColor = theme.accentColor
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("downloader_search_input")
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = theme.subtextColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(theme.accentColor.copy(alpha = 0.2f))
                        .clickable { onSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Go",
                        tint = theme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DownloaderInitialStateView(
    theme: GlassTheme,
    onSampleQueryClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassBox(
            theme = theme,
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("downloader_initial_card")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(theme.accentColor.copy(alpha = 0.2f))
                        .border(1.dp, theme.accentColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Music",
                        tint = theme.accentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Download Music",
                    color = theme.textColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "You can enter the song title and artist, for example:\nEminem – Rap God\n\nOr, you can send a direct link to the song, and I'll download it for you.",
                    color = theme.subtextColor,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Try sample searches:",
                    color = theme.textColor.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    listOf("Eminem – Rap God", "Dua Lipa – Levitating", "https://youtube.com/watch?v=sample").forEach { sample ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(theme.glassFill)
                                .border(1.dp, theme.glassBorder, RoundedCornerShape(16.dp))
                                .clickable { onSampleQueryClick(sample) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "• $sample",
                                color = theme.accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloaderConfirmInputView(
    query: String,
    theme: GlassTheme,
    onCancel: () -> Unit,
    onOk: () -> Unit
) {
    GlassBox(
        theme = theme,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloader_confirm_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.FindInPage,
                contentDescription = null,
                tint = theme.accentColor,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Search Query",
                color = theme.subtextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "\"$query\"",
                color = theme.accentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Is this the song title or link you want to search and download?",
                color = theme.textColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassButton(
                    text = "CANCEL",
                    onClick = onCancel,
                    theme = theme,
                    isHighlighted = false,
                    modifier = Modifier.weight(1f),
                    testTag = "downloader_cancel_button"
                )

                GlassButton(
                    text = "OK",
                    onClick = onOk,
                    theme = theme,
                    isHighlighted = true,
                    modifier = Modifier.weight(1f),
                    testTag = "downloader_ok_button"
                )
            }
        }
    }
}

@Composable
fun DownloaderSearchingView(
    theme: GlassTheme
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    GlassBox(
        theme = theme,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloader_searching_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.size(130.dp)
                )
            } else {
                CircularProgressIndicator(
                    color = theme.accentColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(56.dp)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Searching...",
                color = theme.textColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Scanning sources to find the highest quality audio file for you...",
                color = theme.subtextColor,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun DownloaderSearchResultsView(
    results: List<DownloaderSearchResult>,
    selectedItem: DownloaderSearchResult?,
    theme: GlassTheme,
    onSelectItem: (DownloaderSearchResult) -> Unit
) {
    GlassBox(
        theme = theme,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloader_results_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Choose the song you want",
                    color = theme.textColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${results.size} items",
                    color = theme.accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                results.forEach { item ->
                    val isSelected = selectedItem?.id == item.id
                    DownloaderSearchResultItem(
                        item = item,
                        isSelected = isSelected,
                        theme = theme,
                        onClick = { onSelectItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloaderSearchResultItem(
    item: DownloaderSearchResult,
    isSelected: Boolean,
    theme: GlassTheme,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else if (isSelected) 1.03f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "itemScale"
    )

    val backgroundBrush = if (isSelected) {
        Brush.horizontalGradient(
            listOf(
                theme.accentColor.copy(alpha = 0.28f),
                theme.glassFill
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                theme.glassFill,
                theme.glassFill.copy(alpha = 0.5f)
            )
        )
    }

    val borderBrush = if (isSelected) {
        Brush.linearGradient(
            listOf(
                theme.accentColor,
                theme.accentColor.copy(alpha = 0.4f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                theme.glassBorder,
                Color.White.copy(alpha = 0.05f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundBrush)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = theme.accentColor),
                onClick = onClick
            )
            .padding(12.dp)
            .testTag("downloader_result_item_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassArtworkCard(
                gradientIndex = item.gradientIndex,
                theme = theme,
                targetSize = 96,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = if (isSelected) theme.accentColor else theme.textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.artist,
                        color = theme.subtextColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " • ${item.duration}",
                        color = theme.subtextColor.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) theme.accentColor else theme.glassFill
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) theme.accentColor else theme.glassBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.FileDownload,
                    contentDescription = if (isSelected) "Selected" else "Download",
                    tint = if (isSelected) Color.White else theme.subtextColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun DownloaderDownloadingItemView(
    item: DownloaderSearchResult,
    progress: Float,
    theme: GlassTheme
) {
    GlassBox(
        theme = theme,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloader_downloading_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            GlassArtworkCard(
                gradientIndex = item.gradientIndex,
                theme = theme,
                targetSize = 128,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = item.title,
                color = theme.textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = item.artist,
                color = theme.subtextColor,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Downloading track...",
                    color = theme.textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = theme.accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = theme.accentColor,
                trackColor = theme.glassBorder
            )
        }
    }
}

@Composable
fun DownloaderCompleteView(
    item: DownloaderSearchResult,
    theme: GlassTheme,
    onDownloadAnother: () -> Unit
) {
    GlassBox(
        theme = theme,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloader_complete_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                theme.accentColor.copy(alpha = 0.4f),
                                theme.accentColor.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .border(1.5.dp, theme.accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = theme.accentColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassArtworkCard(
                gradientIndex = item.gradientIndex,
                theme = theme,
                targetSize = 128,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.title,
                color = theme.textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = item.artist,
                color = theme.subtextColor,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Download complete – the song has been added to your music library.",
                color = theme.textColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            GlassButton(
                text = "Download Another Song",
                icon = Icons.Default.Refresh,
                onClick = onDownloadAnother,
                theme = theme,
                isHighlighted = true,
                modifier = Modifier.fillMaxWidth(),
                testTag = "downloader_download_another_button"
            )
        }
    }
}

@Composable
fun DownloaderErrorNetworkView(
    theme: GlassTheme,
    onRetry: () -> Unit
) {
    GlassBox(
        theme = theme,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloader_error_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SignalWifiOff,
                    contentDescription = "Network Error",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Connection Error",
                color = theme.textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Dear user, it seems you are offline or connected without a VPN. Please check your connection and try again.",
                color = theme.subtextColor,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            GlassButton(
                text = "Retry",
                icon = Icons.Default.Refresh,
                onClick = onRetry,
                theme = theme,
                isHighlighted = true,
                modifier = Modifier.fillMaxWidth(),
                testTag = "downloader_retry_button"
            )
        }
    }
}

@Composable
fun DownloaderOutdatedEngineView(
    theme: GlassTheme,
    onUpdateEngine: () -> Unit
) {
    GlassBox(
        theme = theme,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloader_outdated_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Engine Outdated",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Outdated Downloader Engine",
                color = theme.textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Outdated Downloader Engine. An update is required to extract streams from YouTube.",
                color = theme.subtextColor,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            GlassButton(
                text = "Update Downloader Engine",
                icon = Icons.Default.DownloadForOffline,
                onClick = onUpdateEngine,
                theme = theme,
                isHighlighted = true,
                modifier = Modifier.fillMaxWidth(),
                testTag = "downloader_update_engine_button"
            )
        }
    }
}

@Composable
fun DownloaderEngineUpdatingView(
    theme: GlassTheme
) {
    GlassBox(
        theme = theme,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloader_updating_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            CircularProgressIndicator(
                color = theme.accentColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Updating Extractor Engine...",
                color = theme.textColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Downloading latest YouTube & audio stream rules from online repository...",
                color = theme.subtextColor,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun DownloaderUnderDevelopmentOverlay(
    theme: GlassTheme,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "overlay_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val floatTranslation by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatTranslation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Intercept touch events to lock the underlying screen */ }
            .background(
                Brush.verticalGradient(
                    listOf(
                        (if (theme.isLight) Color(0xFFF8FAFC) else Color.Black).copy(alpha = 0.65f),
                        theme.glassFill.copy(alpha = 0.88f),
                        (if (theme.isLight) Color(0xFFE2E8F0) else Color.Black).copy(alpha = 0.75f)
                    )
                )
            )
            .statusBarsPadding()
            .padding(bottom = 80.dp, start = 8.dp, end = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassBox(
            theme = theme,
            shape = RoundedCornerShape(32.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("downloader_under_development_card")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Badge: FEATURE IN PROGRESS
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.accentColor.copy(alpha = 0.15f))
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    theme.accentColor.copy(alpha = 0.6f),
                                    theme.accentColor.copy(alpha = 0.2f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(theme.accentColor)
                        )
                        Text(
                            text = "FEATURE IN PROGRESS",
                            color = theme.accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Glowing Neon Lock with subtle floating & pulse animation
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            translationY = floatTranslation
                        }
                ) {
                    // Outer Pulsing Neon Halo
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        theme.accentColor.copy(alpha = glowAlpha * 0.45f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Inner Glass Icon Container
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        theme.accentColor.copy(alpha = 0.25f),
                                        theme.glassFill
                                    )
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    listOf(
                                        theme.accentColor,
                                        theme.accentColor.copy(alpha = 0.35f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Feature Locked",
                            tint = theme.accentColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Heading
                Text(
                    text = "Under Development",
                    color = theme.textColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subtext
                Text(
                    text = "Direct audio extraction and downloading are currently undergoing scheduled upgrades. This module will be unlocked in an upcoming update.",
                    color = theme.subtextColor,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


