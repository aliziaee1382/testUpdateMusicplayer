package ir.ali0003.musicplayer.ui.glass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ir.ali0003.musicplayer.model.GlassTheme
import ir.ali0003.musicplayer.model.MusicIdentifyResult
import ir.ali0003.musicplayer.service.RecognitionResponse
import ir.ali0003.musicplayer.service.SoundRecognitionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

@Composable
fun SoundToolsScreen(
    theme: GlassTheme,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var identifiedResultForDialog by remember { mutableStateOf<MusicIdentifyResult?>(null) }
    var isSearchingAudio by remember { mutableStateOf(false) }
    var isAnalyzingVideo by remember { mutableStateOf(false) }
    var searchStatusText by remember { mutableStateOf<String?>(null) }

    // Video Picker Launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isAnalyzingVideo = true
            searchStatusText = "Extracting audio from video..."

            coroutineScope.launch(Dispatchers.IO) {
                val tempAudioFile = File(context.cacheDir, "video_audio_${System.currentTimeMillis()}.m4a")
                val extracted = extractAudioFromVideoUri(context, uri, tempAudioFile)

                if (extracted && tempAudioFile.exists() && tempAudioFile.length() > 0) {
                    withContext(Dispatchers.Main) {
                        searchStatusText = "Identifying music on server..."
                    }
                    val response = SoundRecognitionService.recognizeAudioFile(tempAudioFile)
                    tempAudioFile.delete()

                    withContext(Dispatchers.Main) {
                        isAnalyzingVideo = false
                        searchStatusText = null
                        when (response) {
                            is RecognitionResponse.Success -> {
                                identifiedResultForDialog = response.result
                            }
                            is RecognitionResponse.NotFound -> {
                                Toast.makeText(context, response.message, Toast.LENGTH_LONG).show()
                            }
                            is RecognitionResponse.Error -> {
                                Toast.makeText(context, response.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    tempAudioFile.delete()
                    withContext(Dispatchers.Main) {
                        isAnalyzingVideo = false
                        searchStatusText = null
                        Toast.makeText(context, "Could not extract audio track from video.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Centered Screen Title: "sound tools"
            Text(
                text = "sound tools",
                color = theme.textColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sound_tools_title")
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Section 1: Live Recording Capsule ("top and hold to search" with Mic on right)
            LiveRecordingCapsule(
                theme = theme,
                isSearching = isSearchingAudio,
                onStartSearch = { audioFile ->
                    isSearchingAudio = true
                    searchStatusText = "Identifying audio..."
                    coroutineScope.launch(Dispatchers.IO) {
                        val response = SoundRecognitionService.recognizeAudioFile(audioFile)
                        audioFile.delete()

                        withContext(Dispatchers.Main) {
                            isSearchingAudio = false
                            searchStatusText = null
                            when (response) {
                                is RecognitionResponse.Success -> {
                                    identifiedResultForDialog = response.result
                                }
                                is RecognitionResponse.NotFound -> {
                                    Toast.makeText(context, response.message, Toast.LENGTH_LONG).show()
                                }
                                is RecognitionResponse.Error -> {
                                    Toast.makeText(context, response.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: "find music from video" with "upload from gallery" button
            FindMusicFromVideoCard(
                theme = theme,
                isAnalyzing = isAnalyzingVideo,
                onUploadClicked = {
                    videoPickerLauncher.launch("video/*")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: "video To music" with upload -> arrow -> download buttons
            VideoToMusicCard(
                theme = theme,
                modifier = Modifier.fillMaxWidth()
            )

            // Status message during active search/analysis
            AnimatedVisibility(
                visible = isSearchingAudio || isAnalyzingVideo,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = theme.accentColor,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = searchStatusText ?: "Processing...",
                        color = theme.subtextColor,
                        fontSize = 13.sp
                    )
                }
            }

            // Bottom Spacer for navigation bar and mini player clearance
            Spacer(modifier = Modifier.height(120.dp))
        }

        // Identified Music Result Dialog
        identifiedResultForDialog?.let { result ->
            IdentifiedMusicDialog(
                result = result,
                theme = theme,
                onDismiss = {
                    identifiedResultForDialog = null
                }
            )
        }
    }
}

@Composable
private fun LiveRecordingCapsule(
    theme: GlassTheme,
    isSearching: Boolean,
    onStartSearch: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isHolding by remember { mutableStateOf(false) }
    var activeRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var currentRecordFile by remember { mutableStateOf<File?>(null) }
    var recordStartTime by remember { mutableLongStateOf(0L) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission is required to record audio.", Toast.LENGTH_SHORT).show()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isHolding) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "capsuleScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulseAnim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val softGreenFill = remember(theme) {
        Color(0x3310B981)
    }

    val softGreenBorder = remember(theme) {
        Color(0x6634D399)
    }

    val capsuleShape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(capsuleShape)
            .background(if (isHolding) softGreenFill.copy(alpha = pulseAlpha) else softGreenFill)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        softGreenBorder,
                        Color.White.copy(alpha = 0.2f),
                        softGreenBorder
                    )
                ),
                shape = capsuleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@detectTapGestures
                        }

                        if (isSearching) {
                            return@detectTapGestures
                        }

                        // Start Audio Recording
                        val recFile = File(context.cacheDir, "live_sample_${System.currentTimeMillis()}.m4a")
                        currentRecordFile = recFile
                        recordStartTime = System.currentTimeMillis()

                        var recorder: MediaRecorder? = null
                        try {
                            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }
                            recorder.apply {
                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                setAudioEncodingBitRate(128000)
                                setAudioSamplingRate(44100)
                                setOutputFile(recFile.absolutePath)
                                prepare()
                                start()
                            }
                            activeRecorder = recorder
                            isHolding = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            recorder?.release()
                            activeRecorder = null
                            isHolding = false
                            currentRecordFile = null
                            Toast.makeText(context, "Cannot start microphone recorder.", Toast.LENGTH_SHORT).show()
                            return@detectTapGestures
                        }

                        // Wait for release
                        val released = tryAwaitRelease()
                        isHolding = false

                        val durationMs = System.currentTimeMillis() - recordStartTime
                        val recordedFile = currentRecordFile

                        // Safely stop recorder
                        try {
                            activeRecorder?.stop()
                        } catch (e: Exception) {
                            // If stopped too early, recorder throws
                        } finally {
                            activeRecorder?.release()
                            activeRecorder = null
                        }

                        if (durationMs < 4000L) {
                            recordedFile?.delete()
                            currentRecordFile = null
                            Toast.makeText(
                                context,
                                "Hold longer to capture enough audio sample (at least 5 seconds)",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                            onStartSearch(recordedFile)
                        } else {
                            recordedFile?.delete()
                            currentRecordFile = null
                        }
                    }
                )
            }
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("sound_tools_mic_capsule"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when {
                    isHolding -> "listening... release when ready"
                    isSearching -> "identifying music..."
                    else -> "top and hold to search"
                },
                color = theme.textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0x3334D399))
                    .border(1.dp, Color(0x6634D399), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        color = theme.accentColor,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Search by Audio",
                        tint = theme.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FindMusicFromVideoCard(
    theme: GlassTheme,
    isAnalyzing: Boolean,
    onUploadClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(24.dp)

    GlassBox(
        theme = theme,
        shape = cardShape,
        contentPadding = PaddingValues(20.dp),
        modifier = modifier.testTag("sound_tools_find_music_card")
    ) {
        Text(
            text = "find music from video",
            color = theme.textColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Capsule button "upload from gallery"
        CapsuleActionButton(
            text = if (isAnalyzing) "analyzing video..." else "upload from gallery",
            icon = if (isAnalyzing) Icons.Default.HourglassEmpty else Icons.Default.Upload,
            theme = theme,
            onClick = onUploadClicked,
            enabled = !isAnalyzing,
            modifier = Modifier.fillMaxWidth(),
            testTag = "sound_tools_upload_gallery_btn"
        )
    }
}

@Composable
private fun VideoToMusicCard(
    theme: GlassTheme,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(24.dp)

    GlassBox(
        theme = theme,
        shape = cardShape,
        contentPadding = PaddingValues(20.dp),
        modifier = modifier.testTag("sound_tools_video_to_music_card")
    ) {
        Text(
            text = "video To music",
            color = theme.textColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Upload capsule button
            CapsuleActionButton(
                text = "upload",
                icon = Icons.Default.Upload,
                theme = theme,
                onClick = { /* Placeholder for conversion upload */ },
                modifier = Modifier.weight(1f),
                testTag = "sound_tools_convert_upload_btn"
            )

            // Arrow forward icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Convert",
                    tint = theme.accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Download capsule button
            CapsuleActionButton(
                text = "download",
                icon = Icons.Default.Download,
                theme = theme,
                onClick = { /* Placeholder for conversion download */ },
                modifier = Modifier.weight(1f),
                testTag = "sound_tools_convert_download_btn"
            )
        }
    }
}

@Composable
private fun CapsuleActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: GlassTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "capsuleBtnScale"
    )

    val capsuleShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(capsuleShape)
            .background(theme.glassFill)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        theme.glassBorder,
                        Color.White.copy(alpha = 0.15f),
                        theme.glassBorder
                    )
                ),
                shape = capsuleShape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = theme.accentColor),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) theme.accentColor else theme.subtextColor,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = text,
                color = if (enabled) theme.textColor else theme.subtextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Extracts up to 10 seconds of audio track from a video Uri into an output .m4a file
 * using Android standard MediaExtractor and MediaMuxer without external dependencies.
 */
private fun extractAudioFromVideoUri(
    context: Context,
    videoUri: Uri,
    outputFile: File,
    maxDurationUs: Long = 10_000_000L // 10 seconds in microseconds
): Boolean {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    try {
        extractor.setDataSource(context, videoUri, null)
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }

        if (audioTrackIndex < 0 || audioFormat == null) {
            return false
        }

        extractor.selectTrack(audioTrackIndex)

        if (outputFile.exists()) {
            outputFile.delete()
        }

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerAudioTrack = muxer.addTrack(audioFormat)
        muxer.start()

        val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
        } else {
            64 * 1024
        }
        val buffer = ByteBuffer.allocate(maxBufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        var firstSampleTimeUs: Long = -1L

        while (true) {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) {
                break
            }
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags

            if (firstSampleTimeUs < 0) {
                firstSampleTimeUs = bufferInfo.presentationTimeUs
            }

            if (bufferInfo.presentationTimeUs - firstSampleTimeUs > maxDurationUs) {
                break
            }

            muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
            extractor.advance()
        }
        return true
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    } finally {
        try {
            muxer?.stop()
        } catch (ignored: Exception) {}
        try {
            muxer?.release()
        } catch (ignored: Exception) {}
        try {
            extractor.release()
        } catch (ignored: Exception) {}
    }
}
