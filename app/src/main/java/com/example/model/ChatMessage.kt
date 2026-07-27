package com.example.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "chat_messages")
@Serializable
data class ChatMessage(
    @PrimaryKey val id: Long = 0,
    @ColumnInfo(name = "sender_id") @SerialName("sender_id") val senderId: String,
    val text: String,
    val status: String = "SENT",
    @ColumnInfo(name = "created_at") @SerialName("created_at") val createdAt: String? = null,
    val reactions: String = "",
    @ColumnInfo(name = "is_pinned") @SerialName("is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "reply_to_id") @SerialName("reply_to_id") val replyToId: Long? = null,
    @ColumnInfo(name = "reply_to_text") @SerialName("reply_to_text") val replyToText: String? = null,
    @ColumnInfo(name = "reply_to_sender") @SerialName("reply_to_sender") val replyToSender: String? = null,
    val type: String = "text", // "text" | "image" | "video" | "doc" | "voice"
    @ColumnInfo(name = "content_url") @SerialName("content_url") val contentUrl: String? = null,
    @ColumnInfo(name = "local_path") @SerialName("local_path") val localPath: String? = null,
    @ColumnInfo(name = "file_name") @SerialName("file_name") val fileName: String? = null,
    @ColumnInfo(name = "file_size_kb") @SerialName("file_size_kb") val fileSizeKb: Int? = null,
    @ColumnInfo(name = "mime_type") @SerialName("mime_type") val mimeType: String? = null,
    @ColumnInfo(name = "duration_sec") @SerialName("duration_sec") val durationSec: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun parseReactions(): Map<String, List<String>> {
        if (reactions.isBlank()) return emptyMap()
        val result = mutableMapOf<String, List<String>>()
        val parts = reactions.split("|")
        for (part in parts) {
            val emojiAndUsers = part.split(":")
            if (emojiAndUsers.size == 2) {
                val emoji = emojiAndUsers[0]
                val users = emojiAndUsers[1].split(",").filter { it.isNotBlank() }
                if (users.isNotEmpty()) {
                    result[emoji] = users
                }
            }
        }
        return result
    }

    companion object {
        fun formatReactions(map: Map<String, List<String>>): String {
            return map.entries
                .filter { it.value.isNotEmpty() }
                .joinToString("|") { entry ->
                    "${entry.key}:${entry.value.distinct().joinToString(",")}"
                }
        }
    }
}


