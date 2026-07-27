package com.example.api

import android.content.Context
import android.util.Log
import com.example.util.TimeEngine
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FirestoreCleaner
 *
 * Utility to inspect, clean, and consolidate Firestore user history and temporary nodes.
 *
 * KEY RESPONSIBILITIES:
 * 1. Sanitizes user email key so user always reconnects to the exact same folder (no duplicate folders on login/logout).
 * 2. Prunes duplicate session documents, empty/corrupted drafts, and orphaned temporary records in Firestore.
 * 3. Consolidates legacy raw email document structures into the normalized `users/{sanitizedEmail}` path.
 */
object FirestoreCleaner {
    private const val TAG = "FirestoreCleaner"
    private const val MIN_CLEAN_INTERVAL_MS = 60000L // 1 minute debounce per user

    private val lastCleanTimes = ConcurrentHashMap<String, Long>()
    private val isCleaningMap = ConcurrentHashMap<String, AtomicBoolean>()

    fun cleanUserData(context: Context, email: String, force: Boolean = false) {
        val sanitized = DevicePresenceManager.sanitizeEmail(email)
        if (sanitized.isBlank()) return

        val now = TimeEngine.getTrueTimeMs()
        val lastRun = lastCleanTimes[sanitized] ?: 0L
        if (!force && (now - lastRun < MIN_CLEAN_INTERVAL_MS)) {
            Log.d(TAG, "Cleaner skipped for $sanitized (debounced within $MIN_CLEAN_INTERVAL_MS ms)")
            return
        }

        val flag = isCleaningMap.computeIfAbsent(sanitized) { AtomicBoolean(false) }
        if (!flag.compareAndSet(false, true)) {
            Log.d(TAG, "Cleaning already in progress for $sanitized")
            return
        }

        lastCleanTimes[sanitized] = now

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!com.example.util.NetworkChecker.isOnline(context)) {
                    flag.set(false)
                    return@launch
                }

                val firestore = FirebaseFirestore.getInstance(
                    FirebaseApp.getInstance(),
                    "main"
                )

                // 1. Check for legacy non-sanitized document paths (e.g. raw email with upper case or dots)
                val rawTrimmedEmail = email.trim()
                if (rawTrimmedEmail.isNotEmpty() && rawTrimmedEmail != sanitized) {
                    consolidateLegacyUserFolder(firestore, rawTrimmedEmail, sanitized)
                }

                // 2. Clean focus_records subcollection under sanitized email
                cleanSubcollectionDuplicates(firestore, sanitized, "focus_records")

                // 3. Clean focus_history subcollection under sanitized email
                cleanSubcollectionDuplicates(firestore, sanitized, "focus_history")

                // 4. Clean daily_records sessions subcollections
                cleanDailyRecordsDuplicates(firestore, sanitized)

