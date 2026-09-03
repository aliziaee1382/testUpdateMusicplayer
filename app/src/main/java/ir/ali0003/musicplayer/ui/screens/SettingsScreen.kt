package ir.ali0003.musicplayer.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.ali0003.musicplayer.model.AppUpdateInfo
import ir.ali0003.musicplayer.model.GlassTheme
import ir.ali0003.musicplayer.model.ListItemSize
import ir.ali0003.musicplayer.ui.glass.GlassButton
import ir.ali0003.musicplayer.ui.glass.GlassCard
import ir.ali0003.musicplayer.ui.glass.GlassSlider
import ir.ali0003.musicplayer.ui.glass.UpdateAvailableDialog
import ir.ali0003.musicplayer.ui.glass.UpdateCheckingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    theme: GlassTheme,
    minDurationFilterSeconds: Int = 0,
    onMinDurationFilterChange: (Int) -> Unit = {},
    listItemSize: ListItemSize = ListItemSize.SMALL,
    onListItemSizeChange: (ListItemSize) -> Unit = {},
    isDynamicBgEnabled: Boolean = true,
    onDynamicBgChange: (Boolean) -> Unit = {},
    onOpenThemeSelector: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onScanLocalMusic: (() -> Unit)? = null,
    onOpenHiddenTracks: (() -> Unit)? = null,
    hiddenCount: Int = 0,
    onOpenHiddenFolders: (() -> Unit)? = null,
    hiddenFoldersCount: Int = 0,
    scrollToTopTrigger: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }

    val (versionName, versionCode) = remember(context) {
        try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val vName = pInfo.versionName ?: "1.0"
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            Pair(vName, vCode)
        } catch (e: Exception) {
            Pair("1.0", 1L)
        }
    }

    fun checkForUpdates() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url("https://chat0003.ir/0003player/version.json")
                    .header("User-Agent", "0003Player-Android")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        isCheckingUpdate = false
                        Toast.makeText(context, "Unable to check for updates. Please try again later.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val body = response.body?.string() ?: ""
                val json = org.json.JSONObject(body)

                val latestCode = json.optInt("latest_version_code", json.optInt("version_code", json.optInt("versionCode", 0)))
                val latestName = json.optString("latest_version_name", json.optString("version_name", json.optString("versionName", "")))
                val downloadUrl = json.optString("download_url", json.optString("downloadUrl", ""))

                val changelogList = mutableListOf<String>()
                val changelogArray = json.optJSONArray("change_log") ?: json.optJSONArray("changelog")
                if (changelogArray != null) {
                    for (i in 0 until changelogArray.length()) {
                        val entry = changelogArray.optString(i)
                        if (entry.isNotBlank()) changelogList.add(entry.trim())
                    }
                } else {
                    val changelogStr = json.optString("change_log", json.optString("changelog", ""))
                    if (changelogStr.isNotBlank()) {
                        changelogList.addAll(
                            changelogStr.split("\n")
                                .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                                .filter { it.isNotEmpty() }
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    isCheckingUpdate = false
                    if (latestCode > versionCode) {
                        availableUpdateInfo = AppUpdateInfo(
                            latestVersionCode = latestCode,
                            latestVersionName = latestName.ifBlank { "1.0" },
                            downloadUrl = downloadUrl,
                            changelog = changelogList
                        )
                    } else {
                        Toast.makeText(context, "You have the latest version (v$versionName).", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isCheckingUpdate = false
                    Toast.makeText(context, "Unable to check for updates. Please try again later.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen_column"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Settings & Appearance",
                    color = theme.textColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Customize themes, sound effects, equalizer & audio settings",
                    color = theme.subtextColor,
                    fontSize = 13.sp
                )
            }
        }

        // Section 1: Visual Theme & Appearance
        item {
            Text(
                text = "APPEARANCE & THEME",
                color = theme.subtextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(
                    onClick = onOpenThemeSelector,
                    theme = theme,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings_theme_selector_card"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(theme.accentColor, theme.glowColor)
                                        )
                                    )
                                    .border(1.5.dp, theme.glassBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Glass Theme Customization",
                                    color = theme.textColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${theme.colorNameEn} • ${if (theme.isLight) "Light Mode" else "Dark Mode"}",
                                    color = theme.accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Select Theme",
                            tint = theme.subtextColor
                        )
                    }
                }

                GlassCard(
                    onClick = { onDynamicBgChange(!isDynamicBgEnabled) },
                    theme = theme,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings_dynamic_bg_card"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(theme.accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = theme.accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Animated Background",
                                    color = theme.textColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Enable smooth ambient background motion",
                                    color = theme.subtextColor,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Switch(
                            checked = isDynamicBgEnabled,
                            onCheckedChange = { onDynamicBgChange(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = theme.accentColor,
                                uncheckedThumbColor = theme.subtextColor,
                                uncheckedTrackColor = theme.textColor.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }

        // Section 1.5: Song List Display Size (Grid/List Item Density)
        item {
            Text(
                text = "SONG LIST DISPLAY SIZE",
                color = theme.subtextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            GlassCard(
                onClick = {},
                theme = theme,
                modifier = Modifier.fillMaxWidth(),
                testTag = "list_item_density_card"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(theme.accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = theme.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Track Card Density",
                                color = theme.textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Current: ${listItemSize.labelEn} • Cover ${listItemSize.coverSizeDp}dp",
                                color = theme.subtextColor,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.textColor.copy(alpha = 0.08f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ListItemSize.entries.forEach { size ->
                            val isSelected = listItemSize == size
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) theme.accentColor else Color.Transparent
                                    )
                                    .clickable { onListItemSizeChange(size) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = size.labelEn,
                                    color = if (isSelected) Color.White else theme.textColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Equalizer & Sound Customization
        item {
            Text(
                text = "SOUND & EQUALIZER",
                color = theme.subtextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            GlassCard(
                onClick = onOpenEqualizer,
                theme = theme,
                modifier = Modifier.fillMaxWidth(),
                testTag = "settings_equalizer_card"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(theme.accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = theme.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Audio Equalizer & Presets",
                                color = theme.textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Bass Boost, Treble & 5-Band EQ",
                                color = theme.subtextColor,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Equalizer",
                        tint = theme.subtextColor
                    )
                }
            }
        }

        // Section 3.5: Music Duration Filter
        item {
            Text(
                text = "MUSIC FILTERING",
                color = theme.subtextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            GlassCard(
                onClick = {},
                theme = theme,
                modifier = Modifier.fillMaxWidth(),
                testTag = "music_duration_filter_card"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = theme.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Minimum Track Duration",
                                    color = theme.textColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (minDurationFilterSeconds > 0)
                                        "Filter tracks shorter than $minDurationFilterSeconds seconds"
                                    else
                                        "No duration filter applied",
                                    color = theme.subtextColor,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Text(
                            text = "${minDurationFilterSeconds}s",
                            color = theme.accentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassSlider(
                        value = minDurationFilterSeconds.toFloat(),
                        onValueChange = { newValue ->
                            onMinDurationFilterChange(newValue.toInt().coerceIn(1, 100))
                        },
                        valueRange = 1f..100f,
                        theme = theme,
                        testTag = "duration_filter_slider"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1s",
                            color = theme.subtextColor,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "100s",
                            color = theme.subtextColor,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Section 4: Device Storage & Scan
        if (onScanLocalMusic != null) {
            item {
                Text(
                    text = "STORAGE & LOCAL MEDIA",
                    color = theme.subtextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassCard(
                        onClick = { onScanLocalMusic.invoke() },
                        theme = theme,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "settings_scan_storage_card"
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = theme.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Smart Auto-Scan Active",
                                        color = theme.textColor,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "New tracks are scanned automatically every app launch",
                                        color = theme.subtextColor,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Scan",
                                tint = theme.subtextColor
                            )
                        }
                    }

                    if (onOpenHiddenTracks != null) {
                        GlassCard(
                            onClick = { onOpenHiddenTracks.invoke() },
                            theme = theme,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "settings_hidden_tracks_card"
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = theme.accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "Hidden Tracks",
                                            color = theme.textColor,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (hiddenCount > 0) "$hiddenCount track(s) hidden" else "No hidden tracks",
                                            color = theme.subtextColor,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open",
                                    tint = theme.subtextColor
                                )
                            }
                        }
                    }

                    if (onOpenHiddenFolders != null) {
                        GlassCard(
                            onClick = { onOpenHiddenFolders.invoke() },
                            theme = theme,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "settings_hidden_folders_card"
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOff,
                                        contentDescription = null,
                                        tint = theme.accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "Hidden Folders",
                                            color = theme.textColor,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (hiddenFoldersCount > 0) "$hiddenFoldersCount folder(s) hidden" else "No hidden folders",
                                            color = theme.subtextColor,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open",
                                    tint = theme.subtextColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 5: About & Updates
        item {
            Text(
                text = "ABOUT & UPDATES",
                color = theme.subtextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(
                    onClick = { checkForUpdates() },
                    theme = theme,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings_check_updates_card"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = theme.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Check for Updates",
                                    color = theme.textColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Check for newer versions and features",
                                    color = theme.subtextColor,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Check for Updates",
                            tint = theme.subtextColor
                        )
                    }
                }
            }
        }

        // Donation / Support Button
        item {
            val supportUrl = "https://daramet.com/Aliziaee1382"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(theme.glassFill)
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                theme.glassBorder,
                                theme.accentColor.copy(alpha = 0.3f),
                                theme.glassBorder
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = theme.accentColor)
                    ) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .testTag("settings_donate_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "اگه حال میکنی حمایت کن 💰",
                        color = theme.textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Dynamic Version Footer
        item {
            Text(
                text = "Version $versionName (Build $versionCode)",
                color = theme.subtextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
    }

    if (isCheckingUpdate) {
        UpdateCheckingDialog(
            theme = theme,
            onDismiss = { isCheckingUpdate = false }
        )
    }

    availableUpdateInfo?.let { updateInfo ->
        UpdateAvailableDialog(
            currentVersionName = versionName,
            updateInfo = updateInfo,
            onDownload = { url ->
                if (url.isNotBlank()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open download link.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { availableUpdateInfo = null },
            theme = theme
        )
    }
}
