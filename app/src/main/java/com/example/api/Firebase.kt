package com.example.api

import android.content.Context
import com.example.ui.FocusRecord
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// =========================================================================
// SECTION 1: DATA MODELS & SCHEMAS (STRICTLY CLEANED)
// =========================================================================

@JsonClass(generateAdapter = true)
data class DeviceStats(
    // Used to track if this specific Build.MODEL is currently logged into the app.
    val isLoggedIn: Boolean? = true,

    // "PENDING" or "COMPLETED" for Firestore file generation.
    val uploadStatus: String? = "COMPLETED",

    // The device model name (e.g., "SM-G998B").
    val deviceName: String? = null,
    
    val allTimeFocusMs: Long = 0L,
    val lastUpdateDate: String? = null,
    val lastActiveTime: String? = null,
    val past30DaysFocusMs: Long = 0L,
    val past50DaysFocusMs: Long = 0L,
    val past7DaysFocusMs: Long = 0L,
    val todayFocusMs: Long = 0L,
    val lastUpdatedMs: Long = 0L
)

@JsonClass(generateAdapter = true)
data class BellSignal(
    val isProcessed: Boolean = false,
    val senderDisplayName: String = "",
    val senderUsername: String = "",
    val timestamp: Long = 0L
)

@JsonClass(generateAdapter = true)
data class UpdateConfigRemote(
    val apkFileId: String? = null,
    val githubOwner: String? = null,
    val githubRepo: String? = null,
    val versionId: Int? = null
)



// =========================================================================
// SECTION 2: FIREBASE RETROFIT SERVICE (FROM FIREBASE_API)
// =========================================================================

interface FirebaseApi {
    @GET("bells/{username}.json")
    suspend fun getBellSignal(
        @Path("username") username: String
    ): Response<BellSignal?>

    @PUT("bells/{username}.json")
    suspend fun putBellSignal(@Path("username") username: String, @Body signal: BellSignal?): Response<Unit>

    @GET("requests/{username}.json")
    suspend fun getPeerRequests(
        @Path("username") username: String
    ): Response<Map<String, Boolean>?>

    @PUT("requests/{username}/{requester}.json")
    suspend fun putPeerRequest(
        @Path("username") username: String,
        @Path("requester") requester: String,
        @Body request: Boolean
    ): Boolean

    @DELETE("requests/{username}/{requester}.json")
    suspend fun deletePeerRequest(
        @Path("username") username: String,
        @Path("requester") requester: String
    ): Response<Unit>

    @GET("transfer/{requester}/{provider}.json")
    suspend fun getTransferredData(
        @Path("requester") requester: String,
        @Path("provider") provider: String
    ): Response<List<FocusRecord>?>

    @PUT("transfer/{requester}/{provider}.json")
    suspend fun putTransferredData(
        @Path("requester") requester: String,
        @Path("provider") provider: String,
        @Body records: List<FocusRecord>?
    ): List<FocusRecord>?

    @DELETE("transfer/{requester}/{provider}.json")
    suspend fun deleteTransferredData(
        @Path("requester") requester: String,
        @Path("provider") provider: String
    ): Response<Unit>

    @DELETE("users/{username}/focusTimer.json")
    suspend fun deleteFocusTimer(
        @Path("username") username: String
    ): Response<Unit>
}

object Firebase {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private var cachedApi: FirebaseApi? = null
    private var cachedUrl: String? = null

    @Volatile
    private var appContextRef: java.lang.ref.WeakReference<Context>? = null

    var appContext: Context?
        get() = appContextRef?.get()
        set(value) {
            appContextRef = value?.let { java.lang.ref.WeakReference(it.applicationContext) }
            value?.let { setupServerTimeOffsetListener(it) }
        }

    @Volatile
    private var isFirebaseInitialized = false
    private val isInitializing = java.util.concurrent.atomic.AtomicBoolean(false)
    private val isCleaningUpBranches = java.util.concurrent.atomic.AtomicBoolean(false)

    fun ensureFirebaseInitialized(context: Context) {
        if (isFirebaseInitialized) return
        if (!isInitializing.compareAndSet(false, true)) return
        try {
            try {
                com.google.firebase.FirebaseApp.getInstance()
                isFirebaseInitialized = true
            } catch (e: IllegalStateException) {
                try {
                    val url = com.example.api.FirebaseConfig.getDatabaseUrl(context)
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:432934819080:android:919f6a7b8f1a2a56bcc8bd")
                        .setApiKey("AIzaSyAzHCPmTOmkFV3i7-TJ7E4GMBiA9NIIn3I")
                        .setDatabaseUrl(url)
                        .setProjectId("lifeosca")
                        .build()
                    com.google.firebase.FirebaseApp.initializeApp(context.applicationContext, options)
                    android.util.Log.i("FirebaseClient", "Successfully initialized custom FirebaseApp programmatically")
                    isFirebaseInitialized = true
                } catch (initEx: Exception) {
                    android.util.Log.e("FirebaseClient", "Failed to initialize FirebaseApp programmatically", initEx)
                }
            }
            if (isFirebaseInitialized) {
                enablePersistenceSafely(context)
                cleanupOldDatabaseBranches(context)
                signInAnonymouslyIfPossible(context)
            }
        } finally {
            isInitializing.set(false)
        }
    }

