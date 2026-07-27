package com.example.api

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.util.TimeEngine
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DailyAggregationWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "DailyAggregationWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting DailyAggregationWorker execution...")

        // Retrieve user email
        val email = inputData.getString("email") ?: DynamicCommandManager.activeEmail.let {
            if (it.isNotEmpty()) it else {
                val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val username = prefs.getString("current_username", "") ?: ""
                if (username.contains("@")) username else prefs.getString("user_email_${username}", "") ?: ""
            }
        }

        if (email.isBlank()) {
            Log.e(TAG, "User email is blank, cannot proceed with daily aggregation.")
            return Result.failure()
        }

        try {
            // Determine "Yesterday" date string
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

            // Hold & Wait Rule: Check ACTIVE_FOCUS_TIMER in RTDB
            val dbUrl = FirebaseConfig.getDatabaseUrl(appContext)
            if (dbUrl.isEmpty()) {
                Log.e(TAG, "Database URL is empty, skipping.")
                return Result.retry()
            }

            val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
            val sanitizedEmail = DevicePresenceManager.sanitizeEmail(email)
            val activeRef = database.getReference("FOCUS_TIMMER")
                .child("USER")
                .child(sanitizedEmail)
                .child("ACTIVE_FOCUS_TIMER")

            val activeSnapshot = readActiveFocusTimer(activeRef)
            if (activeSnapshot.exists()) {
                val status = activeSnapshot.child("Status").getValue(String::class.java) ?: "IDLE"
                
                val timelineSnapshot = activeSnapshot.child("Timeline")
                val timelineList = mutableListOf<TimelineEvent>()
                if (timelineSnapshot.exists()) {
                    for (child in timelineSnapshot.children) {
                        val deviceId = child.child("deviceId").getValue(String::class.java) ?: ""
                        val event = child.child("event").getValue(String::class.java) ?: ""
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        timelineList.add(TimelineEvent(deviceId, event, timestamp))
                    }
                }

                val firstEventTs = timelineList.firstOrNull()?.timestamp ?: 0L
                if (firstEventTs > 0L) {
                    val firstEventDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(firstEventTs))
                    if (!status.equals("Relaxing", ignoreCase = true) && firstEventDateStr == yesterdayDateStr) {
                        Log.d(TAG, "Active session is still in progress from yesterday ($yesterdayDateStr) with status $status. Retrying later...")
                        return Result.retry()
                    }
                }
            }

            // Atomic Compilation: Query Firestore for focus_records where the date matches Yesterday
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(
                com.google.firebase.FirebaseApp.getInstance(),
                "main"
            )

            // We calculate start and end times for yesterday to query Start_Timestamp range
            val calStart = Calendar.getInstance()
            calStart.add(Calendar.DAY_OF_YEAR, -1)
            calStart.set(Calendar.HOUR_OF_DAY, 0)
            calStart.set(Calendar.MINUTE, 0)
            calStart.set(Calendar.SECOND, 0)
            calStart.set(Calendar.MILLISECOND, 0)
            val yesterdayStartMs = calStart.timeInMillis

            val calEnd = Calendar.getInstance()
            calEnd.set(Calendar.HOUR_OF_DAY, 0)
            calEnd.set(Calendar.MINUTE, 0)
            calEnd.set(Calendar.SECOND, 0)
            calEnd.set(Calendar.MILLISECOND, 0)
            val yesterdayEndMs = calEnd.timeInMillis

            val querySnapshot = firestore.collection("users").document(email)
                .collection("focus_records")
                .whereGreaterThanOrEqualTo("Start_Timestamp", yesterdayStartMs)
                .whereLessThan("Start_Timestamp", yesterdayEndMs)
                .get()
                .awaitTask()

            val docsList = querySnapshot.documents
            if (docsList.isEmpty()) {
                Log.d(TAG, "No focus records found for yesterday: $yesterdayDateStr. Skipping aggregation.")
                return Result.success()
            }

            var sumFocusMs = 0L
            var sumBreakMs = 0L

            for (doc in docsList) {
                sumFocusMs += doc.getLong("Total_Focus_Time_Ms") ?: 0L
                sumBreakMs += doc.getLong("Total_Break_Time_Ms") ?: 0L
            }

            val masterDoc = hashMapOf<String, Any>(
                "Date_String" to yesterdayDateStr,
                "Total_Focus_Time_Ms" to sumFocusMs,
                "Total_Break_Time_Ms" to sumBreakMs,
                "Total_Focus_Time_Formatted" to TimelineSyncEngine.formatTimeMsToHhMmSs(sumFocusMs),
                "Total_Break_Time_Formatted" to TimelineSyncEngine.formatTimeMsToHhMmSs(sumBreakMs),
                "Session_Count" to docsList.size.toLong(),
                "Compiled_At" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            // Commit atomic batch: write master doc, delete individuals
            val batch = firestore.batch()
            val masterDocRef = firestore.collection("users").document(email)
                .collection("compiled_daily_records").document(yesterdayDateStr)
            
            batch.set(masterDocRef, masterDoc)

            for (doc in docsList) {
                batch.delete(doc.reference)
            }

            batch.commit().awaitTask()
            Log.d(TAG, "Successfully committed daily aggregation batch for $yesterdayDateStr. Compiled ${docsList.size} sessions.")

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error compiling daily records inside DailyAggregationWorker", e)
            return Result.retry()
        }
    }

    private suspend fun readActiveFocusTimer(ref: com.google.firebase.database.DatabaseReference): com.google.firebase.database.DataSnapshot = suspendCancellableCoroutine { cont ->
        ref.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                cont.resume(snapshot)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                cont.resumeWithException(error.toException())
            }
        })
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(task.exception ?: Exception("Task failed"))
            }
        }
    }

    companion object {
        fun schedule(context: Context, email: String) {
            try {
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()

                val data = androidx.work.Data.Builder()
                    .putString("email", email)
                    .build()

                val workRequest = androidx.work.PeriodicWorkRequest.Builder(
                    DailyAggregationWorker::class.java,
                    24, java.util.concurrent.TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .setInputData(data)
                    .build()

                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "DailyAggregationWork",
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
                Log.d("DailyAggregationWorker", "Successfully scheduled periodic DailyAggregationWorker for user $email")
            } catch (e: Exception) {
                Log.e("DailyAggregationWorker", "Failed to schedule DailyAggregationWorker", e)
            }
        }
    }
}
