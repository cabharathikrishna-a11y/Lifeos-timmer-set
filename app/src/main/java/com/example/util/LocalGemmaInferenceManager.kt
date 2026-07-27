package com.example.util

import android.content.Context

object LocalGemmaInferenceManager {
    fun findAvailableModelPath(context: Context): String? = GeminiNanoManager.getActiveModelPath()
    fun initialize(context: Context): Boolean = GeminiNanoManager.isAICoreAvailable
    suspend fun generateLocalResponse(context: Context, prompt: String): String? = GeminiNanoManager.generatePrompt(context, prompt)
    fun isNativeEngineActive(): Boolean = GeminiNanoManager.isNativeEngineActive()
    fun getActiveModelPath(): String? = GeminiNanoManager.getActiveModelPath()
    fun close() {}
}

