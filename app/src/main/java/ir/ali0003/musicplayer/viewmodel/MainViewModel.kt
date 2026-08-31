package ir.ali0003.musicplayer.viewmodel

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.ali0003.musicplayer.data.local.AppDatabase
import ir.ali0003.musicplayer.data.local.MusicRepository
import ir.ali0003.musicplayer.data.local.ScanProgress
import ir.ali0003.musicplayer.data.local.UserPreferencesEntity
import ir.ali0003.musicplayer.model.*
import ir.ali0003.musicplayer.player.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository
    private val localAudioScanner: ir.ali0003.musicplayer.data.local.LocalAudioScanner
    val playerManager: AudioPlayerManager

    // ViewModel UI States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _activeNavTab = MutableStateFlow("Home") // Home, Explore, Library, VIP
    val activeNavTab: StateFlow<String> = _activeNavTab.asStateFlow()

    private val _librarySortTab = MutableStateFlow("Playlists") // Playlists, Songs, Albums, Artists, Folders
    val librarySortTab: StateFlow<String> = _librarySortTab.asStateFlow()

    // Dialog States
    private val _showEqualizer = MutableStateFlow(false)
    val showEqualizer: StateFlow<Boolean> = _showEqualizer.asStateFlow()

    private val _showSleepTimer = MutableStateFlow(false)
    val showSleepTimer: StateFlow<Boolean> = _showSleepTimer.asStateFlow()

    private val _showThemeSelector = MutableStateFlow(false)
    val showThemeSelector: StateFlow<Boolean> = _showThemeSelector.asStateFlow()

    private val _showCreatePlaylist = MutableStateFlow(false)
    val showCreatePlaylist: StateFlow<Boolean> = _showCreatePlaylist.asStateFlow()

    private val _targetTrackForPlaylist = MutableStateFlow<Track?>(null)
    val targetTrackForPlaylist: StateFlow<Track?> = _targetTrackForPlaylist.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

    // IntentSender for Android 11+ MediaStore Delete Permission
    private val _deleteIntentSender = MutableSharedFlow<IntentSenderRequest>()
    val deleteIntentSender: SharedFlow<IntentSenderRequest> = _deleteIntentSender.asSharedFlow()

    private var pendingDeleteTrackId: Long? = null

    // Data Flows from Repository
    val minDurationFilter: StateFlow<Int>
    val allTracks: StateFlow<List<Track>>
    val hiddenTracks: StateFlow<List<Track>>
    val favoriteTracks: StateFlow<List<Track>>
    val recentlyPlayed: StateFlow<List<Track>>
    val allPlaylists: StateFlow<List<Playlist>>
    val selectedPlaylistTracks: StateFlow<List<Track>>
    val sampleFolders: List<AudioFolder>

    val sortCriterion: StateFlow<TrackSortCriterion>
    val sortOrder: StateFlow<TrackSortOrder>
    val isAutoSystemTheme: StateFlow<Boolean>
    val listItemSize: StateFlow<ListItemSize>
    val isDynamicBgEnabled: StateFlow<Boolean>

    private val _editingTrack = MutableStateFlow<Track?>(null)
    val editingTrack: StateFlow<Track?> = _editingTrack.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).musicDao()
        repository = MusicRepository(dao)
        localAudioScanner = ir.ali0003.musicplayer.data.local.LocalAudioScanner(application)
        playerManager = AudioPlayerManager.getInstance(application)
        playerManager.onFlushListeningTimeListener = { trackId, seconds ->
            viewModelScope.launch(Dispatchers.IO) {
                repository.addListeningTime(trackId, seconds)
            }
        }
        playerManager.onPlaybackStateChanged = { trackId, posMs, queueIds ->
            viewModelScope.launch {
                repository.updatePlaybackState(trackId, posMs, queueIds.joinToString(","))
            }
        }
        sampleFolders = repository.getSampleFolders()

        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }

        val userPrefsFlow = repository.userPreferences.filterNotNull()

        isAutoSystemTheme = userPrefsFlow
            .map { it.isAutoSystemTheme }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                true
            )

        minDurationFilter = userPrefsFlow
            .map { it.minDurationFilterSeconds }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                0
            )

        listItemSize = userPrefsFlow
            .map { ListItemSize.fromName(it.listItemSize) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                ListItemSize.SMALL
            )

        isDynamicBgEnabled = userPrefsFlow
            .map { it.isDynamicBgEnabled }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                true
            )

        allTracks = combine(repository.allTracks, minDurationFilter) { tracks, minSecs ->
            if (minSecs > 0) tracks.filter { it.durationSeconds >= minSecs } else tracks
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        hiddenTracks = repository.hiddenTracks.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        favoriteTracks = combine(repository.favoriteTracks, minDurationFilter) { tracks, minSecs ->
            if (minSecs > 0) tracks.filter { it.durationSeconds >= minSecs } else tracks
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        recentlyPlayed = combine(repository.recentlyPlayed, minDurationFilter) { tracks, minSecs ->
            if (minSecs > 0) tracks.filter { it.durationSeconds >= minSecs } else tracks
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allPlaylists = repository.allPlaylists.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        @OptIn(ExperimentalCoroutinesApi::class)
        selectedPlaylistTracks = combine(
            _selectedPlaylist.flatMapLatest { playlist ->
                if (playlist == null) flowOf(emptyList())
                else repository.getTracksForPlaylist(playlist.id)
            },
            minDurationFilter
        ) { tracks, minSecs ->
            if (minSecs > 0) tracks.filter { it.durationSeconds >= minSecs } else tracks
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        sortCriterion = userPrefsFlow.map { prefs ->
            try {
                TrackSortCriterion.valueOf(prefs.sortCriterion)
            } catch (e: Exception) {
                TrackSortCriterion.DATE_ADDED
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TrackSortCriterion.DATE_ADDED
        )

        sortOrder = userPrefsFlow.map { prefs ->
            try {
                TrackSortOrder.valueOf(prefs.sortOrder)
            } catch (e: Exception) {
                TrackSortOrder.DESCENDING
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TrackSortOrder.DESCENDING
        )

        // Observe user preferences
        viewModelScope.launch {
            repository.userPreferences.collect { prefs ->
                if (prefs != null) {
                    val theme = GlassTheme.ALL_THEMES.find { it.id == prefs.activeThemeId }
                        ?: GlassTheme.DarkGreen
                    playerManager.setTheme(theme)

                    // Restore equalizer settings
                    val preset = EqualizerPreset.PRESETS.find { !it.name.equals("Custom", ignoreCase = true) && it.name.equals(prefs.eqPresetName, ignoreCase = true) }
                    if (preset != null) {
                        playerManager.setEqualizerPreset(preset)
                    } else if (prefs.eqPresetName.equals("Custom", ignoreCase = true)) {
                        playerManager.setCustomGains(listOf(prefs.eq60Hz, prefs.eq230Hz, prefs.eq910Hz, prefs.eq3600Hz, prefs.eq14000Hz))
                    }

                    // Restore UI selections if default
                    if (_selectedCategory.value == "All" && prefs.lastSelectedCategory.isNotBlank()) {
                        _selectedCategory.value = prefs.lastSelectedCategory
                    }
                    if (_activeNavTab.value == "Home" && prefs.lastActiveNavTab.isNotBlank()) {
                        _activeNavTab.value = prefs.lastActiveNavTab
                    }
                    if (_librarySortTab.value == "Playlists" && prefs.lastLibrarySortTab.isNotBlank()) {
                        _librarySortTab.value = prefs.lastLibrarySortTab
                    }
                }
            }
        }

        // Restore playback state on startup only if no active service/player is running
        viewModelScope.launch {
            var hasRestored = false
            combine(allTracks, repository.userPreferences.filterNotNull()) { tracks, prefs ->
                Pair(tracks, prefs)
            }.collect { (tracks, prefs) ->
                if (!hasRestored && tracks.isNotEmpty() && prefs.lastPlayedTrackId != -1L) {
                    if (playerManager.isPlaying.value || playerManager.currentTrack.value != null) {
                        // Sync with active background service / player instance directly
                        hasRestored = true
                    } else {
                        val trackId = prefs.lastPlayedTrackId
                        val posMs = prefs.lastPlaybackPositionMs.toInt()
                        val queueIds = prefs.lastQueueTrackIds.split(",").mapNotNull { it.trim().toLongOrNull() }

                        val tracksMap = tracks.associateBy { it.id }
                        val restoredQueue = if (queueIds.isNotEmpty()) {
                            queueIds.mapNotNull { tracksMap[it] }.ifEmpty { tracks }
                        } else {
                            tracks
                        }
                        val trackIndex = restoredQueue.indexOfFirst { it.id == trackId }
                        if (trackIndex != -1) {
                            playerManager.restorePlaybackState(restoredQueue, trackIndex, posMs)
                            hasRestored = true
                        }
                    }
                }
            }
        }
    }

    fun setSortPreference(criterion: TrackSortCriterion, order: TrackSortOrder) {
        viewModelScope.launch {
            repository.updateSortPreferences(criterion.name, order.name)
        }
    }

    private var scanJob: Job? = null

    fun scanAndLoadLocalAudio(forceRescanAll: Boolean = false) {
        if (scanJob?.isActive == true && _isScanning.value) {
            return
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _scanProgress.value = null
            try {
                val existingIds = if (forceRescanAll) emptySet() else repository.getExistingTrackIds()
                localAudioScanner.scanLocalTracksFlow(existingTrackIds = existingIds, chunkSize = 100)
                    .catch { e -> e.printStackTrace() }
                    .onCompletion {
                        _isScanning.value = false
                    }
                    .collect { batch ->
                        _scanProgress.value = batch.progress
                        if (batch.tracks.isNotEmpty()) {
                            repository.insertLocalTracks(batch.tracks)
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
        viewModelScope.launch {
            repository.updateCategoryPreference(category)
        }
    }

    fun setActiveNavTab(tab: String) {
        _activeNavTab.value = tab
        viewModelScope.launch {
            repository.updateActiveNavTabPreference(tab)
        }
    }

    fun setLibrarySortTab(tab: String) {
        _librarySortTab.value = tab
        viewModelScope.launch {
            repository.updateLibrarySortTabPreference(tab)
        }
    }

    fun toggleNowPlayingExpanded() {
        _isNowPlayingExpanded.value = !_isNowPlayingExpanded.value
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
    }

    // Dialog toggles
    fun setShowEqualizer(show: Boolean) { _showEqualizer.value = show }
    fun setShowSleepTimer(show: Boolean) { _showSleepTimer.value = show }
    fun setShowThemeSelector(show: Boolean) { _showThemeSelector.value = show }
    fun setShowCreatePlaylist(show: Boolean) { _showCreatePlaylist.value = show }

    fun openAddToPlaylistForTrack(track: Track?) {
        _targetTrackForPlaylist.value = track
    }

    // Player Actions
    fun playShuffleAll(currentContextList: List<Track>? = null) {
        val tracks = if (!currentContextList.isNullOrEmpty()) currentContextList else allTracks.value
        if (tracks.isEmpty()) return
        playerManager.setShuffle(true)
        val randomIndex = tracks.indices.random()
        val selectedTrack = tracks[randomIndex]
        playerManager.setQueue(tracks, randomIndex)
        viewModelScope.launch {
            repository.recordPlayed(selectedTrack.id)
        }
    }

    fun playTrack(track: Track, currentContextList: List<Track>? = null) {
        val tracks = if (!currentContextList.isNullOrEmpty()) currentContextList else allTracks.value
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index != -1) {
            playerManager.setQueue(tracks, index)
        } else {
            val fallbackIndex = allTracks.value.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            playerManager.setQueue(allTracks.value, fallbackIndex)
        }
        viewModelScope.launch {
            repository.recordPlayed(track.id)
        }
    }

    fun openEditTrack(track: Track?) {
        _editingTrack.value = track
    }

    fun updateTrackInfo(trackId: Long, title: String, artist: String, album: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val track = allTracks.value.find { it.id == trackId }
            if (track != null) {
                updateAudioFileMetadata(track, title, artist, album)
            }
            repository.updateTrackInfo(trackId, title, artist, album)
            if (playerManager.currentTrack.value?.id == trackId) {
                playerManager.updateCurrentTrackInfo(title, artist, album)
            }
            _editingTrack.value = null
        }
    }

    private fun updateAudioFileMetadata(track: Track, title: String, artist: String, album: String) {
        val context = getApplication<Application>().applicationContext
        try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.TITLE, title)
                put(android.provider.MediaStore.Audio.Media.ARTIST, artist)
                put(android.provider.MediaStore.Audio.Media.ALBUM, album)
            }

            if (track.audioUrl.startsWith("content://")) {
                val uri = android.net.Uri.parse(track.audioUrl)
                context.contentResolver.update(uri, values, null, null)
            } else {
                val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val updated = context.contentResolver.update(
                    uri,
                    values,
                    "${android.provider.MediaStore.Audio.Media.DATA} = ?",
                    arrayOf(track.audioUrl)
                )
                if (updated == 0) {
                    context.contentResolver.update(
                        uri,
                        values,
                        "${android.provider.MediaStore.Audio.Media._ID} = ?",
                        arrayOf(track.id.toString())
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playNext(tracks: List<Track>) {
        playerManager.playNext(tracks)
    }

    fun hideTrack(trackId: Long) {
        viewModelScope.launch {
            repository.hideTrack(trackId)
            playerManager.removeTrackFromQueue(trackId)
        }
    }

    fun hideTracks(trackIds: List<Long>) {
        viewModelScope.launch {
            trackIds.forEach { id ->
                repository.hideTrack(id)
                playerManager.removeTrackFromQueue(id)
            }
        }
    }

    fun unhideTrack(trackId: Long) {
        viewModelScope.launch {
            repository.unhideTrack(trackId)
        }
    }

    fun unhideAllTracks() {
        viewModelScope.launch {
            repository.unhideAllTracks()
        }
    }

    fun deleteTrack(trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val track = allTracks.value.find { it.id == trackId } ?: return@launch
            pendingDeleteTrackId = trackId
            val context = getApplication<Application>().applicationContext

            val uri: Uri = if (track.audioUrl.startsWith("content://")) {
                Uri.parse(track.audioUrl)
            } else {
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                    val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    _deleteIntentSender.emit(intentSenderRequest)
                } catch (e: Exception) {
                    e.printStackTrace()
                    deleteAudioFileFromDevice(track)
                    confirmDeleteTrack(trackId)
                }
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                try {
                    val deletedRows = context.contentResolver.delete(uri, null, null)
                    if (deletedRows > 0) {
                        confirmDeleteTrack(trackId)
                    } else {
                        deleteAudioFileFromDevice(track)
                        confirmDeleteTrack(trackId)
                    }
                } catch (securityException: SecurityException) {
                    if (securityException is android.app.RecoverableSecurityException) {
                        val intentSender = securityException.userAction.actionIntent.intentSender
                        val intentSenderRequest = IntentSenderRequest.Builder(intentSender).build()
                        _deleteIntentSender.emit(intentSenderRequest)
                    } else {
                        securityException.printStackTrace()
                        deleteAudioFileFromDevice(track)
                        confirmDeleteTrack(trackId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    deleteAudioFileFromDevice(track)
                    confirmDeleteTrack(trackId)
                }
            } else {
                // Android 9 and lower
                deleteAudioFileFromDevice(track)
                confirmDeleteTrack(trackId)
            }
        }
    }

    fun confirmDeleteTrack(trackId: Long = pendingDeleteTrackId ?: -1L) {
        if (trackId <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            val track = allTracks.value.find { it.id == trackId }
            if (track != null) {
                try {
                    if (track.audioUrl.isNotBlank() && !track.audioUrl.startsWith("content://")) {
                        val file = java.io.File(track.audioUrl)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                } catch (_: Exception) {}
            }
            repository.deleteTrack(trackId)
            playerManager.removeTrackFromQueue(trackId)
            if (pendingDeleteTrackId == trackId) {
                pendingDeleteTrackId = null
            }
        }
    }

    fun cancelDeleteTrack() {
        pendingDeleteTrackId = null
    }

    private fun deleteAudioFileFromDevice(track: Track) {
        val context = getApplication<Application>().applicationContext
        try {
            if (track.audioUrl.isNotBlank() && !track.audioUrl.startsWith("content://")) {
                val file = java.io.File(track.audioUrl)
                if (file.exists()) {
                    file.delete()
                }
            }

            if (track.audioUrl.startsWith("content://")) {
                val uri = Uri.parse(track.audioUrl)
                context.contentResolver.delete(uri, null, null)
            } else {
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                context.contentResolver.delete(
                    uri,
                    "${MediaStore.Audio.Media.DATA} = ? OR ${MediaStore.Audio.Media._ID} = ?",
                    arrayOf(track.audioUrl, track.id.toString())
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val newIsFavorite = !track.isFavorite
            repository.toggleFavorite(track)
            if (playerManager.currentTrack.value?.id == track.id) {
                playerManager.updateCurrentTrackFavorite(newIsFavorite)
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
        viewModelScope.launch {
            trackIds.forEach { id ->
                repository.addTrackToPlaylist(playlistId, id)
            }
        }
    }

    fun setSelectedPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun removeTracksFromPlaylist(playlistId: Long, trackIds: List<Long>) {
        viewModelScope.launch {
            repository.removeTracksFromPlaylist(playlistId, trackIds)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
            }
        }
    }

    fun playPlaylistQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        playerManager.setQueue(tracks, startIndex)
        viewModelScope.launch {
            repository.recordPlayed(tracks[startIndex].id)
        }
    }

    fun selectTheme(theme: GlassTheme, isAutoSystemTheme: Boolean = false) {
        playerManager.setTheme(theme)
        viewModelScope.launch {
            repository.updateThemePreference(theme.id, isAutoSystemTheme)
        }
    }

    fun setAutoSystemTheme(isAuto: Boolean) {
        viewModelScope.launch {
            repository.updateAutoSystemThemePreference(isAuto)
        }
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        playerManager.setEqualizerPreset(preset)
        viewModelScope.launch {
            if (preset.name.equals("Custom", ignoreCase = true)) {
                repository.updateEqualizerPreferences("Custom", playerManager.eqBandGains.value)
            } else {
                repository.updateEqualizerPreferences(preset.name, preset.gains)
            }
        }
    }

    fun updateCustomEqGain(bandIndex: Int, gain: Float) {
        playerManager.updateCustomBandGain(bandIndex, gain)
        viewModelScope.launch {
            repository.updateEqualizerPreferences("Custom", playerManager.eqBandGains.value)
        }
    }

    fun setMinDurationFilter(seconds: Int) {
        viewModelScope.launch {
            repository.updateMinDurationFilter(seconds)
        }
    }

    fun setListItemSize(size: ListItemSize) {
        viewModelScope.launch {
            repository.updateListItemSize(size)
        }
    }

    fun setDynamicBgEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDynamicBgPreference(enabled)
        }
    }

    fun loadLyricsForTrack(track: Track) {
        if (!track.lyrics.isNullOrBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val extractedLyrics = ir.ali0003.musicplayer.data.local.LocalAudioScanner.extractEmbeddedLyrics(
                context = getApplication(),
                filePath = track.audioUrl,
                contentUri = track.audioUrl
            )
            if (!extractedLyrics.isNullOrBlank()) {
                repository.updateTrackLyrics(track.id, extractedLyrics)
                playerManager.updateCurrentTrackLyrics(track.id, extractedLyrics)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.triggerPlaybackStateSave()
    }
}
