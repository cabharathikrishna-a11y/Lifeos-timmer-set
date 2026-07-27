package com.example.api

import com.example.ui.FocusRecord

data class PeerLiveState(
    val userId: String,
    val displayName: String,
    val currentTag: String,
    val currentTask: String,
    val timerMode: String,
    val status: String,
    val timeline: List<TimelineEvent>,
    val lastUpdated: Long,
    val customEmoji: String? = null,
    val devices: Map<String, com.example.api.DeviceStats>? = null,
    val todayFocusMs: Long = 0L
)

data class PeerUiCardModel(
    val peerState: PeerLiveState,
    val formattedLiveTime: String,
    val rawElapsedMs: Long
)

val PeerLiveState.name: String get() = displayName
val PeerLiveState.nickname: String get() = displayName
val PeerLiveState.emoji: String get() = customEmoji?.ifEmpty { "👤" } ?: "👤"
val PeerLiveState.isOnline: Boolean get() = status != "idle" && status.isNotEmpty()
val PeerLiveState.isFocusing: Boolean get() = status.equals("Focusing", ignoreCase = true)
val PeerLiveState.focusStatus: String get() = status
val PeerLiveState.currentTaskTitle: String get() = currentTask
val PeerLiveState.lastUpdatedTimestamp: Long get() = lastUpdated
val PeerLiveState.todaysFocusRecords: List<FocusRecord>? get() = null
val PeerLiveState.accumulatedTimeMs: Long get() = TimelineSyncEngine.calculateAccumulatedFocusMs(timeline, status)
val PeerLiveState.lastResumeTimeMs: Long? get() = timeline.lastOrNull { 
    val ev = it.event.lowercase().trim()
    ev == "start" || ev == "resume" || ev == "resumed" || ev == "break_ended" || ev == "break end" || ev == "break_end"
}?.timestamp
val PeerLiveState.isGoogleUser: Boolean get() = true

