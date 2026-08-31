package ir.ali0003.musicplayer.downloader

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import ir.ali0003.musicplayer.viewmodel.DownloaderSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class OutdatedExtractorException(message: String) : Exception(message)

class DownloaderEngine(private val context: Context) {

    private val prefs = context.getSharedPreferences("downloader_engine_prefs", Context.MODE_PRIVATE)

    fun getEngineVersion(): String {
        return prefs.getString("engine_version", "v2.4.0") ?: "v2.4.0"
    }

    suspend fun updateEngineScript(): Boolean = withContext(Dispatchers.IO) {
        delay(1800) // Simulated remote downloading of yt-dlp extractor rules
        prefs.edit().putString("engine_version", "v2.5.1").apply()
        val configFile = File(context.filesDir, "downloader_config.json")
        configFile.writeText("""{"version":"v2.5.1","extractor":"youtube_dl_v2","updated_at":${System.currentTimeMillis()}}""")
        true
    }

    suspend fun extractMetadataAndResults(query: String): List<DownloaderSearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()

        // Trigger outdated extractor exception for keyword "outdated", "update_needed", or "yt_error"
        if (q.contains("outdated") || q.contains("update_needed") || q.contains("yt_error")) {
            throw OutdatedExtractorException("YouTube stream extractor is outdated. An update is required to extract streams.")
        }

        delay(1800) // Simulated network extraction delay

        val title = when {
            query.contains(" – ") -> query.substringAfter(" – ")
            query.contains(" - ") -> query.substringAfter(" - ")
            q.contains("youtube.com") || q.contains("youtu.be") -> "YouTube Audio Track"
            q.contains("soundcloud.com") -> "SoundCloud Audio Track"
            else -> query
        }

        val artist = when {
            query.contains(" – ") -> query.substringBefore(" – ")
            query.contains(" - ") -> query.substringBefore(" - ")
            else -> "Artist"
        }

        listOf(
            DownloaderSearchResult("yt_1", title.ifBlank { "Rap God" }, artist, "6:03", 0),
            DownloaderSearchResult("yt_2", "${title.ifBlank { "Rap God" }} (Explicit)", artist, "6:04", 1),
            DownloaderSearchResult("yt_3", "${title.ifBlank { "Rap God" }} (Live in London)", artist, "6:12", 2),
            DownloaderSearchResult("yt_4", "${title.ifBlank { "Rap God" }} (Instrumental)", artist, "6:00", 3)
        )
    }

    suspend fun downloadAudioStreamAndSave(
        item: DownloaderSearchResult,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "Music").apply { mkdirs() }
        if (!musicDir.exists()) musicDir.mkdirs()

        val sanitizedTitle = item.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val sanitizedArtist = item.artist.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val fileName = "${sanitizedArtist}_-_${sanitizedTitle}.mp3"
        val outputFile = File(musicDir, fileName)

        val totalBytes = 3_500_000 // ~3.5MB audio stream size
        val chunkSize = 175_000
        var bytesWritten = 0

        FileOutputStream(outputFile).use { fos ->
            val dummyBuffer = ByteArray(chunkSize) { (it % 256).toByte() }
            while (bytesWritten < totalBytes) {
                delay(100)
                fos.write(dummyBuffer)
                bytesWritten += chunkSize
                val progress = (bytesWritten.toFloat() / totalBytes).coerceAtMost(1.0f)
                withContext(Dispatchers.Main) {
                    onProgress(progress)
                }
            }
        }

        // Tag ID3 / track metadata
        tagAudioMetadata(outputFile, item)

        // Notify Android MediaStore via MediaScannerConnection so it appears in Music Library
        MediaScannerConnection.scanFile(
            context,
            arrayOf(outputFile.absolutePath),
            arrayOf("audio/mpeg")
        ) { _, _ -> }

        outputFile
    }

    private fun tagAudioMetadata(file: File, item: DownloaderSearchResult) {
        val metadataFile = File(file.parentFile, "${file.nameWithoutExtension}.meta")
        metadataFile.writeText("TITLE=${item.title}\nARTIST=${item.artist}\nDURATION=${item.duration}\nENGINE_VERSION=${getEngineVersion()}")
    }
}
