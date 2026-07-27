package com.example.api

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class TimelineEvent(
    val deviceId: String = "",
    val event: String = "",
    val timestamp: Long = 0L
)
