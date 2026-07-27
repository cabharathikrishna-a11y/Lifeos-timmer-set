package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.example.util.FocusTimerManager

class TimerStopwatchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.updateStopwatchWidget(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        WidgetUpdater.updateStopwatchWidget(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        Log.d("TimerStopwatchWidget", "Widget received broadcast action: $action")
        
        FocusTimerManager.init(context)
        when (action) {
            "com.example.widget.ACTION_STOPWATCH_START_PAUSE" -> {
                val isRunning = FocusTimerManager.isStopwatchActive.value && !FocusTimerManager.isPaused.value
                if (isRunning) {
                    FocusTimerManager.pauseStopwatch(context)
                } else {
                    FocusTimerManager.startStopwatch(context, isResuming = true)
                }
                WidgetUpdater.updateAllWidgets(context)
            }
            "com.example.widget.ACTION_STOPWATCH_BREAK" -> {
                FocusTimerManager.takeBreakFromStopwatch(context)
                WidgetUpdater.updateAllWidgets(context)
            }
            "com.example.widget.ACTION_STOPWATCH_RESET" -> {
                FocusTimerManager.resetStopwatch(context, saveSession = true)
                WidgetUpdater.updateAllWidgets(context)
            }
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                WidgetUpdater.updateStopwatchWidget(context, isPartialUpdate = true)
            }
        }
    }
}
