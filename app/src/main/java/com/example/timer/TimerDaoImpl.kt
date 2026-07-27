package com.example.timer

import com.example.data.AppDatabase
import com.example.data.LocalHistoryVault
import com.example.data.OutboxQueue

class TimerDaoImpl(
    private val db: AppDatabase,
    private val context: android.content.Context
) : TimerDao {
    override suspend fun getTodayManualFocusTimeMs(date: String): Long {
        return db.localHistoryVaultDao().getTodayManualFocusTimeMs(date)
    }

    override suspend fun getTodayTotalFocusTimeMs(date: String): Long {
        return db.localHistoryVaultDao().getTodayTotalFocusTimeMs(date)
    }

    override suspend fun archiveToVault(record: LocalHistoryVault) {
        db.localHistoryVaultDao().insertRecord(record)
    }

    override suspend fun enqueueOutboxMutation(mutation: OutboxMutation) {
        val deviceId = com.example.util.DeviceIdProvider.getDeviceId(context)
        val modifiedPayload = try {
            val json = org.json.JSONObject(mutation.payloadJson)
            json.put("deviceId", deviceId)
            json.toString()
        } catch (e: Exception) {
            mutation.payloadJson
        }

        val outboxItem = OutboxQueue(
            mutation_id = mutation.mutationId,
            created_at_ms = mutation.createdAtMs,
            routing_target = mutation.routingTarget,
            action_type = mutation.actionType,
            payload_json = modifiedPayload
        )
        db.outboxQueueDao().insertQueueItem(outboxItem)
    }
}
