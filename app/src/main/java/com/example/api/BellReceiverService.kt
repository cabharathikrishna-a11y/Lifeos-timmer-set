package com.example.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object BellReceiverService {
    private const val TAG = "BellReceiverService"
    private const val CHANNEL_ID = "focus_peers"
    private const val CHANNEL_NAME = "Focus Peers"

    private var bellsRef: DatabaseReference? = null
    private var bellsListener: ValueEventListener? = null

    fun startListening(context: Context, myEmail: String) {
        if (myEmail.isBlank()) return
        stopListening()

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) {
                Log.e(TAG, "Database URL is empty, cannot listen to bells.")
                return
            }

            val database = FirebaseDatabase.getInstance(dbUrl)
            val mySanitized = DevicePresenceManager.sanitizeEmail(myEmail)

            val bRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(mySanitized)
                .child("BELLS")

            bellsRef = bRef

            createNotificationChannel(context)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    for (child in snapshot.children) {
                        val key = child.key ?: continue
                        val isProcessed = child.child("isProcessed").getValue(Boolean::class.java) ?: false
                        if (!isProcessed) {
                            val senderName = child.child("senderName").getValue(String::class.java) ?: "A friend"
                            val nudgeType = child.child("nudgeType").getValue(String::class.java) ?: "SALUTE"
                            
                            Log.d(TAG, "Received bell from $senderName of type $nudgeType")
                            
                            // 1. Process / Notification & Haptics
                            triggerBellAction(context, senderName, nudgeType)

                            // 2. Atomic Cleanup: Immediately remove node from RTDB
                            child.ref.removeValue()
                                .addOnSuccessListener {
                                    Log.d(TAG, "Successfully removed processed Bell node for $key")
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Failed to remove processed Bell node for $key", e)
                                    // Fallback: Set isProcessed to true
                                    child.ref.child("isProcessed").setValue(true)
                                }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Bells listener cancelled", error.toException())
                }
            }

            bRef.addValueEventListener(listener)
            bellsListener = listener
            Log.d(TAG, "Started listening to Bells for $myEmail")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting Bells listener", e)
        }
    }

    fun stopListening() {
        try {
            bellsRef?.let { ref ->
                bellsListener?.let { listener ->
                    ref.removeEventListener(listener)
                }
            }
            bellsRef = null
            bellsListener = null
            Log.d(TAG, "Bells listener cleaned up successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Bells listener", e)
        }
    }

    private fun triggerBellAction(context: Context, senderName: String, nudgeType: String) {
        // Trigger 5-second Bell Sound & Vibration Audio
        try {
            com.example.util.FocusTimerManager.playFriendReminderBellSound(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play bell sound: ${e.message}", e)
        }

        // Trigger Haptic Feedback
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (nudgeType.equals("WAKE_UP", ignoreCase = true)) {
                        // Wake-up: Continuous pulsing buzz (vibrate 400ms, sleep 200ms, repeat 3 times)
                        val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val effect = VibrationEffect.createWaveform(pattern, -1)
                            v.vibrate(effect)
                        } else {
                            @Suppress("DEPRECATION")
                            v.vibrate(pattern, -1)
                        }
                    } else {
                        // Salute: Two short, sharp pulses (vibrate 100ms, sleep 100ms, vibrate 100ms)
                        val pattern = longArrayOf(0, 100, 100, 100)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val effect = VibrationEffect.createWaveform(pattern, -1)
                            v.vibrate(effect)
                        } else {
                            @Suppress("DEPRECATION")
                            v.vibrate(pattern, -1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute haptic feedback", e)
        }

        // Fire System Notification
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                val title = "🔔 Nudge from $senderName!"
                val body = if (nudgeType.equals("WAKE_UP", ignoreCase = true)) {
                    "Time to get back to the grind!"
                } else {
                    "Saluting your intense focus session!"
                }

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("NAVIGATE_TO", "TIMER")
                    putExtra("SHOW_TIMER_PAGE", true)
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    System.currentTimeMillis().toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Safe fallback small icon
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fire system notification", e)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager != null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifications for real-time focus peer nudges and salutes"
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Notification Channel", e)
            }
        }
    }
}
