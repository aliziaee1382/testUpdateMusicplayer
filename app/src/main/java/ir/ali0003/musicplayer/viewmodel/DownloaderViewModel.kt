package ir.ali0003.musicplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.ali0003.musicplayer.downloader.DownloaderEngine
import ir.ali0003.musicplayer.downloader.OutdatedExtractorException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DownloaderUiState {
    INITIAL,
    CONFIRM_INPUT,
    SEARCHING_LOADING,
    SEARCH_RESULTS,
    DOWNLOADING_ITEM,
    COMPLETE,
    ERROR_NETWORK,
    ERROR_OUTDATED_ENGINE,
    ENGINE_UPDATING
}

data class DownloaderSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val gradientIndex: Int = 0
)

val DEFAULT_SEARCH_RESULTS = listOf(
    DownloaderSearchResult("1", "Rap God", "Eminem", "6:03", 0),
    DownloaderSearchResult("2", "Rap God (Explicit)", "Eminem", "6:04", 1),
    DownloaderSearchResult("3", "Rap God (Live in London)", "Eminem", "6:12", 2),
    DownloaderSearchResult("4", "Rap God (Instrumental)", "Eminem", "6:00", 3)
)

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val downloaderEngine = DownloaderEngine(application)

    private val _uiState = MutableStateFlow(DownloaderUiState.INITIAL)
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<DownloaderSearchResult>>(DEFAULT_SEARCH_RESULTS)
    val searchResults: StateFlow<List<DownloaderSearchResult>> = _searchResults.asStateFlow()

    private val _selectedItem = MutableStateFlow<DownloaderSearchResult?>(null)
    val selectedItem: StateFlow<DownloaderSearchResult?> = _selectedItem.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private var activeJob: Job? = null

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onSearchInitiated() {
        if (_searchQuery.value.isNotBlank()) {
            _uiState.value = DownloaderUiState.CONFIRM_INPUT
        }
    }

    fun onCancelInput() {
        activeJob?.cancel()
        _searchQuery.value = ""
        _uiState.value = DownloaderUiState.INITIAL
    }

    fun isDirectAudioLink(query: String): Boolean {
        val q = query.trim().lowercase()
        return q.contains("youtube.com") ||
                q.contains("youtu.be") ||
                q.contains("soundcloud.com") ||
                q.endsWith(".mp3") ||
                q.endsWith(".m4a") ||
                q.endsWith(".wav")
    }

    fun onConfirmInputOk() {
        val query = _searchQuery.value.trim()

        // Check for simulated offline/network error keywords
        if (query.equals("error", ignoreCase = true) || query.equals("offline", ignoreCase = true)) {
            _uiState.value = DownloaderUiState.ERROR_NETWORK
            return
        }

        // Check for outdated engine trigger
        if (query.contains("outdated", ignoreCase = true) || query.contains("yt_error", ignoreCase = true)) {
            _uiState.value = DownloaderUiState.ERROR_OUTDATED_ENGINE
            return
        }

        if (isDirectAudioLink(query)) {
            val directItem = DownloaderSearchResult(
                id = "direct_1",
                title = extractTitleFromQuery(query),
                artist = "Direct Download",
                duration = "3:45",
                gradientIndex = 0
            )
            _selectedItem.value = directItem
            startDownloadProcess(directItem)
        } else {
            performSearch(query)
        }
    }

    private fun extractTitleFromQuery(query: String): String {
        return when {
            query.contains(" – ") -> query.substringBefore(" – ")
            query.contains(" - ") -> query.substringBefore(" - ")
            query.contains("youtube.com") || query.contains("youtu.be") -> "YouTube Audio Track"
            query.contains("soundcloud.com") -> "SoundCloud Audio Track"
            else -> query.take(30)
        }
    }

    private fun performSearch(query: String) {
        activeJob?.cancel()
        _uiState.value = DownloaderUiState.SEARCHING_LOADING

        activeJob = viewModelScope.launch {
            try {
                val results = downloaderEngine.extractMetadataAndResults(query)
                _searchResults.value = results
                _uiState.value = DownloaderUiState.SEARCH_RESULTS
            } catch (e: OutdatedExtractorException) {
                _uiState.value = DownloaderUiState.ERROR_OUTDATED_ENGINE
            } catch (e: Exception) {
                _uiState.value = DownloaderUiState.ERROR_NETWORK
            }
        }
    }

    fun onSelectItem(item: DownloaderSearchResult) {
        _selectedItem.value = item
        startDownloadProcess(item)
    }

    private fun startDownloadProcess(item: DownloaderSearchResult) {
        activeJob?.cancel()
        _uiState.value = DownloaderUiState.DOWNLOADING_ITEM
        _downloadProgress.value = 0f

        activeJob = viewModelScope.launch {
            try {
                downloaderEngine.downloadAudioStreamAndSave(item) { progress ->
                    _downloadProgress.value = progress
                }
                _uiState.value = DownloaderUiState.COMPLETE
            } catch (e: OutdatedExtractorException) {
                _uiState.value = DownloaderUiState.ERROR_OUTDATED_ENGINE
            } catch (e: Exception) {
                _uiState.value = DownloaderUiState.ERROR_NETWORK
            }
        }
    }

    fun onUpdateDownloaderEngine() {
        activeJob?.cancel()
        _uiState.value = DownloaderUiState.ENGINE_UPDATING

        activeJob = viewModelScope.launch {
            val success = downloaderEngine.updateEngineScript()
            if (success) {
                // Clear trigger query and restart search automatically
                if (_searchQuery.value.contains("outdated", ignoreCase = true) || _searchQuery.value.contains("yt_error", ignoreCase = true)) {
                    _searchQuery.value = "Eminem – Rap God"
                }
                onConfirmInputOk()
            } else {
                _uiState.value = DownloaderUiState.ERROR_OUTDATED_ENGINE
            }
        }
    }

    fun onRetryNetwork() {
        onConfirmInputOk()
    }

    fun onDownloadAnother() {
        activeJob?.cancel()
        _searchQuery.value = ""
        _selectedItem.value = null
        _downloadProgress.value = 0f
        _uiState.value = DownloaderUiState.INITIAL
    }

    fun setUiState(state: DownloaderUiState) {
        activeJob?.cancel()
        _uiState.value = state
        if (state == DownloaderUiState.INITIAL && _searchQuery.value.isEmpty()) {
            _searchQuery.value = ""
        } else if (state == DownloaderUiState.CONFIRM_INPUT && _searchQuery.value.isEmpty()) {
            _searchQuery.value = "Eminem – Rap God"
        } else if ((state == DownloaderUiState.SEARCH_RESULTS || state == DownloaderUiState.DOWNLOADING_ITEM || state == DownloaderUiState.COMPLETE) && _selectedItem.value == null) {
            _selectedItem.value = _searchResults.value.firstOrNull() ?: DEFAULT_SEARCH_RESULTS.first()
        }
    }
}
