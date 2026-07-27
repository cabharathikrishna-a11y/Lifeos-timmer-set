package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.KeepNote
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * KeepNotesLiveSyncEngine
 *
 * Provides real-time, zero-duplicate cross-device sync for Keep Notes using
 * Firebase Realtime Database (`FOCUS_TIMMER/USER/{sanitizedEmail}/KEEP_NOTES`)
 * and FCM live signals.
 */
object KeepNotesLiveSyncEngine {
    private const val TAG = "KeepNotesLiveSync"
    private val isListening = AtomicBoolean(false)
    private var activeValueListener: ValueEventListener? = null
    private var activeUserEmail: String = ""

    fun startListening(context: Context, email: String, database: AppDatabase) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return

        if (isListening.get() && activeUserEmail == sanitizedEmail) return

        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        try {
            stopListening(context)

            activeUserEmail = sanitizedEmail
            val rtdb = FirebaseDatabase.getInstance(dbUrl)
            val notesRef = rtdb.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("KEEP_NOTES")

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    CoroutineScope(Dispatchers.IO).launch {
                        reconcileKeepNotesFromSnapshot(context, database, snapshot, sanitizedEmail)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "KeepNotes listener cancelled: ${error.message}")
                }
            }

            notesRef.addValueEventListener(listener)
            activeValueListener = listener
            isListening.set(true)
            Log.d(TAG, "Started real-time listener on KEEP_NOTES for $sanitizedEmail")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting KeepNotes live listener", e)
        }
    }

    fun stopListening(context: Context) {
        if (!isListening.get()) return
        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isNotEmpty() && activeUserEmail.isNotBlank()) {
                val rtdb = FirebaseDatabase.getInstance(dbUrl)
                val sanitizedEmail = DevicePresenceManager.sanitizeEmail(activeUserEmail)
                val notesRef = rtdb.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)
                    .child("KEEP_NOTES")
                activeValueListener?.let { notesRef.removeEventListener(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping KeepNotes listener", e)
        } finally {
            activeValueListener = null
            isListening.set(false)
            activeUserEmail = ""
        }
    }

    fun pushNoteToCloud(context: Context, email: String, note: KeepNote, isDeleted: Boolean = false) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return

        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rtdb = FirebaseDatabase.getInstance(dbUrl)
                val noteKey = generateNoteKey(note)
                val noteRef = rtdb.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)
                    .child("KEEP_NOTES")
                    .child(noteKey)

                if (isDeleted) {
                    noteRef.setValue(null)
                } else {
                    val deviceKey = DevicePresenceManager.getDeviceKey(context)
                    val payload = mapOf(
                        "noteKey" to noteKey,
                        "title" to note.title,
                        "content" to note.content,
                        "timestamp" to note.timestamp,
                        "isPinned" to note.isPinned,
                        "colorHex" to note.colorHex,
                        "websiteUrl" to (note.websiteUrl ?: ""),
                        "customLogoUrl" to (note.customLogoUrl ?: ""),
                        "originDeviceId" to deviceKey,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    noteRef.setValue(payload)
                }
                Log.d(TAG, "Pushed KeepNote to RTDB key=$noteKey isDeleted=$isDeleted")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push KeepNote to RTDB", e)
            }
        }
    }

    fun deleteNoteFromCloud(context: Context, email: String, note: KeepNote) {
        pushNoteToCloud(context, email, note, isDeleted = true)
    }

    fun pullKeepNotesFromCloud(context: Context, email: String, database: AppDatabase) {
        val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
        if (sanitizedEmail.isBlank()) return

        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rtdb = FirebaseDatabase.getInstance(dbUrl)
                val notesRef = rtdb.getReference("FOCUS_TIMMER")
                    .child("USER")
                    .child(sanitizedEmail)
                    .child("KEEP_NOTES")

                notesRef.get().addOnSuccessListener { snapshot ->
                    CoroutineScope(Dispatchers.IO).launch {
                        reconcileKeepNotesFromSnapshot(context, database, snapshot, sanitizedEmail)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pulling KeepNotes from cloud", e)
            }
        }
    }

    private suspend fun reconcileKeepNotesFromSnapshot(
        context: Context,
        database: AppDatabase,
        snapshot: DataSnapshot,
        sanitizedEmail: String
    ) {
        try {
            val keepNoteDao = database.keepNoteDao()
            val localNotes = keepNoteDao.getAllKeepNotesDirect()

            val remoteNotesMap = mutableMapOf<String, RemoteNoteItem>()
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val key = child.key ?: continue
                    val title = child.child("title").getValue(String::class.java) ?: ""
                    val content = child.child("content").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                    val isPinned = child.child("isPinned").getValue(Boolean::class.java) ?: false
                    val colorHex = child.child("colorHex").getValue(String::class.java) ?: "#202124"
                    val websiteUrl = child.child("websiteUrl").getValue(String::class.java)
                    val customLogoUrl = child.child("customLogoUrl").getValue(String::class.java)
                    val isDeleted = child.child("isDeleted").getValue(Boolean::class.java) ?: false

                    val signature = createSignature(title, content)
                    remoteNotesMap[signature] = RemoteNoteItem(
                        key = key,
                        title = title,
                        content = content,
                        timestamp = timestamp,
                        isPinned = isPinned,
                        colorHex = colorHex,
                        websiteUrl = websiteUrl?.takeIf { it.isNotBlank() },
                        customLogoUrl = customLogoUrl?.takeIf { it.isNotBlank() },
                        isDeleted = isDeleted
                    )
                }
            }

            // Deduplicate local notes map
            val localNotesMapBySignature = mutableMapOf<String, KeepNote>()
            val duplicateLocalNotesToDelete = mutableListOf<KeepNote>()

            for (local in localNotes) {
                val sig = createSignature(local.title, local.content)
                if (localNotesMapBySignature.containsKey(sig)) {
                    duplicateLocalNotesToDelete.add(local)
                } else {
                    localNotesMapBySignature[sig] = local
                }
            }

            // Delete local duplicates first
            for (dup in duplicateLocalNotesToDelete) {
                keepNoteDao.deleteKeepNote(dup)
                Log.d(TAG, "Deduplicated and removed duplicate local note id=${dup.id} title=${dup.title}")
            }

            // Reconcile remote vs local
            for ((sig, remote) in remoteNotesMap) {
                if (remote.isDeleted) {
                    localNotesMapBySignature[sig]?.let { localNote ->
                        keepNoteDao.deleteKeepNote(localNote)
                        Log.d(TAG, "Deleted local note matching remote deletion: ${localNote.title}")
                    }
                    continue
                }

                val existingLocal = localNotesMapBySignature[sig]
                if (existingLocal == null) {
                    val newNote = KeepNote(
                        title = remote.title,
                        content = remote.content,
                        timestamp = remote.timestamp,
                        isPinned = remote.isPinned,
                        colorHex = remote.colorHex,
                        isSynced = true,
                        websiteUrl = remote.websiteUrl,
                        customLogoUrl = remote.customLogoUrl
                    )
                    keepNoteDao.insertKeepNote(newNote)
                    Log.d(TAG, "Inserted new note from cloud live sync: ${remote.title}")
                } else {
                    if (existingLocal.isPinned != remote.isPinned ||
                        existingLocal.colorHex != remote.colorHex ||
                        existingLocal.websiteUrl != remote.websiteUrl ||
                        existingLocal.customLogoUrl != remote.customLogoUrl ||
                        !existingLocal.isSynced
                    ) {
                        val updated = existingLocal.copy(
                            isPinned = remote.isPinned,
                            colorHex = remote.colorHex,
                            isSynced = true,
                            websiteUrl = remote.websiteUrl,
                            customLogoUrl = remote.customLogoUrl
                        )
                        keepNoteDao.updateKeepNote(updated)
                        Log.d(TAG, "Updated local note from cloud live sync id=${existingLocal.id}")
                    }
                }
            }

            // Push any un-synced local notes to cloud if missing from remote
            for ((sig, local) in localNotesMapBySignature) {
                if (!remoteNotesMap.containsKey(sig) && !local.isSynced) {
                    pushNoteToCloud(context, sanitizedEmail, local)
                    keepNoteDao.updateKeepNote(local.copy(isSynced = true))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in reconcileKeepNotesFromSnapshot", e)
        }
    }

    private data class RemoteNoteItem(
        val key: String,
        val title: String,
        val content: String,
        val timestamp: Long,
        val isPinned: Boolean,
        val colorHex: String,
        val websiteUrl: String?,
        val customLogoUrl: String?,
        val isDeleted: Boolean
    )

    fun createSignature(title: String, content: String): String {
        return "${title.trim()}|${content.trim()}"
    }

    fun generateNoteKey(note: KeepNote): String {
        val sig = createSignature(note.title, note.content)
        val hash = abs(sig.hashCode())
        return "note_${note.timestamp}_${hash}"
    }
}
