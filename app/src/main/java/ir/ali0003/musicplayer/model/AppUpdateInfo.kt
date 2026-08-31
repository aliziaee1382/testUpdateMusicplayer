package ir.ali0003.musicplayer.model

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val changelog: List<String>
)
