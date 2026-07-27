package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HealthRecord
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Retrieve database health states
    val selectedDate by viewModel.selectedHealthDate.collectAsStateWithLifecycle()
    val rawRecord by viewModel.healthRecordForSelectedDate.collectAsStateWithLifecycle()
    val googleFitSyncStatus by viewModel.googleFitSyncStatus.collectAsStateWithLifecycle()
    val allRecords by viewModel.healthRecordsList.collectAsStateWithLifecycle()

    val defaultStepGoal by viewModel.defaultStepGoal.collectAsStateWithLifecycle()
    val defaultSleepGoalMinutes by viewModel.defaultSleepGoalMinutes.collectAsStateWithLifecycle()
    val defaultWaterGoalMl by viewModel.defaultWaterGoalMl.collectAsStateWithLifecycle()

    // Ensure we have a non-null record for the selected date
    val record = rawRecord ?: HealthRecord(
        dateString = selectedDate,
        stepGoal = defaultStepGoal,
        sleepGoalMinutes = defaultSleepGoalMinutes,
        waterGoalMl = defaultWaterGoalMl
    )

    var showStepsDetails by remember { mutableStateOf(false) }
    var showSleepDetails by remember { mutableStateOf(false) }
    var showFoodDetails by remember { mutableStateOf(false) }
    var showWaterDetails by remember { mutableStateOf(false) }

    val showSleepDetailsDirectly by viewModel.showSleepDetailsDirectly.collectAsStateWithLifecycle()
    LaunchedEffect(showSleepDetailsDirectly) {
        if (showSleepDetailsDirectly) {
            showSleepDetails = true
            viewModel.showSleepDetailsDirectly.value = false
        }
    }

    androidx.activity.compose.BackHandler(enabled = showStepsDetails || showSleepDetails || showFoodDetails || showWaterDetails) {
        showStepsDetails = false
        showSleepDetails = false
        showFoodDetails = false
        showWaterDetails = false
    }

    // Dialog state controllers
    var showManualLogDialog by remember { mutableStateOf(false) }
    var metricToLog by remember { mutableStateOf("") } // "Steps", "Sleep", "Calories", "HeartRate"

    // Real-time hardware step detector sensor integration
    DisposableEffect(key1 = selectedDate) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val stepDetector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var listener: SensorEventListener? = null
        if (sensorManager != null) {
            listener = object : SensorEventListener {
                private var lastStepTime = 0L
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    val currentTime = System.currentTimeMillis()
                    if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                        // Increment step count directly on physical step detection
                        viewModel.updateHealthMetric(steps = record.steps + 1)
                    } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        // Accelero step detection fallback using a standard peak threshold algorithm
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())
                        if (magnitude > 14.5 && currentTime - lastStepTime > 350) {
                            lastStepTime = currentTime
                            viewModel.updateHealthMetric(steps = record.steps + 1)
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            // Register both step counter and raw motion backup
            if (stepDetector != null) {
                sensorManager.registerListener(listener, stepDetector, SensorManager.SENSOR_DELAY_UI)
            } else if (accelerometer != null) {
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }
        }

        onDispose {
            listener?.let { sensorManager?.unregisterListener(it) }
        }
    }

    // Main Health Dashboard scaffold
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080F))
            .testTag("health_view_container")
    ) {
        if (showStepsDetails) {
            StepsDetailsPage(
                viewModel = viewModel,
                record = record,
                onBack = { showStepsDetails = false }
            )
        } else if (showSleepDetails) {
            SleepDetailsPage(
                viewModel = viewModel,
                record = record,
                onBack = { showSleepDetails = false }
            )
        } else if (showFoodDetails) {
            FoodDetailsPage(
                viewModel = viewModel,
                record = record,
                onBack = { showFoodDetails = false }
            )
        } else if (showWaterDetails) {
            WaterDetailsPage(
                viewModel = viewModel,
                record = record,
                onBack = { showWaterDetails = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
            // Screen Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Fitness & Wellness",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                // Selected date display and editor selector
                IconButton(
                    onClick = {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val calendar = Calendar.getInstance()
                        try {
                            val parsedDate = sdf.parse(selectedDate)
                            if (parsedDate != null) {
                                calendar.time = parsedDate
                            }
                        } catch (e: Exception) {
                            // ignore, fallback to current time
                        }
                        
                        val datePickerDialog = android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val cal = Calendar.getInstance()
                                cal.set(Calendar.YEAR, year)
                                cal.set(Calendar.MONTH, month)
                                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                val newDateStr = sdf.format(cal.time)
                                viewModel.selectHealthDate(newDateStr)
                                Toast.makeText(context, "Selected: $newDateStr", Toast.LENGTH_SHORT).show()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        )
                        datePickerDialog.show()
                    },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .testTag("date_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Select Date",
                        tint = Color.White
                    )
                }
            }

            // Current date bar banner
            val displayDateText = try {
                val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val parsed = sdfInput.parse(selectedDate)
                if (parsed != null) {
                    val todayStr = viewModel.getCurrentDateString()
                    val cal = Calendar.getInstance()
                    val yesterdayStr = sdfInput.format(cal.time)
                    
                    when (selectedDate) {
                        todayStr -> "Today (${selectedDate})"
                        yesterdayStr -> "Yesterday (${selectedDate})"
                        else -> {
                            val sdfDisplay = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US)
                            sdfDisplay.format(parsed)
                        }
                    }
                } else {
                    selectedDate
                }
            } catch (e: Exception) {
                selectedDate
            }
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.getDpOrZero())
                                .background(SuccessGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayDateText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (record.isSynced) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Synced",
                                tint = WaterBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Synced with Fit",
                                color = WaterBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Offline Cache",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Local Sensors",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Main body area showing Summary Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SummaryTab(
                    viewModel = viewModel,
                    record = record,
                    onLogMetric = { metric ->
                        metricToLog = metric
                        showManualLogDialog = true
                    },
                    onWaterIncrement = { amountMl ->
                        val currentWater = record.waterMl
                        viewModel.updateHealthMetric(waterMl = currentWater + amountMl)
                    },
                    onStepsClick = { showStepsDetails = true },
                    onSleepClick = { showSleepDetails = true },
                    onFoodClick = { showFoodDetails = true },
                    onWaterClick = { showWaterDetails = true }
                )
            }
        }
    }
}

    // Manual Log Dialog modal
    if (showManualLogDialog) {
        var inputValue by remember {
            mutableStateOf(
                when (metricToLog) {
                    "Sleep" -> record.sleepMinutes.toString()
                    "Calories" -> record.caloriesBurned.toString()
                    "HeartRate" -> record.heartRateAvg.toString()
                    else -> ""
                }
            )
        }
        var inputGoalValue by remember {
            mutableStateOf(
                when (metricToLog) {
                    "Sleep" -> record.sleepGoalMinutes.toString()
                    "Calories" -> record.calorieGoal.toString()
                    "HeartRate" -> "130"
                    else -> ""
                }
            )
        }

        // Dedicated states for sleep editing to guarantee smooth and reliable user typing experience
        var sleepHoursString by remember { mutableStateOf((record.sleepMinutes / 60).toString()) }
        var sleepMinsString by remember { mutableStateOf((record.sleepMinutes % 60).toString()) }
        var sleepGoalHoursString by remember { mutableStateOf((record.sleepGoalMinutes / 60).toString()) }
        var sleepGoalMinsString by remember { mutableStateOf((record.sleepGoalMinutes % 60).toString()) }

        Dialog(onDismissRequest = { showManualLogDialog = false }) {
            Surface(
                color = Color(0xFF161722),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = when (metricToLog) {
                            "Sleep" -> Icons.Default.Hotel
                            "Calories" -> Icons.Default.Restaurant
                            else -> Icons.Default.Favorite
                        },
                        contentDescription = null,
                        tint = WaterBlue,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (metricToLog == "Calories") "Log Food Intake" else "Log $metricToLog",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (metricToLog == "Calories") "Enter your recorded food calories consumed to update your local nutrition goals." else "Enter your recorded health metric data point to update your local dashboard.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (metricToLog == "Sleep") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sleep Duration", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = sleepHoursString,
                                    onValueChange = { sleepHoursString = it },
                                    label = { Text("Hours") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WaterBlue,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = sleepMinsString,
                                    onValueChange = { sleepMinsString = it },
                                    label = { Text("Minutes") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WaterBlue,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Sleep Target / Goal", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = sleepGoalHoursString,
                                    onValueChange = { sleepGoalHoursString = it },
                                    label = { Text("Hours") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WaterBlue,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = sleepGoalMinsString,
                                    onValueChange = { sleepGoalMinsString = it },
                                    label = { Text("Minutes") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WaterBlue,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    } else {
                        if (metricToLog == "Calories") {
                            Text(
                                text = "QUICK ADD MEAL",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val meals = listOf(
                                    "Breakfast (+400)" to 400,
                                    "Lunch (+650)" to 650,
                                    "Dinner (+700)" to 700,
                                    "Snack (+250)" to 250
                                )
                                meals.forEach { (name, kcal) ->
                                    Button(
                                        onClick = {
                                            val current = inputValue.toIntOrNull() ?: 0
                                            inputValue = (current + kcal).toString()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f)),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(name, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Value Input
                        OutlinedTextField(
                            value = inputValue,
                            onValueChange = { inputValue = it },
                            label = { Text(if (metricToLog == "Calories") "Calories Consumed" else "Metric Value") },
                            placeholder = {
                                Text(
                                    when (metricToLog) {
                                        "Calories" -> "e.g., 500 (kcal)"
                                        else -> "e.g., 75 (bpm)"
                                    }
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = WaterBlue,
                                unfocusedLabelColor = Color.LightGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("metric_input_field")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Goal Input
                        OutlinedTextField(
                            value = inputGoalValue,
                            onValueChange = { inputGoalValue = it },
                            label = { Text(if (metricToLog == "Calories") "Calorie Intake Goal" else "Daily Target/Goal") },
                            placeholder = {
                                Text(
                                    when (metricToLog) {
                                        "Calories" -> "e.g., 2000"
                                        else -> "e.g., 130"
                                    }
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = WaterBlue,
                                unfocusedLabelColor = Color.LightGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("metric_goal_field")
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showManualLogDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                if (metricToLog == "Sleep") {
                                    val hr = sleepHoursString.toIntOrNull() ?: 0
                                    val min = sleepMinsString.toIntOrNull() ?: 0
                                    val gHr = sleepGoalHoursString.toIntOrNull() ?: 0
                                    val gMin = sleepGoalMinsString.toIntOrNull() ?: 0

                                    val finalMins = hr * 60 + min
                                    val finalGoalMins = gHr * 60 + gMin

                                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                    val fallbackEndHour = 7
                                    val fallbackEndMin = 0
                                    val startTotalMinutes = (fallbackEndHour * 60 - finalMins + 1440) % 1440
                                    val fallbackStartHour = startTotalMinutes / 60
                                    val fallbackStartMin = startTotalMinutes % 60
                                    prefs.edit()
                                        .putString("sleep_start_time_${record.dateString}", String.format(Locale.US, "%02d:%02d", fallbackStartHour, fallbackStartMin))
                                        .putString("sleep_end_time_${record.dateString}", String.format(Locale.US, "%02d:%02d", fallbackEndHour, fallbackEndMin))
                                        .apply()

                                    viewModel.updateHealthMetric(
                                        sleepMinutes = finalMins,
                                        sleepGoalMinutes = finalGoalMins
                                    )
                                    showManualLogDialog = false
                                    Toast.makeText(context, "Sleep Cycle updated!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val value = inputValue.toIntOrNull()
                                    val goal = inputGoalValue.toIntOrNull()
                                    if (value != null) {
                                        when (metricToLog) {
                                            "Calories" -> viewModel.updateHealthMetric(
                                                caloriesBurned = value,
                                                calorieGoal = goal
                                            )
                                            "HeartRate" -> viewModel.updateHealthMetric(
                                                heartRateAvg = value,
                                                heartRateMin = (value - 15).coerceAtLeast(40),
                                                heartRateMax = (value + 35).coerceAtMost(200)
                                            )
                                        }
                                        showManualLogDialog = false
                                        Toast.makeText(context, "${metricToLog} updated!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Data", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryTab(
    viewModel: AppViewModel,
    record: HealthRecord,
    onLogMetric: (String) -> Unit,
    onWaterIncrement: (Int) -> Unit,
    onStepsClick: () -> Unit,
    onSleepClick: () -> Unit,
    onFoodClick: () -> Unit,
    onWaterClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("summary_scroll_col"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // High-fidelity dynamic Ring Visualizer (clickable)
        item {
            Box(modifier = Modifier.clickable { onStepsClick() }) {
                FitnessActivityRingCard(record = record)
            }
        }

        // Action Quick metrics Logger
        item {
            QuickLogIntakeCard(
                viewModel = viewModel,
                record = record,
                onWaterIncrement = onWaterIncrement,
                onWaterClick = onWaterClick
            )
        }

        // Active minutes & Calories burnout details
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MetricDetailCard(
                        title = "Sleep Cycle",
                        metric = "${record.sleepMinutes / 60}h ${record.sleepMinutes % 60}m",
                        target = "Goal: ${record.sleepGoalMinutes / 60}h",
                        icon = Icons.Default.Hotel,
                        progress = (record.sleepMinutes.toFloat() / record.sleepGoalMinutes.toFloat()).coerceIn(0f, 1f),
                        color = Color(0xFF9575CD),
                        onLogClick = { onLogMetric("Sleep") },
                        onCardClick = onSleepClick
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    FoodTrackerCard(
                        record = record,
                        onClick = onFoodClick
                    )
                }
            }
        }
    }
}

@Composable
fun FitnessActivityRingCard(record: HealthRecord) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121420)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fitness_ring_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Text(
                    text = "ACTIVITY PROGRESS",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Keep Moving!",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Steps stat
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(WaterBlue, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Steps: ",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${record.steps} / ${record.stepGoal}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Active Minutes stat
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(SuccessGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Active: ",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${record.activeMinutes} / ${record.activeMinutesGoal} min",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Central Canvas Rings
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(110.dp)) {
                    val strokeWidth = 10.dp.toPx()
                    
                    // Background step circle
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = size.minDimension / 2 - strokeWidth,
                        style = Stroke(width = strokeWidth)
                    )
                    // Foreground steps sweep
                    val stepPercent = (record.steps.toFloat() / record.stepGoal.toFloat()).coerceIn(0f, 1f)
                    drawArc(
                        color = WaterBlue,
                        startAngle = -90f,
                        sweepAngle = stepPercent * 360f,
                        useCenter = false,
                        size = Size(size.width - strokeWidth * 2, size.height - strokeWidth * 2),
                        topLeft = Offset(strokeWidth, strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Background active minutes circle
                    val innerStrokeWidth = 8.dp.toPx()
                    val innerOffset = strokeWidth + innerStrokeWidth + 4.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = size.minDimension / 2 - innerOffset,
                        style = Stroke(width = innerStrokeWidth)
                    )
                    // Foreground active minutes sweep
                    val activePercent = (record.activeMinutes.toFloat() / record.activeMinutesGoal.toFloat()).coerceIn(0f, 1f)
                    drawArc(
                        color = SuccessGreen,
                        startAngle = -90f,
                        sweepAngle = activePercent * 360f,
                        useCenter = false,
                        size = Size(size.width - innerOffset * 2, size.height - innerOffset * 2),
                        topLeft = Offset(innerOffset, innerOffset),
                        style = Stroke(width = innerStrokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${((record.steps.toFloat() / record.stepGoal.toFloat()) * 100).toInt()}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "Goal Achieved",
                        fontSize = 8.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun QuickLogIntakeCard(
    viewModel: AppViewModel,
    record: HealthRecord,
    onWaterIncrement: (Int) -> Unit,
    onWaterClick: () -> Unit
) {
    val waterReminderEnabled by viewModel.waterReminderEnabled.collectAsStateWithLifecycle()
    val waterReminderIntervalMins by viewModel.waterReminderIntervalMins.collectAsStateWithLifecycle()
    val waterReminderStartTime by viewModel.waterReminderStartTime.collectAsStateWithLifecycle()
    val waterReminderEndTime by viewModel.waterReminderEndTime.collectAsStateWithLifecycle()

    var showRemindersSettings by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13141F)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onWaterClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalDrink,
                        contentDescription = "Water Hydration",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Water Intake",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showRemindersSettings = !showRemindersSettings },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Water Reminders Settings",
                            tint = if (waterReminderEnabled) WaterBlue else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${record.waterMl} / ${record.waterGoalMl} ml",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Glass progress display line
            val progress = (record.waterMl.toFloat() / record.waterGoalMl.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = WaterBlue,
                trackColor = Color.White.copy(alpha = 0.05f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive cup logger shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onWaterIncrement(250) },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Cup",
                        tint = WaterBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cup (+250ml)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onWaterIncrement(500) },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Bottle",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bottle (+500ml)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Expandable Water Reminder Settings panel directly inside the water widget!
            if (showRemindersSettings) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Water Drinking Reminders",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Periodic notifications to stay hydrated",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = waterReminderEnabled,
                        onCheckedChange = { viewModel.updateWaterReminderEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = WaterBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                if (waterReminderEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Reminder Interval: ${if (waterReminderIntervalMins == 30f) "30 mins" else if (waterReminderIntervalMins == 60f) "1 hr" else if (waterReminderIntervalMins == 90f) "1.5 hrs" else if (waterReminderIntervalMins == 120f) "2 hrs" else "${waterReminderIntervalMins / 60} hrs"}",
                            color = WaterBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = waterReminderIntervalMins,
                            onValueChange = {
                                val steppedValue = when {
                                    it < 45f -> 30f
                                    it < 75f -> 60f
                                    it < 105f -> 90f
                                    it < 135f -> 120f
                                    it < 165f -> 150f
                                    else -> 180f
                                }
                                viewModel.updateWaterReminderIntervalMins(steppedValue)
                            },
                            valueRange = 30f..180f,
                            colors = SliderDefaults.colors(
                                thumbColor = WaterBlue,
                                activeTrackColor = WaterBlue,
                                inactiveTrackColor = Color.DarkGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Wake-up Time", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = waterReminderStartTime,
                                onValueChange = { viewModel.updateWaterReminderStartTime(it) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Sleeping Time", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = waterReminderEndTime,
                                onValueChange = { viewModel.updateWaterReminderEndTime(it) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveHeartRatePulseCard(record: HealthRecord, onLogClick: () -> Unit) {
    val context = LocalContext.current
    var isMeasuring by remember { mutableStateOf(false) }
    var pulseReading by remember { mutableIntStateOf(record.heartRateAvg) }
    val coroutineScope = rememberCoroutineScope()

    // Pulse animation logic
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isMeasuring) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isMeasuring) 400 else 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_pulse"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15121F)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Heart Pulse",
                        tint = Color(0xFFFF4081),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onLogClick() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Heart Rate Pulse",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(
                    onClick = { onLogClick() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Pulse", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Heart graphic & reading
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFFF4081).copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart Pulse Beat",
                            tint = Color(0xFFFF4081),
                            modifier = Modifier
                                .size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isMeasuring) "Measuring..." else "${pulseReading} bpm",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Min: ${record.heartRateMin} bpm / Max: ${record.heartRateMax} bpm",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                // Interactive check button
                Button(
                    onClick = {
                        if (!isMeasuring) {
                            isMeasuring = true
                            coroutineScope.launch {
                                // Simulate high-precision diagnostic scanning sequence
                                for (i in 1..8) {
                                    delay(400)
                                    pulseReading = (65..120).random()
                                }
                                isMeasuring = false
                                Toast.makeText(context, "Pulse reading locked!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMeasuring) Color(0xFFFF4081) else Color.White.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isMeasuring
                ) {
                    Icon(
                        imageVector = if (isMeasuring) Icons.Default.HourglassEmpty else Icons.Default.CameraAlt,
                        contentDescription = "Read Pulse",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMeasuring) "Reading..." else "Scan Pulse",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Animated EKG Emitter line on canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val phaseOffset = if (isMeasuring) {
                    val progressTransition = rememberInfiniteTransition("offset")
                    val value by progressTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 100f,
                        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
                        label = "pulse_wave"
                    )
                    value
                } else {
                    0f
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path()
                    val width = size.width
                    val height = size.height
                    path.moveTo(0f, height / 2)

                    var x = 0f
                    val step = 10f
                    while (x < width) {
                        val phase = (x + phaseOffset) % 150f
                        val y = if (phase > 40f && phase < 60f) {
                            // Draw an EKG peak point
                            val relative = (phase - 40f) / 20f
                            val factor = Math.sin(relative * Math.PI)
                            (height / 2) - (factor * (height * 0.4f)).toFloat()
                        } else if (phase >= 60f && phase < 80f) {
                            // Draw an EKG negative trough
                            val relative = (phase - 60f) / 20f
                            val factor = Math.sin(relative * Math.PI)
                            (height / 2) + (factor * (height * 0.2f)).toFloat()
                        } else {
                            height / 2
                        }
                        path.lineTo(x, y)
                        x += step
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFFFF4081).copy(alpha = if (isMeasuring) 1.0f else 0.4f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun MetricDetailCard(
    title: String,
    metric: String,
    target: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    progress: Float,
    color: Color,
    onLogClick: () -> Unit,
    onCardClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onCardClick != null) {
                    Modifier.clickable { onCardClick() }
                } else {
                    Modifier
                }
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onLogClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Log", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(text = title, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text(text = metric, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(text = target, fontSize = 9.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = color,
                trackColor = Color.White.copy(alpha = 0.05f)
            )
        }
    }
}

@Composable
fun TrendsTab(allRecords: List<HealthRecord>) {
    val itemsToShow = allRecords.take(7).reversed()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("trends_tab_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121422)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "WEEKLY STEPS GRAPH",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Daily Walk Trends",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (itemsToShow.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No trend records logged yet. Shake or walk to begin logging metrics.", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        // Custom Canvas Steps Graph Drawing
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val spacing = canvasWidth / 8
                                val maxSteps = (itemsToShow.maxOfOrNull { it.steps } ?: 10000).coerceAtLeast(10000)

                                // Draw baseline targets
                                val baselineY = canvasHeight - (10000f / maxSteps.toFloat() * canvasHeight)
                                drawLine(
                                    color = WaterBlue.copy(alpha = 0.25f),
                                    start = Offset(0f, baselineY),
                                    end = Offset(canvasWidth, baselineY),
                                    strokeWidth = 2f
                                )

                                itemsToShow.forEachIndexed { idx, rec ->
                                    val barWidth = spacing * 0.6f
                                    val xOffset = (idx + 1) * spacing - (barWidth / 2)
                                    val percent = rec.steps.toFloat() / maxSteps.toFloat()
                                    val barHeight = canvasHeight * percent
                                    val yOffset = canvasHeight - barHeight

                                    // Draw background bar anchor
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.05f),
                                        topLeft = Offset(xOffset, 0f),
                                        size = Size(barWidth, canvasHeight)
                                    )

                                    // Draw active metrics foreground rectangle
                                    drawRect(
                                        color = if (rec.steps >= 10000) SuccessGreen else WaterBlue,
                                        topLeft = Offset(xOffset, yOffset),
                                        size = Size(barWidth, barHeight)
                                    )
                                }
                            }
                        }

                        // Labels Row under the custom Canvas Graph
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            itemsToShow.forEach { rec ->
                                val label = rec.dateString.split("-").lastOrNull() ?: rec.dateString
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Historic Logging List Table
        item {
            Text(
                text = "HISTORICAL METRICS LOG",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WaterBlue,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }

        if (allRecords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No historic metrics recorded yet.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            items(allRecords.size) { index ->
                val rec = allRecords[index]
                Surface(
                    color = Color.White.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = rec.dateString, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            val loggedMealCount = listOf(rec.breakfastFoods, rec.lunchFoods, rec.dinnerFoods, rec.snacksFoods).count { it.isNotEmpty() }
                            Text(text = "Sleep: ${rec.sleepMinutes / 60}h | Water: ${rec.waterMl}ml | Food: $loggedMealCount meals", color = Color.Gray, fontSize = 11.sp)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${rec.steps} steps",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (rec.steps >= rec.stepGoal) SuccessGreen else WaterBlue,
                                fontSize = 13.sp
                            )
                            if (rec.isSynced) {
                                Icon(Icons.Default.CloudDone, contentDescription = "Synced", tint = WaterBlue, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleSyncTab(
    statusMessage: String,
    onConnectFit: () -> Unit,
    onClearCache: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("google_sync_tab_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Branded integration card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101222)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Cloud Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Google Fit REST API & Health Connect Sync",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Sync your personal fitness sensors securely with Google Cloud. We sync step counters, active duration, food calorie intake, heart beat rate, and hydration cycles automatically.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Connection diagnostics status block
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (statusMessage.contains("successfully", ignoreCase = true)) SuccessGreen
                                    else if (statusMessage.contains("Connecting", ignoreCase = true)) Color.Yellow
                                    else Color.Gray,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Status: $statusMessage",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary sync trigger button
                Button(
                    onClick = onConnectFit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("google_fit_sync_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "Sync", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sync & Authorize Google Fit REST API",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Local cache reset card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sensors Diagnostics",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "If steps do not increment or you wish to reset your diagnostic dashboard, you can trigger a full sensor baseline clear.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onClearCache,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Clear Local Health Cache", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// Utility extension helper to support safe 0.dp constraints
private fun Int.getDpOrZero(): androidx.compose.ui.unit.Dp {
    return if (this <= 0) 0.dp else this.dp
}

@Composable
fun StepsDetailsPage(
    viewModel: AppViewModel,
    record: HealthRecord,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedHealthDate.collectAsStateWithLifecycle()

    var inputSteps by remember { mutableStateOf(record.steps.toString()) }
    var inputGoal by remember { mutableStateOf(record.stepGoal.toString()) }
    var inputCalories by remember { mutableStateOf(record.caloriesBurned.toString()) }
    var inputActiveMinutes by remember { mutableStateOf(record.activeMinutes.toString()) }

    LaunchedEffect(record.dateString, record.steps, record.stepGoal, record.caloriesBurned, record.activeMinutes) {
        inputSteps = record.steps.toString()
        inputGoal = record.stepGoal.toString()
        inputCalories = record.caloriesBurned.toString()
        inputActiveMinutes = record.activeMinutes.toString()
    }

    // Format display for selected date
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayDateText = try {
        val parsed = sdf.parse(selectedDate)
        if (parsed != null) {
            val sdfDisplay = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US)
            sdfDisplay.format(parsed)
        } else {
            selectedDate
        }
    } catch (e: Exception) {
        selectedDate
    }

    // Distance calculation: steps * 0.00075 km
    val computedDistance = (record.steps * 0.00075f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080F))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("HEALTH & FITNESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WaterBlue, letterSpacing = 2.sp)
                    Text("Steps Details Tracker", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            // Calendar selector button (top right)
            IconButton(
                onClick = {
                    val calendar = Calendar.getInstance()
                    try {
                        val parsedDate = sdf.parse(selectedDate)
                        if (parsedDate != null) {
                            calendar.time = parsedDate
                        }
                    } catch (e: Exception) {}

                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.YEAR, year)
                            cal.set(Calendar.MONTH, month)
                            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            val newDateStr = sdf.format(cal.time)
                            viewModel.selectHealthDate(newDateStr)
                            Toast.makeText(context, "Selected date: $newDateStr", Toast.LENGTH_SHORT).show()
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                    datePickerDialog.show()
                },
                modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.DateRange, contentDescription = "Select Date", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Display current date being edited
        Text(
            text = "Viewing details for: $displayDateText",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121420)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ACTIVITY SUMMARY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${record.steps} / ${record.stepGoal} steps", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(
                        text = "Distance: ${String.format(java.util.Locale.US, "%.2f", computedDistance)} km | Est. Burned: ${(record.steps * 0.04f).toInt()} kcal",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
                Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    val progress = if (record.stepGoal > 0) (record.steps.toFloat() / record.stepGoal.toFloat()).coerceIn(0f, 1f) else 0f
                    CircularProgressIndicator(
                        progress = progress,
                        color = WaterBlue,
                        strokeWidth = 6.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(text = "${(progress * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Manual Input Section
        Text(
            text = "ENTER DETAILS MANUALLY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WaterBlue,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = inputSteps,
                onValueChange = { inputSteps = it },
                label = { Text("Steps Walked") },
                placeholder = { Text("e.g., 8500") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WaterBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedLabelColor = WaterBlue,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = inputGoal,
                onValueChange = { inputGoal = it },
                label = { Text("Step Goal Target") },
                placeholder = { Text("e.g., 10000") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WaterBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedLabelColor = WaterBlue,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = inputCalories,
                        onValueChange = { inputCalories = it },
                        label = { Text("Food Calories (kcal)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = WaterBlue,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = inputActiveMinutes,
                        onValueChange = { inputActiveMinutes = it },
                        label = { Text("Active Minutes") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = WaterBlue,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = {
                    val s = inputSteps.toIntOrNull() ?: record.steps
                    val g = inputGoal.toIntOrNull() ?: record.stepGoal
                    val c = inputCalories.toIntOrNull() ?: record.caloriesBurned
                    val m = inputActiveMinutes.toIntOrNull() ?: record.activeMinutes
                    viewModel.updateHealthMetric(
                        steps = s,
                        stepGoal = g,
                        caloriesBurned = c,
                        activeMinutes = m
                    )
                    Toast.makeText(context, "Statistics saved manually!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
            ) {
                Text("Save Manual Details", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SleepDetailsPage(
    viewModel: AppViewModel,
    record: HealthRecord,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allRecords by viewModel.healthRecordsList.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedHealthDate.collectAsStateWithLifecycle()

    var startHour by remember { mutableIntStateOf(23) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(7) }
    var endMinute by remember { mutableIntStateOf(0) }

    LaunchedEffect(record.dateString, record.sleepMinutes) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedStart = prefs.getString("sleep_start_time_${record.dateString}", null)
        val savedEnd = prefs.getString("sleep_end_time_${record.dateString}", null)

        if (savedStart != null && savedEnd != null) {
            val startParts = savedStart.split(":")
            val endParts = savedEnd.split(":")
            if (startParts.size == 2 && endParts.size == 2) {
                startHour = startParts[0].toIntOrNull() ?: 23
                startMinute = startParts[1].toIntOrNull() ?: 0
                endHour = endParts[0].toIntOrNull() ?: 7
                endMinute = endParts[1].toIntOrNull() ?: 0
                return@LaunchedEffect
            }
        }

        val totalMins = record.sleepMinutes
        if (totalMins <= 0) {
            startHour = 23
            startMinute = 0
            endHour = 7
            endMinute = 0
        } else {
            endHour = 7
            endMinute = 0
            val startTotalMinutes = (7 * 60 - totalMins + 1440) % 1440
            startHour = startTotalMinutes / 60
            startMinute = startTotalMinutes % 60
        }
    }

    val computedMinutes = remember(startHour, startMinute, endHour, endMinute) {
        val startTotal = startHour * 60 + startMinute
        val endTotal = endHour * 60 + endMinute
        if (endTotal >= startTotal) {
            endTotal - startTotal
        } else {
            (1440 - startTotal) + endTotal
        }
    }

    val computedHours = computedMinutes / 60.0f

    // Format display for selected date
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayDateText = try {
        val parsed = sdf.parse(selectedDate)
        if (parsed != null) {
            val sdfDisplay = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US)
            sdfDisplay.format(parsed)
        } else {
            selectedDate
        }
    } catch (e: Exception) {
        selectedDate
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080F))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SLEEP TRACKER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9575CD),
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Sleep Analytics",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            // Calendar selector button (top right)
            IconButton(
                onClick = {
                    val calendar = Calendar.getInstance()
                    try {
                        val parsedDate = sdf.parse(selectedDate)
                        if (parsedDate != null) {
                            calendar.time = parsedDate
                        }
                    } catch (e: Exception) {}

                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.YEAR, year)
                            cal.set(Calendar.MONTH, month)
                            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            val newDateStr = sdf.format(cal.time)
                            viewModel.selectHealthDate(newDateStr)
                            Toast.makeText(context, "Selected date: $newDateStr", Toast.LENGTH_SHORT).show()
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                    datePickerDialog.show()
                },
                modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.DateRange, contentDescription = "Select Date", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active Date Sleep Record Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = displayDateText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val hoursNum = record.sleepMinutes / 60
                        val minsNum = record.sleepMinutes % 60
                        Text(
                            text = "${hoursNum}h ${minsNum}m",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "Goal: ${record.sleepGoalMinutes / 60}h (480m)",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    // State classification banner inside Card
                    val sleepHours = record.sleepMinutes / 60.0f
                    val (statusText, statusColor) = when {
                        sleepHours < 6.0f -> "Inadequate" to Color(0xFFEF5350)
                        sleepHours < 7.0f -> "Needs Improvement" to Color(0xFFFFCA28)
                        else -> "Optimal" to Color(0xFF66BB6A)
                    }
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 20-Day Sleep Data Bar Graph Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "20-Day Sleep History",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Swipe horizontally. Click any bar to view or edit that day's sleep",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                // Build past 20 days of sleep data ending at TODAY to prevent shifting on selection
                val todayDateStr = viewModel.getCurrentDateString()
                val last20Days = (0..19).map { offset ->
                    val cal = Calendar.getInstance()
                    try {
                        val parsed = sdf.parse(todayDateStr)
                        if (parsed != null) cal.time = parsed
                    } catch (e: Exception) {}
                    cal.add(Calendar.DATE, -offset)
                    sdf.format(cal.time)
                }.reversed() // chronological order

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    last20Days.forEach { barDateString ->
                        val matchingRecord = allRecords.find { it.dateString == barDateString }
                        val mins = matchingRecord?.sleepMinutes ?: 0
                        val hrs = mins / 60.0f
                        
                        val isCurrentSelected = barDateString == selectedDate
                        
                        // Height proportional to duration. Let's set 10 hours as 100% height = 120.dp
                        val barHeight = (120 * (hrs / 10f).coerceIn(0.04f, 1f)).dp
                        
                        val barColor = when {
                            hrs < 6.0f -> Color(0xFFEF5350)
                            hrs < 7.0f -> Color(0xFFFFCA28)
                            else -> Color(0xFF66BB6A)
                        }

                        // Day label (e.g., "Mon")
                        val dayLabel = try {
                            val parsedDate = sdf.parse(barDateString)
                            if (parsedDate != null) {
                                SimpleDateFormat("EEE", Locale.US).format(parsedDate)
                            } else {
                                ""
                            }
                        } catch (e: Exception) {
                            ""
                        }

                        // Short Date label (e.g., "07/05")
                        val shortDateLabel = try {
                            val parsedDate = sdf.parse(barDateString)
                            if (parsedDate != null) {
                                SimpleDateFormat("MM/dd", Locale.US).format(parsedDate)
                            } else {
                                ""
                            }
                        } catch (e: Exception) {
                            ""
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(44.dp)
                                .clickable {
                                    viewModel.selectHealthDate(barDateString)
                                }
                                .padding(horizontal = 2.dp)
                        ) {
                            // Value text above the bar
                            Text(
                                text = if (hrs > 0f) String.format(Locale.US, "%.1fh", hrs) else "-",
                                fontSize = 10.sp,
                                color = if (isCurrentSelected) Color.White else Color.Gray,
                                fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Normal
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // The vertical bar
                            Box(
                                modifier = Modifier
                                    .height(barHeight)
                                    .width(26.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (isCurrentSelected) barColor else barColor.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        width = if (isCurrentSelected) 2.dp else 0.dp,
                                        color = if (isCurrentSelected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Day name
                            Text(
                                text = dayLabel,
                                fontSize = 10.sp,
                                color = if (isCurrentSelected) Color.White else Color.Gray,
                                fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            // Date
                            Text(
                                text = shortDateLabel,
                                fontSize = 8.sp,
                                color = if (isCurrentSelected) Color.White.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bedtime and Wakeup editor cards
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Edit Sleep Times",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Adjust your bedtime and wake up time below to calculate sleep duration",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Bedtime Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Bedtime (Sleep Start)", fontSize = 13.sp, color = Color.LightGray, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "%02d:%02d", startHour, startMinute),
                            fontSize = 24.sp,
                            color = Color(0xFF9575CD),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Hour adjusting
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Hours", fontSize = 9.sp, color = Color.Gray)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { startHour = (startHour + 23) % 24 },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease start hour", tint = Color.White)
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", startHour),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { startHour = (startHour + 1) % 24 },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Increase start hour", tint = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Minute adjusting
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Minutes", fontSize = 9.sp, color = Color.Gray)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { startMinute = (startMinute + 55) % 60 },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease start minute", tint = Color.White)
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", startMinute),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { startMinute = (startMinute + 5) % 60 },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Increase start minute", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Wake up time Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Wake Up (Sleep End)", fontSize = 13.sp, color = Color.LightGray, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "%02d:%02d", endHour, endMinute),
                            fontSize = 24.sp,
                            color = Color(0xFF66BB6A),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Hour adjusting
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Hours", fontSize = 9.sp, color = Color.Gray)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { endHour = (endHour + 23) % 24 },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease end hour", tint = Color.White)
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", endHour),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { endHour = (endHour + 1) % 24 },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Increase end hour", tint = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Minute adjusting
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Minutes", fontSize = 9.sp, color = Color.Gray)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { endMinute = (endMinute + 55) % 60 },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease end minute", tint = Color.White)
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", endMinute),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { endMinute = (endMinute + 5) % 60 },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Increase end minute", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Reactive calculation summary
                val compHoursPart = computedMinutes / 60
                val compMinsPart = computedMinutes % 60
                
                val (badgeText, badgeColor) = when {
                    computedHours < 6.0f -> "Inadequate (< 6h)" to Color(0xFFEF5350)
                    computedHours < 7.0f -> "Needs Improvement (6 - 7h)" to Color(0xFFFFCA28)
                    else -> "Optimal (>= 7h)" to Color(0xFF66BB6A)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(badgeColor.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .border(1.dp, badgeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Calculated Duration", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = "${compHoursPart} hrs ${compMinsPart} mins",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SAVE BUTTON
                Button(
                    onClick = {
                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("sleep_start_time_${record.dateString}", String.format(Locale.US, "%02d:%02d", startHour, startMinute))
                            .putString("sleep_end_time_${record.dateString}", String.format(Locale.US, "%02d:%02d", endHour, endMinute))
                            .putBoolean("sleep_manually_saved_${record.dateString}", true)
                            .putBoolean("sleep_manually_saved_ever", true)
                            .apply()
                        viewModel.updateHealthMetric(sleepMinutes = computedMinutes)
                        Toast.makeText(context, "Sleep hours updated successfully!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9575CD)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Sleep Record", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // AUTOMATED DETECTION BUTTON
                OutlinedButton(
                    onClick = {
                        viewModel.trackSleepFromDeviceUsage(context, force = true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF66BB6A)),
                    border = BorderStroke(1.dp, Color(0xFF66BB6A).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Autorenew, contentDescription = null, tint = Color(0xFF66BB6A))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto-Detect Sleep (Device Usage)", color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FoodTrackerCard(
    record: HealthRecord,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = Color(0xFFFFA726),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Food Log",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Display logged meals status
            val meals = listOf(
                "BF" to record.breakfastFoods.isNotEmpty(),
                "LH" to record.lunchFoods.isNotEmpty(),
                "DN" to record.dinnerFoods.isNotEmpty(),
                "SN" to record.snacksFoods.isNotEmpty()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                meals.forEach { (name, logged) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (logged) Color(0xFFFFA726).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (logged) Color(0xFFFFA726).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (logged) Color(0xFFFFA726) else Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Show a preview of logged foods
            val previewText = remember(record) {
                val list = mutableListOf<String>()
                if (record.breakfastFoods.isNotEmpty()) list.add("BF: ${record.breakfastFoods}")
                if (record.lunchFoods.isNotEmpty()) list.add("LH: ${record.lunchFoods}")
                if (record.dinnerFoods.isNotEmpty()) list.add("DN: ${record.dinnerFoods}")
                if (record.snacksFoods.isNotEmpty()) list.add("SN: ${record.snacksFoods}")
                
                if (list.isEmpty()) "No meals logged today" else list.joinToString(", ")
            }

            Text(
                text = previewText,
                fontSize = 11.sp,
                color = if (previewText == "No meals logged today") Color.Gray else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FoodDetailsPage(
    viewModel: AppViewModel,
    record: HealthRecord,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allRecords by viewModel.healthRecordsList.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedHealthDate.collectAsStateWithLifecycle()

    var breakfastInput by remember { mutableStateOf(record.breakfastFoods) }
    var lunchInput by remember { mutableStateOf(record.lunchFoods) }
    var dinnerInput by remember { mutableStateOf(record.dinnerFoods) }
    var snacksInput by remember { mutableStateOf(record.snacksFoods) }

    LaunchedEffect(record.dateString, record.breakfastFoods, record.lunchFoods, record.dinnerFoods, record.snacksFoods) {
        breakfastInput = record.breakfastFoods
        lunchInput = record.lunchFoods
        dinnerInput = record.dinnerFoods
        snacksInput = record.snacksFoods
    }

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayDateText = try {
        val parsed = sdf.parse(selectedDate)
        if (parsed != null) {
            val sdfDisplay = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US)
            sdfDisplay.format(parsed)
        } else {
            selectedDate
        }
    } catch (e: Exception) {
        selectedDate
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080F))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "NUTRITION JOURNAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFA726),
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Mindful Food Log",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            // Date picker
            IconButton(
                onClick = {
                    val calendar = Calendar.getInstance()
                    try {
                        val parsedDate = sdf.parse(selectedDate)
                        if (parsedDate != null) {
                            calendar.time = parsedDate
                        }
                    } catch (e: Exception) {}

                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.YEAR, year)
                            cal.set(Calendar.MONTH, month)
                            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            val newDateStr = sdf.format(cal.time)
                            viewModel.selectHealthDate(newDateStr)
                            Toast.makeText(context, "Selected: $newDateStr", Toast.LENGTH_SHORT).show()
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                    datePickerDialog.show()
                },
                modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.DateRange, contentDescription = "Select Date", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active Date Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFFFA726).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFFFFA726))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = displayDateText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "We focus on clean, mindful eating rather than calorie counting.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Meals entry list
        Text(
            text = "What did you eat today?",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val mealSections = listOf(
            Triple("Breakfast", breakfastInput, { v: String -> breakfastInput = v }),
            Triple("Lunch", lunchInput, { v: String -> lunchInput = v }),
            Triple("Dinner", dinnerInput, { v: String -> dinnerInput = v }),
            Triple("Snacks", snacksInput, { v: String -> snacksInput = v })
        )

        mealSections.forEach { (title, value, onValueChange) ->
            val mealIcon = when (title) {
                "Breakfast" -> Icons.Default.FreeBreakfast
                "Lunch" -> Icons.Default.Restaurant
                "Dinner" -> Icons.Default.LocalDining
                else -> Icons.Default.Fastfood
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(imageVector = mealIcon, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { Text("e.g., Oatmeal with berries and coffee", fontSize = 13.sp, color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFA726),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = {
                viewModel.updateHealthMetric(
                    breakfastFoods = breakfastInput,
                    lunchFoods = lunchInput,
                    dinnerFoods = dinnerInput,
                    snacksFoods = snacksInput
                )
                Toast.makeText(context, "Meals updated successfully!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Meals Log", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 7-Day History list
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "7-Day Food Journal History",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Build past 7 days of food logs ending at selectedDate
                val last7Days = (0..6).map { offset ->
                    val cal = Calendar.getInstance()
                    try {
                        val parsed = sdf.parse(selectedDate)
                        if (parsed != null) cal.time = parsed
                    } catch (e: Exception) {}
                    cal.add(Calendar.DATE, -offset)
                    sdf.format(cal.time)
                }

                last7Days.forEach { historyDate ->
                    val matchingRecord = allRecords.find { it.dateString == historyDate }
                    val hasFood = matchingRecord != null && (
                        matchingRecord.breakfastFoods.isNotEmpty() ||
                        matchingRecord.lunchFoods.isNotEmpty() ||
                        matchingRecord.dinnerFoods.isNotEmpty() ||
                        matchingRecord.snacksFoods.isNotEmpty()
                    )

                    val isCurrent = historyDate == selectedDate

                    val dateLabel = try {
                        val parsed = sdf.parse(historyDate)
                        if (parsed != null) {
                            if (historyDate == viewModel.getCurrentDateString()) {
                                "Today"
                            } else {
                                SimpleDateFormat("EEEE, MMM d", Locale.US).format(parsed)
                            }
                        } else {
                            historyDate
                        }
                    } catch (e: Exception) {
                        historyDate
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectHealthDate(historyDate) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dateLabel,
                                fontSize = 13.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) Color(0xFFFFA726) else Color.White
                            )
                            
                            if (matchingRecord != null && hasFood) {
                                val foodSummary = buildString {
                                    if (matchingRecord.breakfastFoods.isNotEmpty()) append("BF: ${matchingRecord.breakfastFoods} | ")
                                    if (matchingRecord.lunchFoods.isNotEmpty()) append("LH: ${matchingRecord.lunchFoods} | ")
                                    if (matchingRecord.dinnerFoods.isNotEmpty()) append("DN: ${matchingRecord.dinnerFoods} | ")
                                    if (matchingRecord.snacksFoods.isNotEmpty()) append("SN: ${matchingRecord.snacksFoods}")
                                }.trim().removeSuffix("|").trim()
                                
                                Text(
                                    text = foodSummary,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "No food logged for this day",
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(
                                    1.dp,
                                    if (hasFood) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .background(
                                    if (hasFood) Color(0xFF66BB6A).copy(alpha = 0.15f) else Color.Transparent,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasFood) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF66BB6A),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    if (historyDate != last7Days.last()) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}

@Composable
fun WaterDetailsPage(
    viewModel: AppViewModel,
    record: HealthRecord,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allRecords by viewModel.healthRecordsList.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedHealthDate.collectAsStateWithLifecycle()

    var inputWater by remember { mutableStateOf(record.waterMl.toString()) }
    var inputGoal by remember { mutableStateOf(record.waterGoalMl.toString()) }

    LaunchedEffect(record.dateString, record.waterMl, record.waterGoalMl) {
        inputWater = record.waterMl.toString()
        inputGoal = record.waterGoalMl.toString()
    }

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayDateText = try {
        val parsed = sdf.parse(selectedDate)
        if (parsed != null) {
            val sdfDisplay = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US)
            sdfDisplay.format(parsed)
        } else {
            selectedDate
        }
    } catch (e: Exception) {
        selectedDate
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080F))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "WATER TRACKER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Hydration Analytics",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            // Calendar selector button (top right)
            IconButton(
                onClick = {
                    val calendar = Calendar.getInstance()
                    try {
                        val parsedDate = sdf.parse(selectedDate)
                        if (parsedDate != null) {
                            calendar.time = parsedDate
                        }
                    } catch (e: Exception) {}

                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.YEAR, year)
                            cal.set(Calendar.MONTH, month)
                            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            val newDateStr = sdf.format(cal.time)
                            viewModel.selectHealthDate(newDateStr)
                            Toast.makeText(context, "Selected date: $newDateStr", Toast.LENGTH_SHORT).show()
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                    datePickerDialog.show()
                },
                modifier = Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.DateRange, contentDescription = "Select Date", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active Date Water Record Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = displayDateText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${record.waterMl} ml",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "Goal: ${record.waterGoalMl} ml",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    val progress = if (record.waterGoalMl > 0) (record.waterMl.toFloat() / record.waterGoalMl.toFloat()).coerceIn(0f, 1f) else 0f
                    val (statusText, statusColor) = when {
                        progress < 0.5f -> "Dehydrated" to Color(0xFFEF5350)
                        progress < 1.0f -> "Needs Improvement" to Color(0xFFFFCA28)
                        else -> "Optimal" to Color(0xFF66BB6A)
                    }
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Progress Indicator
                val progressVal = if (record.waterGoalMl > 0) (record.waterMl.toFloat() / record.waterGoalMl.toFloat()).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = progressVal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = WaterBlue,
                    trackColor = Color.White.copy(alpha = 0.05f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 30-Day Water Data Bar Graph Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "30-Day Water History",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Swipe horizontally. Click any bar to view or edit that day's water",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                val todayDateStr = viewModel.getCurrentDateString()
                val last30Days = (0..29).map { offset ->
                    val cal = Calendar.getInstance()
                    try {
                        val parsed = sdf.parse(todayDateStr)
                        if (parsed != null) cal.time = parsed
                    } catch (e: Exception) {}
                    cal.add(Calendar.DATE, -offset)
                    sdf.format(cal.time)
                }.reversed()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    last30Days.forEach { barDateString ->
                        val matchingRecord = allRecords.find { it.dateString == barDateString }
                        val ml = matchingRecord?.waterMl ?: 0
                        val goal = matchingRecord?.waterGoalMl ?: 2000
                        val ratio = if (goal > 0) ml.toFloat() / goal.toFloat() else 0f
                        
                        val isCurrentSelected = barDateString == selectedDate
                        
                        // Height proportional to intake. Standardize max height representing 3000ml = 120.dp
                        val barHeight = (120 * (ml.toFloat() / 3000f).coerceIn(0.04f, 1.2f)).dp
                        
                        val barColor = when {
                            ratio < 0.5f -> Color(0xFFEF5350)
                            ratio < 1.0f -> Color(0xFFFFCA28)
                            else -> Color(0xFF66BB6A)
                        }

                        val dayLabel = try {
                            val parsedDate = sdf.parse(barDateString)
                            if (parsedDate != null) {
                                SimpleDateFormat("EEE", Locale.US).format(parsedDate)
                            } else {
                                ""
                            }
                        } catch (e: Exception) {
                            ""
                        }

                        val shortDateLabel = try {
                            val parsedDate = sdf.parse(barDateString)
                            if (parsedDate != null) {
                                SimpleDateFormat("MM/dd", Locale.US).format(parsedDate)
                            } else {
                                ""
                            }
                        } catch (e: Exception) {
                            ""
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(44.dp)
                                .clickable {
                                    viewModel.selectHealthDate(barDateString)
                                }
                        ) {
                            Text(
                                text = "${ml}ml",
                                fontSize = 8.sp,
                                color = if (isCurrentSelected) Color.White else Color.Gray,
                                fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .height(barHeight)
                                    .width(26.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (isCurrentSelected) barColor else barColor.copy(alpha = 0.5f)
                                    )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = dayLabel,
                                fontSize = 10.sp,
                                color = if (isCurrentSelected) WaterBlue else Color.Gray,
                                fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = shortDateLabel,
                                fontSize = 8.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manual log fields & Quick adjustments
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Manual Hydration Intake Log",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Adjust ml amount manually or set target goals",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Quick Increment Shortcuts inside detail page
                Text(text = "Quick Logging Additions", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val current = inputWater.toIntOrNull() ?: 0
                            inputWater = (current + 250).toString()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+250 ml", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val current = inputWater.toIntOrNull() ?: 0
                            inputWater = (current + 500).toString()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+500 ml", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            inputWater = "0"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Text Fields
                OutlinedTextField(
                    value = inputWater,
                    onValueChange = { inputWater = it },
                    label = { Text("Water Intake (ml)") },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        focusedLabelColor = WaterBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        unfocusedLabelColor = Color.Gray,
                        cursorColor = WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputGoal,
                    onValueChange = { inputGoal = it },
                    label = { Text("Water Goal Target (ml)") },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        focusedLabelColor = WaterBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        unfocusedLabelColor = Color.Gray,
                        cursorColor = WaterBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // SAVE BUTTON
                Button(
                    onClick = {
                        val finalWater = inputWater.toIntOrNull() ?: record.waterMl
                        val finalGoal = inputGoal.toIntOrNull() ?: record.waterGoalMl
                        viewModel.updateHealthMetric(
                            waterMl = finalWater,
                            waterGoalMl = finalGoal
                        )
                        Toast.makeText(context, "Water record updated successfully!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Hydration Record", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
