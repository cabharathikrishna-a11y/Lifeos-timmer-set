package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.model.ChatMessage
import com.example.util.ChatNotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val appSettings = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val masterSilent = prefs.getBoolean("master_silent_mode", false) || appSettings.getBoolean("master_silent_mode", false)
        val isBackgroundSilent = prefs.getBoolean("background_services_silent_mode", false) && prefs.getBoolean("app_is_backgrounded", false)

        if (masterSilent || isBackgroundSilent) {
            Log.d(TAG, "Silent/Sleep mode is active. Suppressing push notification.")
            return
        }

        Log.d(TAG, "From: ${remoteMessage.from}, Priority: ${remoteMessage.priority}, OriginalPriority: ${remoteMessage.originalPriority}")

        var resolvedTitle = remoteMessage.notification?.title
        var resolvedBody = remoteMessage.notification?.body

        // 1. Check localization fields in data payload or notification payload (FCM v1 localization)
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val titleLocKey = data["title_loc_key"] ?: data["title_key"]
            val bodyLocKey = data["body_loc_key"] ?: data["body_key"]

            if (!titleLocKey.isNullOrBlank()) {
                resolvedTitle = resolveLocalizedString(titleLocKey, data["title_loc_args"])
            }
            if (!bodyLocKey.isNullOrBlank()) {
                resolvedBody = resolveLocalizedString(bodyLocKey, data["body_loc_args"])
            }
        }

        // 2. Check if payload is settings update signal or chat message
        if (data.isNotEmpty()) {
            val messageType = data["type"] ?: data["category"] ?: "text"
            if (messageType == "SETTINGS_UPDATED" || messageType == "settings_sync") {
                val email = prefs.getString("user_email", "") ?: ""
                if (email.isNotBlank()) {
                    Log.i(TAG, "Received FCM SETTINGS_UPDATED signal. Pulling updated settings from Firestore...")
                    com.example.api.UserSettingsSyncEngine.pullSettingsFromCloud(applicationContext, email)
                }
                return
            }

            if (messageType == "KEEP_NOTES_SYNC" || messageType == "keep_notes_sync" || messageType == "keep_notes" ||
                messageType == "ALL_DATA_SYNC" || messageType == "TASKS_SYNC" || messageType == "JOURNAL_SYNC" ||
                messageType == "HEALTH_SYNC" || messageType == "FILES_SYNC" || messageType == "FINANCE_SYNC" ||
                messageType == "GDRIVE_SYNC_TRIGGER") {
                val email = prefs.getString("user_email", "") ?: ""
                if (email.isNotBlank()) {
                    Log.i(TAG, "Received FCM live sync signal ($messageType). Pulling updated app data from cloud...")
                    val db = AppDatabase.getInstance(applicationContext)
                    com.example.api.AppDataLiveSyncEngine.pullAllDataFromCloud(applicationContext, email, db)
                    
                    if (messageType == "GDRIVE_SYNC_TRIGGER" && com.example.util.GoogleDriveSyncManager.hasDrivePermission(applicationContext)) {
                        CoroutineScope(Dispatchers.IO).launch {
                            com.example.util.GoogleDriveSyncManager.restoreAllAppData(applicationContext, db)
                        }
                    }
                }
                return
            }

            val isChatMessage = messageType == "chat" ||
                    data.containsKey("sender_id") ||
                    data.containsKey("chat_message") ||
                    data.containsKey("sender")

            if (isChatMessage) {
                val senderId = data["sender_id"] ?: data["sender"] ?: "Community Member"
                val text = data["text"] ?: data["chat_message"] ?: data["body"] ?: resolvedBody ?: ""
                val msgId = data["id"]?.toLongOrNull() ?: data["message_id"]?.toLongOrNull() ?: System.currentTimeMillis()
                val createdAt = data["created_at"] ?: data["createdAt"] ?: java.time.Instant.now().toString()
                val replyToId = data["reply_to_id"]?.toLongOrNull() ?: data["replyToId"]?.toLongOrNull()
                val replyToText = data["reply_to_text"] ?: data["replyToText"]
                val replyToSender = data["reply_to_sender"] ?: data["replyToSender"]
                val reactions = data["reactions"] ?: ""
                val contentUrl = data["content_url"] ?: data["contentUrl"]

                val chatMsg = ChatMessage(
                    id = msgId,
                    senderId = senderId,
                    text = text,
                    status = "SENT",
                    createdAt = createdAt,
                    timestamp = System.currentTimeMillis(),
                    reactions = reactions,
                    replyToId = replyToId,
                    replyToText = replyToText,
                    replyToSender = replyToSender,
                    type = messageType,
                    contentUrl = contentUrl
                )

                // Store message directly in Room DB (Local persistence) for reactive UI update
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(applicationContext)
                        db.chatMessageDao().insertMessage(chatMsg)
                        Log.d(TAG, "Persisted FCM chat message $msgId into Room DB successfully.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed storing FCM chat message into Room DB", e)
                    }
                }

                // Show rich conversation notification with direct reply capability
                ChatNotificationHelper.sendNotification(applicationContext, chatMsg)
                return
            }
        }

        // 3. Fallback standard notification
        val finalTitle = resolvedTitle ?: remoteMessage.notification?.title ?: "Notification"
        val finalBody = resolvedBody ?: remoteMessage.notification?.body ?: ""
        if (finalTitle.isNotBlank() || finalBody.isNotBlank()) {
            sendNotification(finalTitle, finalBody)
        }
    }

    private fun resolveLocalizedString(key: String, argsString: String?): String? {
        return try {
            val resId = resources.getIdentifier(key, "string", packageName)
            if (resId != 0) {
                if (!argsString.isNullOrBlank()) {
                    val argsList = argsString.split(",").map { it.trim() }.toTypedArray()
                    resources.getString(resId, *argsList)
                } else {
                    resources.getString(resId)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving localized string for key: $key", e)
            null
        }
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.w(TAG, "onDeletedMessages received: FCM server dropped or deleted pending messages. Requesting full sync.")
        try {
            val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("fcm_needs_full_sync", true).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling onDeletedMessages", e)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("fcm_registration_token", token)
            .putLong("fcm_token_timestamp", System.currentTimeMillis())
            .apply()

        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String?) {
        if (token.isNullOrEmpty()) return
        try {
            val context = applicationContext
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val email = prefs.getString("user_email", "") ?: ""
            if (email.isBlank()) return

            val dbUrl = com.example.api.FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) return

            val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
            val sanitizedEmail = com.example.api.DevicePresenceManager.sanitizeEmail(email)
            val deviceKey = com.example.api.DevicePresenceManager.getDeviceKey(context)

            val tokenRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("DEVICES_LOGGED_IN")
                .child(deviceKey)
                .child("fcm token number")

            tokenRef.setValue(token).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Successfully synchronized newly refreshed FCM token to RTDB under $deviceKey")
                } else {
                    Log.e(TAG, "Failed to synchronize refreshed FCM token", task.exception)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendRegistrationToServer", e)
        }
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            val lower = "$title $messageBody".lowercase()
            if (lower.contains("chat") || lower.contains("message")) {
                putExtra("NAVIGATE_TO", "MESSAGES")
                putExtra("navigate_to", "chat")
                putExtra("IS_CHAT_NOTIFICATION", true)
            } else {
                putExtra("NAVIGATE_TO", "DEEPA_AI")
                putExtra("IS_NOTIFICATION", true)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "fcm_default_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}
