package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class UserPresence(
    val userId: String,
    val isOnline: Boolean = true,
    val isTyping: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
