package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.LocalShieldsVault
import com.example.util.TimeEngine
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

object StreakShieldManager {
    private const val TAG = "StreakShieldManager"

    private var realtimeListener: ValueEventListener? = null
    private var currentListeningRef: DatabaseReference? = null

    fun getLocalShieldsFlow(context: Context): Flow<List<LocalShieldsVault>> {
        val db = AppDatabase.getInstance(context)
        return db.localShieldsVaultDao().getAllShields()
    }

    suspend fun getLocalUnconsumedShields(context: Context): List<LocalShieldsVault> {
        val db = AppDatabase.getInstance(context)
        return db.localShieldsVaultDao().getUnconsumedShieldsSync()
    }

    /**
     * Starts a continuous Realtime listener on Firebase RTDB for shields.
     * Guarantees 2-way instant synchronization across all devices signed into the account.
     */
    fun startRealtimeShieldSync(context: Context, myEmail: String) {
        if (myEmail.isBlank()) return
        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isEmpty()) return

        val mySanitized = DevicePresenceManager.sanitizeEmail(myEmail)
        val database = FirebaseDatabase.getInstance(dbUrl)
        val myShieldsRef = database.getReference("FOCUS_TIMMER")
            .child("USER")
            .child(mySanitized)
            .child("SHIELDS")

        if (currentListeningRef != null && currentListeningRef?.path?.toString() == myShieldsRef.path.toString()) {
            return // Already listening
        }

        stopRealtimeShieldSync()

        realtimeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        val dao = db.localShieldsVaultDao()
                        val trueTime = TimeEngine.getTrueTimeMs()

                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val startupGrantedKey = "startup_shields_granted_$mySanitized"
                        val hasGrantedStartup = prefs.getBoolean(startupGrantedKey, false)

                        val allCloudShields = mutableListOf<LocalShieldsVault>()

