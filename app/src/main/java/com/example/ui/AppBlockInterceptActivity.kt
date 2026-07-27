package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AppBlockHelper
import kotlinx.coroutines.delay

class AppBlockInterceptActivity : ComponentActivity() {
    private val interceptPkgState = mutableStateOf("com.instagram.android")
    private val isLimitBlockState = mutableStateOf(false)
    private val isStrictModeInterceptState = mutableStateOf(false)
    private val resetTriggerState = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        interceptPkgState.value = intent.getStringExtra("INTERCEPTED_PACKAGE") ?: "com.instagram.android"
        isLimitBlockState.value = intent.getBooleanExtra("IS_LIMIT_BLOCK", false)
        isStrictModeInterceptState.value = intent.getBooleanExtra("IS_STRICT_MODE_INTERCEPT", false)
        
        setContent {
            val interceptPkg = interceptPkgState.value
            val isLimitBlock = isLimitBlockState.value
            val context = androidx.compose.ui.platform.LocalContext.current

            val resetTrigger = resetTriggerState.value
            var countdownSeconds by remember(resetTrigger) { mutableStateOf(15) }
            var isCountdownActive by remember(resetTrigger) { mutableStateOf(true) }
            var limitBypassed by remember(resetTrigger, interceptPkg) {
                mutableStateOf(com.example.util.AppBlockHelper.isDailyBypassed(context, interceptPkg))
            }

            LaunchedEffect(isLimitBlock, isCountdownActive, resetTrigger) {
                if (isLimitBlock && isCountdownActive) {
                    countdownSeconds = 15
                    while (countdownSeconds > 0) {
                        delay(1000L)
                        countdownSeconds--
                    }
                    isCountdownActive = false
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black.copy(alpha = 0.6f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    val appLabel = remember(interceptPkg) {
                        try {
                            val pm = packageManager
                            val info = pm.getApplicationInfo(interceptPkg, 0)
                            pm.getApplicationLabel(info).toString()
                        } catch (e: Exception) {
                            interceptPkg.substringAfterLast('.')
                        }
                    }
                    var customMinutes by remember { mutableStateOf(5f) }
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E20),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isStrictModeInterceptState.value) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Strict Lock",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "STRICT FOCUS MODE ACTIVE",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Access Restricted: $appLabel",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "A focus session is currently in progress. Access to $appLabel is restricted to prevent distraction.\n\nYou must pause or end your focus timer to open this app, or close the app to return to your home screen.",
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Button 1: Pause Timer
                                    Button(
                                        onClick = {
                                            if (com.example.util.FocusTimerManager.isTimerRunning.value) {
                                                com.example.util.FocusTimerManager.pauseTimer(applicationContext)
                                            }
                                            if (com.example.util.FocusTimerManager.isStopwatchActive.value) {
                                                com.example.util.FocusTimerManager.pauseStopwatch(applicationContext)
                                            }
                                            try {
                                                val launchIntent = packageManager.getLaunchIntentForPackage(interceptPkg)
                                                if (launchIntent != null) {
                                                    startActivity(launchIntent)
                                                }
                                            } catch (e: Exception) {}
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF5AB9EA),
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Text(text = "Pause Focus Timer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    // Button 2: End Timer
                                    Button(
                                        onClick = {
                                            if (com.example.util.FocusTimerManager.isTimerRunning.value) {
                                                com.example.util.FocusTimerManager.resetTimer(applicationContext, saveSession = true)
                                            }
                                            if (com.example.util.FocusTimerManager.isStopwatchActive.value) {
                                                com.example.util.FocusTimerManager.resetStopwatch(applicationContext, saveSession = true)
                                            }
                                            try {
                                                val launchIntent = packageManager.getLaunchIntentForPackage(interceptPkg)
                                                if (launchIntent != null) {
                                                    startActivity(launchIntent)
                                                }
                                            } catch (e: Exception) {}
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF5AB9EA).copy(alpha = 0.2f),
                                            contentColor = Color(0xFF5AB9EA)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5AB9EA)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Text(text = "End Focus Session", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Button 3: Close App (Exit)
                                    Button(
                                        onClick = {
                                            try {
                                                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                                    addCategory(Intent.CATEGORY_HOME)
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                startActivity(homeIntent)
                                            } catch (e: Exception) {}
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF5252),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Text(text = "Close $appLabel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Session Warning Icon",
                                        tint = if (isLimitBlock) Color(0xFFFF5252) else Color(0xFF5AB9EA),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = if (isLimitBlock) "LIMIT EXCEEDED" else "LIFE OS APP MONITOR",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (isLimitBlock) {
                                            "Daily screen limit reached for $appLabel."
                                        } else {
                                            "You are attempting to open $appLabel."
                                        },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isLimitBlock) {
                                            "Time is over for the day.\n\nYou have used all your allocated usage for today. Access is strictly disabled to cultivate focus. To continue, you must wait for the countdown or choose to Close App."
                                        } else {
                                            "Please allocate your session time usage below. Once the duration is over, Life OS will automatically block access.\n\nSelecting 'Close App' will safely exit and return to home."
                                        },
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (isLimitBlock && !limitBypassed) {
                                        // 15-second countdown & Close App buttons
                                        Button(
                                            onClick = {
                                                if (!isCountdownActive) {
                                                    com.example.util.AppBlockHelper.setDailyBypass(context, interceptPkg, true)
                                                    limitBypassed = true
                                                }
                                            },
                                            enabled = !isCountdownActive,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isCountdownActive) Color(0xFF5AB9EA).copy(alpha = 0.3f) else Color(0xFF5AB9EA),
                                                contentColor = if (isCountdownActive) Color.Gray else Color.Black
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                        ) {
                                            val buttonText = if (isCountdownActive) {
                                                "Still Use the App (${countdownSeconds}s)"
                                            } else {
                                                "Still Use the App"
                                            }
                                            Text(text = buttonText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Button(
                                            onClick = {
                                                try {
                                                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                                        addCategory(Intent.CATEGORY_HOME)
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                    startActivity(homeIntent)
                                                } catch (e: Exception) {}
                                                finish()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFFF5252),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                        ) {
                                            Text(
                                                text = "Close App",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    } else {
                                        // Show normal session options (Quick presets and Custom slider)
                                        Text(
                                            text = "⚡ QUICK PRESETS",
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                        
                                        // Predefined presets
                                        listOf(5, 10, 15).forEach { mins ->
                                            Button(
                                                onClick = {
                                                    AppBlockHelper.startTemporarySession(applicationContext, interceptPkg, mins)
                                                    try {
                                                        val launchIntent = packageManager.getLaunchIntentForPackage(interceptPkg)
                                                        if (launchIntent != null) {
                                                            startActivity(launchIntent)
                                                        }
                                                    } catch (e: Exception) {
                                                        android.widget.Toast.makeText(applicationContext, "Session set", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    finish()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF5AB9EA),
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp)
                                            ) {
                                                Text(
                                                    text = "Use for $mins minutes",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "⚙️ CUSTOM FOCUS TIMER",
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Duration:",
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "${customMinutes.toInt()} mins",
                                                color = Color(0xFF5AB9EA),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        
                                        Slider(
                                            value = customMinutes,
                                            onValueChange = { customMinutes = it.coerceIn(1f, 60f) },
                                            valueRange = 1f..60f,
                                            steps = 59,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFF5AB9EA),
                                                activeTrackColor = Color(0xFF5AB9EA),
                                                inactiveTrackColor = Color(0xFF2B2B30)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        
                                        Button(
                                            onClick = {
                                                val mins = customMinutes.toInt()
                                                AppBlockHelper.startTemporarySession(applicationContext, interceptPkg, mins)
                                                try {
                                                    val launchIntent = packageManager.getLaunchIntentForPackage(interceptPkg)
                                                    if (launchIntent != null) {
                                                        startActivity(launchIntent)
                                                    }
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(applicationContext, "Session set", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                finish()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF5AB9EA).copy(alpha = 0.15f),
                                                contentColor = Color(0xFF5AB9EA)
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5AB9EA)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp)
                                        ) {
                                            Text(
                                                text = "Start Custom ${customMinutes.toInt()} Min Session",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Button(
                                            onClick = {
                                                try {
                                                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                                        addCategory(Intent.CATEGORY_HOME)
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                    startActivity(homeIntent)
                                                } catch (e: Exception) {}
                                                finish()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFFF5252),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                        ) {
                                            Text(
                                                text = "Close App",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        interceptPkgState.value = intent.getStringExtra("INTERCEPTED_PACKAGE") ?: "com.instagram.android"
        isLimitBlockState.value = intent.getBooleanExtra("IS_LIMIT_BLOCK", false)
        isStrictModeInterceptState.value = intent.getBooleanExtra("IS_STRICT_MODE_INTERCEPT", false)
        resetTriggerState.value += 1
    }
}
