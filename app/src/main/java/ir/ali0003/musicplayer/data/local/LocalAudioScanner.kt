package ir.ali0003.musicplayer.data.local

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import ir.ali0003.musicplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ScanProgress(
    val current: Int,
    val total: Int
)

data class ScanBatch(
    val progress: ScanProgress,
    val tracks: List<Track>
)

class LocalAudioScanner(private val context: Context) {

    fun scanLocalTracksFlow(
        existingTrackIds: Set<Long> = emptySet(),
        chunkSize: Int = 100
    ): Flow<ScanBatch> = flow {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection: String? = null
        val chunkBuffer = mutableListOf<Track>()
        var index = 0
        var currentScanned = 0

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val totalCount = cursor.count
                emit(ScanBatch(ScanProgress(0, totalCount), emptyList()))

                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    currentScanned++
                    val id = cursor.getLong(idColumn)
                    val trackId = id + 500000L

                    // Skip tracks that are already saved in local database for fast incremental scanning
                    if (existingTrackIds.contains(trackId)) {
                        index++
                        if (currentScanned % 50 == 0 || currentScanned == totalCount) {
                            emit(ScanBatch(ScanProgress(currentScanned, totalCount), emptyList()))
                        }
                        continue
                    }

                    val durationMs = cursor.getInt(durationColumn)
                    val filePath = cursor.getString(dataColumn) ?: ""
                    val rawTitle = cursor.getString(titleColumn) ?: ""

                    // Fast extension check without disk I/O
                    val extension = if (filePath.contains('.')) {
                        filePath.substringAfterLast('.').lowercase()
                    } else ""

                    if (extension.isNotEmpty() && extension !in VALID_MUSIC_EXTENSIONS) {
                        if (currentScanned % 50 == 0 || currentScanned == totalCount) {
                            emit(ScanBatch(ScanProgress(currentScanned, totalCount), emptyList()))
                        }
                        continue
                    }

                    // Skip system or hidden paths
                    if (isSystemOrHiddenPath(filePath)) {
                        if (currentScanned % 50 == 0 || currentScanned == totalCount) {
                            emit(ScanBatch(ScanProgress(currentScanned, totalCount), emptyList()))
                        }
                        continue
                    }

                    // Extract title efficiently
                    val title = when {
                        rawTitle.isNotBlank() && rawTitle != "<unknown>" -> rawTitle
                        filePath.isNotBlank() -> {
                            val fileName = filePath.substringAfterLast('/')
                            if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
                        }
                        else -> "Track $index"
                    }

                    // Extract folder name safely
                    val folderName = extractFolderName(filePath)

                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Local Songs"
                    val albumId = cursor.getLong(albumIdColumn)

                    val dateAddedSec = if (dateAddedColumn >= 0) cursor.getLong(dateAddedColumn) else 0L
                    val dateModifiedSec = if (dateModifiedColumn >= 0) cursor.getLong(dateModifiedColumn) else 0L
                    val dateAddedMs = if (dateAddedSec > 0) dateAddedSec * 1000L else System.currentTimeMillis()
                    val dateModifiedMs = if (dateModifiedSec > 0) dateModifiedSec * 1000L else System.currentTimeMillis()

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    // Smart album art resolution: Embedded picture -> MediaStore -> null
                    val albumArtUri = resolveAlbumArtUri(
                        context = context,
                        filePath = filePath,
                        contentUri = contentUri,
                        trackId = trackId,
                        albumId = albumId
                    )

                    val cleanArtist = if (artist == "<unknown>" || artist.isBlank()) "Local Artist" else artist
                    val cleanAlbum = if (album == "<unknown>" || album.isBlank()) "Local Album" else album
                    val calculatedDurationSec = if (durationMs > 0) durationMs / 1000 else 180

                    chunkBuffer.add(
                        Track(
                            id = trackId,
                            title = title,
                            artist = cleanArtist,
                            album = cleanAlbum,
                            durationSeconds = calculatedDurationSec.coerceAtLeast(1),
                            audioUrl = if (contentUri.isNotBlank()) contentUri else filePath,
                            category = folderName,
                            coverGradientIndex = (index % 5),
                            albumArtUri = albumArtUri,
                            isLocal = true,
                            folderName = folderName,
                            dateAddedTimestamp = dateAddedMs,
                            dateModifiedTimestamp = dateModifiedMs,
                            lyrics = null
                        )
                    )
                    index++

                    if (chunkBuffer.size >= chunkSize) {
                        emit(ScanBatch(ScanProgress(currentScanned, totalCount), chunkBuffer.toList()))
                        chunkBuffer.clear()
                    }
                }

