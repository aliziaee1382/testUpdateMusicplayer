package ir.ali0003.musicplayer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val activeThemeId: String = "dark_green",
    val sortCriterion: String = "DATE_ADDED",
    val sortOrder: String = "DESCENDING",
    val lastSelectedCategory: String = "All",
    val lastActiveNavTab: String = "Home",
    val lastLibrarySortTab: String = "Playlists",
    val eqPresetName: String = "Flat",
    val eq60Hz: Float = 0f,
    val eq230Hz: Float = 0f,
    val eq910Hz: Float = 0f,
    val eq3600Hz: Float = 0f,
    val eq14000Hz: Float = 0f,
    val spatialAudioEnabled: Boolean = true,
    val flacModeEnabled: Boolean = true,
    val minDurationFilterSeconds: Int = 0,
    val lastPlayedTrackId: Long = -1L,
    val lastPlaybackPositionMs: Long = 0L,
    val lastQueueTrackIds: String = "",
    val isAutoSystemTheme: Boolean = true,
    val listItemSize: String = "SMALL",
    val isDynamicBgEnabled: Boolean = true,
    val hiddenFolders: String = ""
) {
    fun getHiddenFolderSet(): Set<String> {
        val raw = hiddenFolders.trim()
        if (raw.isBlank() || raw == "[]") return emptySet()
        return if (raw.startsWith("[") && raw.endsWith("]")) {
            try {
                val jsonArray = org.json.JSONArray(raw)
                val set = mutableSetOf<String>()
                for (i in 0 until jsonArray.length()) {
                    val str = jsonArray.optString(i)?.trim()
                    if (!str.isNullOrEmpty()) set.add(str)
                }
                set
            } catch (e: Exception) {
                raw.removeSurrounding("[", "]")
                    .split("|||", ",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
        } else {
            raw.split("|||").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    fun withHiddenFolderSet(set: Set<String>): String {
        val cleanSet = set.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val jsonArray = org.json.JSONArray()
        cleanSet.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }
}