                Log.d(TAG, "Firestore cleaning & consolidation completed successfully for $sanitized")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning user data for $sanitized", e)
            } finally {
                flag.set(false)
            }
        }
    }

    suspend fun purgeAllCloudUserData(context: Context, email: String) {
        val sanitized = DevicePresenceManager.sanitizeEmail(email)
        val rawTrimmed = email.trim()
        val targetEmails = mutableSetOf<String>()
        if (sanitized.isNotBlank()) targetEmails.add(sanitized)
        if (rawTrimmed.isNotBlank()) targetEmails.add(rawTrimmed)

        if (targetEmails.isEmpty()) {
            Log.w(TAG, "purgeAllCloudUserData called with blank email")
            return
        }

        Log.i(TAG, "Starting complete cloud data purge for $targetEmails")

        // 1. Firestore Purge
        try {
            val firestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "main")
            val subcollections = listOf(
                "focus_records", "focus_history", "daily_records", "user_settings",
                "outbox", "keep_notes", "journal_entries", "tasks", "habits",
                "contacts", "ledger", "vault", "presence", "notifications", "friends"
            )

            for (targetKey in targetEmails) {
                val userDocRef = firestore.collection("users").document(targetKey)
                for (subName in subcollections) {
                    try {
                        val subDocs = userDocRef.collection(subName).get().await()
                        for (doc in subDocs.documents) {
                            if (subName == "daily_records") {
                                try {
                                    val sessions = doc.reference.collection("sessions").get().await()
                                    for (sDoc in sessions.documents) {
                                        sDoc.reference.delete().await()
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed deleting sessions subcollection: ${e.message}")
                                }
                            }
                            doc.reference.delete().await()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Subcollection $subName delete warning for $targetKey: ${e.message}")
                    }
                }
                userDocRef.delete().await()
                Log.i(TAG, "Purged Firestore document users/$targetKey")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error purging Firestore user data: ${e.message}", e)
        }

        // 2. Realtime Database Purge
        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isNotEmpty()) {
                val rtdb = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                val rootRef = rtdb.getReference("FOCUS_TIMMER")

                for (targetKey in targetEmails) {
                    try {
                        rootRef.child("USER").child(targetKey).removeValue().await()
                        rootRef.child("LEADERBOARD").child(targetKey).removeValue().await()
                        rootRef.child("DEVICE_PRESENCE").child(targetKey).removeValue().await()
                        rootRef.child("ACTIVE_FOCUS").child(targetKey).removeValue().await()
                        rootRef.child("USER_SETTINGS").child(targetKey).removeValue().await()
                        rootRef.child("KEEP_NOTES").child(targetKey).removeValue().await()
                        rootRef.child("BELL_SIGNALS").child(targetKey).removeValue().await()
                        rootRef.child("SPHERE_PEERS").child(targetKey).removeValue().await()
                        Log.i(TAG, "Purged RTDB nodes for $targetKey")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error purging RTDB node for $targetKey: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error purging Realtime Database user data: ${e.message}", e)
        }
    }

    private suspend fun consolidateLegacyUserFolder(
        firestore: FirebaseFirestore,
        legacyKey: String,
        targetKey: String
    ) {
        try {
            val legacyDocRef = firestore.collection("users").document(legacyKey)
            val legacyDocSnap = legacyDocRef.get().await()

            if (legacyDocSnap.exists()) {
                Log.i(TAG, "Found legacy user folder '$legacyKey', consolidating to '$targetKey'")
                
                // Copy collections
                val collections = listOf("focus_records", "focus_history")
                for (col in collections) {
                    val query = legacyDocRef.collection(col).get().await()
                    for (doc in query.documents) {
                        val data = doc.data ?: continue
                        firestore.collection("users").document(targetKey)
                            .collection(col).document(doc.id)
                            .set(data, com.google.firebase.firestore.SetOptions.merge())
                            .await()
                        // Delete legacy doc
                        doc.reference.delete().await()
                    }
                }
                
                // Delete legacy user root document
                legacyDocRef.delete().await()
                Log.i(TAG, "Successfully migrated legacy folder '$legacyKey' -> '$targetKey'")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice during legacy folder consolidation: ${e.message}")
        }
    }

    private suspend fun cleanSubcollectionDuplicates(
        firestore: FirebaseFirestore,
        sanitizedEmail: String,
        collectionName: String
    ) {
        try {
            val colRef = firestore.collection("users").document(sanitizedEmail).collection(collectionName)
            val snapshot = colRef.get().await()

            val seenSessionMap = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
            val seenFingerprints = mutableSetOf<String>()

            for (doc in snapshot.documents) {
                val sId = doc.getString("Session_ID") ?: doc.getString("recordId") ?: doc.id
                val focusMs = doc.getLong("Total_Focus_Time_Ms") ?: doc.getLong("totalFocusMs") ?: 0L
                val startTs = doc.getLong("Start_Timestamp") ?: doc.getLong("startTimeMs") ?: 0L
                val dStr = doc.getString("Date_String") ?: doc.getString("dateString") ?: ""
                val taskTitle = doc.getString("Current_Task") ?: doc.getString("taskTitle") ?: doc.getString("task_title") ?: ""

                if (sId.isBlank() || sId == "null" || sId == "undefined" || sId.startsWith("synced_cloud_") || doc.id.startsWith("synced_cloud_") || taskTitle == "Cloud Synced Focus Session" || focusMs > 86_400_000L) {
                    Log.w(TAG, "Deleting synthetic, corrupted, or invalid $collectionName doc: ${doc.id}")
                    doc.reference.delete().await()
                    continue
                }

                val fingerprint = "${dStr}_${startTs}_${focusMs}_${taskTitle.trim().lowercase()}"

                val existing = seenSessionMap[sId]
                if (existing != null || (fingerprint.isNotBlank() && seenFingerprints.contains(fingerprint))) {
                    Log.i(TAG, "Deleting duplicate session doc ${doc.id} in $collectionName")
                    doc.reference.delete().await()
                } else {
                    seenSessionMap[sId] = doc
                    if (fingerprint.isNotBlank()) seenFingerprints.add(fingerprint)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning subcollection $collectionName for $sanitizedEmail: ${e.message}")
        }
    }

    private suspend fun cleanDailyRecordsDuplicates(
        firestore: FirebaseFirestore,
        sanitizedEmail: String
    ) {
        try {
            val dailyColRef = firestore.collection("users").document(sanitizedEmail).collection("daily_records")
            val dailySnap = dailyColRef.get().await()

            for (dayDoc in dailySnap.documents) {
                val daySessionsRef = dayDoc.reference.collection("sessions")
                val sessionsSnap = daySessionsRef.get().await()

                val seenIds = mutableSetOf<String>()
                for (sDoc in sessionsSnap.documents) {
                    val sId = sDoc.getString("Session_ID") ?: sDoc.getString("recordId") ?: sDoc.id
                    val focusMs = sDoc.getLong("Total_Focus_Time_Ms") ?: sDoc.getLong("totalFocusMs") ?: 0L

                    if (sId.isBlank() || sId.startsWith("synced_cloud_") || sDoc.id.startsWith("synced_cloud_") || focusMs > 86_400_000L || seenIds.contains(sId)) {
                        Log.i(TAG, "Deleting duplicate/corrupted daily_records session doc: ${sDoc.id}")
                        sDoc.reference.delete().await()
                    } else {
                        seenIds.add(sId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning daily_records duplicates for $sanitizedEmail: ${e.message}")
        }
    }
}