    private fun signInAnonymouslyIfPossible(context: Context) {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener {
                        android.util.Log.i("FirebaseClient", "Successfully signed in anonymously for RTDB authenticated access")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("FirebaseClient", "Anonymous sign-in failed", e)
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseClient", "FirebaseAuth sign in anonymously error", e)
        }
    }

    suspend fun ensureAuthenticated(context: Context) = withContext(Dispatchers.IO) {
        ensureFirebaseInitialized(context)
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                android.util.Log.i("FirebaseClient", "Awaiting anonymous sign-in for authenticated RTDB access...")
                auth.signInAnonymously().await()
                android.util.Log.i("FirebaseClient", "Anonymous sign-in complete")
            } else {
                android.util.Log.d("FirebaseClient", "Already authenticated with UID: ${auth.currentUser?.uid}")
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseClient", "Anonymous sign-in await failed", e)
        }
    }

    private var isPersistenceEnabledSet = false

    private fun enablePersistenceSafely(context: Context) {
        if (isPersistenceEnabledSet) return
        isPersistenceEnabledSet = true
        try {
            val url = com.example.api.FirebaseConfig.getDatabaseUrl(context)
            if (url.isNotEmpty()) {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(url)
                database.setPersistenceEnabled(true)
                android.util.Log.i("FirebaseClient", "Firebase RTDB Persistence enabled successfully")
            }
        } catch (pe: Exception) {
            android.util.Log.d("FirebaseClient", "Firebase RTDB Persistence note: ${pe.message}")
        }
    }

    fun cleanupOldDatabaseBranches(context: Context) {
        if (!isCleaningUpBranches.compareAndSet(false, true)) return
        try {
            val url = com.example.api.FirebaseConfig.getDatabaseUrl(context)
            if (url.isNotEmpty()) {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(url)
                database.getReference("users").setValue(null)
                database.getReference("update_config").setValue(null)
                database.getReference("FOCUS_TIMMER/USER/cabharathikrishna").removeValue()
                database.getReference("FOCUS_TIMMER/USER/cabharathikrishan").removeValue()
                android.util.Log.d("FirebaseClient", "Cleaned up legacy lowercase branches and removed legacy short username nodes")
            }
            FirebaseRepairKit.repairGlobalRoots(context)
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val userEmail = prefs.getString("user_email", "") ?: ""
            if (userEmail.isNotBlank()) {
                FirebaseRepairKit.repairUserData(context, userEmail)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseClient", "Failed to clean up legacy database branches", e)
        } finally {
            isCleaningUpBranches.set(false)
        }
    }

    fun resetFirebase(context: Context) {
        try {
            val app = com.google.firebase.FirebaseApp.getInstance()
            app.delete()
            android.util.Log.i("FirebaseClient", "Deleted custom FirebaseApp instance to prepare for re-initialization")
        } catch (e: Exception) {
            // Not initialized or already deleted
        }
        isFirebaseInitialized = false
        synchronized(this) {
            isOffsetListenerAttached = false
            cachedApi = null
            cachedUrl = null
        }
        ensureFirebaseInitialized(context)
        setupServerTimeOffsetListener(context)
    }

    @Volatile
    private var isOffsetListenerAttached = false

    private fun setupServerTimeOffsetListener(context: Context) {
        if (isOffsetListenerAttached) return
        ensureFirebaseInitialized(context)
        isOffsetListenerAttached = true
        try {
            val url = com.example.api.FirebaseConfig.getDatabaseUrl(context.applicationContext)
            if (url.isNotEmpty()) {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(url)
                val offsetRef = database.getReference(".info/serverTimeOffset")
                offsetRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val offset = snapshot.getValue(Long::class.java) ?: 0L
                        com.example.util.TimeEngine.serverTimeOffsetMs = offset
                        
                        val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putLong("firebase_server_time_offset", offset).apply()
                        android.util.Log.d("FirebaseClient", "Firebase serverTimeOffset updated: $offset")
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        android.util.Log.e("FirebaseClient", "Firebase offset listener cancelled", error.toException())
                    }
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseClient", "Failed to attach serverTimeOffset listener", e)
        }
    }

    fun triggerSettingsUpdated(context: Context, username: String) {
        if (username.isBlank()) return
        try {
            val url = com.example.api.FirebaseConfig.getDatabaseUrl(context)
            val database = com.google.firebase.database.FirebaseDatabase.getInstance(url)
            database.getReference("FOCUS_TIMMER/USER")
                .child(username)
                .child("settingsLastUpdatedTs")
                .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                .addOnSuccessListener {
                    android.util.Log.d("FirebaseClient", "Atomic ping: settingsLastUpdatedTs updated in RTDB.")
                }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseClient", "Failed to update settingsLastUpdatedTs in RTDB", e)
        }
    }

    val api: FirebaseApi
        get() {
            val ctx = appContext
            var baseUrl = if (ctx != null) FirebaseConfig.getDatabaseUrl(ctx) else activeUrl
            baseUrl = baseUrl.trim()
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/"
            }
            synchronized(this) {
                if (cachedApi != null && cachedUrl == baseUrl) {
                    return cachedApi!!
                }
                try {
                    val retrofit = Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .addConverterFactory(MoshiConverterFactory.create(moshi))
                        .build()
                    val realApi = retrofit.create(FirebaseApi::class.java)
                    val interceptingApi = InterceptingFirebaseApi(realApi) { appContext }
                    cachedApi = interceptingApi
                    cachedUrl = baseUrl
                    return interceptingApi
                } catch (e: Exception) {
                    android.util.Log.e("FirebaseClient", "Failed to build Retrofit API for $baseUrl, falling back to Stub", e)
                    return StubFirebaseApi()
                }
            }
        }

    @Volatile
    var activeUrl: String = if (FirebaseConfig.DATABASE_URL.endsWith("/")) FirebaseConfig.DATABASE_URL else "${FirebaseConfig.DATABASE_URL}/"
        set(value) {
            val trimmed = value.trim()
            val sanitized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            field = sanitized
        }

