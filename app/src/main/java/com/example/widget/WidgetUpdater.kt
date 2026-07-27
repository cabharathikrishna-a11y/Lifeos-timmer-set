package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.util.FocusTimerManager

object WidgetUpdater {

    fun getPendingIntentFlags(isMutable: Boolean = false): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isMutable) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    /**
     * Programmatically requests the Android Launcher to pin a widget to the Home Screen (Android 8.0+ / API 26+)
     */
    fun requestPinWidget(context: Context, providerClass: Class<*>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val myProvider = ComponentName(context, providerClass)
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    9000,
                    Intent(context, providerClass).apply { action = "com.example.widget.ACTION_WIDGET_PINNED" },
                    getPendingIntentFlags()
                )
                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            }
        }
    }

    /**
     * Updates the Friends Focus Widget ("Who is Focusing")
     * Supports responsive layouts (Android 12+) with Small and Standard views.
     */
    fun updateFriendsFocusWidget(context: Context, statusText: String? = null) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, FriendsFocusWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        val defaultText = run {
            FocusTimerManager.init(context)
            val isMeFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value)
                    && FocusTimerManager.isFocusPhase.value
                    && !FocusTimerManager.isPaused.value
            if (isMeFocusing) {
                val task = FocusTimerManager.attachedTask.value?.title ?: "Focus Session"
                "🎯 Focusing: $task"
            } else {
                val activePeersFocusing = com.example.api.PeerLiveSphereManager.peerLiveStates.value.values.filter {
                    it.status.equals("Focusing", ignoreCase = true)
                }
                if (activePeersFocusing.isNotEmpty()) {
                    val peer = activePeersFocusing.first()
                    "🎯 ${peer.displayName}: ${peer.currentTask}"
                } else {
                    "No active peers focusing"
                }
            }
        }

        val textToShow = if (statusText != null) {
            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_friends_focus_text", statusText)
                .apply()
            statusText
        } else {
            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_friends_focus_text", defaultText)
                .apply()
            defaultText
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val pendingIntent = PendingIntent.getActivity(context, 2001, intent, getPendingIntentFlags())

        for (widgetId in allWidgetIds) {
            val largeView = RemoteViews(context.packageName, R.layout.widget_friends_focus).apply {
                setTextViewText(R.id.focus_status_text, textToShow)
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }

            val finalViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val smallView = RemoteViews(context.packageName, R.layout.widget_friends_focus_small).apply {
                    setTextViewText(R.id.focus_status_text, textToShow)
                    setOnClickPendingIntent(android.R.id.background, pendingIntent)
                }
                val viewMap = mapOf(
                    SizeF(140f, 50f) to smallView,
                    SizeF(200f, 80f) to largeView
                )
                RemoteViews(viewMap)
            } else {
                largeView
            }

            appWidgetManager.updateAppWidget(widgetId, finalViews)
        }
    }

    private fun formatTime(seconds: Int): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
        }
    }

    /**
     * Updates the Stopwatch Widget using Chronometer and responsive layouts (Android 12+ API 31+)
     */
    fun updateStopwatchWidget(context: Context, isPartialUpdate: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, TimerStopwatchWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        FocusTimerManager.init(context)
        val seconds = FocusTimerManager.stopwatchSeconds.value
        val isRunning = FocusTimerManager.isStopwatchActive.value && !FocusTimerManager.isPaused.value
        val isPaused = FocusTimerManager.isPaused.value && (FocusTimerManager.wasStartedFromStopwatch.value || seconds > 0)

        val startPauseIntent = Intent(context, TimerStopwatchWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_STOPWATCH_START_PAUSE"
        }
        val startPausePending = PendingIntent.getBroadcast(context, 3001, startPauseIntent, getPendingIntentFlags())

        val breakIntent = Intent(context, TimerStopwatchWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_STOPWATCH_BREAK"
        }
        val breakPending = PendingIntent.getBroadcast(context, 3004, breakIntent, getPendingIntentFlags())

        val resetIntent = Intent(context, TimerStopwatchWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_STOPWATCH_RESET"
        }
        val resetPending = PendingIntent.getBroadcast(context, 3002, resetIntent, getPendingIntentFlags())

        val rootIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val rootPending = PendingIntent.getActivity(context, 3003, rootIntent, getPendingIntentFlags())

        val btnStartPauseText = if (isRunning) "⏸ PAUSE" else if (isPaused || seconds > 0) "▶ RESUME" else "▶ START"
        val btnResetText = if (isRunning || isPaused || seconds > 0) "◼ END" else "◼ RESET"

        for (widgetId in allWidgetIds) {
            val largeView = RemoteViews(context.packageName, R.layout.widget_stopwatch).apply {
                if (isRunning) {
                    val baseTime = android.os.SystemClock.elapsedRealtime() - seconds * 1000L
                    setChronometer(R.id.stopwatch_time_display, baseTime, null, true)
                } else {
                    val staticText = formatTime(seconds)
                    val safeFormat = staticText.replace("%", "%%")
                    setChronometer(R.id.stopwatch_time_display, android.os.SystemClock.elapsedRealtime(), safeFormat, false)
                }

                setTextViewText(R.id.btn_stopwatch_start_pause, btnStartPauseText)
                setOnClickPendingIntent(R.id.btn_stopwatch_start_pause, startPausePending)

                setTextViewText(R.id.btn_stopwatch_reset, btnResetText)
                setOnClickPendingIntent(R.id.btn_stopwatch_reset, resetPending)

                if (isRunning) {
                    setViewVisibility(R.id.btn_stopwatch_break, android.view.View.VISIBLE)
                    setOnClickPendingIntent(R.id.btn_stopwatch_break, breakPending)
                } else {
                    setViewVisibility(R.id.btn_stopwatch_break, android.view.View.GONE)
                }

                setOnClickPendingIntent(R.id.stopwatch_title, rootPending)
                setOnClickPendingIntent(R.id.stopwatch_time_display, rootPending)
            }

            if (isPartialUpdate) {
                appWidgetManager.partiallyUpdateAppWidget(widgetId, largeView)
                continue
            }

            val finalViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val smallView = RemoteViews(context.packageName, R.layout.widget_stopwatch_small).apply {
                    if (isRunning) {
                        val baseTime = android.os.SystemClock.elapsedRealtime() - seconds * 1000L
                        setChronometer(R.id.stopwatch_time_display, baseTime, null, true)
                    } else {
                        val staticText = formatTime(seconds)
                        val safeFormat = staticText.replace("%", "%%")
                        setChronometer(R.id.stopwatch_time_display, android.os.SystemClock.elapsedRealtime(), safeFormat, false)
                    }

                    setTextViewText(R.id.btn_stopwatch_start_pause, btnStartPauseText)
                    setOnClickPendingIntent(R.id.btn_stopwatch_start_pause, startPausePending)
                    setOnClickPendingIntent(R.id.stopwatch_time_display, rootPending)
                }
                val viewMap = mapOf(
                    SizeF(140f, 70f) to smallView,
                    SizeF(200f, 100f) to largeView
                )
                RemoteViews(viewMap)
            } else {
                largeView
            }

            appWidgetManager.updateAppWidget(widgetId, finalViews)
        }
    }

    /**
     * Updates the Pomodoro Widget using countdown Chronometer and responsive layouts (Android 12+ API 31+)
     */
    fun updatePomodoroWidget(context: Context, isPartialUpdate: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, PomodoroWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        FocusTimerManager.init(context)
        val totalSecs = FocusTimerManager.timerSecondsLeft.value
        val isRunning = FocusTimerManager.isTimerRunning.value && !FocusTimerManager.isPaused.value
        val isPaused = FocusTimerManager.isPaused.value && !FocusTimerManager.wasStartedFromStopwatch.value
        val isFocus = FocusTimerManager.isFocusPhase.value

        val headerText = if (isFocus) "POMODORO FOCUS 🎯" else "REST BREAK ☕"
        val headerColor = if (isFocus) 0xFF30D158.toInt() else 0xFFFF9500.toInt()
        val btnStartPauseText = if (isRunning) "⏸ PAUSE" else if (isPaused) "▶ RESUME" else "▶ START"
        val btnBreakText = if (isFocus) "☕ BREAK" else "⏭ FOCUS"
        val btnResetText = if (isRunning || isPaused) "◼ END" else "◼ RESET"

        val startPauseIntent = Intent(context, PomodoroWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_POMO_START_PAUSE"
        }
        val startPausePending = PendingIntent.getBroadcast(context, 4001, startPauseIntent, getPendingIntentFlags())

        val breakIntent = Intent(context, PomodoroWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_POMO_BREAK"
        }
        val breakPending = PendingIntent.getBroadcast(context, 4004, breakIntent, getPendingIntentFlags())

        val resetIntent = Intent(context, PomodoroWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_POMO_RESET"
        }
        val resetPending = PendingIntent.getBroadcast(context, 4002, resetIntent, getPendingIntentFlags())

        val rootIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val rootPending = PendingIntent.getActivity(context, 4003, rootIntent, getPendingIntentFlags())

        for (widgetId in allWidgetIds) {
            val largeView = RemoteViews(context.packageName, R.layout.widget_pomodoro).apply {
                setTextViewText(R.id.pomo_title, headerText)
                setTextColor(R.id.pomo_title, headerColor)

                if (isRunning) {
                    val baseTime = android.os.SystemClock.elapsedRealtime() + totalSecs * 1000L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setChronometerCountDown(R.id.pomo_time_display, true)
                    }
                    setChronometer(R.id.pomo_time_display, baseTime, null, true)
                } else {
                    val staticText = formatTime(totalSecs)
                    val safeFormat = staticText.replace("%", "%%")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setChronometerCountDown(R.id.pomo_time_display, true)
                    }
                    setChronometer(R.id.pomo_time_display, android.os.SystemClock.elapsedRealtime(), safeFormat, false)
                }

                setTextViewText(R.id.btn_pomo_start_pause, btnStartPauseText)
                setOnClickPendingIntent(R.id.btn_pomo_start_pause, startPausePending)

                setTextViewText(R.id.btn_pomo_reset, btnResetText)
                setOnClickPendingIntent(R.id.btn_pomo_reset, resetPending)

                if (isRunning || !isFocus) {
                    setViewVisibility(R.id.btn_pomo_break, android.view.View.VISIBLE)
                    setTextViewText(R.id.btn_pomo_break, btnBreakText)
                    setOnClickPendingIntent(R.id.btn_pomo_break, breakPending)
                } else {
                    setViewVisibility(R.id.btn_pomo_break, android.view.View.GONE)
                }

                setOnClickPendingIntent(R.id.pomo_title, rootPending)
                setOnClickPendingIntent(R.id.pomo_time_display, rootPending)
            }

            if (isPartialUpdate) {
                appWidgetManager.partiallyUpdateAppWidget(widgetId, largeView)
                continue
            }

            val finalViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val smallView = RemoteViews(context.packageName, R.layout.widget_pomodoro_small).apply {
                    setTextViewText(R.id.pomo_title, headerText)
                    setTextColor(R.id.pomo_title, headerColor)

                    if (isRunning) {
                        val baseTime = android.os.SystemClock.elapsedRealtime() + totalSecs * 1000L
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            setChronometerCountDown(R.id.pomo_time_display, true)
                        }
                        setChronometer(R.id.pomo_time_display, baseTime, null, true)
                    } else {
                        val staticText = formatTime(totalSecs)
                        val safeFormat = staticText.replace("%", "%%")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            setChronometerCountDown(R.id.pomo_time_display, true)
                        }
                        setChronometer(R.id.pomo_time_display, android.os.SystemClock.elapsedRealtime(), safeFormat, false)
                    }

                    setTextViewText(R.id.btn_pomo_start_pause, btnStartPauseText)
                    setOnClickPendingIntent(R.id.btn_pomo_start_pause, startPausePending)
                    setOnClickPendingIntent(R.id.pomo_time_display, rootPending)
                }
                val viewMap = mapOf(
                    SizeF(140f, 70f) to smallView,
                    SizeF(200f, 100f) to largeView
                )
                RemoteViews(viewMap)
            } else {
                largeView
            }

            appWidgetManager.updateAppWidget(widgetId, finalViews)
        }
    }

    /**
     * Forces full updates across all widgets
     */
    fun updateAllWidgets(context: Context) {
        try {
            updateFriendsFocusWidget(context)
            updateStopwatchWidget(context)
            updatePomodoroWidget(context)
        } catch (e: Exception) {
            Log.e("WidgetUpdater", "Error updating widgets: ${e.message}")
        }
    }
}
