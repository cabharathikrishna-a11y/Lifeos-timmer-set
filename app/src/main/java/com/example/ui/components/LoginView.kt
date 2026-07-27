package com.example.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.AppViewModel
import com.example.util.NetworkChecker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginView(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val hasInternet = remember { NetworkChecker.isInternetAvailable(context) }
    
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var loggingIn by remember { mutableStateOf(false) }
    val requestDriveScope = false
    var testerCountdown by remember { mutableStateOf(-1) }
    
    val keyboardController = LocalSoftwareKeyboardController.current

    // Set up standard Google Sign In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val email = account?.email ?: ""
                val displayName = account?.displayName ?: ""
                val idToken = account?.idToken
                if (email.isNotEmpty()) {
                    loggingIn = true
                    val username = email.substringBefore("@").replace(".", "_")
                    viewModel.handleGoogleSignInSuccess(username, email, displayName, idToken)
                } else {
                    errorMsg = "No email address associated with this Google Account."
                }
            } catch (e: ApiException) {
                errorMsg = "Google Sign-In failed: Status code ${e.statusCode}. ${e.localizedMessage ?: e.message}"
            } catch (e: Exception) {
                errorMsg = "Google Sign-In failed: ${e.localizedMessage ?: e.message}"
            }
        } else {
            errorMsg = "Google sign-in was cancelled or failed (Result code: ${result.resultCode})."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF04060A),
                        Color(0xFF0C0E17),
                        Color(0xFF020305)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!hasInternet) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "No Connection",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Internet Connection",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please connect to the internet to access the centralized database.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
            return@Box
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(vertical = 32.dp, horizontal = 24.dp)
                .fillMaxWidth(0.95f)
                .verticalScroll(rememberScrollState())
        ) {
            // Elegant Visual Branding Header with Real App Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF38B0F2).copy(alpha = 0.5f),
                                Color(0xFF38B0F2).copy(alpha = 0.1f)
                            )
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val job = scope.launch {
                                    for (i in 1..15) {
                                        delay(1000)
                                        testerCountdown = i
                                        if (i == 5) {
                                            Toast.makeText(context, "Entering Tester Mode in 10s...", Toast.LENGTH_SHORT).show()
                                        } else if (i == 10) {
                                            Toast.makeText(context, "Entering Tester Mode in 5s...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    // Successfully held for 15s!
                                    Toast.makeText(context, "Tester Mode activated! 🕵️", Toast.LENGTH_LONG).show()
                                    viewModel.activateTesterMode()
                                }
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    job.cancel()
                                    testerCountdown = -1
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (testerCountdown != -1) {
                    Text(
                        text = (15 - testerCountdown).toString(),
                        color = Color(0xFF38B0F2),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "LIFE OS Logo",
                        modifier = Modifier
                            .size(64.dp)
                            .padding(4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "LIFE OS",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Your centralized digital environment.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Main Google Sign-In Button (Official design language)
                Card(
                    onClick = {
                        try {
                            errorMsg = null
                            val webClientId = try {
                                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                                if (resId != 0) context.getString(resId) else "432934819080-jn72ep6smqj8r4q46jj1gjqchepi91ue.apps.googleusercontent.com"
                            } catch (e: Exception) {
                                "432934819080-jn72ep6smqj8r4q46jj1gjqchepi91ue.apps.googleusercontent.com"
                            }

                            val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                            
                            if (requestDriveScope) {
                                val driveScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
                                gsoBuilder.requestScopes(driveScope)
                            }
                            
                            if (webClientId.isNotEmpty()) {
                                gsoBuilder.requestIdToken(webClientId)
                            }
                            val gso = gsoBuilder.build()
                            val googleSignInClient = GoogleSignIn.getClient(context, gso)
                            
                            // Sign out of previous sessions first to guarantee account selection chooser opens
                            googleSignInClient.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        } catch (e: Exception) {
                            errorMsg = "Google API not initialized: ${e.message}"
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Draw a beautiful vector Google "G" Logo matching official colors
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color(0xFF4285F4)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            color = Color(0xFF1F1F1F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1616)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Authentication Error",
                                color = Color(0xFFFF5252),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMsg!!,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            if (loggingIn) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF38B0F2),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
