package ir.ali0003.musicplayer.model

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val coverGradientIndex: Int = 0,
    val isSystemPlaylist: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isSmartPlaylist: Boolean
        get() = id == ID_RECENTLY_PLAYED || id == ID_MOST_PLAYED

    val isEditable: Boolean
        get() = !isSystemPlaylist && !isSmartPlaylist

    companion object {
        const val ID_RECENTLY_PLAYED = -1L
        const val ID_MOST_PLAYED = -2L
        const val ID_FAVORITES = 1L
    }
}
