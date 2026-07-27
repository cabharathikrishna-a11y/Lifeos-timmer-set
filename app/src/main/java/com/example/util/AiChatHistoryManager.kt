package com.example.util

import android.content.Context
import com.example.ui.ChatMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object AiChatHistoryManager {

    private const val CHAT_HISTORY_FILE = "ai_chat_history_1year.json"
    private const val AI_MEMORIES_FILE = "ai_personal_memories.json"
    private val ONE_YEAR_MS = TimeUnit.DAYS.toMillis(365)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val chatListAdapter = moshi.adapter<List<ChatMessage>>(
        Types.newParameterizedType(List::class.java, ChatMessage::class.java)
    )

    private val memoryListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    /**
     * Loads chat history from internal disk, filtering out entries older than 365 days.
     */
    fun loadChatHistory(context: Context): List<ChatMessage> {
        return try {
            val file = File(context.filesDir, CHAT_HISTORY_FILE)
            if (!file.exists()) return emptyList()

            val json = file.readText()
            val list = chatListAdapter.fromJson(json) ?: emptyList()
            val now = System.currentTimeMillis()
            val cutoff = now - ONE_YEAR_MS

            // Filter 1 year history window
            val filtered = list.filter { it.timestamp >= cutoff }
            if (filtered.size != list.size) {
                saveChatHistory(context, filtered)
            }
            filtered
        } catch (e: Exception) {
            android.util.Log.e("AiChatHistoryManager", "Error loading chat history", e)
            emptyList()
        }
    }

    /**
     * Saves chat history to disk, keeping up to 1 year of history.
     */
    fun saveChatHistory(context: Context, messages: List<ChatMessage>) {
        try {
            val file = File(context.filesDir, CHAT_HISTORY_FILE)
            val now = System.currentTimeMillis()
            val cutoff = now - ONE_YEAR_MS

            // Keep entries within 1 year window
            val filtered = messages.filter { it.timestamp >= cutoff }
            val json = chatListAdapter.toJson(filtered)
            file.writeText(json)
        } catch (e: Exception) {
            android.util.Log.e("AiChatHistoryManager", "Error saving chat history", e)
        }
    }

    /**
     * Searches past chat history from up to 1 year back for a keyword/phrase.
     */
    fun searchChatHistory(context: Context, query: String): List<ChatMessage> {
        val history = loadChatHistory(context)
        if (query.isBlank()) return history
        val lowercaseQuery = query.lowercase().trim()
        return history.filter { it.text.lowercase().contains(lowercaseQuery) }
    }

    /**
     * Generates a context summary of past recent messages to feed into AI prompts.
     */
    fun getRecentChatContextForPrompt(context: Context, limit: Int = 8): String {
        val history = loadChatHistory(context)
        if (history.isEmpty()) return ""
        val recent = history.takeLast(limit)
        return buildString {
            append("\n--- PAST RECENT CONVERSATION HISTORY (Up to 1 Year Context Window) ---\n")
            recent.forEach { msg ->
                val role = if (msg.isUser) "Ranker (User)" else "Deepa AI"
                val snippet = msg.text.take(200).replace("\n", " ")
                append("• [$role]: $snippet\n")
            }
            append("--- END PAST CONVERSATION HISTORY ---\n\n")
        }
    }

    // ==========================================
    // AI PERSONAL MEMORY VAULT
    // ==========================================

    fun loadPersonalMemories(context: Context): List<String> {
        return try {
            val file = File(context.filesDir, AI_MEMORIES_FILE)
            if (!file.exists()) return emptyList()
            val json = file.readText()
            memoryListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePersonalMemory(context: Context, memory: String): Boolean {
        if (memory.isBlank()) return false
        try {
            val current = loadPersonalMemories(context).toMutableList()
            if (!current.contains(memory)) {
                current.add(memory)
                val file = File(context.filesDir, AI_MEMORIES_FILE)
                file.writeText(memoryListAdapter.toJson(current))
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun deletePersonalMemory(context: Context, memory: String): Boolean {
        try {
            val current = loadPersonalMemories(context).toMutableList()
            val removed = current.removeIf { it.equals(memory, ignoreCase = true) || it.contains(memory, ignoreCase = true) }
            if (removed) {
                val file = File(context.filesDir, AI_MEMORIES_FILE)
                file.writeText(memoryListAdapter.toJson(current))
            }
            return removed
        } catch (e: Exception) {
            return false
        }
    }

    fun getFormattedMemoriesForAiPrompt(context: Context): String {
        val memories = loadPersonalMemories(context)
        if (memories.isEmpty()) return ""
        return buildString {
            append("\n--- AI PERSONAL MEMORY VAULT (User Facts & Preferences) ---\n")
            memories.forEach { fact ->
                append("• $fact\n")
            }
            append("--- END AI PERSONAL MEMORY VAULT ---\n\n")
        }
    }
}