                        if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                            if (!hasGrantedStartup) {
                                val uuid1 = UUID.randomUUID().toString()
                                val uuid2 = UUID.randomUUID().toString()
                                val shield1 = mapOf(
                                    "Donor_Email" to "system@focussphere.com",
                                    "Donor_Name" to "Focus Sphere System (Startup Free Shield)",
                                    "Granted_Timestamp" to trueTime,
                                    "Is_Consumed" to false,
                                    "Consumed_Date" to null
                                )
                                val shield2 = mapOf(
                                    "Donor_Email" to "system@focussphere.com",
                                    "Donor_Name" to "Focus Sphere System (Startup Free Shield)",
                                    "Granted_Timestamp" to trueTime - 1000L,
                                    "Is_Consumed" to false,
                                    "Consumed_Date" to null
                                )
                                myShieldsRef.child(uuid1).setValue(shield1)
                                myShieldsRef.child(uuid2).setValue(shield2)
                                prefs.edit().putBoolean(startupGrantedKey, true).apply()

                                val local1 = LocalShieldsVault(uuid1, "system@focussphere.com", "Focus Sphere System (Startup Free Shield)", trueTime, false, null)
                                val local2 = LocalShieldsVault(uuid2, "system@focussphere.com", "Focus Sphere System (Startup Free Shield)", trueTime - 1000L, false, null)
                                allCloudShields.add(local1)
                                allCloudShields.add(local2)
                                Log.d(TAG, "Granted 2 free start-up shields to $myEmail")
                                FocusLogManager.logEvent(context, "Granted 2 startup free Streak Shields to protect your daily study streak!")
                            }
                        } else {
                            prefs.edit().putBoolean(startupGrantedKey, true).apply()

                            for (child in snapshot.children) {
                                val uuid = child.key ?: continue
                                if (uuid.startsWith("_")) continue
                                val donorEmail = child.child("Donor_Email").getValue(String::class.java)
                                    ?: child.child("donor_email").getValue(String::class.java) ?: ""
                                val donorName = child.child("Donor_Name").getValue(String::class.java)
                                    ?: child.child("donor_name").getValue(String::class.java) ?: ""
                                val grantedTs = child.child("Granted_Timestamp").getValue(Long::class.java)
                                    ?: child.child("granted_timestamp").getValue(Long::class.java) ?: 0L
                                val isConsumed = child.child("Is_Consumed").getValue(Boolean::class.java)
                                    ?: child.child("is_consumed").getValue(Boolean::class.java)
                                    ?: false
                                val consumedDate = child.child("Consumed_Date").getValue(String::class.java)
                                    ?: child.child("consumed_date").getValue(String::class.java)

                                // Auto-Decay (30-Day TTL for unconsumed shields)
                                if (!isConsumed && grantedTs > 0L && (trueTime - grantedTs) > 2592000000L) {
                                    Log.d(TAG, "Auto-decay: Shield $uuid is older than 30 days. Deleting from cloud.")
                                    myShieldsRef.child(uuid).removeValue()
                                    dao.deleteShield(uuid)
                                    continue
                                }

                                val localShield = LocalShieldsVault(
                                    uuid = uuid,
                                    donor_email = donorEmail,
                                    donor_name = donorName,
                                    granted_timestamp = grantedTs,
                                    is_consumed = isConsumed,
                                    consumed_date = consumedDate
                                )
                                allCloudShields.add(localShield)
                            }
                        }

                        if (allCloudShields.isNotEmpty()) {
                            dao.insertShields(allCloudShields)
                        }

                        Log.d(TAG, "Realtime sync: ${allCloudShields.size} shields updated locally.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in realtime shield sync listener", e)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Realtime shield sync cancelled", error.toException())
            }
        }

        currentListeningRef = myShieldsRef
        myShieldsRef.addValueEventListener(realtimeListener!!)
        Log.d(TAG, "Started realtime shield sync listener for $myEmail")
    }

    fun stopRealtimeShieldSync() {
        realtimeListener?.let { listener ->
            currentListeningRef?.removeEventListener(listener)
        }
        realtimeListener = null
        currentListeningRef = null
    }

    /**
     * Gifts a shield to a friend.
     * Rules:
     * 1. Sender pays 20 XP (tracked via /FOCUS_TIMMER/USER/{sender}/DEDUCTED_XP in RTDB).
     */
    fun giftShield(
        context: Context,
        senderEmail: String,
        senderName: String,
        friendEmail: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (senderEmail.isBlank() || friendEmail.isBlank()) {
            onFailure(IllegalArgumentException("Emails cannot be blank"))
            return
        }

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) {
                onFailure(IllegalStateException("Database URL is empty"))
                return
            }

            val database = FirebaseDatabase.getInstance(dbUrl)
            val friendSanitized = DevicePresenceManager.sanitizeEmail(friendEmail)
            val senderSanitized = DevicePresenceManager.sanitizeEmail(senderEmail)

            val friendShieldsRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(friendSanitized)
                .child("SHIELDS")

            friendShieldsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val uuid = UUID.randomUUID().toString()
                    val trueTime = TimeEngine.getTrueTimeMs()

                    val shieldPayload = mapOf(
                        "Donor_Email" to senderEmail,
                        "Donor_Name" to senderName,
                        "Granted_Timestamp" to trueTime,
                        "Is_Consumed" to false,
                        "Consumed_Date" to null
                    )

                    friendShieldsRef.child(uuid).setValue(shieldPayload).addOnCompleteListener { writeTask ->
                        if (writeTask.isSuccessful) {
                            val senderDeductedXpRef = database.getReference("FOCUS_TIMMER")
                                .child("USER")
                                .child(senderSanitized)
                                .child("DEDUCTED_XP")

                            senderDeductedXpRef.addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(xpSnapshot: DataSnapshot) {
                                    val currentDeducted = xpSnapshot.getValue(Int::class.java) ?: 0
                                    val newDeducted = currentDeducted + 20
                                    senderDeductedXpRef.setValue(newDeducted).addOnCompleteListener { xpTask ->
                                        if (xpTask.isSuccessful) {
                                            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                            prefs.edit().putInt("deducted_xp_${senderEmail}", newDeducted).apply()
                                            Log.d(TAG, "Shield successfully gifted to $friendEmail. 20 XP deducted.")
                                            FocusLogManager.logEvent(context, "Gifted 1 Streak Shield to $friendEmail. 20 XP deducted.")
                                            onSuccess()
                                        } else {
                                            Log.w(TAG, "Shield gifted but failed to deduct XP from sender")
                                            FocusLogManager.logEvent(context, "Gifted 1 Streak Shield to $friendEmail (failed to deduct XP from sender).")
                                            onSuccess()
                                        }
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    onSuccess()
                                }
                            })
                        } else {
                            onFailure(writeTask.exception ?: Exception("Failed to write shield node"))
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    onFailure(error.toException())
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error in giftShield", e)
            onFailure(e)
        }
    }

    /**
     * Fetches shields from cloud RTDB, performs auto-decay, and caches remaining locally.
     */
    fun fetchAndSyncShields(context: Context, myEmail: String, onComplete: (() -> Unit)? = null) {
        if (myEmail.isBlank()) return

        try {
            val dbUrl = FirebaseConfig.getDatabaseUrl(context)
            if (dbUrl.isEmpty()) return

            val database = FirebaseDatabase.getInstance(dbUrl)
            val mySanitized = DevicePresenceManager.sanitizeEmail(myEmail)

            val myShieldsRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(mySanitized)
                .child("SHIELDS")

            myShieldsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getInstance(context)
                            val dao = db.localShieldsVaultDao()
                            val trueTime = TimeEngine.getTrueTimeMs()

                            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            val startupGrantedKey = "startup_shields_granted_$mySanitized"
                            val hasGrantedStartup = prefs.getBoolean(startupGrantedKey, false)

                            val activeCloudShields = mutableListOf<LocalShieldsVault>()

                            if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                                if (!hasGrantedStartup) {
                                    val uuid1 = UUID.randomUUID().toString()
                                    val uuid2 = UUID.randomUUID().toString()
                                    val shield1 = mapOf(
                                        "Donor_Email" to "system@focussphere.com",
                                        "Donor_Name" to "Focus Sphere System (Startup Free Shield)",
                                        "Granted_Timestamp" to trueTime,
                                        "Is_Consumed" to false,
                                        "Consumed_Date" to null
                                    )
                                    val shield2 = mapOf(
                                        "Donor_Email" to "system@focussphere.com",
                                        "Donor_Name" to "Focus Sphere System (Startup Free Shield)",
                                        "Granted_Timestamp" to trueTime - 1000L,
                                        "Is_Consumed" to false,
                                        "Consumed_Date" to null
                                    )
                                    myShieldsRef.child(uuid1).setValue(shield1)
                                    myShieldsRef.child(uuid2).setValue(shield2)
                                    prefs.edit().putBoolean(startupGrantedKey, true).apply()

                                    val local1 = LocalShieldsVault(uuid1, "system@focussphere.com", "Focus Sphere System (Startup Free Shield)", trueTime, false, null)
                                    val local2 = LocalShieldsVault(uuid2, "system@focussphere.com", "Focus Sphere System (Startup Free Shield)", trueTime - 1000L, false, null)
                                    activeCloudShields.add(local1)
                                    activeCloudShields.add(local2)
                                    Log.d(TAG, "Granted 2 free start-up shields to $myEmail")
                                    FocusLogManager.logEvent(context, "Granted 2 startup free Streak Shields to protect your daily study streak!")
                                }
                            } else {
                                prefs.edit().putBoolean(startupGrantedKey, true).apply()

                                for (child in snapshot.children) {
                                    val uuid = child.key ?: continue
                                    if (uuid.startsWith("_")) continue
                                    val donorEmail = child.child("Donor_Email").getValue(String::class.java) 
                                        ?: child.child("donor_email").getValue(String::class.java) ?: ""
                                    val donorName = child.child("Donor_Name").getValue(String::class.java) 
                                        ?: child.child("donor_name").getValue(String::class.java) ?: ""
                                    val grantedTs = child.child("Granted_Timestamp").getValue(Long::class.java) 
                                        ?: child.child("granted_timestamp").getValue(Long::class.java) ?: 0L
                                    val isConsumed = child.child("Is_Consumed").getValue(Boolean::class.java) 
                                        ?: child.child("is_consumed").getValue(Boolean::class.java) 
                                        ?: false
                                    val consumedDate = child.child("Consumed_Date").getValue(String::class.java)
                                        ?: child.child("consumed_date").getValue(String::class.java)

                                    if (!isConsumed && grantedTs > 0L && (trueTime - grantedTs) > 2592000000L) {
                                        Log.d(TAG, "Auto-decay: Shield $uuid is older than 30 days. Deleting from cloud.")
                                        myShieldsRef.child(uuid).removeValue()
                                        dao.deleteShield(uuid)
                                        continue
                                    }

                                    val localShield = LocalShieldsVault(
                                        uuid = uuid,
                                        donor_email = donorEmail,
                                        donor_name = donorName,
                                        granted_timestamp = grantedTs,
                                        is_consumed = isConsumed,
                                        consumed_date = consumedDate
                                    )
                                    activeCloudShields.add(localShield)
                                }
                            }

                            if (activeCloudShields.isNotEmpty()) {
                                dao.insertShields(activeCloudShields)
                            }

                            Log.d(TAG, "Synced ${activeCloudShields.size} shields from RTDB cloud.")
                            withContext(Dispatchers.Main) {
                                onComplete?.invoke()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in fetchAndSyncShields internal block", e)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "fetchAndSyncShields listener cancelled", error.toException())
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in fetchAndSyncShields", e)
        }
    }

    fun grantFreeShieldAndResetXp(context: Context, email: String) {
        val sanitized = DevicePresenceManager.sanitizeEmail(email)
        val trueTime = TimeEngine.getTrueTimeMs()
        val uuid = UUID.randomUUID().toString()
        
        val dbUrl = FirebaseConfig.getDatabaseUrl(context)
        if (dbUrl.isNotEmpty()) {
            val database = FirebaseDatabase.getInstance(dbUrl)
            val ref = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitized)
                
            val shieldPayload = mapOf(
                "Donor_Email" to "system@focustimmer.com",
                "Donor_Name" to "System Reward",
                "Granted_Timestamp" to trueTime,
                "Is_Consumed" to false,
                "Consumed_Date" to null
            )
            ref.child("SHIELDS").child(uuid).setValue(shieldPayload)
            
            ref.child("ARENA").child("XpScore").setValue(0)
            ref.child("ARENA").child("ActiveStreak").setValue(0)
            ref.child("ARENA").child("Last_Updated").setValue(trueTime)
            
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("extra_credits_xp", 0)
                .putInt("deducted_xp_${email}", 0)
                .putInt("xp_offset_penalty_${email}", 0)
                .apply()
        }
        
        val db = AppDatabase.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val localShield = LocalShieldsVault(
                    uuid = uuid,
                    donor_email = "system@focustimmer.com",
                    donor_name = "System Reward",
                    granted_timestamp = trueTime,
                    is_consumed = false,
                    consumed_date = null
                )
                db.localShieldsVaultDao().insertShields(listOf(localShield))
                Log.d(TAG, "Granted 1 free shield to $email due to 7 days inactivity.")
                FocusLogManager.logEvent(context, "Welcome back! Granted 1 free Streak Shield and reset XP to 0 due to 7+ days inactivity.")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving free shield locally", e)
            }
        }
    }
}

