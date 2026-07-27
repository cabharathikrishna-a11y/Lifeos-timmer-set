package com.example.data

import com.example.model.ChatMessage
import com.example.model.UserPresence
import com.example.util.SupabaseManager
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class ChatRepository(
    private val chatMessageDao: ChatMessageDao? = try {
        AppDatabase.getInstance(com.example.MainApplication.instance).chatMessageDao()
    } catch (e: Exception) {
        null
    }
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun getAllCachedMessagesFlow(): Flow<List<ChatMessage>> {
        return chatMessageDao?.getAllCachedMessagesFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun getPinnedCachedMessages(): List<ChatMessage> {
        val roomPinned = try {
            chatMessageDao?.getPinnedMessages() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val context = com.example.MainApplication.instance
        val backupJson = com.example.util.PrefsDataStore.getString(context, "pinned_messages_backup_v1", "[]") ?: "[]"
        val backupPinned = try {
            json.decodeFromString<List<ChatMessage>>(backupJson)
        } catch (e: Exception) {
            emptyList()
        }

        val combinedMap = LinkedHashMap<Long, ChatMessage>()
        for (m in roomPinned) {
            combinedMap[m.id] = m.copy(isPinned = true)
        }
        for (m in backupPinned) {
            if (!combinedMap.containsKey(m.id)) {
                combinedMap[m.id] = m.copy(isPinned = true)
            }
        }

        val merged = combinedMap.values.toList()
        if (merged.isNotEmpty()) {
            try {
                chatMessageDao?.insertMessages(merged)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return merged
    }

    suspend fun updatePinnedBackupStore(message: ChatMessage, isPinned: Boolean) {
        try {
            val context = com.example.MainApplication.instance
            val backupJson = com.example.util.PrefsDataStore.getString(context, "pinned_messages_backup_v1", "[]") ?: "[]"
            val currentList = try {
                json.decodeFromString<List<ChatMessage>>(backupJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }

            if (isPinned) {
                currentList.removeAll { it.id == message.id }
                currentList.add(message.copy(isPinned = true))
            } else {
                currentList.removeAll { it.id == message.id }
            }

            val updatedJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ChatMessage.serializer()), currentList)
            com.example.util.PrefsDataStore.putString(context, "pinned_messages_backup_v1", updatedJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchPinnedMessages(): List<ChatMessage> {
        val cachedPinned = getPinnedCachedMessages()
        val remotePinned = mutableListOf<ChatMessage>()

        try {
            val supabasePinned = SupabaseManager.client.from("messages")
                .select {
                    filter {
                        eq("is_pinned", true)
                    }
                }
                .decodeList<ChatMessage>()
            remotePinned.addAll(supabasePinned)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val combinedMap = LinkedHashMap<Long, ChatMessage>()
        for (m in cachedPinned) {
            combinedMap[m.id] = m.copy(isPinned = true)
        }
        for (m in remotePinned) {
            combinedMap[m.id] = m.copy(isPinned = true)
        }

        val result = combinedMap.values.toList()
        if (result.isNotEmpty()) {
            try {
                chatMessageDao?.insertMessages(result)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val context = com.example.MainApplication.instance
                val updatedJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ChatMessage.serializer()), result)
                com.example.util.PrefsDataStore.putString(context, "pinned_messages_backup_v1", updatedJson)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }

    suspend fun searchMessages(query: String): List<ChatMessage> {
        if (query.isBlank()) return getCachedMessages()
        return try {
            chatMessageDao?.searchMessages(query) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getCachedMessages(): List<ChatMessage> {
        val cached = chatMessageDao?.getAllCachedMessages() ?: emptyList()
        val pinned = getPinnedCachedMessages()
        val map = LinkedHashMap<Long, ChatMessage>()
        for (m in cached) {
            map[m.id] = m
        }
        for (p in pinned) {
            val existing = map[p.id]
            if (existing != null) {
                map[p.id] = existing.copy(isPinned = true)
            } else {
                map[p.id] = p.copy(isPinned = true)
            }
        }
        return map.values.sortedBy { it.id }
    }

    suspend fun getRecentCachedMessages(limit: Int): List<ChatMessage> {
        val cached = chatMessageDao?.getRecentCachedMessages(limit) ?: emptyList()
        val pinned = getPinnedCachedMessages()
        val map = LinkedHashMap<Long, ChatMessage>()
        for (m in cached) {
            map[m.id] = m
        }
        for (p in pinned) {
            val existing = map[p.id]
            if (existing != null) {
                map[p.id] = existing.copy(isPinned = true)
            } else {
                map[p.id] = p.copy(isPinned = true)
            }
        }
        return map.values.sortedBy { it.id }
    }

    suspend fun fetchAndCacheRecentMessages(limit: Int): List<ChatMessage> {
        val pinned = fetchPinnedMessages()
        val recent = try {
            val remoteMessages = SupabaseManager.client.from("messages")
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<ChatMessage>()
                .reversed()

            if (remoteMessages.isNotEmpty()) {
                chatMessageDao?.insertMessages(remoteMessages)
            }
            val cached = chatMessageDao?.getRecentCachedMessages(limit)
            if (!cached.isNullOrEmpty()) cached else remoteMessages
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val fallbackRemote = SupabaseManager.client.from("messages")
                    .select {
                        order("created_at", Order.ASCENDING)
                    }
                    .decodeList<ChatMessage>()
                if (fallbackRemote.isNotEmpty()) {
                    chatMessageDao?.insertMessages(fallbackRemote)
                }
                chatMessageDao?.getRecentCachedMessages(limit) ?: fallbackRemote
            } catch (e2: Exception) {
                chatMessageDao?.getRecentCachedMessages(limit) ?: emptyList()
            }
        }

        val map = LinkedHashMap<Long, ChatMessage>()
        for (m in recent) {
            map[m.id] = m
        }
        for (p in pinned) {
            val existing = map[p.id]
            if (existing != null) {
                map[p.id] = existing.copy(isPinned = true)
            } else {
                map[p.id] = p.copy(isPinned = true)
            }
        }
        return map.values.sortedBy { it.id }
    }

    suspend fun fetchAndCacheLast12MonthsMessages(): List<ChatMessage> {
        return fetchAndCacheRecentMessages(100)
    }

    suspend fun getInitialMessages(): List<ChatMessage> {
        return fetchAndCacheLast12MonthsMessages()
    }

    suspend fun sendMessage(
        text: String,
        senderId: String,
        replyToId: Long? = null,
        replyToText: String? = null,
        replyToSender: String? = null
    ): ChatMessage {
        val uniqueId = System.currentTimeMillis()
        val nowIso = java.time.Instant.now().toString()
        val message = ChatMessage(
            id = uniqueId,
            senderId = senderId,
            text = text,
            status = "SENT",
            createdAt = nowIso,
            timestamp = uniqueId,
            replyToId = replyToId,
            replyToText = replyToText,
            replyToSender = replyToSender
        )
        val sent = try {
            val res = SupabaseManager.client.from("messages")
                .insert(message) {
                    select()
                }
                .decodeSingle<ChatMessage>()
            if (res.id != 0L) res else res.copy(id = uniqueId)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback if Supabase schema is missing reply_to columns
            try {
                val fallbackMsg = ChatMessage(id = uniqueId, senderId = senderId, text = text, status = "SENT")
                val res2 = SupabaseManager.client.from("messages")
                    .insert(fallbackMsg) { select() }
                    .decodeSingle<ChatMessage>()
                    .copy(replyToId = replyToId, replyToText = replyToText, replyToSender = replyToSender)
                if (res2.id != 0L) res2 else res2.copy(id = uniqueId)
            } catch (e2: Exception) {
                message
            }
        }

        try {
            chatMessageDao?.insertMessage(sent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sent
    }

    suspend fun insertMessageToCache(message: ChatMessage) {
        try {
            chatMessageDao?.insertMessage(message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun insertMessagesToCache(messages: List<ChatMessage>) {
        try {
            chatMessageDao?.insertMessages(messages)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun editMessage(messageId: Long, newText: String): ChatMessage? {
        return try {
            val updated = SupabaseManager.client.from("messages")
                .update({
                    set("text", newText)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                    select()
                }
                .decodeSingleOrNull<ChatMessage>()

            if (updated != null) {
                try {
                    chatMessageDao?.insertMessage(updated)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            updated
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateMessageReaction(messageId: Long, reactions: String) {
        try {
            SupabaseManager.client.from("messages")
                .update({
                    set("reactions", reactions)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            chatMessageDao?.updateMessageReactions(messageId, reactions)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                chatMessageDao?.updateMessageReactions(messageId, reactions)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    suspend fun pinMessage(messageId: Long, isPinned: Boolean) {
        try {
            SupabaseManager.client.from("messages")
                .update({
                    set("is_pinned", isPinned)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            chatMessageDao?.updateMessagePinned(messageId, isPinned)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                chatMessageDao?.updateMessagePinned(messageId, isPinned)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }

        try {
            val msg = chatMessageDao?.getAllCachedMessages()?.find { it.id == messageId }
            if (msg != null) {
                updatePinnedBackupStore(msg, isPinned)
            } else {
                updatePinnedBackupStore(ChatMessage(id = messageId, senderId = "", text = "", isPinned = isPinned), isPinned)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markIncomingMessagesAsRead(currentUserId: String = "user_me") {
        try {
            SupabaseManager.client.from("messages")
                .update({
                    set("status", "READ")
                }) {
                    filter {
                        neq("sender_id", currentUserId)
                        neq("status", "READ")
                    }
                }
            chatMessageDao?.markIncomingMessagesAsRead()
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                chatMessageDao?.markIncomingMessagesAsRead()
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    suspend fun markMessagesAsRead(messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        try {
            messageIds.forEach { id ->
                SupabaseManager.client.from("messages")
                    .update({
                        set("status", "READ")
                    }) {
                        filter {
                            eq("id", id)
                        }
                    }
            }
            chatMessageDao?.markMessagesAsRead(messageIds)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                chatMessageDao?.markMessagesAsRead(messageIds)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    suspend fun deleteMessage(messageId: Long) {
        try {
            SupabaseManager.client.from("messages")
                .delete {
                    filter {
                        eq("id", messageId)
                    }
                }
            chatMessageDao?.deleteMessageById(messageId)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                chatMessageDao?.deleteMessageById(messageId)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    fun subscribeToNewMessages(): Flow<ChatMessage> = callbackFlow {
        val channel = SupabaseManager.client.channel("public:messages")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }
        val job = launch {
            try {
                channel.subscribe()
                changeFlow.collect { action ->
                    when (action) {
                        is PostgresAction.Insert -> {
                            try {
                                val msg = json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                                chatMessageDao?.insertMessage(msg)
                                trySend(msg)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        is PostgresAction.Update -> {
                            try {
                                val msg = json.decodeFromJsonElement(ChatMessage.serializer(), action.record)
                                chatMessageDao?.insertMessage(msg)
                                trySend(msg)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        is PostgresAction.Delete -> {
                            try {
                                val id = action.oldRecord["id"]?.jsonPrimitive?.longOrNull
                                if (id != null) {
                                    chatMessageDao?.deleteMessageById(id)
                                    trySend(ChatMessage(id = id, senderId = "", text = "__DELETED__"))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        awaitClose {
            launch {
                try {
                    channel.unsubscribe()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            job.cancel()
        }
    }

    private var presenceChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    fun subscribeToPresence(currentUserId: String): Flow<Map<String, UserPresence>> = callbackFlow {
        val channel = SupabaseManager.client.channel("presence:study_group")
        presenceChannel = channel
        val presenceMap = mutableMapOf<String, UserPresence>()

        val job = launch {
            try {
                channel.subscribe()
                channel.track(
                    buildJsonObject {
                        put("userId", currentUserId)
                        put("isOnline", true)
                        put("isTyping", false)
                        put("updatedAt", System.currentTimeMillis())
                    }
                )

                channel.presenceDataFlow<JsonObject>().collect { presenceList ->
                    presenceMap.clear()
                    presenceList.forEach { jsonObject ->
                        try {
                            val uid = jsonObject["userId"]?.jsonPrimitive?.content ?: ""
                            val isOnline = jsonObject["isOnline"]?.jsonPrimitive?.booleanOrNull ?: true
                            val isTyping = jsonObject["isTyping"]?.jsonPrimitive?.booleanOrNull ?: false
                            val updatedAt = jsonObject["updatedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                            if (uid.isNotEmpty()) {
                                presenceMap[uid] = UserPresence(uid, isOnline, isTyping, updatedAt)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    trySend(presenceMap.toMap())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        awaitClose {
            launch {
                try {
                    channel.track(
                        buildJsonObject {
                            put("userId", currentUserId)
                            put("isOnline", false)
                            put("isTyping", false)
                            put("updatedAt", System.currentTimeMillis())
                        }
                    )
                    channel.unsubscribe()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            job.cancel()
            presenceChannel = null
        }
    }

    suspend fun updatePresenceStatus(currentUserId: String, isTyping: Boolean) {
        try {
            val channel = presenceChannel ?: SupabaseManager.client.channel("presence:study_group")
            channel.track(
                buildJsonObject {
                    put("userId", currentUserId)
                    put("isOnline", true)
                    put("isTyping", isTyping)
                    put("updatedAt", System.currentTimeMillis())
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