                if (chunkBuffer.isNotEmpty() || currentScanned < totalCount) {
                    emit(ScanBatch(ScanProgress(currentScanned, totalCount), chunkBuffer.toList()))
                    chunkBuffer.clear()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (chunkBuffer.isNotEmpty()) {
            emit(ScanBatch(ScanProgress(currentScanned, currentScanned), chunkBuffer.toList()))
            chunkBuffer.clear()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun scanLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Track>()
        scanLocalTracksFlow(chunkSize = 100).collect { batch ->
            result.addAll(batch.tracks)
        }
        result
    }

    companion object {
        private val VALID_MUSIC_EXTENSIONS = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus", "wma", "3gp")

        private fun isSystemOrHiddenPath(filePath: String): Boolean {
            if (filePath.isBlank()) return false
            val lowerPath = filePath.lowercase()

            if (lowerPath.contains("/.")) return true
            if (lowerPath.contains("/android/data/") || lowerPath.contains("/android/obb/")) return true
            if (lowerPath.contains("/cache/") || lowerPath.contains("/.cache/")) return true

            return false
        }

        private fun extractFolderName(filePath: String): String {
            if (filePath.isBlank() || !filePath.contains('/')) return "Phone Storage"
            return try {
                val lastSlash = filePath.lastIndexOf('/')
                if (lastSlash <= 0) return "Phone Storage"
                val prevSlash = filePath.lastIndexOf('/', lastSlash - 1)
                if (prevSlash != -1) {
                    val folder = filePath.substring(prevSlash + 1, lastSlash)
                    if (folder.isNotBlank()) folder else "Phone Storage"
                } else {
                    "Phone Storage"
                }
            } catch (e: Exception) {
                "Phone Storage"
            }
        }

        fun resolveAlbumArtUriFast(albumId: Long): String? {
            return if (albumId > 0) {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()
            } else null
        }

        fun resolveAlbumArtUri(
            context: Context,
            filePath: String,
            contentUri: String,
            trackId: Long,
            albumId: Long
        ): String? {
            // Priority 1: Extract embedded picture directly from audio file metadata
            val embeddedArt = extractEmbeddedPicture(context, filePath, contentUri, trackId)
            if (!embeddedArt.isNullOrBlank()) {
                return embeddedArt
            }

            // Priority 2: MediaStore album art URI if accessible
            if (albumId > 0) {
                val mediaStoreArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()
                try {
                    context.contentResolver.openInputStream(Uri.parse(mediaStoreArtUri))?.use {
                        return mediaStoreArtUri
                    }
                } catch (e: Exception) {
                    // MediaStore art file not present or unreadable
                }
            }

            // Priority 3: Fallback to null (triggers clean solid placeholder)
            return null
        }

        fun extractEmbeddedPicture(
            context: Context,
            filePath: String,
            contentUri: String,
            trackId: Long
        ): String? {
            val cacheDir = File(context.cacheDir, "album_covers")
            val coverFile = File(cacheDir, "cover_$trackId.jpg")
            if (coverFile.exists() && coverFile.length() > 0L) {
                return Uri.fromFile(coverFile).toString()
            }
            val retriever = MediaMetadataRetriever()
            try {
                if (contentUri.isNotBlank()) {
                    retriever.setDataSource(context, Uri.parse(contentUri))
                } else if (filePath.isNotBlank()) {
                    retriever.setDataSource(filePath)
                } else {
                    return null
                }
                val artBytes = retriever.embeddedPicture
                if (artBytes != null && artBytes.isNotEmpty()) {
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }
                    coverFile.writeBytes(artBytes)
                    return Uri.fromFile(coverFile).toString()
                }
            } catch (e: Exception) {
                // Ignore cleanly
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {}
            }
            return null
        }

        fun resolvePhysicalPath(context: Context, uriString: String): String? {
            if (!uriString.startsWith("content://")) return uriString
            return try {
                val uri = Uri.parse(uriString)
                val projection = arrayOf(MediaStore.Audio.Media.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                        if (columnIndex != -1) cursor.getString(columnIndex) else null
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }

        fun extractEmbeddedLyrics(context: Context, filePath: String, contentUri: String): String? {
            // 1. Try standard MediaMetadataRetriever first
            val retriever = MediaMetadataRetriever()
            try {
                if (contentUri.isNotBlank()) {
                    retriever.setDataSource(context, Uri.parse(contentUri))
                } else if (filePath.isNotBlank()) {
                    retriever.setDataSource(filePath)
                }
                val lyrics = retriever.extractMetadata(1000)
                if (!lyrics.isNullOrBlank()) return lyrics.trim()
            } catch (e: Exception) {
                // ignore & fallback to direct ID3 byte stream parsing
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }

            // 2. Fallback: Direct ID3v2 USLT / ULT Frame Byte Parser
            return try {
                val candidatePath = when {
                    filePath.isNotBlank() -> filePath
                    contentUri.isNotBlank() -> contentUri
                    else -> return null
                }

                val resolvedPath = resolvePhysicalPath(context, candidatePath)
                if (!resolvedPath.isNullOrBlank()) {
                    val file = java.io.File(resolvedPath)
                    if (file.exists() && file.length() > 0) {
                        val lyricsFromFile = parseId3LyricsFromFile(file)
                        if (!lyricsFromFile.isNullOrBlank()) return lyricsFromFile
                    }
                }

                val targetUriString = if (contentUri.startsWith("content://")) contentUri else candidatePath
                if (targetUriString.startsWith("content://")) {
                    val uri = Uri.parse(targetUriString)
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        parseId3LyricsFromStream(inputStream)
                    }
                } else null
            } catch (e: Exception) {
                null
            }
        }

        private fun parseId3LyricsFromFile(file: java.io.File): String? {
            return try {
                java.io.RandomAccessFile(file, "r").use { raf ->
                    val header = ByteArray(10)
                    if (raf.read(header) < 10) return null
                    // Check ID3 header magic "ID3"
                    if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null

                    val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                                  ((header[7].toInt() and 0x7F) shl 14) or
                                  ((header[8].toInt() and 0x7F) shl 7) or
                                  (header[9].toInt() and 0x7F)

                    if (tagSize <= 0) return null
                    val tagData = ByteArray(tagSize.coerceAtMost(1024 * 1024)) // limit to 1MB
                    raf.readFully(tagData)

                    // Search for "USLT" or "ULT" frame tags inside byte buffer
                    var pos = 0
                    while (pos < tagData.size - 10) {
                        val frameId = String(tagData, pos, 4, Charsets.US_ASCII)
                        if (frameId == "USLT" || frameId == "ULT ") {
                            val frameSize = ((tagData[pos + 4].toInt() and 0xFF) shl 24) or
                                            ((tagData[pos + 5].toInt() and 0xFF) shl 16) or
                                            ((tagData[pos + 6].toInt() and 0xFF) shl 8) or
                                            (tagData[pos + 7].toInt() and 0xFF)
                            if (frameSize > 0 && pos + 10 + frameSize <= tagData.size) {
                                val frameContent = tagData.copyOfRange(pos + 10, pos + 10 + frameSize)
                                // Encoding byte at index 0, Language 3 bytes (1..3)
                                val textBytes = if (frameContent.size > 4) frameContent.copyOfRange(4, frameContent.size) else frameContent
                                val rawText = String(textBytes, Charsets.UTF_8)
                                val cleaned = cleanExtractedLyrics(rawText)
                                if (cleaned != null) return cleaned
                            }
                        }
                        pos++
                    }
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun parseId3LyricsFromStream(stream: java.io.InputStream): String? {
            return try {
                val header = ByteArray(10)
                var bytesRead = 0
                while (bytesRead < 10) {
                    val count = stream.read(header, bytesRead, 10 - bytesRead)
                    if (count < 0) return null
                    bytesRead += count
                }
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null

                val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                              ((header[7].toInt() and 0x7F) shl 14) or
                              ((header[8].toInt() and 0x7F) shl 7) or
                              (header[9].toInt() and 0x7F)

                if (tagSize <= 0) return null
                val targetSize = tagSize.coerceAtMost(1024 * 1024)
                val tagData = ByteArray(targetSize)
                var dataRead = 0
                while (dataRead < targetSize) {
                    val count = stream.read(tagData, dataRead, targetSize - dataRead)
                    if (count < 0) break
                    dataRead += count
                }

                var pos = 0
                while (pos < dataRead - 10) {
                    val frameId = String(tagData, pos, 4, Charsets.US_ASCII)
                    if (frameId == "USLT" || frameId == "ULT ") {
                        val frameSize = ((tagData[pos + 4].toInt() and 0xFF) shl 24) or
                                        ((tagData[pos + 5].toInt() and 0xFF) shl 16) or
                                        ((tagData[pos + 6].toInt() and 0xFF) shl 8) or
                                        (tagData[pos + 7].toInt() and 0xFF)
                        if (frameSize > 0 && pos + 10 + frameSize <= dataRead) {
                            val frameContent = tagData.copyOfRange(pos + 10, pos + 10 + frameSize)
                            val textBytes = if (frameContent.size > 4) frameContent.copyOfRange(4, frameContent.size) else frameContent
                            val rawText = String(textBytes, Charsets.UTF_8)
                            val cleaned = cleanExtractedLyrics(rawText)
                            if (cleaned != null) return cleaned
                        }
                    }
                    pos++
                }
                null
            } catch (e: Exception) {
                null
            }
        }

        private fun cleanExtractedLyrics(rawText: String): String? {
            var text = rawText
            if (text.contains("APIC")) {
                text = text.substringBefore("APIC")
            }
            if (text.contains("image/jpeg")) {
                text = text.substringBefore("image/jpeg")
            }
            if (text.contains("image/png")) {
                text = text.substringBefore("image/png")
            }
            val finalText = text
                .replace("\u0000", "")
                .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
                .trim()
            return if (finalText.isNotBlank()) finalText else null
        }
    }
}

