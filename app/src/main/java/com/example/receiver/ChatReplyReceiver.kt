package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.data.AppDatabase
import com.example.data.ChatRepository
import com.example.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.action.REPLY_CHAT") {
            val results = RemoteInput.getResultsFromIntent(intent)
            val replyText = results?.getCharSequence("key_text_reply")?.toString()
            val targetSender = intent.getStringExtra("CHAT_SENDER_ID") ?: "community_chat"

            if (!replyText.isNullOrBlank()) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        val repository = ChatRepository(db.chatMessageDao())
                        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val mySenderId = appPrefs.getString("logged_in_username", "")?.ifBlank { null } ?: "user_me"

                        val nowIso = java.time.Instant.now().toString()
                        val newMessage = ChatMessage(
                            id = System.currentTimeMillis(),
                            senderId = mySenderId,
                            text = replyText,
                            status = "SENT",
                            createdAt = nowIso,
                            timestamp = System.currentTimeMillis(),
                            replyToSender = targetSender
                        )

                        repository.insertMessageToCache(newMessage)

                        // Sync to Firebase RTDB if available
                        try {
                            val dbUrl = com.example.api.FirebaseConfig.getChatDatabaseUrl(context)
                            val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                            val msgKey = newMessage.id.toString()
                            val payload = mapOf(
                                "id" to newMessage.id,
                                "sender" to newMessage.senderId,
                                "senderId" to newMessage.senderId,
                                "text" to newMessage.text,
                                "createdAt" to newMessage.createdAt,
                                "timestamp" to newMessage.timestamp,
                                "status" to newMessage.status,
                                "replyToSender" to newMessage.replyToSender
                            )
                            database.getReference("community_chat/messages").child(msgKey).setValue(payload)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        // Re-trigger notification update or dismissal
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        val notifId = intent.getIntExtra("NOTIFICATION_ID", 2001)
                        notificationManager.cancel(notifId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
