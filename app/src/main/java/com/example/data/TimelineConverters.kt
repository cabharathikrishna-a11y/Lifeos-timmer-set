package com.example.data

import androidx.room.TypeConverter
import com.example.api.TimelineEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TimelineConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromTimelineEventList(value: List<TimelineEvent>?): String {
        if (value == null) return "[]"
        return gson.toJson(value)
    }

    @TypeConverter
    fun toTimelineEventList(value: String?): List<TimelineEvent> {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<TimelineEvent>>() {}.type
        return try {
            gson.fromJson(value, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
