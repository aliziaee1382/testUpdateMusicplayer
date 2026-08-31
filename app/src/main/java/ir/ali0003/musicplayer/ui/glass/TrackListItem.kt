package ir.ali0003.musicplayer.ui.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.ali0003.musicplayer.model.GlassTheme
import ir.ali0003.musicplayer.model.ListItemSize
import ir.ali0003.musicplayer.model.Track

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: Track,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    listItemSize: ListItemSize = ListItemSize.SMALL,
    theme: GlassTheme,
    itemShape: Shape = RoundedCornerShape(12.dp),
    isLastInGroup: Boolean = false,
    showDivider: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onOpenAddToPlaylist: (() -> Unit)? = null,
    rankText: String? = null,
    testTag: String = "track_list_item_${track.id}"
) {
    val glassBg = remember(theme, isSelected) {
        if (isSelected) theme.accentColor.copy(alpha = 0.18f) else theme.glassFill
    }
    val titleColor = remember(isCurrent, isSelected, theme) {
        if (isSelected || isCurrent) theme.accentColor else theme.textColor
    }
    val subtextColor = remember(theme) { theme.subtextColor }
    val dividerColor = remember(theme) { theme.textColor.copy(alpha = 0.15f) }
    val artistAlbumText = remember(track.artist, track.album) { "${track.artist} • ${track.album}" }
    val formattedDurationText = remember(track.durationSeconds) { track.formattedDuration() }
    val coverSizeDp = remember(listItemSize) { listItemSize.coverSize }
    val iconSizeDp = remember(listItemSize) { (listItemSize.coverSizeDp * 0.58f).dp.coerceAtLeast(32.dp) }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable { onClick() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(itemShape)
                .background(glassBg)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 1.dp,
                            color = theme.accentColor.copy(alpha = 0.6f),
                            shape = itemShape
                        )
                    } else Modifier
                )
                .then(clickModifier)
                .padding(horizontal = 12.dp, vertical = listItemSize.verticalPadding)
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (rankText != null) {
                    Text(
                        text = rankText,
                        color = if (rankText == "#1") theme.accentColor else subtextColor,
                        fontSize = (listItemSize.titleSp - 1).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                }

                Box(contentAlignment = Alignment.Center) {
                    GlassArtworkCard(
                        gradientIndex = track.coverGradientIndex,
                        isPlaying = isCurrent && isPlaying,
                        imageUrl = track.albumArtUri,
                        trackId = track.id,
                        theme = theme,
                        targetSize = 128,
                        modifier = Modifier.size(coverSizeDp)
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(coverSizeDp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.accentColor.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(coverSizeDp * 0.55f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = titleColor,
                        fontSize = listItemSize.titleSize,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = artistAlbumText,
                        color = subtextColor,
                        fontSize = listItemSize.subtitleSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = formattedDurationText,
                    color = subtextColor,
                    fontSize = listItemSize.subtitleSize
                )

                Spacer(modifier = Modifier.width(6.dp))

                if (onOpenAddToPlaylist != null) {
                    GlassIconButton(
                        icon = Icons.Default.PlaylistAdd,
                        contentDescription = "Add to Playlist",
                        onClick = onOpenAddToPlaylist,
                        theme = theme,
                        size = iconSizeDp,
                        testTag = "add_playlist_button_${track.id}"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (onToggleFavorite != null) {
                    GlassIconButton(
                        icon = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        onClick = onToggleFavorite,
                        isActive = track.isFavorite,
                        tint = if (track.isFavorite) Color(0xFFEF4444) else subtextColor,
                        theme = theme,
                        size = iconSizeDp,
                        testTag = "favorite_button_${track.id}"
                    )
                }
            }
        }

        if (showDivider && !isLastInGroup) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = dividerColor
            )
        }
    }
}

