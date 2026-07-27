package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "tools") val tools: List<Map<String, Map<String, String>>>? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
) {
    @JsonClass(generateAdapter = true)
    data class Content(
        @Json(name = "parts") val parts: List<Part>
    )

    @JsonClass(generateAdapter = true)
    data class Part(
        @Json(name = "text") val text: String? = null,
        @Json(name = "inlineData") val inlineData: InlineData? = null
    )

    @JsonClass(generateAdapter = true)
    data class InlineData(
        @Json(name = "mimeType") val mimeType: String,
        @Json(name = "data") val data: String
    )

    @JsonClass(generateAdapter = true)
    data class GenerationConfig(
        @Json(name = "responseModalities") val responseModalities: List<String>? = null,
        @Json(name = "imageConfig") val imageConfig: ImageConfig? = null,
        @Json(name = "thinkingConfig") val thinkingConfig: ThinkingConfig? = null,
        @Json(name = "speechConfig") val speechConfig: SpeechConfig? = null
    )

    @JsonClass(generateAdapter = true)
    data class ImageConfig(
        @Json(name = "aspectRatio") val aspectRatio: String,
        @Json(name = "imageSize") val imageSize: String
    )

    @JsonClass(generateAdapter = true)
    data class ThinkingConfig(
        @Json(name = "thinkingLevel") val thinkingLevel: String
    )

    @JsonClass(generateAdapter = true)
    data class SpeechConfig(
        @Json(name = "voiceConfig") val voiceConfig: VoiceConfig
    )

    @JsonClass(generateAdapter = true)
    data class VoiceConfig(
        @Json(name = "prebuiltVoiceConfig") val prebuiltVoiceConfig: PrebuiltVoiceConfig
    )

    @JsonClass(generateAdapter = true)
    data class PrebuiltVoiceConfig(
        @Json(name = "voiceName") val voiceName: String
    )
}

@JsonClass(generateAdapter = true)
data class VeoRequest(
    @Json(name = "prompt") val prompt: String,
    @Json(name = "config") val config: VeoConfig? = null
)

@JsonClass(generateAdapter = true)
data class VeoConfig(
    @Json(name = "numberOfVideos") val numberOfVideos: Int = 1,
    @Json(name = "resolution") val resolution: String = "1080p",
    @Json(name = "aspectRatio") val aspectRatio: String = "16:9"
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
) {
    @JsonClass(generateAdapter = true)
    data class Candidate(
        @Json(name = "content") val content: Content?,
        @Json(name = "groundingMetadata") val groundingMetadata: GroundingMetadata? = null
    )

    @JsonClass(generateAdapter = true)
    data class Content(
        @Json(name = "parts") val parts: List<Part>?
    )

    @JsonClass(generateAdapter = true)
    data class Part(
        @Json(name = "text") val text: String? = null,
        @Json(name = "inlineData") val inlineData: InlineData? = null
    )

    @JsonClass(generateAdapter = true)
    data class InlineData(
        @Json(name = "mimeType") val mimeType: String?,
        @Json(name = "data") val data: String?
    )

    @JsonClass(generateAdapter = true)
    data class GroundingMetadata(
        @Json(name = "webSearchQueries") val webSearchQueries: List<String>? = null
    )
}

@JsonClass(generateAdapter = true)
data class VeoResponse(
    @Json(name = "name") val name: String? = null,
    @Json(name = "response") val response: VeoResponseContent? = null
)

@JsonClass(generateAdapter = true)
data class VeoResponseContent(
    @Json(name = "generatedVideos") val generatedVideos: List<VeoVideoItem>? = null
)

@JsonClass(generateAdapter = true)
data class VeoVideoItem(
    @Json(name = "video") val video: VeoVideoField? = null
)

@JsonClass(generateAdapter = true)
data class VeoVideoField(
    @Json(name = "uri") val uri: String? = null,
    @Json(name = "bytesBase64Encoded") val bytesBase64Encoded: String? = null
)

data class GeminiResult(
    val text: String,
    val base64Image: String? = null,
    val base64Audio: String? = null,
    val videoUri: String? = null,
    val modelUsed: String
)

