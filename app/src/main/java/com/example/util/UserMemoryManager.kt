package com.example.util

import android.content.Context
import com.example.ui.ChatMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

/**
 * Data model for a stored memory snippet in the local vector database.
 */
data class MemorySnippet(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val speaker: String = "User", // "User" or "AI"
    val timestamp: Long = System.currentTimeMillis(),
    val vector: List<Float> = emptyList(), // Dense vector representation for cosine similarity
    val topics: List<String> = emptyList(),
    val importanceScore: Float = 1.0f
)

/**
 * Result model for a vector similarity match.
 */
data class VectorSearchResult(
    val snippet: MemorySnippet,
    val similarityScore: Float
)

/**
 * UserMemoryManager manages local vector indexing and semantic recall for past chat snippets
 * and user interactions using on-device term-frequency & character-ngram vector embeddings
 * with cosine similarity matching.
 */
object UserMemoryManager {

    private const val VECTOR_MEMORY_FILE = "user_vector_memories.json"
    private const val VECTOR_DIM = 128 // 128-dimensional local embedding vector

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val snippetListAdapter = moshi.adapter<List<MemorySnippet>>(
        Types.newParameterizedType(List::class.java, MemorySnippet::class.java)
    )

    // Cached memory snippets in memory for fast vector dot product search
    @Volatile
    private var memoryCache: MutableList<MemorySnippet>? = null

    /**
     * Loads all stored memory snippets from local disk.
     */
    fun loadMemories(context: Context): List<MemorySnippet> {
        val cached = memoryCache
        if (cached != null) return cached

        return synchronized(this) {
            val file = File(context.filesDir, VECTOR_MEMORY_FILE)
            if (!file.exists()) {
                val empty = mutableListOf<MemorySnippet>()
                memoryCache = empty
                empty
            } else {
                try {
                    val json = file.readText()
                    val list = snippetListAdapter.fromJson(json)?.toMutableList() ?: mutableListOf()
                    memoryCache = list
                    list
                } catch (e: Exception) {
                    android.util.Log.e("UserMemoryManager", "Failed to load vector memories", e)
                    val empty = mutableListOf<MemorySnippet>()
                    memoryCache = empty
                    empty
                }
            }
        }
    }

    /**
     * Saves memory snippets list to local disk JSON database.
     */
    private fun saveMemoriesDisk(context: Context, list: List<MemorySnippet>) {
        synchronized(this) {
            try {
                val file = File(context.filesDir, VECTOR_MEMORY_FILE)
                val json = snippetListAdapter.toJson(list)
                file.writeText(json)
                memoryCache = list.toMutableList()
            } catch (e: Exception) {
                android.util.Log.e("UserMemoryManager", "Failed to save vector memories", e)
            }
        }
    }

    /**
     * Generate a 128-dimensional normalized dense embedding vector for text using
     * subword n-gram hashing and term frequency features.
     */
    fun generateTextEmbedding(text: String): List<Float> {
        val vector = FloatArray(VECTOR_DIM) { 0f }
        val cleaned = text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9\\s]"), " ")
        val words = cleaned.split(Regex("\\s+")).filter { it.length >= 2 }

        if (words.isEmpty()) {
            return vector.toList()
        }

        // 1. Word level hash feature projection
        for (word in words) {
            val hash = word.hashCode()
            val index = Math.abs(hash) % VECTOR_DIM
            val sign = if (hash >= 0) 1.0f else -1.0f
            vector[index] += sign * 1.5f

            // 2. Subword 3-gram character feature projection
            if (word.length >= 3) {
                for (i in 0..word.length - 3) {
                    val ngram = word.substring(i, i + 3)
                    val ngHash = ngram.hashCode()
                    val ngIdx = Math.abs(ngHash) % VECTOR_DIM
                    val ngSign = if (ngHash >= 0) 0.5f else -0.5f
                    vector[ngIdx] += ngSign
                }
            }
        }

