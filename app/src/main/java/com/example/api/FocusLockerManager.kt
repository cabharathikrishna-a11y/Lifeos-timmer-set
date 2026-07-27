package com.example.api

import android.content.Context
import android.util.Log
import com.example.util.TimeEngine
import com.example.util.FocusTimerManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ParticipantInfo(
    val email: String,
    val sanitizedEmail: String,
    val displayName: String,
    val joinTimestamp: Long
)

data class FocusLockerUiModel(
    val roomId: String = "",
    val roomName: String = "",
    val hostEmail: String = "",
    val participants: List<ParticipantInfo> = emptyList(),
    val isHost: Boolean = false
)

object FocusLockerManager {
    private const val TAG = "FocusLockerManager"

    private val _uiState = MutableStateFlow(FocusLockerUiModel())
    val uiState: StateFlow<FocusLockerUiModel> = _uiState.asStateFlow()

    private var roomListener: ValueEventListener? = null
    private var roomRef: com.google.firebase.database.DatabaseReference? = null
    

    fun sanitizeEmailForRoom(email: String): String {
        return email.lowercase().trim().replace(".", "_")
    }

    fun getFallbackDisplayName(email: String): String {
        val clean = email.substringBefore("@").replace(".", "_")
        val prefix = clean.substringBefore("_")
        return prefix.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString()
        }
    }

    fun createRoom(
        context: Context,
        myEmail: String,
        roomName: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (myEmail.isBlank()) {
            onFailure(IllegalArgumentException("Email cannot be blank"))
            return
        }

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) {
                onFailure(IllegalStateException("Database URL is empty"))
                return
            }

            val database = FirebaseDatabase.getInstance(dbUrl)
            val roomId = "ROOM_${System.currentTimeMillis()}"
            val sanitizedMyEmail = sanitizeEmailForRoom(myEmail)
            val legacySanitizedMyEmail = DevicePresenceManager.sanitizeEmail(myEmail)
            val trueTime = TimeEngine.getTrueTimeMs()

            val roomRef = database.getReference("FOCUS_TIMMER")
                .child("SHARED_ROOMS")
                .child(roomId)

            val participantsMap = mutableMapOf<String, Any>()
            participantsMap[sanitizedMyEmail] = trueTime
            if (sanitizedMyEmail != legacySanitizedMyEmail) {
                participantsMap[legacySanitizedMyEmail] = trueTime
            }

            val payload = mapOf(
                "Host_Email" to myEmail.lowercase().trim(),
                "Room_Name" to roomName,
                "Participants" to participantsMap
            )

            roomRef.setValue(payload).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Successfully created shared room: $roomId")
                    performActualJoin(context, myEmail, roomId)
                    onSuccess(roomId)
                } else {
                    onFailure(task.exception ?: Exception("Failed to write room state"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in createRoom", e)
            onFailure(e)
        }
    }

    fun joinRoom(context: Context, myEmail: String, roomId: String) {
        if (myEmail.isBlank() || roomId.isBlank()) return

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Firebase DB URL is empty!", android.widget.Toast.LENGTH_LONG).show()
                }
                return
            }

            val database = FirebaseDatabase.getInstance(dbUrl)
            val roomsRef = database.getReference("FOCUS_TIMMER").child("SHARED_ROOMS")

            // Normalize entered Room ID candidates (handles case variations and numeric-only entries)
            val trimmedInput = roomId.trim()
            val candidates = mutableListOf<String>()
            
            // Add exact entered input
            candidates.add(trimmedInput)
            
            // Add case-normalized and prefix-aware formats
            if (trimmedInput.startsWith("room_", ignoreCase = true)) {
                val numPart = trimmedInput.substring(5)
                candidates.add("ROOM_" + numPart)
            } else if (!trimmedInput.startsWith("ROOM_")) {
                candidates.add("ROOM_" + trimmedInput)
            }

            val distinctCandidates = candidates.distinct()
            Log.d(TAG, "Attempting to join room with input: '$roomId'. Candidates to try: $distinctCandidates")

            fun tryJoin(index: Int) {
                if (index >= distinctCandidates.size) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Room not found. Please check the Room ID.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    return
                }
                val candidateId = distinctCandidates[index]
                Log.d(TAG, "Querying Firebase for room ID candidate: '$candidateId'")
                roomsRef.child(candidateId).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        Log.d(TAG, "onDataChange for '$candidateId': exists=${snapshot.exists()}, hasHost=${snapshot.child("Host_Email").exists()}")
                        if (snapshot.exists()) {
                            val hostEmailExists = snapshot.child("Host_Email").exists()
                            if (hostEmailExists) {
                                Log.i(TAG, "Successfully found room '$candidateId' with host. Performing join...")
                                performActualJoin(context, myEmail, candidateId)
                            } else {
                                Log.w(TAG, "Room snapshot exists for '$candidateId', but 'Host_Email' is missing!")
                                tryJoin(index + 1)
                            }
                        } else {
                            tryJoin(index + 1)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Firebase query cancelled for '$candidateId': ${error.message} (code: ${error.code})")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(context, "Firebase error: ${error.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        tryJoin(index + 1)
                    }
                })
            }
            tryJoin(0)

        } catch (e: Exception) {
            Log.e(TAG, "Error joining room", e)
        }
    }

    private fun performActualJoin(context: Context, myEmail: String, roomId: String) {
        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) return

            val database = FirebaseDatabase.getInstance(dbUrl)
            val sanitizedMyEmail = sanitizeEmailForRoom(myEmail)
            val legacySanitizedMyEmail = DevicePresenceManager.sanitizeEmail(myEmail)
            val trueTime = TimeEngine.getTrueTimeMs()

            // Save joined roomId in SharedPreferences for low-data reconnect queries
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("last_joined_room_id_$sanitizedMyEmail", roomId).apply()
            prefs.edit().putString("last_joined_room_id_$legacySanitizedMyEmail", roomId).apply()

            // Update participant list under both sanitizations to be absolutely safe and compatible with other devices!
            val roomParticipantsRef = database.getReference("FOCUS_TIMMER")
                .child("SHARED_ROOMS")
                .child(roomId)
                .child("Participants")

            roomParticipantsRef.child(sanitizedMyEmail).setValue(trueTime)
            if (sanitizedMyEmail != legacySanitizedMyEmail) {
                roomParticipantsRef.child(legacySanitizedMyEmail).setValue(trueTime)
            }

            // Start listening to the room
            listenToRoom(context, myEmail, roomId)
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Successfully joined the room!", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in performActualJoin", e)
        }
    }

    fun leaveRoom(context: Context, myEmail: String, discardGroup: Boolean = false) {
        val currentRoomId = _uiState.value.roomId
        if (currentRoomId.isBlank() || myEmail.isBlank()) return

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) return

            val database = FirebaseDatabase.getInstance(dbUrl)
            val sanitizedMyEmail = sanitizeEmailForRoom(myEmail)
            val legacySanitizedMyEmail = DevicePresenceManager.sanitizeEmail(myEmail)

            // Clear joined roomId from SharedPreferences
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("last_joined_room_id_$sanitizedMyEmail").apply()
            prefs.edit().remove("last_joined_room_id_$legacySanitizedMyEmail").apply()

            if (discardGroup) {
                // Record discarded room ID so auto-reconnect never reconnects this user to it
                val existing = prefs.getStringSet("discarded_room_ids_$sanitizedMyEmail", emptySet()) ?: emptySet()
                val updated = existing.toMutableSet().apply { add(currentRoomId) }
                prefs.edit().putStringSet("discarded_room_ids_$sanitizedMyEmail", updated).apply()
            }

            // Remove participant node
            database.getReference("FOCUS_TIMMER")
                .child("SHARED_ROOMS")
                .child(currentRoomId)
                .child("Participants")
                .child(sanitizedMyEmail)
                .removeValue()

            database.getReference("FOCUS_TIMMER")
                .child("SHARED_ROOMS")
                .child(currentRoomId)
                .child("Participants")
                .child(legacySanitizedMyEmail)
                .removeValue()

            // If "Only Leave" (discardGroup == false) AND host, then disband.
            // If "Leave and Discard" (discardGroup == true), DO NOT delete the focus group node from Firebase so other members can keep studying!
            if (!discardGroup && _uiState.value.isHost) {
                database.getReference("FOCUS_TIMMER")
                    .child("SHARED_ROOMS")
                    .child(currentRoomId)
                    .removeValue()
            }

            // Cleanup local state
            stopListening()

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val msg = if (discardGroup) "Left and discarded focus group." else "Left focus group."
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error leaving room", e)
        }
    }

    private fun listenToRoom(context: Context, myEmail: String, roomId: String) {
        stopListening()

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) return

            val database = FirebaseDatabase.getInstance(dbUrl)
            val ref = database.getReference("FOCUS_TIMMER")
                .child("SHARED_ROOMS")
                .child(roomId)

            roomRef = ref

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        // Room deleted remotely (e.g. host deleted/left)
                        stopListening()
                        return
                    }

                    val hostEmail = snapshot.child("Host_Email").getValue(String::class.java) ?: ""
                    val roomName = snapshot.child("Room_Name").getValue(String::class.java) ?: ""

                    val participantsList = mutableListOf<ParticipantInfo>()
                    val participantsSnapshot = snapshot.child("Participants")
                    if (participantsSnapshot.exists()) {
                        for (child in participantsSnapshot.children) {
                            val sanitized = child.key ?: continue
                            val joinTs = child.getValue(Long::class.java) ?: 0L
                            
                            // Reconstruct plain email or approximate it
                            val rawEmail = if (sanitized.contains("_at_") || sanitized.contains("_dot_")) {
                                sanitized.replace("_dot_", ".").replace("_at_", "@")
                            } else if (sanitized.contains("@")) {
                                sanitized
                            } else if (sanitized.contains("_")) {
                                sanitized.replace(Regex("_([a-zA-Z0-9]+)_([a-zA-Z]{2,})$")) { match ->
                                    "@" + match.groupValues[1] + "." + match.groupValues[2]
                                }
                            } else {
                                sanitized
                            }
                            val displayName = getFallbackDisplayName(rawEmail)
                            participantsList.add(
                                ParticipantInfo(
                                    email = rawEmail,
                                    sanitizedEmail = sanitized,
                                    displayName = displayName,
                                    joinTimestamp = joinTs
                                )
                            )
                        }
                    }

                    val distinctParticipants = participantsList.distinctBy { it.email.lowercase().trim() }

                    val isHost = (myEmail.lowercase().trim() == hostEmail.lowercase().trim())

                    _uiState.value = FocusLockerUiModel(
                        roomId = roomId,
                        roomName = roomName,
                        hostEmail = hostEmail,
                        participants = distinctParticipants,
                        isHost = isHost
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Room listener cancelled", error.toException())
                }
            }

            ref.addValueEventListener(listener)
            roomListener = listener

        } catch (e: Exception) {
            Log.e(TAG, "Error starting room listener", e)
        }
    }

    fun stopListening() {
        roomListener?.let { listener ->
            roomRef?.removeEventListener(listener)
        }
        roomListener = null
        roomRef = null
        _uiState.value = FocusLockerUiModel()
    }

    fun checkForExistingRoomsAndReconnect(context: Context, myEmail: String) {
        if (myEmail.isBlank()) return
        Log.d(TAG, "Checking for existing rooms to reconnect for: $myEmail")
        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) return

            val database = FirebaseDatabase.getInstance(dbUrl)
            val sanitizedMyEmail = sanitizeEmailForRoom(myEmail)
            val legacySanitizedMyEmail = DevicePresenceManager.sanitizeEmail(myEmail)
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

            // Step 1: Attempt to check using saved roomId from SharedPreferences first (extremely low-data single-node lookup)
            var savedRoomId = prefs.getString("last_joined_room_id_$sanitizedMyEmail", "") ?: ""
            if (savedRoomId.isBlank()) {
                savedRoomId = prefs.getString("last_joined_room_id_$legacySanitizedMyEmail", "") ?: ""
            }
            if (savedRoomId.isNotBlank()) {
                val roomRef = database.getReference("FOCUS_TIMMER")
                    .child("SHARED_ROOMS")
                    .child(savedRoomId)
                roomRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val participantsSnapshot = snapshot.child("Participants")
                            if (participantsSnapshot.exists()) {
                                val cleanMyEmail = myEmail.lowercase().trim()
                                val isParticipant = participantsSnapshot.children.any { child ->
                                    val k = child.key ?: ""
                                    val childEmail = child.child("Email").getValue(String::class.java)
                                        ?: child.child("email").getValue(String::class.java) ?: ""
                                    k.equals(sanitizedMyEmail, ignoreCase = true) ||
                                    k.equals(legacySanitizedMyEmail, ignoreCase = true) ||
                                    k.replace("_", ".").replace(",", ".").replace("%2E", ".").lowercase().trim() == cleanMyEmail ||
                                    (childEmail.isNotBlank() && childEmail.lowercase().trim() == cleanMyEmail)
                                }
                                if (isParticipant) {
                                    Log.i(TAG, "Auto-reconnect (Saved Room): Found active participant entry in roomId: $savedRoomId. Reconnecting...")
                                    performActualJoin(context, myEmail, savedRoomId)
                                    return
                                }
                            }
                        }
                        // If room doesn't exist or we are not in it, fall back to the query
                        queryExistingRooms(context, myEmail, database, sanitizedMyEmail, legacySanitizedMyEmail, prefs)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Auto-reconnect (Saved Room): check cancelled", error.toException())
                        queryExistingRooms(context, myEmail, database, sanitizedMyEmail, legacySanitizedMyEmail, prefs)
                    }
                })
            } else {
                queryExistingRooms(context, myEmail, database, sanitizedMyEmail, legacySanitizedMyEmail, prefs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkForExistingRoomsAndReconnect", e)
        }
    }

    private fun queryExistingRooms(
        context: Context,
        myEmail: String,
        database: FirebaseDatabase,
        sanitizedMyEmail: String,
        legacySanitizedMyEmail: String,
        prefs: android.content.SharedPreferences
    ) {
        try {
            val roomsRef = database.getReference("FOCUS_TIMMER").child("SHARED_ROOMS")
            val discardedRooms = prefs.getStringSet("discarded_room_ids_$sanitizedMyEmail", emptySet()) ?: emptySet()
            roomsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val cleanMyEmail = myEmail.lowercase().trim()
                    var alreadyInRoomId: String? = null
                    var firstAvailableRoomId: String? = null

                    if (snapshot.exists()) {
                        for (roomSnapshot in snapshot.children) {
                            val roomId = roomSnapshot.key ?: continue
                            if (discardedRooms.contains(roomId)) {
                                Log.i(TAG, "Skipping discarded focus group $roomId during auto-join check.")
                                continue
                            }

                            if (firstAvailableRoomId == null) {
                                firstAvailableRoomId = roomId
                            }

                            val participantsSnapshot = roomSnapshot.child("Participants")
                            if (participantsSnapshot.exists()) {
                                val isParticipant = participantsSnapshot.children.any { child ->
                                    val k = child.key ?: ""
                                    val childEmail = child.child("Email").getValue(String::class.java)
                                        ?: child.child("email").getValue(String::class.java) ?: ""
                                    k.equals(sanitizedMyEmail, ignoreCase = true) ||
                                    k.equals(legacySanitizedMyEmail, ignoreCase = true) ||
                                    k.replace("_", ".").replace(",", ".").replace("%2E", ".").lowercase().trim() == cleanMyEmail ||
                                    (childEmail.isNotBlank() && childEmail.lowercase().trim() == cleanMyEmail)
                                }
                                if (isParticipant) {
                                    alreadyInRoomId = roomId
                                    break
                                }
                            }
                        }
                    }

                    if (alreadyInRoomId != null) {
                        if (_uiState.value.roomId != alreadyInRoomId) {
                            Log.i(TAG, "Auto-reconnect (Query): Found existing focus group participant entry in roomId: $alreadyInRoomId. Reconnecting...")
                            performActualJoin(context, myEmail, alreadyInRoomId)
                        }
                    } else if (firstAvailableRoomId != null) {
                        Log.i(TAG, "Auto-join: Automatically joining existing shared room: $firstAvailableRoomId")
                        performActualJoin(context, myEmail, firstAvailableRoomId)
                    } else {
                        Log.i(TAG, "Auto-join: No existing shared room found. Creating default shared room...")
                        createRoom(context, myEmail, "Global Focus Room", onSuccess = {}, onFailure = {})
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Auto-reconnect (Query): selective query check cancelled", error.toException())
                }
            })
        } catch (ex: Exception) {
            Log.e(TAG, "Error performing room query reconnection", ex)
        }
    }
}
