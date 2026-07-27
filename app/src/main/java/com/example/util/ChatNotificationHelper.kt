package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.model.ChatMessage

object ChatNotificationHelper {

    fun sendNotification(context: Context, message: ChatMessage) {
        try {
            val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val appSettings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

            val currentUsername = appPrefs.getString("logged_in_username", "")?.trim()?.lowercase() ?: ""
            val currentUserEmail = appPrefs.getString("user_email", "")?.trim()?.lowercase() ?: ""
            val currentSanitizedEmail = appPrefs.getString("sanitized_user_email", "")?.trim()?.lowercase() ?: ""
            val emailPrefix = if (currentUserEmail.contains("@")) currentUserEmail.substringBefore("@") else ""

            val sender = message.senderId.trim().lowercase()
            // Do NOT trigger notifications for messages sent by the user themselves!
            if (sender.isNotBlank() && (
                sender == "user_me" ||
                sender == "user me" ||
                sender == "me" ||
                sender == "myself" ||
                (currentUsername.isNotBlank() && sender == currentUsername) ||
                (currentUserEmail.isNotBlank() && sender == currentUserEmail) ||
                (currentSanitizedEmail.isNotBlank() && sender == currentSanitizedEmail) ||
                (emailPrefix.isNotBlank() && sender == emailPrefix)
            )) {
                return
            }

            val masterSilent = appPrefs.getBoolean("master_silent_mode", false) || appSettings.getBoolean("master_silent_mode", false)
            val chatNotifEnabled = appPrefs.getBoolean("chat_notifications_enabled", true) && appSettings.getBoolean("chat_notifications_enabled", true)
            if (!chatNotifEnabled) return

            val soundEnabled = appPrefs.getBoolean("chat_sound_enabled", true) && appSettings.getBoolean("chat_sound_enabled", true) && !masterSilent

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "chat_messages_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = if (soundEnabled) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW
                val channel = NotificationChannel(channelId, "Chat Messages", importance).apply {
                    description = "Notifications for incoming and automated chat messages"
                    enableVibration(soundEnabled)
                    if (soundEnabled) {
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    } else {
                        setSound(null, null)
                    }
                }
                notificationManager.createNotificationChannel(channel)
            }

            val senderDisplayName = message.senderId.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val cleanText = message.text
                .replace(Regex("""\n?\[DM:[^\]]+\]"""), "")
                .replace(Regex("""\n?\[TASK_ACTION:[^\]]+\]"""), "")
                .replace(Regex("""\n?\[SYLLABUS_ACTION:[^\]]+\]"""), "")
                .trim()

            if (cleanText.isEmpty()) return

            val shortcutId = "conv_$sender"
            val locusId = LocusIdCompat(shortcutId)

            // 1. Create Person objects for People and Conversations integration
            val senderPerson = Person.Builder()
                .setName(senderDisplayName)
                .setKey(sender)
                .setBot(sender.contains("gemini") || sender.contains("bot") || sender.contains("ai"))
                .build()

            val meDisplayName = if (currentUsername.isNotBlank()) currentUsername else "Me"
            val userMePerson = Person.Builder()
                .setName(meDisplayName)
                .setKey("user_me")
                .build()

            // 2. Publish long-lived conversation shortcut using ShortcutManagerCompat
            val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "MESSAGES")
                putExtra("CHAT_SENDER_ID", sender)
                putExtra("IS_NOTIFICATION", true)
            }

            val conversationShortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(senderDisplayName)
                .setLongLabel("Conversation with $senderDisplayName")
                .setPerson(senderPerson)
                .setLongLived(true)
                .setLocusId(locusId)
                .setIntent(shortcutIntent)
                .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_dialog_info))
                .setIsConversation()
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(context, conversationShortcut)

            // 3. Android 12+ ConversationStatus integration via reflection for safe SDK compatibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val peopleManager = context.getSystemService("people")
                    if (peopleManager != null) {
                        val statusBuilderClass = Class.forName("android.app.people.ConversationStatus\$Builder")
                        val statusBuilder = statusBuilderClass.getConstructor(String::class.java, Int::class.javaPrimitiveType)
                            .newInstance(shortcutId, 1) // 1 = ACTIVITY_ANNIVERSARY / ACTIVE
                        
                        val setDescMethod = statusBuilderClass.getMethod("setDescription", CharSequence::class.java)
                        setDescMethod.invoke(statusBuilder, "Messaging $senderDisplayName")
                        
                        val buildMethod = statusBuilderClass.getMethod("build")
                        val statusObj = buildMethod.invoke(statusBuilder)
                        
                        val statusClass = Class.forName("android.app.people.ConversationStatus")
                        val peopleManagerClass = peopleManager.javaClass
                        val pushMethod = peopleManagerClass.methods.firstOrNull { 
                            it.name == "pushConversationStatus" || it.name == "addOrUpdateConversationStatus" 
                        }
                        pushMethod?.invoke(peopleManager, shortcutId, statusObj)
                    }
                } catch (e: Throwable) {
                    // Fallback gracefully on devices or SDKs without PeopleManager
                }
            }

            // 4. Build MessagingStyle conversation notification with Direct Reply RemoteInput
            val reqCode = (message.id % 1000000).toInt().let { if (it == 0) System.currentTimeMillis().toInt() else it }
            val pendingIntent = PendingIntent.getActivity(
                context,
                reqCode,
                shortcutIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val remoteInput = androidx.core.app.RemoteInput.Builder("key_text_reply")
                .setLabel("Reply to $senderDisplayName")
                .build()

            val replyIntent = Intent(context, com.example.receiver.ChatReplyReceiver::class.java).apply {
                action = "com.example.action.REPLY_CHAT"
                putExtra("CHAT_SENDER_ID", sender)
                putExtra("NOTIFICATION_ID", reqCode)
            }

            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Reply",
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            val messagingStyle = NotificationCompat.MessagingStyle(userMePerson)
                .setConversationTitle("Chat with $senderDisplayName")
                .addMessage(cleanText, message.timestamp, senderPerson)

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(messagingStyle)
                .setShortcutId(shortcutId)
                .setLocusId(locusId)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(if (soundEnabled) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .addAction(replyAction)
                .setAutoCancel(true)

            if (soundEnabled) {
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                builder.setVibrate(longArrayOf(0, 150, 100, 150))
            }

            notificationManager.notify(reqCode, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

