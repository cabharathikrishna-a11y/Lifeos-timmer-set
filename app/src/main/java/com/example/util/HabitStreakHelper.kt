package com.example.util

import com.example.data.Habit
import com.example.data.HabitCompletion
import java.text.SimpleDateFormat
import java.util.*

object HabitStreakHelper {

    fun calculateStreak(habit: Habit, completions: List<HabitCompletion>): Int {
        val dates = completions.filter { it.habitId == habit.id }.map { it.dateString }.distinct()
        return when (habit.frequency.uppercase()) {
            "WEEKLY" -> calculateWeeklyStreak(dates, habit.weeklyDay)
            "MONTHLY", "MONTHLY_ONCE" -> {
                if (dates.isEmpty()) return 0
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH) // 0-indexed
                
                val completedThisMonth = dates.any { d ->
                    try {
                        val dateCal = Calendar.getInstance()
                        dateCal.time = sdf.parse(d)!!
                        dateCal.get(Calendar.YEAR) == currentYear && dateCal.get(Calendar.MONTH) == currentMonth
                    } catch (e: Exception) { false }
                }
                
                val lastMonthCal = Calendar.getInstance()
                lastMonthCal.add(Calendar.MONTH, -1)
                val lastYear = lastMonthCal.get(Calendar.YEAR)
                val lastMonth = lastMonthCal.get(Calendar.MONTH)
                
                val completedLastMonth = dates.any { d ->
                    try {
                        val dateCal = Calendar.getInstance()
                        dateCal.time = sdf.parse(d)!!
                        dateCal.get(Calendar.YEAR) == lastYear && dateCal.get(Calendar.MONTH) == lastMonth
                    } catch (e: Exception) { false }
                }
                
                if (!completedThisMonth && !completedLastMonth) return 0
                
                var streak = 0
                val checkCal = Calendar.getInstance()
                if (completedThisMonth) {
                    // start from current month
                } else {
                    checkCal.add(Calendar.MONTH, -1)
                }
                
                while (true) {
                    val y = checkCal.get(Calendar.YEAR)
                    val m = checkCal.get(Calendar.MONTH)
                    val hasComp = dates.any { d ->
                        try {
                            val dateCal = Calendar.getInstance()
                            dateCal.time = sdf.parse(d)!!
                            dateCal.get(Calendar.YEAR) == y && dateCal.get(Calendar.MONTH) == m
                        } catch (e: Exception) { false }
                    }
                    if (hasComp) {
                        streak++
                        checkCal.add(Calendar.MONTH, -1)
                    } else {
                        break
                    }
                }
                streak
            }
            else -> calculateDailyStreak(dates) // DAILY
        }
    }

    private fun calculateDailyStreak(dates: List<String>): Int {
        if (dates.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        
        val todayStr = sdf.format(today.time)
        
        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DATE, -1)
        val yesterdayStr = sdf.format(yesterday.time)
        
        if (!dates.contains(todayStr) && !dates.contains(yesterdayStr)) {
            return 0
        }
        
        var streak = 0
        val checkCal = Calendar.getInstance()
        if (dates.contains(todayStr)) {
            checkCal.time = today.time
        } else {
            checkCal.time = yesterday.time
        }
        
        while (true) {
            val checkStr = sdf.format(checkCal.time)
            if (dates.contains(checkStr)) {
                streak++
                checkCal.add(Calendar.DATE, -1)
            } else {
                break
            }
        }
        return streak
    }

    private fun calculateWeeklyStreak(dates: List<String>, weeklyDay: Int): Int {
        if (dates.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        val thisWeekScheduled = Calendar.getInstance()
        thisWeekScheduled.set(Calendar.HOUR_OF_DAY, 0)
        thisWeekScheduled.set(Calendar.MINUTE, 0)
        thisWeekScheduled.set(Calendar.SECOND, 0)
        thisWeekScheduled.set(Calendar.MILLISECOND, 0)
        
        val safeDay = if (weeklyDay in 1..7) weeklyDay else Calendar.SUNDAY
        var safetyCounter = 0
        while (thisWeekScheduled.get(Calendar.DAY_OF_WEEK) != safeDay && safetyCounter < 7) {
            thisWeekScheduled.add(Calendar.DATE, -1)
            safetyCounter++
        }
        
        val thisWeekStr = sdf.format(thisWeekScheduled.time)
        
        val lastWeekScheduled = Calendar.getInstance()
        lastWeekScheduled.time = thisWeekScheduled.time
        lastWeekScheduled.add(Calendar.DATE, -7)
        val lastWeekStr = sdf.format(lastWeekScheduled.time)
        
        val hasCompletedThisWeek = dates.contains(thisWeekStr)
        val hasCompletedLastWeek = dates.contains(lastWeekStr)
        
        if (!hasCompletedThisWeek && !hasCompletedLastWeek) {
            return 0
        }
        
        val isTodayScheduled = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == weeklyDay
        val todayStr = sdf.format(Date())
        
        val startCal = Calendar.getInstance()
        if (hasCompletedThisWeek) {
            startCal.time = thisWeekScheduled.time
        } else if (thisWeekStr == todayStr) {
            startCal.time = lastWeekScheduled.time
        } else {
            return 0
        }
        
        var streak = 0
        while (true) {
            val checkStr = sdf.format(startCal.time)
            if (dates.contains(checkStr)) {
                streak++
                startCal.add(Calendar.DATE, -7)
            } else {
                break
            }
        }
        return streak
    }
}
