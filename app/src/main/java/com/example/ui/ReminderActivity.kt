package com.example.ui

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderActivity : ComponentActivity() {

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var taskId: Int = -1
    private var rawTaskId: Int = -1
    private var taskTitle: String = ""
    private var taskTime: String = ""
    private var taskPriority: String = "MEDIUM"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Parse extras
        taskId = intent.getIntExtra("TASK_ID", -1)
        rawTaskId = intent.getIntExtra("RAW_TASK_ID", -1)
        taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Task Reminder"
        taskTime = intent.getStringExtra("TASK_TIME") ?: ""
        taskPriority = intent.getStringExtra("TASK_PRIORITY") ?: "MEDIUM"

        Log.d("ReminderActivity", "Reminder activity launched for task $taskId, priority: $taskPriority")

        // Lockscreen / Keep Screen active parameters compatibility for modern Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enableEdgeToEdge()
        startAlert()

        setContent {
            MyApplicationTheme {
                if (taskId == 20001) {
                    BedtimeScreen(
                        onDismiss = {
                            stopAlert()
                            cancelNotification()
                            finish()
                        }
                    )
                } else if (taskId == 20002) {
                    WakeUpAlarmScreen(
                        onSnooze = { minutes ->
                            stopAlert()
                            cancelNotification()
                            AlarmScheduler.scheduleSnooze(applicationContext, taskId, taskTitle, taskTime, taskPriority, minutes)
                            finish()
                        },
                        onDismiss = {
                            stopAlert()
                            cancelNotification()
                            val prefs = applicationContext.getSharedPreferences("app_prefs", MODE_PRIVATE)
                            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                            prefs.edit().putString("actual_wake_up_time_$todayStr", "LATE").apply()
                            finish()
                        },
                        onWokeUp = {
                            stopAlert()
                            cancelNotification()
                            val prefs = applicationContext.getSharedPreferences("app_prefs", MODE_PRIVATE)
                            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                            val curTimeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
                            prefs.edit().putString("actual_wake_up_time_$todayStr", curTimeStr).apply()
                            finish()
                        }
                    )
                } else {
                    // Priority-specific auto-dismiss
                    if (taskPriority.uppercase() == "LOW" || taskPriority.uppercase() == "NONE") {
                        LaunchedEffect(Unit) {
                            Log.d("ReminderActivity", "Auto-dismissing in 5 minutes.")
                            kotlinx.coroutines.delay(5 * 60 * 1000L) // 5 minutes (300,000 ms)
                            stopAlert()
                            finish()
                        }
                    }

                    ReminderScreen(
                        title = taskTitle.substringBefore(" (").trim(),
                        time = taskTime,
                        onDismiss = {
                            stopAlert()
                            cancelNotification()
                            finish()
                        },
                        onSnooze = { minutes ->
                            stopAlert()
                            cancelNotification()
                            AlarmScheduler.scheduleSnooze(applicationContext, taskId, taskTitle, taskTime, taskPriority, minutes)
                            finish()
                        },
                        onComplete = {
                            stopAlert()
                            cancelNotification()
                            markTaskCompletedAndFinish()
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun BedtimeScreen(
        onDismiss: () -> Unit
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF06070D)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "🌙",
                    fontSize = 72.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Bedtime Reminder",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "It's time to wind down and prepare for sleep. Rest well!",
                    color = Color.LightGray,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38B0F2)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.width(200.dp).height(48.dp)
                ) {
                    Text("Goodnight", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    @Composable
    private fun WakeUpAlarmScreen(
        onSnooze: (Int) -> Unit,
        onDismiss: () -> Unit,
        onWokeUp: () -> Unit
    ) {
        var snoozeMinutes by remember { mutableStateOf(10) }
        val timeFormat = remember { java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()) }
        var currentTime by remember { mutableStateOf(timeFormat.format(java.util.Date())) }

        LaunchedEffect(Unit) {
            while (true) {
                currentTime = timeFormat.format(java.util.Date())
                kotlinx.coroutines.delay(1000L)
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF090A10)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                ) {
                    Text(
                        text = "☀️",
                        fontSize = 64.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Good Morning!",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentTime,
                        color = Color(0xFF38B0F2),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Interactive Buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Woke Up button (Primary)
                    Button(
                        onClick = onWokeUp,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("alarm_woke_up_btn")
                    ) {
                        Text(
                            text = "I literally woke up!",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Dismiss button (Secondary)
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("alarm_dismiss_btn")
                    ) {
                        Text(
                            text = "I am gonna wake up late",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Snooze controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { if (snoozeMinutes > 5) snoozeMinutes -= 5 },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height(2.dp)
                                    .background(Color.White.copy(alpha = 0.7f), shape = RoundedCornerShape(1.dp))
                            )
                        }

                        Button(
                            onClick = { onSnooze(snoozeMinutes) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFC7A168).copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .widthIn(min = 160.dp)
                                .height(44.dp)
                                .border(1.dp, Color(0xFFC7A168).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                        ) {
                            Text(
                                text = "Snooze $snoozeMinutes mins",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE5C088)
                            )
                        }

                        IconButton(
                            onClick = { if (snoozeMinutes < 60) snoozeMinutes += 5 },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase Snooze",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ReminderScreen(
        title: String,
        time: String,
        onDismiss: () -> Unit,
        onSnooze: (Int) -> Unit,
        onComplete: () -> Unit
    ) {
        var snoozeMinutes by remember { mutableStateOf(15) }
        val context = LocalContext.current

        val customBgBitmap = remember {
            val file = java.io.File(context.filesDir, "reminder_bg.jpg")
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val useCustom = prefs.getBoolean("use_custom_reminder_bg", false)
            if (useCustom && file.exists()) {
                try {
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (customBgBitmap != null) {
                Image(
                    bitmap = customBgBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF070709))
                        .drawBehind {
                            // Top-right teal glow aura
                            drawCircle(
                                color = Color(0xFF0F766E).copy(alpha = 0.28f),
                                radius = size.width * 0.8f,
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.25f)
                            )
                            // Left purple glow aura
                            drawCircle(
                                color = Color(0xFF6D28D9).copy(alpha = 0.22f),
                                radius = size.width * 0.7f,
                                center = androidx.compose.ui.geometry.Offset(0f, size.height * 0.55f)
                            )
                            // Bottom sandy gold gold glow aura
                            drawCircle(
                                color = Color(0xFFD97706).copy(alpha = 0.24f),
                                radius = size.width * 0.9f,
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.95f)
                            )
                        }
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 48.dp)
            ) {
                // Header details matching clean aesthetics
                Text(
                    text = "L I F E   O S   R E M I N D E R",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )

                // Central Focus Text & Buttons (Take my vitamins, 2:00 pm, Dismiss, Complete)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .testTag("reminder_task_title")
                        )

                        if (time.isNotEmpty() && time != "None") {
                            Text(
                                text = time,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Centered stack buttons: Dismiss and Complete (styled beautifully like pill shape buttons)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.width(180.dp)
                    ) {
                        // Dismiss button with dark translucent container
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A3D3C).copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reminder_dismiss_btn")
                        ) {
                            Text(
                                text = "Dismiss",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        // Complete button with elegant cyan/teal container
                        Button(
                            onClick = onComplete,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F766E).copy(alpha = 0.9f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reminder_completed_btn")
                        ) {
                            Text(
                                text = "Complete",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Bottom Interactive Capsule Bar for Custom Snoozing
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .testTag("reminder_snooze_container")
                ) {
                    // Minus Button
                    IconButton(
                        onClick = { if (snoozeMinutes > 5) snoozeMinutes -= 5 },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(2.dp)
                                .background(Color.White.copy(alpha = 0.7f), shape = RoundedCornerShape(1.dp))
                        )
                    }

                    // Central Snooze Button
                    Button(
                        onClick = { onSnooze(snoozeMinutes) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC7A168).copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .widthIn(min = 160.dp)
                            .height(48.dp)
                            .border(1.dp, Color(0xFFC7A168).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .testTag("reminder_snooze_btn")
                    ) {
                        Text(
                            text = "Snooze $snoozeMinutes mins",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE5C088)
                        )
                    }

                    // Plus Button
                    IconButton(
                        onClick = { if (snoozeMinutes < 120) snoozeMinutes += 5 },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase Snooze",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    private fun startAlert() {
        val prefsForSilent = getSharedPreferences("app_prefs", MODE_PRIVATE)
        
        // Wakeup alarm (20002) always rings, other alarms check silent settings
        if (taskId != 20002) {
            if (prefsForSilent.getBoolean("master_silent_mode", false) || prefsForSilent.getBoolean("task_silent_mode", false)) {
                Log.d("ReminderActivity", "Silent mode or master silent mode is ON. Suppressing sound and vibration.")
                return
            }
        }

        val alarmSoundKey = when (taskPriority.uppercase()) {
            "HIGH" -> "task_high_alarm_sound"
            "MEDIUM" -> "task_medium_alarm_sound"
            else -> "task_low_alarm_sound"
        }
        var isAlarmSoundEnabled = prefsForSilent.getBoolean(alarmSoundKey, false)
        if (taskId == 20002) {
            isAlarmSoundEnabled = true // Force ring wake-up alarm!
        }
        if (taskId == 20001) {
            isAlarmSoundEnabled = false // Bedtime reminders do not require alarm sound!
        }

        val isBT = isBluetoothAudioConnected(applicationContext)

        if (isAlarmSoundEnabled) {
            Log.d("ReminderActivity", "Continuous Alarm Sound is enabled for priority: $taskPriority")
            try {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(applicationContext, alarmUri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(if (isBT) android.media.AudioAttributes.USAGE_MEDIA else android.media.AudioAttributes.USAGE_ALARM)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                    }
                    if (isBT) {
                        setVolume(0.2f, 0.2f) // Safe & gentle volume over Bluetooth
                    }
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("ReminderActivity", "MediaPlayer alarm launch failed, trying ringtone: ${e.message}")
                try {
                    val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)?.apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            isLooping = true
                        }
                        play()
                    }
                } catch (ex: Exception) {
                    Log.e("ReminderActivity", "Ringtone backup failed: ${ex.message}")
                }
            }

            // Continuous vibration if vibration is enabled
            try {
                val taskVibrationEnabled = prefsForSilent.getBoolean("task_vibration_enabled", true)
                if (taskVibrationEnabled) {
                    vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.let { v ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val pattern = longArrayOf(0, 800, 800)
                            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                        } else {
                            @Suppress("DEPRECATION")
                            val pattern = longArrayOf(0, 800, 800)
                            v.vibrate(pattern, 0)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ReminderActivity", "Vibrator waveform launch failed: ${e.message}")
            }
        } else {
            if (taskId == 20001) {
                Log.d("ReminderActivity", "Bedtime reminder: Alarm sound suppressed.")
            } else {
                // Audio playback configured using TYPE_NOTIFICATION (single shot alert)
                try {
                    val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)?.apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            val attributes = android.media.AudioAttributes.Builder()
                                .setUsage(if (isBT) android.media.AudioAttributes.USAGE_MEDIA else android.media.AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                            this.audioAttributes = attributes
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            isLooping = false
                        }
                        play()
                    }
                } catch (e: Exception) {
                    Log.e("ReminderActivity", "Ringtone launch omitted: ${e.message}")
                }
            }

            // Single short vibration for all priorities
            try {
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val taskVibrationEnabled = prefs.getBoolean("task_vibration_enabled", true)
                if (taskVibrationEnabled) {
                    vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.let { v ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            v.vibrate(600)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ReminderActivity", "Vibrator launch failed: ${e.message}")
            }
        }
    }

    private fun stopAlert() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("ReminderActivity", "Alert stop failed: ${e.message}")
        }
    }

    private fun cancelNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (taskId != -1) {
                notificationManager.cancel(taskId)
            }
            if (rawTaskId != -1) {
                notificationManager.cancel(rawTaskId)
            }
        } catch (e: Exception) {
            Log.e("ReminderActivity", "Notification cancellation failed: ${e.message}")
        }
    }

    private fun markTaskCompletedAndFinish() {
        if (taskId == -1) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val taskList = db.taskDao().getAllTasks().first()
                val targetTask = taskList.find { it.id == taskId }
                
                if (targetTask != null) {
                    db.taskDao().updateTask(targetTask.copy(isCompleted = true))
                    Log.d("ReminderActivity", "Task $taskId completed successfully in background.")
                    AlarmScheduler.cancelReminder(applicationContext, taskId)
                }
            } catch (e: Exception) {
                Log.e("ReminderActivity", "Error updates completion state in DB: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            stopAlert()
            try {
                com.example.util.FocusTimerManager.stopAlarm()
                com.example.util.BackgroundMediaManager.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                val streams = intArrayOf(
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.STREAM_ALARM,
                    android.media.AudioManager.STREAM_RING,
                    android.media.AudioManager.STREAM_NOTIFICATION,
                    android.media.AudioManager.STREAM_SYSTEM,
                    android.media.AudioManager.STREAM_VOICE_CALL
                )
                for (stream in streams) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            audioManager.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_MUTE, 0)
                        } else {
                            @Suppress("DEPRECATION")
                            audioManager.setStreamMute(stream, true)
                        }
                    } catch (e: Exception) {
                        try {
                            audioManager.setStreamVolume(stream, 0, 0)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun isBluetoothAudioConnected(context: Context): Boolean {
        return try {
            val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                for (device in devices) {
                    val type = device.type
                    if (type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        return true
                    }
                }
            }
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
                return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        stopAlert()
        super.onDestroy()
    }
}
