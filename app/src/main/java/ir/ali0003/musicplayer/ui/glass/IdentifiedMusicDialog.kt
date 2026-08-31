package ir.ali0003.musicplayer.ui.glass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import ir.ali0003.musicplayer.model.GlassTheme
import ir.ali0003.musicplayer.model.MusicIdentifyResult

private fun isValidWebUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val trimmed = url.trim()
    return (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) 
           && trimmed.lowercase() != "null"
}

@Composable
fun IdentifiedMusicDialog(
    result: MusicIdentifyResult,
    theme: GlassTheme,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var animateIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animateIn = true
    }

    val scale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dialogScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(220),
        label = "dialogAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .testTag("identified_music_dialog"),
            contentAlignment = Alignment.Center
        ) {
            val containerShape = RoundedCornerShape(28.dp)
            val dialogScrollState = rememberScrollState()
            
            val finalYoutubeUrl = remember(result.youtubeUrl, result.title, result.artist) {
                if (isValidWebUrl(result.youtubeUrl)) {
                    result.youtubeUrl!!.trim()
                } else {
                    "https://www.google.com/search?q=" + Uri.encode("${result.title} ${result.artist} youtube")
                }
            }

            val finalSpotifyUrl = remember(result.spotifyUrl, result.title, result.artist) {
                if (isValidWebUrl(result.spotifyUrl)) {
                    result.spotifyUrl!!.trim()
                } else {
                    "https://www.google.com/search?q=" + Uri.encode("${result.title} ${result.artist} spotify")
                }
            }

            val googleSearchUrl = remember(result.title, result.artist) {
                "https://www.google.com/search?q=" + Uri.encode("${result.title} ${result.artist}")
            }

            // Main container card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .clip(containerShape)
                    .background(theme.glassFill.copy(alpha = 0.94f))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                theme.glassBorder.copy(alpha = 0.7f),
                                Color(0x3334D399),
                                theme.glassBorder.copy(alpha = 0.4f)
                            )
                        ),
                        shape = containerShape
                    )
                    .verticalScroll(dialogScrollState)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header with close icon button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("dialog_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.subtextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Album Cover (160x160dp with RoundedCornerShape 18dp)
                val coverShape = RoundedCornerShape(18.dp)
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(coverShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF1E293B),
                                    Color(0xFF0F172A),
                                    theme.accentColor.copy(alpha = 0.35f)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.15f), coverShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!result.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = result.coverUrl,
                            contentDescription = result.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Music Cover",
                            tint = theme.accentColor,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Music Title (Uppercase, Bold, 22sp, White/textColor)
                Text(
                    text = result.title.uppercase(),
                    color = theme.textColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("identified_track_title")
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Artist Name (15sp, subtextColor)
                Text(
                    text = result.artist,
                    color = theme.subtextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("identified_track_artist")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Copyright notice (exact English text, 12sp, soft color)
                Text(
                    text = "Due to copyright regulations, direct download is not available. You can stream this track on the platforms below.",
                    color = theme.subtextColor.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // YouTube Capsule Action Button
                PlatformStreamButton(
                    platformName = "youtube",
                    icon = Icons.Default.PlayArrow,
                    backgroundColor = Color(0xFFFF0000),
                    url = finalYoutubeUrl,
                    toastMessage = "YouTube link copied to clipboard",
                    context = context,
                    testTag = "btn_open_youtube",
                    copyTestTag = "btn_copy_youtube"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Spotify Capsule Action Button
                PlatformStreamButton(
                    platformName = "Spotify®",
                    icon = Icons.Default.MusicNote,
                    backgroundColor = Color(0xFF1DB954),
                    url = finalSpotifyUrl,
                    toastMessage = "Spotify link copied to clipboard",
                    context = context,
                    testTag = "btn_open_spotify",
                    copyTestTag = "btn_copy_spotify"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Google Search Capsule Action Button
                PlatformStreamButton(
                    platformName = "Search on Google",
                    icon = Icons.Default.Search,
                    backgroundColor = Color(0xFF2563EB),
                    url = googleSearchUrl,
                    toastMessage = "Google search link copied to clipboard",
                    context = context,
                    testTag = "btn_open_google",
                    copyTestTag = "btn_copy_google"
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PlatformStreamButton(
    platformName: String,
    icon: ImageVector,
    backgroundColor: Color,
    url: String?,
    toastMessage: String,
    context: Context,
    testTag: String,
    copyTestTag: String
) {
    val buttonShape = RoundedCornerShape(20.dp)
    val targetUrl = remember(url, platformName) {
        url?.takeIf { it.isNotBlank() } ?: when (platformName) {
            "youtube" -> "https://www.youtube.com"
            "Spotify®" -> "https://open.spotify.com"
            else -> "https://www.google.com"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(buttonShape)
            .background(backgroundColor)
            .border(1.dp, Color.White.copy(alpha = 0.25f), buttonShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left clickable area: Open URL in Browser / App
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White),
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                .padding(start = 20.dp, end = 8.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = platformName,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Subtle vertical divider between open and copy
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(26.dp)
                .background(Color.White.copy(alpha = 0.25f))
        )

        // Right clickable area: Copy link to Clipboard
        Box(
            modifier = Modifier
                .width(54.dp)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(platformName, targetUrl)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                    }
                )
                .testTag(copyTestTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $platformName link",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
