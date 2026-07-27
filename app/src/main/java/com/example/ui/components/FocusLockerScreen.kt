package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.FocusLockerManager
import com.example.api.FocusLockerUiModel
import com.example.api.ParticipantInfo
import com.example.ui.AppViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusLockerScreen(viewModel: AppViewModel, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val email = viewModel.getActiveUserEmail()
    val roomState by FocusLockerManager.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var roomNameToCreate by remember { mutableStateOf("") }
    var roomIdToJoin by remember { mutableStateOf("") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    val isInRoom = roomState.roomId.isNotEmpty()

    // Screen container with elegant slate dark background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11))
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(48.dp)
                    .zIndex(10f)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }
        if (!isInRoom) {
            // Idle / Lobby Screen: Option to Create or Join Room
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFF3D00).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Study Groups",
                        tint = Color(0xFFFF3D00),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "THE STUDY GROUP",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Offline-first study groups. Form a group of users to track focus and study together, without locking your personal timer settings!",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Create Room Button
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF3D00)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create Study Room",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Join Room Button
                OutlinedButton(
                    onClick = { showJoinDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Group, contentDescription = "Join")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Join via Room ID",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Immersive, Collaborative Room Connection UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Room Name
                    Text(
                        text = roomState.roomName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Room ID copyable badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(roomState.roomId))
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Room ID: ${roomState.roomId}",
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy ID",
                                tint = Color.LightGray,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Active Live Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "FRIENDS FOCUS COLLABORATION ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981),
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Center visual: Connected Live Radar Sphere
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    // Glowing interactive/decorative sphere indicator
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.05f))
                            .border(2.dp, Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                .border(1.5.dp, Color(0xFF10B981).copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Active Sync",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Connected in Study Room",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your study room is linked to Friends Focus Details! All members of this room are now automatically visible in your Friends Focus dashboard, showing real-time focus timers, current tasks, and customizable tag indicators.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        lineHeight = 20.sp
                    )
                }

                // Bottom Content: Members list & Action Button
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Participant Row Label
                    Text(
                        text = "STUDY GROUP MEMBERS (${roomState.participants.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Horizontal Avatars list
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(roomState.participants) { peer ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(
                                            width = 1.5.dp,
                                            color = if (peer.email == roomState.hostEmail) Color(0xFFFFD700) else Color.White.copy(alpha = 0.2f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = peer.displayName.take(2).uppercase(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (peer.email == roomState.hostEmail) "${peer.displayName} 👑" else peer.displayName,
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(64.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Leave Room Button (Always visible to all)
                    OutlinedButton(
                        onClick = { showLeaveDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF5252)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Leave")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (roomState.isHost) "Disband & Exit Room" else "Leave Shared Room",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // LEAVE OPTIONS DIALOG
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = Color(0xFF1E1E24),
            icon = {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFFFF5252)
                )
            },
            title = {
                Text(
                    text = "Leave Focus Group",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose how you want to leave this Focus Group:",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "• Only Leave",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Leaves the focus group session. The group remains active on the network.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "• Leave and Discard",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Leaves and discards this focus group locally on this device. Note: This will NOT delete the focus group for other members.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        FocusLockerManager.leaveRoom(context, email, discardGroup = true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252)
                    )
                ) {
                    Text("Leave & Discard", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { showLeaveDialog = false }
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                    OutlinedButton(
                        onClick = {
                            showLeaveDialog = false
                            FocusLockerManager.leaveRoom(context, email, discardGroup = false)
                        }
                    ) {
                        Text("Only Leave", color = Color.White)
                    }
                }
            }
        )
    }

    // CREATE ROOM DIALOG
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF1E1E24),
            title = {
                Text(
                    text = "Create Shared Room",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = roomNameToCreate,
                        onValueChange = { roomNameToCreate = it },
                        label = { Text("Room Name") },
                        placeholder = { Text("e.g. September CA Inter Sprint") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF3D00),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = Color(0xFFFF3D00),
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = roomNameToCreate.ifBlank { "Study Sprint" }
                        FocusLockerManager.createRoom(
                            context = context,
                            myEmail = email,
                            roomName = finalName,
                            onSuccess = {
                                showCreateDialog = false
                                roomNameToCreate = ""
                            },
                            onFailure = {
                                Log.e("FocusLocker", "Failed to create room", it)
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00))
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // JOIN ROOM DIALOG
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = Color(0xFF1E1E24),
            title = {
                Text(
                    text = "Join Shared Room",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                OutlinedTextField(
                    value = roomIdToJoin,
                    onValueChange = { roomIdToJoin = it },
                    label = { Text("Room ID") },
                    placeholder = { Text("ROOM_...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF3D00),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedLabelColor = Color(0xFFFF3D00),
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (roomIdToJoin.isNotBlank()) {
                            FocusLockerManager.joinRoom(context, email, roomIdToJoin.trim())
                            showJoinDialog = false
                            roomIdToJoin = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00))
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}
