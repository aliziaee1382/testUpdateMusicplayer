package ir.ali0003.musicplayer.model

data class AudioFolder(
    val name: String,
    val path: String,
    val songCount: Int,
    val totalDurationMin: Int
)
