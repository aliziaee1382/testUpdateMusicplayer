package ir.ali0003.musicplayer.model

import androidx.compose.runtime.Immutable

@Immutable
data class MusicIdentifyResult(
    val title: String,
    val artist: String,
    val album: String = "",
    val releaseDate: String? = null,
    val genre: String? = null,
    val coverUrl: String? = null,
    val youtubeUrl: String? = null,
    val spotifyUrl: String? = null
)