enum class DeepaAiMode(val displayName: String, val icon: String, val description: String) {
    GENERAL("General Chat", "⚡", "Standard conversation & commands"),
    HIGH_THINKING("High Thinking", "🧠", "Complex reasoning with deep analysis"),
    FAST_LOW_LATENCY("Fast Latency", "🚀", "Superfast low-latency responses"),
    GOOGLE_SEARCH("Search Grounding", "🌐", "Live Google Search facts & sources"),
    GOOGLE_MAPS("Maps Grounding", "📍", "Google Maps location & places data"),
    IMAGE_STUDIO("Image Studio", "🎨", "High-res image generation & editing"),
    VEO_VIDEO("Veo Video", "🎬", "Animate & create videos from text"),
    LYRIA_MUSIC("Lyria Music", "🎵", "Generate custom music clips & tracks"),
    VOICE_LIVE("Voice Live", "🎙️", "Real-time voice & speech audio")
}

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1beta/models/{model}:generateVideos")
    suspend fun generateVideos(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: VeoRequest
    ): VeoResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    fun getApiKey(): String {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            throw java.io.IOException("Gemini API key is not configured in Secrets panel.")
        }
        return key
    }

    private suspend fun callWithFallback(
        candidateModels: List<String>,
        apiKey: String,
        request: GeminiRequest
    ): Pair<GeminiResponse, String> {
        var lastException: Exception? = null
        for (model in candidateModels) {
            try {
                val response = apiService.generateContent(model, apiKey, request)
                if (response.candidates != null && response.candidates.isNotEmpty()) {
                    return Pair(response, model)
                }
            } catch (e: Exception) {
                android.util.Log.w("GeminiClient", "Model $model failed (${e.message}), trying next candidate...", e)
                lastException = e
            }
        }
        throw lastException ?: java.io.IOException("All candidate Gemini model endpoints failed.")
    }

    suspend fun getGeminiResponse(prompt: String): String {
        val result = executeDeepaAi(prompt, DeepaAiMode.GENERAL)
        return result.text
    }

    suspend fun getGeminiResult(prompt: String): GeminiResult {
        return executeDeepaAi(prompt, DeepaAiMode.GENERAL)
    }

    suspend fun executeDeepaAi(
        prompt: String,
        mode: DeepaAiMode,
        attachedMedia: Pair<String, String>? = null, // mimeType to base64
        aspectRatio: String = "1:1",
        resolution: String = "2K",
        isMusicClip: Boolean = true
    ): GeminiResult {
        val apiKey = getApiKey()

        // Build parts list
        val parts = mutableListOf<GeminiRequest.Part>()
        if (prompt.isNotBlank()) {
            parts.add(GeminiRequest.Part(text = prompt))
        }
        if (attachedMedia != null && attachedMedia.second.isNotBlank()) {
            parts.add(GeminiRequest.Part(inlineData = GeminiRequest.InlineData(mimeType = attachedMedia.first, data = attachedMedia.second)))
        }

        val systemInstruction = GeminiRequest.Content(
            parts = listOf(
                GeminiRequest.Part(
                    text = "You are Deepa AI, an elite, online AI assistant powered by Google Gemini. You provide sharp, helpful, accurate assistance across study, productivity, and life management."
                )
            )
        )

        val contents = listOf(GeminiRequest.Content(parts = parts))

        return when (mode) {
            DeepaAiMode.GENERAL -> {
                val candidateModels = if (attachedMedia?.first?.startsWith("video/") == true || attachedMedia?.first?.startsWith("image/") == true) {
                    listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-3.1-pro-preview", "gemini-1.5-pro", "gemini-1.5-flash")
                } else {
                    listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.0-flash", "gemini-3.5-flash", "gemini-flash-latest", "gemini-2.5-pro")
                }
                val request = GeminiRequest(contents = contents, systemInstruction = systemInstruction)
                val (response, modelUsed) = callWithFallback(candidateModels, apiKey, request)
                parseStandardResponse(response, modelUsed)
            }

            DeepaAiMode.HIGH_THINKING -> {
                val candidateModels = listOf("gemini-2.5-pro", "gemini-1.5-pro", "gemini-3.1-pro-preview", "gemini-2.5-flash")
                val config = GeminiRequest.GenerationConfig(
                    thinkingConfig = GeminiRequest.ThinkingConfig(thinkingLevel = "HIGH")
                )
                val request = GeminiRequest(contents = contents, generationConfig = config, systemInstruction = systemInstruction)
                val (response, modelUsed) = callWithFallback(candidateModels, apiKey, request)
                parseStandardResponse(response, modelUsed)
            }

            DeepaAiMode.FAST_LOW_LATENCY -> {
                val candidateModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-3.1-flash-lite-preview", "gemini-2.0-flash")
                val request = GeminiRequest(contents = contents, systemInstruction = systemInstruction)
                val (response, modelUsed) = callWithFallback(candidateModels, apiKey, request)
                parseStandardResponse(response, modelUsed)
            }

            DeepaAiMode.GOOGLE_SEARCH -> {
                val candidateModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-3.5-flash")
                val tools = listOf(mapOf("googleSearch" to emptyMap<String, String>()))
                val request = GeminiRequest(contents = contents, tools = tools, systemInstruction = systemInstruction)
                val (response, modelUsed) = callWithFallback(candidateModels, apiKey, request)
                parseStandardResponse(response, modelUsed)
            }

            DeepaAiMode.GOOGLE_MAPS -> {
                val candidateModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-3.5-flash")
                val tools = listOf(mapOf("googleMaps" to emptyMap<String, String>()))
                val request = GeminiRequest(contents = contents, tools = tools, systemInstruction = systemInstruction)
                val (response, modelUsed) = callWithFallback(candidateModels, apiKey, request)
                parseStandardResponse(response, modelUsed)
            }

            DeepaAiMode.IMAGE_STUDIO -> {
                val candidateModels = if (resolution == "4K") listOf("gemini-2.5-flash-image", "gemini-3-pro-image-preview", "gemini-3.1-flash-image-preview") else listOf("gemini-2.5-flash-image", "gemini-3.1-flash-image-preview")
                val config = GeminiRequest.GenerationConfig(
                    responseModalities = listOf("TEXT", "IMAGE"),
                    imageConfig = GeminiRequest.ImageConfig(aspectRatio = aspectRatio, imageSize = resolution)
                )
                val request = GeminiRequest(contents = contents, generationConfig = config, systemInstruction = systemInstruction)
                val (response, modelUsed) = callWithFallback(candidateModels, apiKey, request)
                parseStandardResponse(response, modelUsed)
            }

            DeepaAiMode.VEO_VIDEO -> {
                val targetModel = "veo-3.1-fast-generate-preview"
                val validAspect = if (aspectRatio in listOf("16:9", "9:16")) aspectRatio else "16:9"
                val request = VeoRequest(prompt = prompt, config = VeoConfig(numberOfVideos = 1, resolution = "1080p", aspectRatio = validAspect))
                val veoResponse = apiService.generateVideos(targetModel, apiKey, request)
                val uri = veoResponse.response?.generatedVideos?.firstOrNull()?.video?.uri
                val bytes = veoResponse.response?.generatedVideos?.firstOrNull()?.video?.bytesBase64Encoded
                GeminiResult(
                    text = "🎬 Generated video with Veo 3 ($validAspect):\n" + (uri ?: "Video file processed successfully."),
                    base64Image = bytes,
                    videoUri = uri,
                    modelUsed = targetModel
                )
            }

            DeepaAiMode.LYRIA_MUSIC -> {
                val targetModel = if (isMusicClip) "lyria-3-clip-preview" else "lyria-3-pro-preview"
                val config = GeminiRequest.GenerationConfig(responseModalities = listOf("AUDIO"))
                val request = GeminiRequest(contents = contents, generationConfig = config, systemInstruction = systemInstruction)
                val response = apiService.generateContent(targetModel, apiKey, request)
                parseStandardResponse(response, targetModel)
            }

            DeepaAiMode.VOICE_LIVE -> {
                val targetModel = "gemini-3.1-flash-live-preview"
                val config = GeminiRequest.GenerationConfig(
                    responseModalities = listOf("AUDIO"),
                    speechConfig = GeminiRequest.SpeechConfig(
                        voiceConfig = GeminiRequest.VoiceConfig(
                            prebuiltVoiceConfig = GeminiRequest.PrebuiltVoiceConfig(voiceName = "Kore")
                        )
                    )
                )
                val request = GeminiRequest(contents = contents, generationConfig = config, systemInstruction = systemInstruction)
                val response = try {
                    apiService.generateContent(targetModel, apiKey, request)
                } catch (e: Exception) {
                    apiService.generateContent("gemini-2.5-flash-preview-tts", apiKey, request)
                }
                parseStandardResponse(response, targetModel)
            }
        }
    }

    private fun parseStandardResponse(response: GeminiResponse, modelUsed: String): GeminiResult {
        val candidate = response.candidates?.firstOrNull()
        val textPart = candidate?.content?.parts?.find { it.text != null }?.text
        val imagePart = candidate?.content?.parts?.find { it.inlineData?.mimeType?.startsWith("image/") == true }?.inlineData
        val audioPart = candidate?.content?.parts?.find { it.inlineData?.mimeType?.startsWith("audio/") == true }?.inlineData

        val displayText = textPart ?: when {
            imagePart != null -> "✨ Here is your generated image:"
            audioPart != null -> "🎵 Generated audio content successfully."
            else -> "Response processed successfully."
        }

        return GeminiResult(
            text = displayText,
            base64Image = imagePart?.data,
            base64Audio = audioPart?.data,
            modelUsed = modelUsed
        )
    }
}
