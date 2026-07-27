package com.example.timer

import com.example.data.LocalHistoryVault

interface TimerDao {
    suspend fun getTodayManualFocusTimeMs(date: String): Long
    suspend fun getTodayTotalFocusTimeMs(date: String): Long
    suspend fun archiveToVault(record: LocalHistoryVault)
    suspend fun enqueueOutboxMutation(mutation: OutboxMutation)
}

data class OutboxMutation(
    val mutationId: String,
    val createdAtMs: Long,
    val routingTarget: String,
    val actionType: String,
    val payloadJson: String
)