class StubFirebaseApi : FirebaseApi {
    override suspend fun getBellSignal(username: String): Response<BellSignal?> = Response.success(null)
    override suspend fun putBellSignal(username: String, signal: BellSignal?): Response<Unit> = Response.success(Unit)
    override suspend fun getPeerRequests(username: String): Response<Map<String, Boolean>?> = Response.success(null)
    override suspend fun putPeerRequest(username: String, requester: String, request: Boolean): Boolean = true
    override suspend fun deletePeerRequest(username: String, requester: String): Response<Unit> = Response.success(Unit)
    override suspend fun getTransferredData(requester: String, provider: String): Response<List<FocusRecord>?> = Response.success(null)
    override suspend fun putTransferredData(requester: String, provider: String, records: List<FocusRecord>?): List<FocusRecord>? = records
    override suspend fun deleteTransferredData(requester: String, provider: String): Response<Unit> = Response.success(Unit)
    override suspend fun deleteFocusTimer(username: String): Response<Unit> = Response.success(Unit)
}

class InterceptingFirebaseApi(
    private val delegate: FirebaseApi,
    private val contextProvider: () -> Context?
) : FirebaseApi {

    private fun isTester(): Boolean {
        val ctx = contextProvider() ?: return false
        val prefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_tester_mode", false)
    }

    private fun getAppVersionString(): String {
        val ctx = contextProvider() ?: return "Unknown"
        return try {
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    override suspend fun getBellSignal(username: String): Response<BellSignal?> {
        if (isTester() || username == "tester_mode_user") {
            return Response.success(null)
        }
        return delegate.getBellSignal(username)
    }

    override suspend fun putBellSignal(username: String, signal: BellSignal?): Response<Unit> {
        if (isTester() || username == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing putBellSignal for username: $username in Tester Mode")
            return Response.success(Unit)
        }
        return delegate.putBellSignal(username, signal)
    }

    override suspend fun getPeerRequests(username: String): Response<Map<String, Boolean>?> {
        if (isTester() || username == "tester_mode_user") {
            return Response.success(null)
        }
        return delegate.getPeerRequests(username)
    }

    override suspend fun putPeerRequest(username: String, requester: String, request: Boolean): Boolean {
        if (isTester() || username == "tester_mode_user" || requester == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing putPeerRequest in Tester Mode")
            return true
        }
        return delegate.putPeerRequest(username, requester, request)
    }

    override suspend fun deletePeerRequest(username: String, requester: String): Response<Unit> {
        if (isTester() || username == "tester_mode_user" || requester == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing deletePeerRequest in Tester Mode")
            return Response.success(Unit)
        }
        return delegate.deletePeerRequest(username, requester)
    }

    override suspend fun getTransferredData(requester: String, provider: String): Response<List<FocusRecord>?> {
        return delegate.getTransferredData(requester, provider)
    }

    override suspend fun putTransferredData(requester: String, provider: String, records: List<FocusRecord>?): List<FocusRecord>? {
        if (isTester() || requester == "tester_mode_user" || provider == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing putTransferredData in Tester Mode")
            return records
        }
        return delegate.putTransferredData(requester, provider, records)
    }

    override suspend fun deleteTransferredData(requester: String, provider: String): Response<Unit> {
        if (isTester() || requester == "tester_mode_user" || provider == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing deleteTransferredData in Tester Mode")
            return Response.success(Unit)
        }
        return delegate.deleteTransferredData(requester, provider)
    }

    override suspend fun deleteFocusTimer(username: String): Response<Unit> {
        if (isTester() || username == "tester_mode_user") {
            android.util.Log.d("InterceptingFirebase", "Bypassing deleteFocusTimer in Tester Mode")
            return Response.success(Unit)
        }
        return delegate.deleteFocusTimer(username)
    }
}

// =========================================================================
// SECTION 3: SYNC MANAGER LOGIC (FROM FIREBASE_SYNC_MANAGER)
// =========================================================================

// Extension to bridge Task await
suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            cont.resumeWithException(task.exception ?: Exception("Task failed"))
        }
    }
}

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var lastObservedStatus: String? = null
    
    @Volatile
    var didThisDeviceInitiateEnd: Boolean = false

    private var presenceDatabaseRef: com.google.firebase.database.DatabaseReference? = null
    private var presenceDatabaseListener: com.google.firebase.database.ValueEventListener? = null
    
    // keepPresenceAlive, stopPresenceTracking, and takeCommand removed


    private var mySettingsListenerRegistration: com.google.firebase.database.ValueEventListener? = null
    private var mySettingsDatabaseRef: com.google.firebase.database.DatabaseReference? = null

    fun listenToSettingsUpdates(context: Context, username: String) {
        // Disabled: settings sync is bypassed
    }

    fun stopListeningToSettingsUpdates() {
        // Disabled: settings sync is bypassed
    }

    private var commandDeviceRef: com.google.firebase.database.DatabaseReference? = null
    private var commandDeviceListener: com.google.firebase.database.ValueEventListener? = null

    fun listenToMyFocusTimer(context: Context, username: String) {
        // Purged: Live-sync polling listeners removed
    }

    fun startListeningToStatus(context: Context, username: String) {
        // Purged
    }

    fun stopListeningToStatus() {
        // Purged
    }

    fun stopListeningToMyFocusTimer() {
        // Purged
    }

    private var myCommandsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var myCommandsDatabaseRef: com.google.firebase.database.DatabaseReference? = null
    private var myCommandsListener: com.google.firebase.database.ValueEventListener? = null

    fun listenToFirestoreCommands(context: Context, username: String) {
        // Disabled: active_command tracking and synchronization is bypassed
    }

    fun stopListeningToFirestoreCommands() {
        // Disabled: active_command tracking and synchronization is bypassed
    }

    fun listenToFriendsFocusTimers(context: Context, friendUsernames: List<String>) {
        // Stubs: Peer tracking and synchronization is completely removed
    }

    fun stopListeningToFriendsFocusTimers() {
        // Stubs: Peer tracking and synchronization is completely removed
    }

    // triggerProfileUpdated removed


    fun getTodaySavedFocusMs(context: Context): Long {
        return try {
            val db = com.example.data.AppDatabase.getInstance(context)
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val records = kotlinx.coroutines.runBlocking {
                db.focusRecordDao().getRecordsForDate(todayStr)
            }
            records.sumOf { it.durationSeconds * 1000L }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseSyncManager", "Failed to getTodaySavedFocusMs", e)
            0L
        }
    }

    fun listenToFriends(context: Context, friendUsernames: List<String>) {
        // Optional placeholder or stub for friend listening
    }

    fun stopListening(context: Context) {
        stopListeningToMyFocusTimer()
        stopListeningToFriendsFocusTimers()
        stopListeningToFirestoreCommands()
    }

    fun publishTimerConfigAndSignal(context: Context, username: String) {
        // Bypassed in offline-first mode
    }

    fun verifyCloudState(context: Context, username: String) {
        // Purged: No history pull is needed in zero-data RTDB architecture.
    }

    // updateDeviceStatsInRtdb removed
}

object FirebaseRepository {
    // Left empty: no longer tracking user profiles to avoid RTDB JSON bloat.
}
