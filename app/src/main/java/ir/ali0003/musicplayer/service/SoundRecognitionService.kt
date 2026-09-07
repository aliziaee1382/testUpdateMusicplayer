package ir.ali0003.musicplayer.service

import ir.ali0003.musicplayer.model.MusicIdentifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

sealed class RecognitionResponse {
    data class Success(val result: MusicIdentifyResult) : RecognitionResponse()
    data class NotFound(val message: String = "No matching track found. Please try again.") : RecognitionResponse()
    data class Error(val message: String = "Network error connecting to recognition service.") : RecognitionResponse()
}

object SoundRecognitionService {
    private const val RECOGNIZE_URL = "https://ali0003.s14.telviprobot.top/api/recognize.php"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    suspend fun recognizeAudioFile(file: File): RecognitionResponse = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext RecognitionResponse.Error("Audio file is empty or missing.")
        }

        try {
            val mediaType = "audio/mp4".toMediaTypeOrNull()
            val fileBody = file.asRequestBody(mediaType)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", file.name, fileBody)
                .build()

            val request = Request.Builder()
                .url(RECOGNIZE_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                return@withContext RecognitionResponse.Error("Network error connecting to recognition service.")
            }

            val json = JSONObject(responseBody)
            val status = json.optString("status", "")

            if (status.equals("success", ignoreCase = true)) {
                val data = json.optJSONObject("data")
                if (data != null) {
                    val title = data.optString("title", "Unknown Track")
                    val artist = data.optString("artist", "Unknown Artist")
                    val album = data.optString("album", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: ""
                    val releaseDate = data.optString("release_date", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        ?: data.optString("release_year", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    val genre = if (data.has("genre")) {
                        data.optString("genre", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    } else if (data.has("genres")) {
                        val genresArr = data.optJSONArray("genres")
                        if (genresArr != null && genresArr.length() > 0) {
                            val first = genresArr.optJSONObject(0)?.optString("name") ?: genresArr.optString(0)
                            first.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        } else null
                    } else null
                    val coverUrl = data.optString("cover_image", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    val youtubeUrl = data.optString("youtube_url", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    val spotifyUrl = data.optString("spotify_url", "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

                    val identifyResult = MusicIdentifyResult(
                        title = title,
                        artist = artist,
                        album = album,
                        releaseDate = releaseDate,
                        genre = genre,
                        coverUrl = coverUrl,
                        youtubeUrl = youtubeUrl,
                        spotifyUrl = spotifyUrl
                    )
                    return@withContext RecognitionResponse.Success(identifyResult)
                } else {
                    return@withContext RecognitionResponse.NotFound("No matching track found. Please try again.")
                }
            } else if (status.equals("not_found", ignoreCase = true)) {
                return@withContext RecognitionResponse.NotFound("No matching track found. Please try again.")
            } else {
                val msg = json.optString("message", "No matching track found. Please try again.")
                return@withContext RecognitionResponse.NotFound(msg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext RecognitionResponse.Error("Network error connecting to recognition service.")
        }
    }
}