        // 3. L2 Normalization (Unit vector length)
        var sumSq = 0f
        for (valItem in vector) {
            sumSq += valItem * valItem
        }
        val norm = sqrt(sumSq)
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector.toList()
    }

    /**
     * Compute Cosine Similarity between two normalized vectors.
     */
    fun computeCosineSimilarity(vecA: List<Float>, vecB: List<Float>): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dotProduct = 0f
        for (i in vecA.indices) {
            dotProduct += vecA[i] * vecB[i]
        }
        return dotProduct.coerceIn(-1.0f, 1.0f)
    }

    /**
     * Stores a single chat snippet into the vector database.
     */
    fun storeChatSnippet(
        context: Context,
        content: String,
        speaker: String = "User",
        timestamp: Long = System.currentTimeMillis(),
        topics: List<String> = emptyList(),
        importanceScore: Float = 1.0f
    ): MemorySnippet? {
        val trimmed = content.trim()
        if (trimmed.length < 5) return null // Skip trivial messages

        val embedding = generateTextEmbedding(trimmed)
        val snippet = MemorySnippet(
            content = trimmed,
            speaker = speaker,
            timestamp = timestamp,
            vector = embedding,
            topics = topics,
            importanceScore = importanceScore
        )

        val current = loadMemories(context).toMutableList()
        // Deduplicate exact matches
        val existingIndex = current.indexOfFirst { it.content.equals(trimmed, ignoreCase = true) }
        if (existingIndex >= 0) {
            current[existingIndex] = snippet
        } else {
            current.add(snippet)
        }

        // Keep maximum 500 vector memory snippets to maintain fast search speeds
        if (current.size > 500) {
            current.sortByDescending { it.timestamp }
            while (current.size > 500) {
                current.removeAt(current.size - 1)
            }
        }

        saveMemoriesDisk(context, current)
        return snippet
    }

    /**
     * Convenient method to store both user prompt and AI response pair into memory.
     */
    fun storeChatPair(context: Context, userQuery: String, aiResponse: String) {
        if (userQuery.isNotBlank() && userQuery.length >= 4) {
            storeChatSnippet(context, userQuery, speaker = "User", importanceScore = 1.2f)
        }
        if (aiResponse.isNotBlank() && aiResponse.length >= 10) {
            storeChatSnippet(context, aiResponse, speaker = "AI", importanceScore = 1.0f)
        }
    }

    /**
     * Vector search query to retrieve semantically top-K relevant memory snippets.
     */
    fun searchMemories(
        context: Context,
        query: String,
        topK: Int = 5,
        minSimilarityScore: Float = 0.12f
    ): List<VectorSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val memories = loadMemories(context)
        if (memories.isEmpty()) return emptyList()

        val queryVec = generateTextEmbedding(trimmed)
        val results = mutableListOf<VectorSearchResult>()

        for (snippet in memories) {
            var sim = computeCosineSimilarity(queryVec, snippet.vector)
            // Keyword boost if exact query words overlap
            val lowerContent = snippet.content.lowercase(Locale.ROOT)
            val queryWords = trimmed.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.length > 3 }
            var keywordBonus = 0f
            for (qw in queryWords) {
                if (lowerContent.contains(qw)) {
                    keywordBonus += 0.08f
                }
            }
            val finalScore = (sim + keywordBonus) * snippet.importanceScore

            if (finalScore >= minSimilarityScore) {
                results.add(VectorSearchResult(snippet, finalScore))
            }
        }

        results.sortByDescending { it.similarityScore }
        return results.take(topK)
    }

    /**
     * Formats retrieved vector memories into an AI prompt injection string for contextual grounding.
     */
    fun getRelevantMemoryContextForPrompt(context: Context, currentPrompt: String, topK: Int = 4): String {
        val matches = searchMemories(context, currentPrompt, topK = topK)
        if (matches.isEmpty()) return ""

        return buildString {
            append("\n🧠 --- LOCAL VECTOR MEMORY RECALL (Semantically Relevant Past Chat Snippets) ---\n")
            matches.forEachIndexed { idx, result ->
                val snippet = result.snippet
                val dateStr = android.text.format.DateFormat.format("yyyy-MM-dd", snippet.timestamp)
                val percent = (result.similarityScore * 100).toInt().coerceIn(1, 100)
                append("${idx + 1}. [${snippet.speaker} - $dateStr] (${percent}% match relevance): ${snippet.content}\n")
            }
            append("--- END LOCAL VECTOR MEMORY RECALL ---\n\n")
        }
    }

    /**
     * Delete memory snippet by ID.
     */
    fun deleteMemory(context: Context, snippetId: String): Boolean {
        val current = loadMemories(context).toMutableList()
        val removed = current.removeIf { it.id == snippetId }
        if (removed) {
            saveMemoriesDisk(context, current)
        }
        return removed
    }

    /**
     * Clear all vector memories.
     */
    fun clearAllMemories(context: Context) {
        saveMemoriesDisk(context, emptyList())
    }

    /**
     * Ingest all historical messages from AiChatHistoryManager if not already indexed.
     */
    fun autoIngestChatHistory(context: Context) {
        try {
            val history = AiChatHistoryManager.loadChatHistory(context)
            if (history.isEmpty()) return
            val currentMemories = loadMemories(context)
            val indexedContents = currentMemories.map { it.content }.toSet()

            var newAdded = 0
            for (msg in history) {
                if (msg.text.length >= 6 && !indexedContents.contains(msg.text.trim())) {
                    val speaker = if (msg.isUser) "User" else "AI"
                    storeChatSnippet(
                        context = context,
                        content = msg.text,
                        speaker = speaker,
                        timestamp = msg.timestamp
                    )
                    newAdded++
                }
            }
            if (newAdded > 0) {
                android.util.Log.d("UserMemoryManager", "Indexed $newAdded new historical chat messages into vector store.")
            }
        } catch (e: Exception) {
            android.util.Log.e("UserMemoryManager", "Failed to auto-ingest chat history", e)
        }
    }
}
