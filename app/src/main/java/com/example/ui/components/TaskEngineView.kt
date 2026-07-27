package com.example.ui.components

import com.example.util.MediaPreviewBox
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.PremiumEffects.bouncyClick
import com.example.ui.theme.PremiumEffects.glassmorphicCard
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.R
import android.content.Context
import com.example.data.CustomList
import com.example.data.Task
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import coil.compose.AsyncImage
import com.example.util.SleepTimeHelper
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskEngineView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val customLists by viewModel.customLists.collectAsStateWithLifecycle()
    val financeTransactions by viewModel.financeTransactions.collectAsStateWithLifecycle()
    val isSidebarOpen by viewModel.isLocalSidebarOpen.collectAsStateWithLifecycle()

    val pendingPayload by viewModel.pendingTaskCreationPayload.collectAsStateWithLifecycle()

    // Dialog / Editor states
    var showTaskEditorScreen by remember { mutableStateOf(false) }
    var editingTaskTarget by remember { mutableStateOf<Task?>(null) }

    val extSelectedTaskId by viewModel.selectedTaskId.collectAsStateWithLifecycle()
    LaunchedEffect(extSelectedTaskId) {
        extSelectedTaskId?.let { idVal ->
            val found = tasks.find { it.id == idVal }
            if (found != null) {
                editingTaskTarget = found
                showTaskEditorScreen = true
                viewModel.clearSelectedTaskId()
            }
        }
    }

    LaunchedEffect(pendingPayload) {
        if (pendingPayload != null) {
            editingTaskTarget = null
            showTaskEditorScreen = true
        }
    }
    var showAddListDialog by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<CustomList?>(null) }
    var listOptionMenuFor by remember { mutableStateOf<CustomList?>(null) }

    // Dropdown options state
    var showOptionsMenu by remember { mutableStateOf(false) }
    var hideCompletedTasks by remember { mutableStateOf(false) }
    var showTaskDetails by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf("All") } // "All", "Task", "Habits", "Overdue"

    androidx.activity.compose.BackHandler(enabled = showTaskEditorScreen || showTaskDetails) {
        if (showTaskEditorScreen) {
            showTaskEditorScreen = false
            editingTaskTarget = null
            viewModel.clearPendingTaskCreation()
        } else if (showTaskDetails) {
            showTaskDetails = false
        }
    }

    // Navigation and filtering
    val defaultTaskFolder by viewModel.defaultTaskFolder.collectAsStateWithLifecycle()
    var selectedList by remember(defaultTaskFolder) { mutableStateOf(defaultTaskFolder) }

    // Grouping & Sorting dynamic parameters (default Date to match user description)
    var groupByMode by remember { mutableStateOf("Date") }
    var sortByMode by remember { mutableStateOf("Date") }
    var showGroupSortSheet by remember { mutableStateOf(false) }
    var expandedGroups by remember { mutableStateOf(mapOf<String, Boolean>()) }

    // Selection mode parameters (select tasks bulk operations)
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf(setOf<Int>()) }
    var expandedTaskIds by remember { mutableStateOf(setOf<Int>()) }
    var draggedItemId by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    // Date calculations for smart lists
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayStr = sdf.format(Date())

    val next7DaysStrings = (0..6).map { i ->
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, i)
        sdf.format(c.time)
    }

    // Identify active custom list properties
    val activeCustomList = customLists.find { it.name.equals(selectedList, ignoreCase = true) }
    val activeListColor = activeCustomList?.let { parseColorHex(it.colorHex) } ?: WaterBlue

    // Raw matching tasks
    var currentFiltered = tasks.filter { task ->
        when (selectedList) {
            "All" -> true
            "Today" -> task.dueDateString == todayStr
            "Next 7 Days" -> task.dueDateString in next7DaysStrings
            else -> task.listCategory.equals(selectedList, ignoreCase = true)
        }
    }

    // Handle completed hiding option
    if (hideCompletedTasks) {
        currentFiltered = currentFiltered.filter { !it.isCompleted }
    }

    // Dynamic filter options based on list contents
    val hasTasksInList = currentFiltered.any { !it.title.contains("habit", ignoreCase = true) && !it.listCategory.equals("Habits", ignoreCase = true) }
    val hasHabitsInList = currentFiltered.any { it.title.contains("habit", ignoreCase = true) || it.listCategory.equals("Habits", ignoreCase = true) }
    val hasOverdueInList = currentFiltered.any { it.dueDateString.isNotEmpty() && it.dueDateString < todayStr && !it.isCompleted }

    // Filter tasks based on actual selection Mode
    val filteredList = when (filterMode) {
        "Task" -> currentFiltered.filter { !it.title.contains("habit", ignoreCase = true) && !it.listCategory.equals("Habits", ignoreCase = true) }
        "Habits" -> currentFiltered.filter { it.title.contains("habit", ignoreCase = true) || it.listCategory.equals("Habits", ignoreCase = true) }
        "Overdue" -> currentFiltered.filter { it.dueDateString.isNotEmpty() && it.dueDateString < todayStr && !it.isCompleted }
        else -> currentFiltered
    }

    // Sort logic helper based on sortByMode settings
    val sortedTasksLambda = remember(sortByMode) {
        { tList: List<Task> ->
            when (sortByMode) {
                "Date" -> {
                    tList.sortedWith(compareBy<Task> { it.isCompleted }
                        .thenBy { if (it.dueDateString.isEmpty()) "9999-12-31" else it.dueDateString }
                        .thenBy { it.title }
                    )
                }
                "Title" -> {
                    tList.sortedWith(compareBy<Task> { it.isCompleted }.thenBy { it.title.lowercase() })
                }
                "Priority" -> {
                    val priorityWeight = { p: String ->
                        when (p.uppercase()) {
                            "HIGH" -> 0
                            "MEDIUM" -> 1
                            "LOW" -> 2
                            else -> 3
                        }
                    }
                    tList.sortedWith(compareBy<Task> { it.isCompleted }
                        .thenBy { priorityWeight(it.priority) }
                        .thenBy { if (it.dueDateString.isEmpty()) "9999-12-31" else it.dueDateString }
                    )
                }
                "Tag" -> {
                    val getTag = { t: Task ->
                        val descHashtag = t.description.split(" ").find { it.startsWith("#") }
                        val titleHashtag = t.title.split(" ").find { it.startsWith("#") }
                        (descHashtag ?: titleHashtag ?: t.listCategory).lowercase()
                    }
                    tList.sortedWith(compareBy<Task> { it.isCompleted }.thenBy { getTag(it) }.thenBy { it.title })
                }
                "Custom" -> {
                    tList.sortedWith(compareBy<Task> { it.isCompleted }.thenBy { it.orderIndex }.thenBy { it.id })
                }
                else -> tList
            }
        }
    }

    // Main dynamic grouping logic to structure tasks under expandable/collapsible headers
    val groupedTasks = remember(filteredList, groupByMode, sortByMode, todayStr) {
        when (groupByMode) {
            "None" -> {
                listOf("" to sortedTasksLambda(filteredList))
            }
            "Date" -> {
                val overdue = mutableListOf<Task>()
                val habits = mutableListOf<Task>()
                val today = mutableListOf<Task>()
                val tomorrow = mutableListOf<Task>()
                val upcoming = mutableListOf<Task>()
                val noDate = mutableListOf<Task>()

                filteredList.forEach { t ->
                    val isHabit = t.title.contains("habit", ignoreCase = true) || t.listCategory.equals("Habits", ignoreCase = true)
                    val isOverdue = t.dueDateString.isNotEmpty() && t.dueDateString < todayStr && !t.isCompleted

                    if (isOverdue) {
                        overdue.add(t)
                    } else if (isHabit) {
                        habits.add(t)
                    } else if (t.dueDateString == todayStr) {
                        today.add(t)
                    } else if (t.dueDateString.isNotEmpty()) {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        val tomorrowStr = sdf.format(calendar.time)
                        if (t.dueDateString == tomorrowStr) {
                            tomorrow.add(t)
                        } else if (t.dueDateString > tomorrowStr) {
                            upcoming.add(t)
                        } else {
                            noDate.add(t)
                        }
                    } else {
                        noDate.add(t)
                    }
                }

                val list = mutableListOf<Pair<String, List<Task>>>()
                if (overdue.isNotEmpty()) list.add("Overdue" to sortedTasksLambda(overdue))
                if (habits.isNotEmpty()) list.add("Habit" to sortedTasksLambda(habits))
                if (today.isNotEmpty()) list.add("Today" to sortedTasksLambda(today))
                if (tomorrow.isNotEmpty()) list.add("Tomorrow" to sortedTasksLambda(tomorrow))
                if (upcoming.isNotEmpty()) list.add("Upcoming" to sortedTasksLambda(upcoming))
                if (noDate.isNotEmpty()) list.add("No Date" to sortedTasksLambda(noDate))
                list
            }
            "List" -> {
                val groupsMap = filteredList.groupBy { it.listCategory }
                groupsMap.map { (catName, tasks) ->
                    catName to sortedTasksLambda(tasks)
                }.sortedBy { it.first.lowercase() }
            }
            "Priority" -> {
                val high = mutableListOf<Task>()
                val med = mutableListOf<Task>()
                val low = mutableListOf<Task>()
                val none = mutableListOf<Task>()

                filteredList.forEach { t ->
                    when (t.priority.uppercase()) {
                        "HIGH" -> high.add(t)
                        "MEDIUM" -> med.add(t)
                        "LOW" -> low.add(t)
                        else -> none.add(t)
                    }
                }

                val list = mutableListOf<Pair<String, List<Task>>>()
                if (high.isNotEmpty()) list.add("High Priority" to sortedTasksLambda(high))
                if (med.isNotEmpty()) list.add("Medium Priority" to sortedTasksLambda(med))
                if (low.isNotEmpty()) list.add("Low Priority" to sortedTasksLambda(low))
                if (none.isNotEmpty()) list.add("No Priority" to sortedTasksLambda(none))
                list
            }
            "Tag" -> {
                val getPrimaryTag = { t: Task ->
                    val descHashtag = t.description.split(" ").find { it.startsWith("#") }
                    val titleHashtag = t.title.split(" ").find { it.startsWith("#") }
                    descHashtag ?: titleHashtag ?: t.listCategory
                }
                val groupsMap = filteredList.groupBy { getPrimaryTag(it) }
                groupsMap.map { (tagName, tasks) ->
                    tagName to sortedTasksLambda(tasks)
                }.sortedBy { it.first.lowercase() }
            }
            else -> {
                listOf("" to sortedTasksLambda(filteredList))
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val isWide = maxWidth >= 720.dp

        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (isWide && isSidebarOpen) {
                Card(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.setLocalSidebarOpen(false) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Close Sidebar",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Personal",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "SYSTEM VIEWS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )

                        val systemLists = listOf("All", "Inbox", "Today", "Next 7 Days")
                        systemLists.forEach { listName ->
                            val isSelected = selectedList.equals(listName, ignoreCase = true)
                            val icon = when (listName) {
                                "All" -> Icons.Default.List
                                "Inbox" -> Icons.Default.Email
                                "Today" -> Icons.Default.Star
                                else -> Icons.Default.DateRange
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) WaterBlue.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable { 
                                        selectedList = listName
                                        viewModel.updateDefaultTaskFolder(listName)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = if (isSelected) WaterBlue else Color.Gray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = listName,
                                    color = if (isSelected) WaterBlue else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                val uCount = tasks.count {
                                    !it.isCompleted && when (listName) {
                                        "All" -> true
                                        "Today" -> it.dueDateString == todayStr
                                        "Next 7 Days" -> it.dueDateString in next7DaysStrings
                                        else -> it.listCategory.equals(listName, ignoreCase = true)
                                    }
                                }
                                if (uCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color(0xFF232325))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "$uCount", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "CUSTOM LISTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )

                        val primaryCustomLists = customLists.filter { it.parentListName.isNullOrEmpty() }

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(primaryCustomLists) { parentList ->
                                CustomListSidebarItem(
                                    list = parentList,
                                    isSelected = selectedList.equals(parentList.name, ignoreCase = true),
                                    indent = 0,
                                    taskCount = tasks.count { !it.isCompleted && it.listCategory.equals(parentList.name, ignoreCase = true) },
                                    onSelect = { 
                                        selectedList = parentList.name 
                                        viewModel.updateDefaultTaskFolder(parentList.name)
                                    },
                                    onLongClick = { listOptionMenuFor = parentList }
                                )

                                val subLists = customLists.filter { it.parentListName.equals(parentList.name, ignoreCase = true) }
                                subLists.forEach { childList ->
                                    CustomListSidebarItem(
                                        list = childList,
                                        isSelected = selectedList.equals(childList.name, ignoreCase = true),
                                        indent = 16,
                                        isChild = true,
                                        taskCount = tasks.count { !it.isCompleted && it.listCategory.equals(childList.name, ignoreCase = true) },
                                        onSelect = { 
                                            selectedList = childList.name 
                                            viewModel.updateDefaultTaskFolder(childList.name)
                                        },
                                        onLongClick = { listOptionMenuFor = childList }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAddListDialog = true }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Add List",
                                color = WaterBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Right Main Task Panel
                Column(modifier = Modifier.fillMaxSize()) {
            // Task Header (Matching Screenshot Layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleLocalSidebar() },
                        modifier = Modifier.testTag("toggle_sidebar_header_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Toggle Sidebar",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = selectedList,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                 Row(verticalAlignment = Alignment.CenterVertically) {
                    val gTasksSyncStatus by viewModel.googleTasksSyncStatus.collectAsStateWithLifecycle()
                    val tasksAuthLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                            viewModel.syncGoogleTasks(context)
                        }
                    }

                    val googleSignInLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                            try {
                                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                                if (account != null) {
                                    val email = account.email ?: ""
                                    val displayName = account.displayName ?: ""
                                    val idToken = account.idToken
                                    val username = email.substringBefore("@")
                                    viewModel.handleGoogleSignInSuccess(username, email, displayName, idToken)
                                    
                                    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                    prefs.edit()
                                        .putString("selected_tasks_account", email)
                                        .putString("selected_file_backup_account", email)
                                        .apply()
                                    
                                    viewModel.syncGoogleTasks(context)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("TaskEngineView", "Google Sign-In failed", e)
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val googleAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                            if (googleAccount == null) {
                                try {
                                    val taskScope = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/tasks")
                                    val webClientId = try {
                                        context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName))
                                    } catch (e: Exception) {
                                        ""
                                    }
                                    val gsoBuilder = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .requestScopes(taskScope)
                                    if (webClientId.isNotEmpty()) {
                                        gsoBuilder.requestIdToken(webClientId)
                                    }
                                    val gso = gsoBuilder.build()
                                    val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Could not launch Google Sign-In automatically.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.syncGoogleTasks(context) { intent ->
                                    tasksAuthLauncher.launch(intent)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync Google Tasks",
                            tint = if (gTasksSyncStatus == "Syncing...") WaterBlue else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Show Options Menu",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Polished Dropdown Menu mimicking the screenshot's items
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        modifier = Modifier
                            .width(230.dp)
                            .background(Color(0xFF1E1E20), RoundedCornerShape(12.dp))
                            .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    ) {

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (showTaskDetails) Icons.Default.Info else Icons.Default.List,
                                        contentDescription = null,
                                        tint = if (showTaskDetails) WaterBlue else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (showTaskDetails) "Hide Details" else "Show Details",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            },
                            onClick = {
                                showTaskDetails = !showTaskDetails
                                showOptionsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (hideCompletedTasks) Icons.Default.CheckCircle else Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (hideCompletedTasks) WaterBlue else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (hideCompletedTasks) "Show Completed" else "Hide Completed",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            },
                            onClick = {
                                hideCompletedTasks = !hideCompletedTasks
                                showOptionsMenu = false
                            }
                        )
                        
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Group & Sort", color = Color.White, fontSize = 14.sp)
                                }
                            },
                            onClick = {
                                showOptionsMenu = false
                                showGroupSortSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Select", color = Color.White, fontSize = 14.sp)
                                }
                            },
                            onClick = {
                                showOptionsMenu = false
                                isSelectionMode = true
                                selectedTaskIds = emptySet()
                            }
                        )
                    }
                }
            }

            if (selectedList.equals("Finances", ignoreCase = true)) {
                FinancialTrackerTasksIntegration(viewModel = viewModel)
            } else {
                // Horizontally scrollable row of elegant filter pills matching the screenshot!
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All" Reset Filter Chip (Always shown 1st)
                val isAllSelected = filterMode == "All"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isAllSelected) Color(0xFF1D2C42) else Color(0xFF161618))
                        .clickable { filterMode = "All" }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "All",
                        color = if (isAllSelected) Color(0xFF5390F5) else Color(0xFF7B7B7F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // "Task" Filter Chip (Shown only if tasks there)
                if (hasTasksInList) {
                    val isTaskSelected = filterMode == "Task"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isTaskSelected) Color(0xFF1D2C42) else Color(0xFF161618))
                            .clickable { filterMode = if (isTaskSelected) "All" else "Task" }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Task",
                            color = if (isTaskSelected) Color(0xFF5390F5) else Color(0xFF7B7B7F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // "Habit" Filter Chip (Shown only if habits there)
                if (hasHabitsInList) {
                    val isHabitSelected = filterMode == "Habits"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isHabitSelected) Color(0xFF1D2C42) else Color(0xFF161618))
                            .clickable { filterMode = if (isHabitSelected) "All" else "Habits" }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Habit",
                            color = if (isHabitSelected) Color(0xFF5390F5) else Color(0xFF7B7B7F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // "Overdue" Filter Chip (Shown only if any overdue tasks)
                if (hasOverdueInList) {
                    val isOverdueSelected = filterMode == "Overdue"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isOverdueSelected) Color(0xFF1D2C42) else Color(0xFF161618))
                            .clickable { filterMode = if (isOverdueSelected) "All" else "Overdue" }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Overdue",
                            color = if (isOverdueSelected) Color(0xFF5390F5) else Color(0xFF7B7B7F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Task List (Rendered dynamically using grouped and sorted tasks)
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = Color.Gray.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No objectives found here!", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Create a new task scheduled for this list parameters.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupedTasks.forEach { (groupName, tasksInGroup) ->
                        if (groupByMode != "None" && groupName.isNotEmpty() && !groupName.equals("Today", ignoreCase = true)) {
                            item(key = "group_header_$groupName") {
                                Row(
                                    modifier = Modifier
                                        .animateItem()
                                        .padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF161618))
                                        .clickable {
                                            expandedGroups = expandedGroups.toMutableMap().apply {
                                                put(groupName, !(get(groupName) ?: true))
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = groupName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = Color.White
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${tasksInGroup.size}",
                                            color = Color.LightGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (groupName == "Overdue") {
                                        Text(
                                            text = "Postpone",
                                            color = Color(0xFF2E6FF3),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable {
                                                    tasksInGroup.forEach { t ->
                                                        viewModel.updateTask(t.copy(dueDateString = todayStr))
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        if (groupByMode == "None" || (expandedGroups[groupName] ?: true)) {
                            items(tasksInGroup, key = { it.id }) { task ->
                                val isSelected = selectedTaskIds.contains(task.id)
                                Row(
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .glassmorphicCard(
                                            shape = RoundedCornerShape(12.dp),
                                            borderWidth = 0.5.dp,
                                            borderColor = Color(0x16FFFFFF),
                                            backgroundColor = if (isSelected && isSelectionMode) Color(0xFF132239) else Color(0x7F111116)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedTaskIds = if (isSelected) {
                                                        selectedTaskIds - task.id
                                                    } else {
                                                        selectedTaskIds + task.id
                                                    }
                                                } else {
                                                    editingTaskTarget = task
                                                    showTaskEditorScreen = true
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) {
                                                    isSelectionMode = true
                                                    selectedTaskIds = setOf(task.id)
                                                }
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        if (isSelectionMode) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(end = 12.dp)
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = if (isSelected) Color(0xFF2E6FF3) else Color(0xFF4D4D54),
                                                        shape = CircleShape
                                                    )
                                                    .background(if (isSelected) Color(0xFF2E6FF3) else Color.Transparent),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            val isWontDoTask = task.description.contains("[WontDo]")
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .border(
                                                        width = 1.3.dp,
                                                        color = if (isWontDoTask) Color(0xFFF9325D) else if (task.isCompleted) (when (task.priority.uppercase()) { "HIGH" -> Color(0xFFF9325D).copy(alpha = 0.6f); "MEDIUM" -> Color(0xFFFFB300).copy(alpha = 0.6f); "LOW" -> Color(0xFF2E6FF3).copy(alpha = 0.6f); else -> Color(0xFF7B7B7F) }) else (when (task.priority.uppercase()) { "HIGH" -> Color(0xFFF9325D); "MEDIUM" -> Color(0xFFFFB300); "LOW" -> Color(0xFF2E6FF3); else -> Color(0xFF4D4D54) }),
                                                        shape = RoundedCornerShape(5.dp)
                                                    )
                                                    .background(if (isWontDoTask) Color(0xFFF9325D).copy(alpha = 0.15f) else if (task.isCompleted) (when (task.priority.uppercase()) { "HIGH" -> Color(0xFFF9325D); "MEDIUM" -> Color(0xFFFFB300); "LOW" -> Color(0xFF2E6FF3); else -> Color(0xFF4D4D54) }).copy(alpha = 0.15f) else Color.Transparent)
                                                    .bouncyClick { viewModel.toggleTaskCompletion(task) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isWontDoTask) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Won't Do",
                                                        tint = Color(0xFFF9325D),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                } else if (task.isCompleted) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = (when (task.priority.uppercase()) { "HIGH" -> Color(0xFFF9325D); "MEDIUM" -> Color(0xFFFFB300); "LOW" -> Color(0xFF2E6FF3); else -> Color(0xFF7B7B7F) }),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                        }

                                        Column {
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp,
                                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                                ),
                                                color = if (task.isCompleted) Color(0xFF5D5D62) else Color(0xFFE5E5EA),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            
                                            val cleanDesc = remember(task.description) {
                                                task.description
                                                    .replace(Regex("""\[Time: [^\]]+\]"""), "")
                                                    .replace(Regex("""\[Reminders: [^\]]+\]"""), "")
                                                    .replace(Regex("""\[Repeat: [^\]]+\]"""), "")
                                                    .replace(Regex("""\[Duration: [^\]]+\]"""), "")
                                                    .replace(Regex("""\[Attachment: [^\]]+\]"""), "")
                                                    .replace(Regex("""\[Location: [^\]]+\]"""), "")
                                                    .replace(Regex("""\[WontDo\]"""), "")
                                                    .trim()
                                            }

                                            if (showTaskDetails && cleanDesc.isNotEmpty()) {
                                                Text(
                                                    text = cleanDesc,
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }

                                            val parsedAttachment = remember(task.description) {
                                                val match = Regex("""\[Attachment: ([^\]]+)\]""").find(task.description)
                                                match?.groupValues?.get(1)?.trim()
                                            }

                                            if (showTaskDetails && !parsedAttachment.isNullOrEmpty() && parsedAttachment != "None") {
                                                val previewType = remember(parsedAttachment) {
                                                    val nameLower = parsedAttachment.lowercase()
                                                    if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".webp") || nameLower.contains("image") || nameLower.contains("photo") || nameLower.contains("drive.google.com") || nameLower.startsWith("http")) {
                                                        if (nameLower.contains(".mp4") || nameLower.contains("video") || nameLower.contains(".mov")) "video"
                                                        else if (nameLower.contains(".mp3") || nameLower.contains("audio") || nameLower.contains(".wav")) "audio"
                                                        else "image"
                                                    } else if (nameLower.endsWith(".mp4") || nameLower.endsWith(".mov") || nameLower.endsWith(".3gp") || nameLower.endsWith(".mkv")) {
                                                        "video"
                                                    } else if (nameLower.endsWith(".mp3") || nameLower.endsWith(".m4a") || nameLower.endsWith(".wav") || nameLower.endsWith(".aac")) {
                                                        "audio"
                                                    } else {
                                                        "others"
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                                ) {
                                                    MediaPreviewBox(
                                                        pathOrName = parsedAttachment,
                                                        type = previewType,
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                    )
                                                    Column {
                                                        Text(
                                                            text = parsedAttachment,
                                                            fontSize = 11.sp,
                                                            color = Color.LightGray,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "Task Attachment",
                                                            fontSize = 9.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                            }

                                            // Metadata Row: Due Date, Start Time, and Reminder Alarms
                                            val parsedTime = remember(task.description) {
                                                val match = Regex("""\[Time: ([^\]]+)\]""").find(task.description)
                                                match?.groupValues?.get(1)?.trim()
                                            }
                                            val parsedReminders = remember(task.description) {
                                                val match = Regex("""\[Reminders: ([^\]]+)\]""").find(task.description)
                                                val listStr = match?.groupValues?.get(1) ?: "None"
                                                if (listStr == "None") emptyList() else listStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                            }
                                            val parsedRepeat = remember(task.description) {
                                                val match = Regex("""\[Repeat: ([^\]]+)\]""").find(task.description)
                                                val value = match?.groupValues?.get(1)?.trim()
                                                if (value.isNullOrEmpty() || value == "None") null else value
                                            }

                                            if (task.dueDateString.isNotEmpty() || !parsedTime.isNullOrEmpty() || parsedReminders.isNotEmpty() || parsedRepeat != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    // 1. Due Date
                                                    if (task.dueDateString.isNotEmpty()) {
                                                        val isOverdue = task.dueDateString < todayStr && !task.isCompleted
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Event,
                                                                contentDescription = "Due Date",
                                                                tint = if (isOverdue) Color(0xFFF9325D) else Color(0xFF7B7B7F),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = formatDueDate(task.dueDateString),
                                                                color = if (isOverdue) Color(0xFFF9325D) else Color(0xFF7B7B7F),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }

                                                    // 2. Start Time
                                                    if (!parsedTime.isNullOrEmpty()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.AccessTime,
                                                                contentDescription = "Start Time",
                                                                tint = Color(0xFF2E6FF3),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = parsedTime,
                                                                color = Color(0xFF2E6FF3),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }

                                                    // 3. Alarm Symbol for Reminder
                                                    if (parsedReminders.isNotEmpty()) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.NotificationsActive,
                                                                contentDescription = "Reminder Set",
                                                                tint = Color(0xFFFFB300),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = "Alarm (${parsedReminders.size})",
                                                                color = Color(0xFFFFB300),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }

                                                    // 4. Repeat Symbol for recurring tasks
                                                    if (parsedRepeat != null) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Refresh,
                                                                contentDescription = "Recurring Task",
                                                                tint = Color(0xFF00E676),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = parsedRepeat,
                                                                color = Color(0xFF00E676),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            val combinedText = task.title + " " + task.description
                                            val hashTags = remember(combinedText) {
                                                Regex("""#\w+""").findAll(combinedText).map { it.value }.distinct().toList()
                                            }
                                            val contactTags = remember(combinedText) {
                                                Regex("""@\w+""").findAll(combinedText).map { it.value }.distinct().toList()
                                            }
                                            
                                            CompletionPillsRow(task, viewModel)
                                            
                                            if (hashTags.isNotEmpty() || contactTags.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())
                                                ) {
                                                    contactTags.forEach { contact ->
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF2E4057))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(text = contact, color = WaterBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    hashTags.forEach { tag ->
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF1D2C42))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(text = tag, color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
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
                }
                } // closing brace of else block
            }
        }

        // Circular blue Accent Button matching user's image Bottom Right corner
        if (!isSelectionMode) {
            FloatingActionButton(
                onClick = {
                    editingTaskTarget = null
                    showTaskEditorScreen = true
                },
                containerColor = Color(0xFF2E6FF3),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 4.dp)
                    .size(52.dp)
                    .testTag("add_task_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Task",
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            // Elegant selection action controls bar overlayed at the bottom!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161618)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (selectedTaskIds.size == filteredList.size) {
                                selectedTaskIds = emptySet()
                            } else {
                                selectedTaskIds = filteredList.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = if (selectedTaskIds.size == filteredList.size) Icons.Default.CheckCircle else Icons.Default.Check,
                                contentDescription = "Select All",
                                tint = if (selectedTaskIds.size == filteredList.size) Color(0xFF2E6FF3) else Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${selectedTaskIds.size} Selected",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Mark as Complete
                        IconButton(onClick = {
                            val targetTasks = filteredList.filter { selectedTaskIds.contains(it.id) }
                            targetTasks.forEach { t ->
                                viewModel.toggleTaskCompletion(t)
                            }
                            isSelectionMode = false
                            selectedTaskIds = emptySet()
                        }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Toggle Selected Completed", tint = Color.LightGray)
                        }

                        // Bulk Delete action
                        IconButton(onClick = {
                            val targetTasks = filteredList.filter { selectedTaskIds.contains(it.id) }
                            targetTasks.forEach { t ->
                                viewModel.deleteTask(t)
                            }
                            isSelectionMode = false
                            selectedTaskIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color(0xFFF9325D))
                        }

                        // Close selection mode
                        Button(
                            onClick = {
                                isSelectionMode = false
                                selectedTaskIds = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF3)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Done", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
            }
        }

        if (!isWide) {
            // Scrim background when sidebar is open to dismiss on clicking outside
            androidx.compose.animation.AnimatedVisibility(
                visible = isSidebarOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.setLocalSidebarOpen(false)
                    }
            )
        }

        // Left Sidebar overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = isSidebarOpen,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Card(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.setLocalSidebarOpen(false) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Close Sidebar",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Personal",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "SYSTEM VIEWS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                    )

                    val systemLists = listOf("All", "Inbox", "Today", "Next 7 Days")
                    systemLists.forEach { listName ->
                        val isSelected = selectedList.equals(listName, ignoreCase = true)
                        val icon = when (listName) {
                            "All" -> Icons.Default.List
                            "Inbox" -> Icons.Default.Email
                            "Today" -> Icons.Default.Star
                            else -> Icons.Default.DateRange
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) WaterBlue.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { selectedList = listName }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = if (isSelected) WaterBlue else Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = listName,
                                color = if (isSelected) WaterBlue else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            val uCount = tasks.count {
                                !it.isCompleted && when (listName) {
                                    "All" -> true
                                    "Today" -> it.dueDateString == todayStr
                                    "Next 7 Days" -> it.dueDateString in next7DaysStrings
                                    else -> it.listCategory.equals(listName, ignoreCase = true)
                                }
                            }
                            if (uCount > 0) {
                                Badge(containerColor = Color.DarkGray, contentColor = Color.White) {
                                    Text("$uCount", fontSize = 9.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "CUSTOM LISTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                    )

                    val primaryCustomLists = customLists.filter { it.parentListName.isNullOrEmpty() }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(primaryCustomLists) { parentList ->
                            CustomListSidebarItem(
                                list = parentList,
                                isSelected = selectedList.equals(parentList.name, ignoreCase = true),
                                indent = 0,
                                taskCount = tasks.count { !it.isCompleted && it.listCategory.equals(parentList.name, ignoreCase = true) },
                                onSelect = { 
                                    selectedList = parentList.name 
                                    viewModel.updateDefaultTaskFolder(parentList.name)
                                },
                                onLongClick = { listOptionMenuFor = parentList }
                            )

                            val subLists = customLists.filter { it.parentListName.equals(parentList.name, ignoreCase = true) }
                            subLists.forEach { childList ->
                                CustomListSidebarItem(
                                    list = childList,
                                    isSelected = selectedList.equals(childList.name, ignoreCase = true),
                                    indent = 16,
                                    isChild = true,
                                    taskCount = tasks.count { !it.isCompleted && it.listCategory.equals(childList.name, ignoreCase = true) },
                                    onSelect = { 
                                        selectedList = childList.name 
                                        viewModel.updateDefaultTaskFolder(childList.name)
                                    },
                                    onLongClick = { listOptionMenuFor = childList }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAddListDialog = true }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Add List",
                            color = WaterBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
    }

    // Long press dropdown option trigger
    if (listOptionMenuFor != null) {
        val list = listOptionMenuFor!!
        AlertDialog(
            onDismissRequest = { listOptionMenuFor = null },
            title = { Text("List Options: ${list.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Text("Select an operation to modify list. Deleting this list will automatically move active tasks into 'Inbox'.", color = Color.LightGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        listToEdit = list
                        listOptionMenuFor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Edit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        viewModel.deleteListAndMoveTasksToInbox(list.name)
                        if (selectedList.equals(list.name, ignoreCase = true)) {
                            selectedList = "Inbox"
                        }
                        listOptionMenuFor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Formatted Add List Dialog
    if (showAddListDialog) {
        var listName by remember { mutableStateOf("") }
        var listColorHex by remember { mutableStateOf("#2196F3") }
        var viewTypeSelection by remember { mutableStateOf("List") }
        var isSubListSelected by remember { mutableStateOf(false) }
        var parentListChoice by remember { mutableStateOf<String?>(null) }
        var moreSettingsExpanded by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showAddListDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F0F0F)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { showAddListDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                        Text("Add List", color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            if (listName.trim().isNotEmpty()) {
                                val finalParent = if (isSubListSelected) parentListChoice else null
                                viewModel.createList(
                                    name = listName.trim(),
                                    colorHex = listColorHex,
                                    viewType = viewTypeSelection,
                                    parentListName = finalParent
                                )
                                selectedList = listName.trim()
                                showAddListDialog = false
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = WaterBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, tint = Color.Gray)
                            Spacer(modifier = Modifier.width(10.dp))
                            TextField(
                                value = listName,
                                onValueChange = { listName = it },
                                placeholder = { Text("Name", color = Color.Gray) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("List Color", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            val colorsPalette = listOf("#7F7F7F", "#FF5252", "#FF9800", "#FFEB3B", "#CDDC39", "#4CAF50", "#00BCD4", "#9C27B0")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                colorsPalette.forEach { hex ->
                                    val isSelected = listColorHex == hex
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (hex == "#9C27B0") {
                                                    Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Magenta, Color.Red))
                                                } else {
                                                    SolidColor(Color(android.graphics.Color.parseColor(hex)))
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) Color.White else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { listColorHex = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (hex == "#7F7F7F") {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawLine(Color.Red, Offset(6f, 6f), Offset(size.width - 6f, size.height - 6f), strokeWidth = 3f)
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { moreSettingsExpanded = !moreSettingsExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(if (moreSettingsExpanded) "Less ⌃" else "More ⌄", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    AnimatedVisibility(visible = moreSettingsExpanded) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isSubListSelected, onCheckedChange = { isSubListSelected = it }, colors = CheckboxDefaults.colors(checkedColor = WaterBlue))
                                    Text("Make this a sub-list", color = Color.LightGray, fontSize = 13.sp)
                                }
                                if (isSubListSelected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Select Primary List node:", color = Color.Gray, fontSize = 12.sp)

                                    val listOptions = customLists.filter { it.parentListName.isNullOrEmpty() && !it.name.equals(listName, ignoreCase = true) }
                                    var dropdownExp by remember { mutableStateOf(false) }

                                    Box(modifier = Modifier.padding(top = 4.dp)) {
                                        Button(onClick = { dropdownExp = true }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard)) {
                                            Text(parentListChoice ?: "Select Parent List Node", color = Color.White)
                                        }
                                        DropdownMenu(expanded = dropdownExp, onDismissRequest = { dropdownExp = false }, modifier = Modifier.background(Charcoal)) {
                                            listOptions.forEach { opt ->
                                                DropdownMenuItem(text = { Text(opt.name, color = Color.White) }, onClick = {
                                                    parentListChoice = opt.name
                                                    dropdownExp = false
                                                })
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
    }

    // Formatted Edit List Dialog
    if (listToEdit != null) {
        val originalList = listToEdit!!
        var listName by remember { mutableStateOf(originalList.name) }
        var listColorHex by remember { mutableStateOf(originalList.colorHex) }
        var viewTypeSelection by remember { mutableStateOf(originalList.viewType) }
        var isSubListSelected by remember { mutableStateOf(!originalList.parentListName.isNullOrEmpty()) }
        var parentListChoice by remember { mutableStateOf(originalList.parentListName) }
        var moreSettingsExpanded by remember { mutableStateOf(true) }

        Dialog(onDismissRequest = { listToEdit = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F0F0F)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { listToEdit = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                        Text("Edit List", color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            if (listName.trim().isNotEmpty()) {
                                val finalParent = if (isSubListSelected) parentListChoice else null
                                viewModel.renameListAndTasks(
                                    oldList = originalList,
                                    newList = originalList.copy(
                                        name = listName.trim(),
                                        colorHex = listColorHex,
                                        viewType = viewTypeSelection,
                                        parentListName = finalParent
                                    )
                                )
                                if (selectedList.equals(originalList.name, ignoreCase = true)) {
                                    selectedList = listName.trim()
                                }
                                listToEdit = null
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = WaterBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, tint = Color.Gray)
                            Spacer(modifier = Modifier.width(10.dp))
                            TextField(
                                value = listName,
                                onValueChange = { listName = it },
                                placeholder = { Text("Name", color = Color.Gray) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("List Color", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            val colorsPalette = listOf("#7F7F7F", "#FF5252", "#FF9800", "#FFEB3B", "#CDDC39", "#4CAF50", "#00BCD4", "#9C27B0")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                colorsPalette.forEach { hex ->
                                    val isSelected = listColorHex == hex
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (hex == "#9C27B0") {
                                                    Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Magenta, Color.Red))
                                                } else {
                                                    SolidColor(Color(android.graphics.Color.parseColor(hex)))
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) Color.White else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { listColorHex = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (hex == "#7F7F7F") {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawLine(Color.Red, Offset(6f, 6f), Offset(size.width - 6f, size.height - 6f), strokeWidth = 3f)
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { moreSettingsExpanded = !moreSettingsExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(if (moreSettingsExpanded) "Less ⌃" else "More ⌄", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    AnimatedVisibility(visible = moreSettingsExpanded) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isSubListSelected, onCheckedChange = { isSubListSelected = it }, colors = CheckboxDefaults.colors(checkedColor = WaterBlue))
                                    Text("Make this a sub-list", color = Color.LightGray, fontSize = 13.sp)
                                }
                                if (isSubListSelected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Select Primary List node:", color = Color.Gray, fontSize = 12.sp)

                                    val listOptions = customLists.filter { it.parentListName.isNullOrEmpty() && !it.name.equals(listName, ignoreCase = true) }
                                    var dropdownExp by remember { mutableStateOf(false) }

                                    Box(modifier = Modifier.padding(top = 4.dp)) {
                                        Button(onClick = { dropdownExp = true }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard)) {
                                            Text(parentListChoice ?: "Select Parent List Node", color = Color.White)
                                        }
                                        DropdownMenu(expanded = dropdownExp, onDismissRequest = { dropdownExp = false }, modifier = Modifier.background(Charcoal)) {
                                            listOptions.forEach { opt ->
                                                DropdownMenuItem(text = { Text(opt.name, color = Color.White) }, onClick = {
                                                    parentListChoice = opt.name
                                                    dropdownExp = false
                                                })
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
    }

    // Trigger Beautiful full-screen task editor matching user's screenshots!
    if (showTaskEditorScreen) {
        TaskEditorFullScreen(
            task = editingTaskTarget,
            allLists = customLists,
            currentList = selectedList,
            viewModel = viewModel,
            onDismiss = {
                showTaskEditorScreen = false
                viewModel.clearPendingTaskCreation()
            },
            onSave = { t, desc, p, d, cat, pId ->
                if (t.isNotEmpty()) {
                    val finalCat = if (cat == "All" || cat == "Today" || cat == "Next 7 Days") "Inbox" else cat
                    val isWontDo = desc.contains("[WontDo]")
                    if (editingTaskTarget == null) {
                        viewModel.createTask(
                            title = t,
                            description = desc,
                            estMin = 25,
                            category = finalCat,
                            priority = p,
                            dueDateString = d,
                            parentId = pId,
                            isCompleted = isWontDo
                        )
                    } else {
                        viewModel.updateTask(
                            editingTaskTarget!!.copy(
                                title = t,
                                description = desc,
                                priority = p,
                                dueDateString = d,
                                listCategory = finalCat,
                                parentTaskId = pId,
                                isCompleted = if (isWontDo) true else (if (editingTaskTarget!!.description.contains("[WontDo]") && !isWontDo) false else editingTaskTarget!!.isCompleted)
                            )
                        )
                    }
                }
                showTaskEditorScreen = false
                viewModel.clearPendingTaskCreation()
            },
            onDelete = {
                editingTaskTarget?.let { viewModel.deleteTask(it) }
                showTaskEditorScreen = false
                viewModel.clearPendingTaskCreation()
            },
            pendingPayload = pendingPayload
        )
    }

    // Elegant modal bottom dialog/sheet matching Screenshot 2 precisely
    if (showGroupSortSheet) {
        Dialog(
            onDismissRequest = { showGroupSortSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showGroupSortSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clickable(enabled = false, onClick = {}) // stop click propagation
                        .background(Color.Transparent),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Drag indicator handle at top-center of sheet
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.2.dp))
                                .background(Color.Gray.copy(alpha = 0.4f))
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // "Group by" header
                        Text(
                            text = "Group by",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("List", "Date", "Priority").forEach { option ->
                                val isSelected = groupByMode == option
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) Color(0xFF2E6FF3) else Color(0xFF161618))
                                        .clickable { groupByMode = option }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // "Sort by" header
                        Text(
                            text = "Sort by",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Date", "Title", "Priority").forEach { option ->
                                val isSelected = sortByMode == option
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) Color(0xFF2E6FF3) else Color(0xFF161618))
                                        .clickable { sortByMode = option }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomListSidebarItem(
    list: CustomList,
    isSelected: Boolean,
    indent: Int,
    isChild: Boolean = false,
    taskCount: Int,
    onSelect: () -> Unit,
    onLongClick: () -> Unit
) {
    val listColor = parseColorHex(list.colorHex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) listColor.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isChild) {
            Text("↳", color = listColor.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
        }

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(listColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = list.name,
            color = if (isSelected) listColor else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        if (taskCount > 0) {
            Badge(containerColor = Color.DarkGray, contentColor = Color.White) {
                Text("$taskCount", fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun KanbanColumn(
    title: String,
    tasks: List<Task>,
    headerColor: Color,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Badge(containerColor = headerColor, contentColor = Color.Black) {
                    Text("${tasks.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tasks) { task ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { viewModel.toggleTaskCompletion(task) },
                                    colors = CheckboxDefaults.colors(checkedColor = (when (task.priority.uppercase()) { "HIGH" -> Color(0xFFF9325D); "MEDIUM" -> Color(0xFFFFB300); "LOW" -> Color(0xFF2E6FF3); else -> headerColor }), uncheckedColor = (when (task.priority.uppercase()) { "HIGH" -> Color(0xFFF9325D); "MEDIUM" -> Color(0xFFFFB300); "LOW" -> Color(0xFF2E6FF3); else -> Color.Gray }))
                                )
                                Text(
                                    text = task.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (task.isCompleted) Color.Gray else Color.White,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            val cleanDesc = remember(task.description) {
                                task.description
                                    .replace(Regex("""\[Time: [^\]]+\]"""), "")
                                    .replace(Regex("""\[Reminders: [^\]]+\]"""), "")
                                    .replace(Regex("""\[Repeat: [^\]]+\]"""), "")
                                    .replace(Regex("""\[Duration: [^\]]+\]"""), "")
                                    .replace(Regex("""\[Attachment: [^\]]+\]"""), "")
                                    .replace(Regex("""\[Location: [^\]]+\]"""), "")
                                    .replace(Regex("""\[WontDo\]"""), "")
                                    .trim()
                            }

                            if (cleanDesc.isNotEmpty()) {
                                Text(
                                    text = cleanDesc,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(start = 32.dp, top = 2.dp)
                                )
                            }

                            val parsedAttachment = remember(task.description) {
                                val match = Regex("""\[Attachment: ([^\]]+)\]""").find(task.description)
                                match?.groupValues?.get(1)?.trim()
                            }
                            if (!parsedAttachment.isNullOrEmpty() && parsedAttachment != "None") {
                                val previewType = remember(parsedAttachment) {
                                    val nameLower = parsedAttachment.lowercase()
                                    if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".webp") || nameLower.contains("image") || nameLower.contains("photo") || nameLower.contains("drive.google.com") || nameLower.startsWith("http")) {
                                        if (nameLower.contains(".mp4") || nameLower.contains("video") || nameLower.contains(".mov")) "video"
                                        else if (nameLower.contains(".mp3") || nameLower.contains("audio") || nameLower.contains(".wav")) "audio"
                                        else "image"
                                    } else if (nameLower.endsWith(".mp4") || nameLower.endsWith(".mov") || nameLower.endsWith(".3gp") || nameLower.endsWith(".mkv")) {
                                        "video"
                                    } else if (nameLower.endsWith(".mp3") || nameLower.endsWith(".m4a") || nameLower.endsWith(".wav") || nameLower.endsWith(".aac")) {
                                        "audio"
                                    } else {
                                        "others"
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    MediaPreviewBox(
                                        pathOrName = parsedAttachment,
                                        type = previewType,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Column {
                                        Text(
                                            text = parsedAttachment,
                                            fontSize = 10.sp,
                                            color = Color.LightGray,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Metadata Row: Due Date, Start Time, and Reminder Alarms
                            val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
                            val parsedRepeat = remember(task.description) {
                                val match = Regex("""\[Repeat: ([^\]]+)\]""").find(task.description)
                                val value = match?.groupValues?.get(1)?.trim()
                                if (value.isNullOrEmpty() || value == "None") null else value
                            }
                            val parsedTime = remember(task.description) {
                                val match = Regex("""\[Time: ([^\]]+)\]""").find(task.description)
                                match?.groupValues?.get(1)?.trim()
                            }
                            val parsedReminders = remember(task.description) {
                                val match = Regex("""\[Reminders: ([^\]]+)\]""").find(task.description)
                                val listStr = match?.groupValues?.get(1) ?: "None"
                                if (listStr == "None") emptyList() else listStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            }

                            if (task.dueDateString.isNotEmpty() || !parsedTime.isNullOrEmpty() || parsedReminders.isNotEmpty() || parsedRepeat != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 32.dp, top = 4.dp)
                                ) {
                                    // 1. Due Date
                                    if (task.dueDateString.isNotEmpty()) {
                                        val isOverdue = task.dueDateString < todayStr && !task.isCompleted
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Event,
                                                contentDescription = "Due Date",
                                                tint = if (isOverdue) Color(0xFFF9325D) else Color(0xFF7B7B7F),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = formatDueDate(task.dueDateString),
                                                color = if (isOverdue) Color(0xFFF9325D) else Color(0xFF7B7B7F),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // 2. Start Time
                                    if (!parsedTime.isNullOrEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = "Start Time",
                                                tint = Color(0xFF2E6FF3),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = parsedTime,
                                                color = Color(0xFF2E6FF3),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // 3. Alarm Symbol for Reminder
                                    if (parsedReminders.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = "Reminder Set",
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "Alarm (${parsedReminders.size})",
                                                color = Color(0xFFFFB300),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // 4. Repeat Symbol for recurring tasks
                                    if (parsedRepeat != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Recurring Task",
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = parsedRepeat,
                                                color = Color(0xFF00E676),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        
                        val combinedText = task.title + " " + task.description
                        val hashTags = remember(combinedText) {
                            Regex("""#\w+""").findAll(combinedText).map { it.value }.distinct().toList()
                        }
                        val contactTags = remember(combinedText) {
                            Regex("""@\w+""").findAll(combinedText).map { it.value }.distinct().toList()
                        }
                        
                        if (hashTags.isNotEmpty() || contactTags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 32.dp).fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())
                            ) {
                                contactTags.forEach { contact ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2E4057))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = contact, color = WaterBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                hashTags.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF1D2C42))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = tag, color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
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
}

@Composable
fun ViewTypeCardItem(
    title: String,
    isCrown: Boolean = false,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) WaterBlue else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SurfaceCard else Color.Black.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Charcoal),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (isSelected) WaterBlue else Color.Gray, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (isSelected) WaterBlue else Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (isCrown) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("👑", fontSize = 10.sp)
                }
            }
        }
    }
}

fun parseColorHex(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        WaterBlue
    }
}

fun formatDueDate(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("d MMM", Locale.getDefault())
        val date = inputFormat.parse(dateStr)
        date?.let { outputFormat.format(it) } ?: dateStr
    } catch (e: Exception) {
        dateStr
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "attachment_file"
}

private fun copyUriToInternalStorage(context: android.content.Context, uri: Uri, fileName: String): java.io.File? {
    return com.example.util.StorageHelper.copyFileToInternalSandbox(context, uri)
}

@Composable
fun CompletionPillsRow(
    task: com.example.data.Task,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val allUsersState by viewModel.allUsers.collectAsStateWithLifecycle()
    val foldersState by viewModel.contactFolders.collectAsStateWithLifecycle()
    val contactsState by viewModel.contacts.collectAsStateWithLifecycle()
    val completionsState by viewModel.sharedTaskCompletions.collectAsStateWithLifecycle()
    
    val resolvedTags = remember(task.title, task.description, allUsersState, foldersState, contactsState) {
        com.example.util.MentionParser.resolveMentions(
            text = task.title + " " + task.description,
            allUsers = allUsersState,
            contactFolders = foldersState,
            contacts = contactsState
        )
    }
    
    val myNickname = remember { viewModel.getMyNickname() }
    val allMembers = remember(resolvedTags, myNickname) {
        (listOf(myNickname) + resolvedTags.toList()).distinct()
    }
    
    val taskCompletions = completionsState[task.id] ?: emptyMap()
    
    val completedTimeFormatted = remember(task.description, task.isCompleted) {
        val compMatch = Regex("""\[CompletedAt:\s*(\d+)\]""", RegexOption.IGNORE_CASE).find(task.description)
        val ts = compMatch?.groupValues?.get(1)?.toLongOrNull()
        if (ts != null) {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
            sdf.format(java.util.Date(ts))
        } else if (task.isCompleted) {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
            sdf.format(java.util.Date())
        } else null
    }
    
    if (resolvedTags.isNotEmpty() || completedTimeFormatted != null) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier.padding(top = 4.dp, bottom = 4.dp)
        ) {
            if (completedTimeFormatted != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF00E676).copy(alpha = 0.15f))
                        .border(width = 0.5.dp, color = Color(0xFF00E676).copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed Time",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Completed: $completedTimeFormatted",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                }
            }
            if (resolvedTags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    allMembers.forEach { member ->
                        val isCompleted = taskCompletions[member] ?: if (member == myNickname) task.isCompleted else false
                        
                        val peer = allUsersState.values.find { 
                            it.userId.substringBefore("@").replace(".", "_").lowercase() == member ||
                            it.displayName.lowercase().replace(" ", "") == member
                        }
                        val initials = if (member.length >= 2) member.take(2).uppercase() else member.uppercase()
                        val avatarText = peer?.customEmoji ?: initials
                        
                        val contentColor = if (isCompleted) Color(0xFF00E676) else Color.White
                        val backgroundColor = if (isCompleted) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent
                        val borderColor = if (isCompleted) Color(0xFF00E676) else Color.Gray.copy(alpha = 0.4f)
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(backgroundColor)
                                .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = avatarText,
                                    fontSize = 10.sp,
                                    color = if (isCompleted) Color(0xFF00E676) else Color.LightGray
                                )
                                Text(
                                    text = "@$member",
                                    fontSize = 9.sp,
                                    color = if (isCompleted) Color(0xFF00E676) else Color.Gray,
                                    fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Completed",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(10.dp)
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

@Composable
fun MentionAutocompleteBox(
    query: String,
    allUsers: Map<String, com.example.api.PeerLiveState>,
    folders: List<String>,
    contacts: List<com.example.data.Contact>,
    myNickname: String = "",
    onSelect: (String) -> Unit
) {
    val filteredGroups = remember(query, folders) {
        folders.filter { it != "All" }
            .map { it.replace(" ", "") }
            .filter { it.lowercase().startsWith(query.lowercase()) }
    }
    val filteredPeers = remember(query, allUsers, contacts, myNickname) {
        val list = mutableListOf<Pair<String, Pair<String, String?>>>()
        val myNickClean = myNickname.trim().lowercase().replace(" ", "")

        if (myNickClean.isNotEmpty()) {
            if ("me".startsWith(query.lowercase()) || myNickClean.startsWith(query.lowercase())) {
                list.add(myNickClean to ("Me ($myNickClean)" to "👤"))
            }
        }

        allUsers.values.forEach { peer ->
            val nick = peer.displayName.lowercase().replace(" ", "")
            val emailNick = peer.userId.substringBefore("@").replace(".", "_").lowercase()
            if (nick.startsWith(query.lowercase()) || emailNick.startsWith(query.lowercase())) {
                list.add(emailNick to (peer.displayName to peer.customEmoji))
            }
        }
        contacts.forEach { contact ->
            val nick = if (contact.email.isNotEmpty()) {
                contact.email.substringBefore("@").replace(".", "_").lowercase()
            } else {
                contact.firstName.lowercase()
            }
            if (nick.isNotEmpty() && nick.startsWith(query.lowercase())) {
                val disp = "${contact.firstName} ${contact.lastName}".trim()
                list.add(nick to (disp to null))
            }
        }
        list.distinctBy { it.first }
    }

    if (filteredGroups.isNotEmpty() || filteredPeers.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .heightIn(max = 200.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E20)),
            border = BorderStroke(1.dp, Color(0xFF2C2C2E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                if (filteredGroups.isNotEmpty()) {
                    item {
                        Text(
                            text = "👥 Group Suggestions",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    items(filteredGroups) { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(group) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "@$group", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
                if (filteredPeers.isNotEmpty()) {
                    item {
                        Text(
                            text = "👤 Peer Matches",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    items(filteredPeers) { (nick, pair) ->
                        val (dispName, customEmoji) = pair
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(nick) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "@$nick", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val emojiOrUrl = customEmoji
                                if (!emojiOrUrl.isNullOrEmpty()) {
                                    if (emojiOrUrl.startsWith("http://") || emojiOrUrl.startsWith("https://") || emojiOrUrl.startsWith("data:")) {
                                        coil.compose.AsyncImage(
                                            model = emojiOrUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier.size(18.dp).clip(CircleShape)
                                        )
                                    } else {
                                        Text(text = emojiOrUrl, fontSize = 12.sp)
                                    }
                                } else {
                                    Text(text = "👤", fontSize = 12.sp)
                                }
                                Text(text = dispName, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskEditorFullScreen(
    task: Task?, // null if adding new task
    allLists: List<CustomList>,
    currentList: String,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, priority: String, dueDate: String, category: String, parentTaskId: Int?) -> Unit,
    onDelete: () -> Unit,
    pendingPayload: com.example.ui.PendingTaskPayload? = null
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var priority by remember { mutableStateOf(task?.priority ?: "MEDIUM") }
    var taskDueDate by remember { mutableStateOf(task?.dueDateString ?: (pendingPayload?.dateString ?: "")) }
    var targetCategory by remember { mutableStateOf(task?.listCategory ?: (pendingPayload?.category ?: (if (currentList == "All" || currentList == "Today" || currentList == "Next 7 Days") "Inbox" else currentList))) }

    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val allUsersState by viewModel.allUsers.collectAsStateWithLifecycle()
    val contactsState by viewModel.contacts.collectAsStateWithLifecycle()
    val foldersState by viewModel.contactFolders.collectAsStateWithLifecycle()
    val onFilePickedCallback = remember { mutableStateOf<((String) -> Unit)?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val copiedFile = com.example.util.StorageHelper.copyFileToInternalSandbox(context, it)
            if (copiedFile != null) {
                onFilePickedCallback.value?.invoke(copiedFile.name)
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        if (com.example.util.GoogleDriveSyncManager.hasDrivePermission(context)) {
                            val token = com.example.util.GoogleDriveSyncManager.getAccessToken(context)
                            if (token != null) {
                                val driveUrl = com.example.util.GoogleDriveSyncManager.uploadPublicMediaFileDirect(
                                    context, token, copiedFile, categoryFolder = "Task_Attachments"
                                )
                                if (!driveUrl.isNullOrEmpty()) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        onFilePickedCallback.value?.invoke(driveUrl)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TaskEngine", "Failed uploading task attachment to Google Drive", e)
                    }
                }
            }
        }
    }

    val metaTimePattern = remember { Regex("""\[Time: ([^\]]+)\]""") }
    val metaRemindersPattern = remember { Regex("""\[Reminders: ([^\]]+)\]""") }
    val metaRepeatPattern = remember { Regex("""\[Repeat: ([^\]]+)\]""") }
    val metaDurationPattern = remember { Regex("""\[Duration: ([^\]]+)\]""") }
    val metaAttachmentPattern = remember { Regex("""\[Attachment: ([^\]]+)\]""") }
    val metaLocationPattern = remember { Regex("""\[Location: ([^\]]+)\]""") }
    val metaWontDoPattern = remember { Regex("""\[WontDo\]""") }
    val metaGCalEventIdPattern = remember { Regex("""\n?\[GCalEventId:\s*[^\]]+\]""", RegexOption.IGNORE_CASE) }
    val metaCompletedAtPattern = remember { Regex("""\n?\[CompletedAt:\s*[^\]]+\]""", RegexOption.IGNORE_CASE) }

    var isWontDoState by remember(task?.description) {
        val desc = task?.description ?: ""
        mutableStateOf(desc.contains("[WontDo]"))
    }

    var taskAttachment by remember(task?.description) {
        val desc = task?.description ?: ""
        val match = metaAttachmentPattern.find(desc)
        mutableStateOf(match?.groupValues?.get(1) ?: "None")
    }

    var taskLocation by remember(task?.description) {
        val desc = task?.description ?: ""
        val match = metaLocationPattern.find(desc)
        mutableStateOf(match?.groupValues?.get(1) ?: "None")
    }

    var parentTaskIdState by remember(task?.parentTaskId) { mutableStateOf(task?.parentTaskId) }

    val existingGCalMatch = remember(task?.description) {
        val desc = task?.description ?: ""
        Regex("""\[GCalEventId:\s*[^\]]+\]""", RegexOption.IGNORE_CASE).find(desc)?.value
    }
    val existingCompletedAtMatch = remember(task?.description) {
        val desc = task?.description ?: ""
        Regex("""\[CompletedAt:\s*[^\]]+\]""", RegexOption.IGNORE_CASE).find(desc)?.value
    }

    var cleanDescription by remember(task?.description) {
        val desc = task?.description ?: ""
        var temp = desc
        temp = temp.replace(metaTimePattern, "")
            .replace(metaRemindersPattern, "")
            .replace(metaRepeatPattern, "")
            .replace(metaDurationPattern, "")
            .replace(metaAttachmentPattern, "")
            .replace(metaLocationPattern, "")
            .replace(metaWontDoPattern, "")
            .replace(metaGCalEventIdPattern, "")
            .replace(metaCompletedAtPattern, "")
        mutableStateOf(temp.trim())
    }

    var taskTime by remember(task?.description, pendingPayload) {
        val desc = task?.description ?: ""
        val match = metaTimePattern.find(desc)
        mutableStateOf(match?.groupValues?.get(1) ?: (pendingPayload?.timeString ?: "None"))
    }

    var taskReminders by remember(task?.description) {
        val desc = task?.description ?: ""
        val match = metaRemindersPattern.find(desc)
        val listStr = match?.groupValues?.get(1) ?: "None"
        mutableStateOf(if (listStr == "None") listOf("None") else listStr.split(","))
    }

    var taskRepeat by remember(task?.description) {
        val desc = task?.description ?: ""
        val match = metaRepeatPattern.find(desc)
        mutableStateOf(match?.groupValues?.get(1) ?: "None")
    }

    var taskDuration by remember(task?.description) {
        val desc = task?.description ?: ""
        val match = metaDurationPattern.find(desc)
        mutableStateOf(match?.groupValues?.get(1) ?: "None")
    }

    val isSavedState = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (task == null) {
            val draft = viewModel.getTaskDraft()
            if (draft != null && draft.title.isNotEmpty()) {
                title = draft.title
                cleanDescription = draft.description
                targetCategory = draft.category
                priority = draft.priority
                viewModel.clearTaskDraft()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (task == null && !isSavedState.value && title.isNotEmpty()) {
                viewModel.saveTaskDraft(title, cleanDescription, targetCategory, priority)
            }
        }
    }

    val onSaveWithMeta = {
        isSavedState.value = true
        viewModel.clearTaskDraft()
        val metaBlock = StringBuilder()
        if (taskTime != "None" && taskTime.isNotEmpty()) {
            metaBlock.append("\n[Time: $taskTime]")
        }
        val remindersFiltered = taskReminders.filter { it != "None" && it.isNotEmpty() }
        if (remindersFiltered.isNotEmpty()) {
            metaBlock.append("\n[Reminders: ${remindersFiltered.joinToString(",")}]")
        }
        if (taskRepeat != "None" && taskRepeat.isNotEmpty()) {
            metaBlock.append("\n[Repeat: $taskRepeat]")
        }
        if (taskDuration.isNotEmpty() && taskDuration != "None") {
            metaBlock.append("\n[Duration: $taskDuration]")
        }
        if (taskAttachment != "None" && taskAttachment.isNotEmpty()) {
            metaBlock.append("\n[Attachment: $taskAttachment]")
        }
        if (taskLocation != "None" && taskLocation.isNotEmpty()) {
            metaBlock.append("\n[Location: $taskLocation]")
        }
        if (isWontDoState) {
            metaBlock.append("\n[WontDo]")
        }
        if (existingGCalMatch != null) {
            metaBlock.append("\n$existingGCalMatch")
        }
        if (existingCompletedAtMatch != null) {
            metaBlock.append("\n$existingCompletedAtMatch")
        }
        val finalDesc = (cleanDescription.trim() + metaBlock.toString()).trim()
        onSave(title, finalDesc, priority, taskDueDate, targetCategory, parentTaskIdState)
    }

    // Initial state tracking to determine if changes were made
    val initialTitle = remember(task?.id) { task?.title ?: "" }
    val initialPriority = remember(task?.id) { task?.priority ?: "MEDIUM" }
    val initialDueDate = remember(task?.id) { task?.dueDateString ?: (pendingPayload?.dateString ?: "") }
    val initialCategory = remember(task?.id) { task?.listCategory ?: (pendingPayload?.category ?: (if (currentList == "All" || currentList == "Today" || currentList == "Next 7 Days") "Inbox" else currentList)) }
    val initialCleanDesc = remember(task?.id, task?.description) {
        var temp = task?.description ?: ""
        temp = temp.replace(metaTimePattern, "")
            .replace(metaRemindersPattern, "")
            .replace(metaRepeatPattern, "")
            .replace(metaDurationPattern, "")
            .replace(metaAttachmentPattern, "")
            .replace(metaLocationPattern, "")
            .replace(metaWontDoPattern, "")
            .replace(metaGCalEventIdPattern, "")
            .replace(metaCompletedAtPattern, "")
        temp.trim()
    }
    val initialTime = remember(task?.id, task?.description) {
        val desc = task?.description ?: ""
        metaTimePattern.find(desc)?.groupValues?.get(1) ?: (pendingPayload?.timeString ?: "None")
    }
    val initialReminders = remember(task?.id, task?.description) {
        val desc = task?.description ?: ""
        val match = metaRemindersPattern.find(desc)
        val listStr = match?.groupValues?.get(1) ?: "None"
        if (listStr == "None") listOf("None") else listStr.split(",")
    }
    val initialRepeat = remember(task?.id, task?.description) {
        metaRepeatPattern.find(task?.description ?: "")?.groupValues?.get(1) ?: "None"
    }
    val initialDuration = remember(task?.id, task?.description) {
        metaDurationPattern.find(task?.description ?: "")?.groupValues?.get(1) ?: "None"
    }
    val initialAttachment = remember(task?.id, task?.description) {
        metaAttachmentPattern.find(task?.description ?: "")?.groupValues?.get(1) ?: "None"
    }
    val initialLocation = remember(task?.id, task?.description) {
        metaLocationPattern.find(task?.description ?: "")?.groupValues?.get(1) ?: "None"
    }
    val initialWontDo = remember(task?.id, task?.description) {
        (task?.description ?: "").contains("[WontDo]")
    }
    val initialParentId = remember(task?.id, task?.parentTaskId) { task?.parentTaskId }

    val hasChanges = remember(
        title, priority, taskDueDate, targetCategory, cleanDescription,
        taskTime, taskReminders, taskRepeat, taskDuration, taskAttachment,
        taskLocation, isWontDoState, parentTaskIdState
    ) {
        if (task == null) {
            title.isNotBlank() || cleanDescription.isNotBlank()
        } else {
            title != initialTitle ||
            priority != initialPriority ||
            taskDueDate != initialDueDate ||
            targetCategory != initialCategory ||
            cleanDescription.trim() != initialCleanDesc ||
            taskTime != initialTime ||
            taskReminders != initialReminders ||
            taskRepeat != initialRepeat ||
            taskDuration != initialDuration ||
            taskAttachment != initialAttachment ||
            taskLocation != initialLocation ||
            isWontDoState != initialWontDo ||
            parentTaskIdState != initialParentId
        }
    }

    // Dialog state controllers
    var showDateReminderPicker by remember { mutableStateOf(false) }
    var showPriorityDropdown by remember { mutableStateOf(false) }
    var showActionMenuDialog by remember { mutableStateOf(false) }
    var showListDropdownMenu by remember { mutableStateOf(false) }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showLinkParentDialog by remember { mutableStateOf(false) }
    var showAddSubtaskDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val handleDismissAttempt = {
        if (hasChanges) {
            showUnsavedDialog = true
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { handleDismissAttempt() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (showUnsavedDialog) {
                    AlertDialog(
                        onDismissRequest = { showUnsavedDialog = false },
                        title = { Text("Unsaved Changes", color = Color.White) },
                        text = { Text("You have unsaved changes. Do you want to save or discard them?", color = Color.LightGray) },
                        containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                        confirmButton = {
                            TextButton(onClick = {
                                showUnsavedDialog = false
                                onSaveWithMeta()
                            }) {
                                Text("Save", color = WaterBlue)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showUnsavedDialog = false
                                onDismiss()
                            }) {
                                Text("Discard", color = Color(0xFFF9325D))
                            }
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // 1. Top Bar (ArrowBack, Flag Icon, Expand/Collapse, ActionMenu 3-dots)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { handleDismissAttempt() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Go Back & Save",
                                tint = Color.White
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Flag Icon with Priority Color
                            Box {
                                IconButton(onClick = { showPriorityDropdown = true }) {
                                    val flagColor = when (priority.uppercase()) {
                                        "HIGH" -> Color(0xFFF9325D) // High Priority: Red
                                        "MEDIUM" -> Color(0xFFFFB300) // Medium Priority: Yellow
                                        "LOW" -> Color(0xFF2E6FF3) // Low Priority: Blue
                                        else -> Color.Gray // No Priority
                                    }
                                    FlagIcon(color = flagColor)
                                }

                                DropdownMenu(
                                    expanded = showPriorityDropdown,
                                    onDismissRequest = { showPriorityDropdown = false },
                                    modifier = Modifier
                                        .width(220.dp)
                                        .background(Color(0xFF1E1E20), RoundedCornerShape(12.dp))
                                        .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                ) {
                                    listOf(
                                        "HIGH" to "High Priority",
                                        "MEDIUM" to "Medium Priority",
                                        "LOW" to "Low Priority",
                                        "NONE" to "No Priority"
                                    ).forEach { (pKey, pLabel) ->
                                        val isSel = priority == pKey
                                        val pColor = when (pKey) {
                                            "HIGH" -> Color(0xFFF9325D)
                                            "MEDIUM" -> Color(0xFFFFB300)
                                            "LOW" -> Color(0xFF2E6FF3)
                                            else -> Color.LightGray
                                        }

                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    FlagIcon(color = pColor, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(pLabel, color = Color.White, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    if (isSel) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E6FF3), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                priority = pKey
                                                showPriorityDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (title.isNotEmpty()) {
                                        onSaveWithMeta()
                                    }
                                },
                                enabled = title.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save Task",
                                    tint = if (title.isNotEmpty()) Color(0xFF2E6FF3) else Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(onClick = { showAttachmentDialog = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_attachment),
                                    contentDescription = "Upload / Add Attachment",
                                    tint = if (taskAttachment != "None" && taskAttachment.isNotEmpty()) Color(0xFF2E6FF3) else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(onClick = { showActionMenuDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Action Menu",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Date/Reminder selection pill (Screenshot 1)
                    val isDateSet = taskDueDate.isNotEmpty() || taskAttachment != "None" || taskLocation != "None"
                    val pillLabel = remember(taskDueDate, taskTime, taskReminders, taskRepeat, taskDuration, taskAttachment, taskLocation) {
                        val listParts = mutableListOf<String>()
                        if (taskDueDate.isNotEmpty()) {
                            val formattedDate = formatDueDate(taskDueDate)
                            listParts.add("📅 $formattedDate")
                        }
                        if (taskTime != "None" && taskTime.isNotEmpty()) {
                            listParts.add(taskTime)
                        }
                        val remindersFiltered = taskReminders.filter { it != "None" && it.isNotEmpty() }
                        if (remindersFiltered.isNotEmpty()) {
                            listParts.add("${remindersFiltered.size} Reminders")
                        }
                        if (taskRepeat != "None" && taskRepeat.isNotEmpty()) {
                            listParts.add(taskRepeat)
                        }
                        if (taskDuration.isNotEmpty() && taskDuration != "None") {
                            listParts.add("$taskDuration mins")
                        }
                        if (taskAttachment != "None" && taskAttachment.isNotEmpty()) {
                            listParts.add("📎 $taskAttachment")
                        }
                        if (taskLocation != "None" && taskLocation.isNotEmpty()) {
                            listParts.add("📍 $taskLocation")
                        }
                        if (listParts.isEmpty()) {
                            "Date and Reminder"
                        } else {
                            listParts.joinToString(" • ")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Date reminder component
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(width = 0.5.dp, color = Color(0xFF4D4D54), shape = RoundedCornerShape(6.dp))
                                .clickable { showDateReminderPicker = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = if (isDateSet) Color(0xFF2E6FF3) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = pillLabel,
                                color = if (isDateSet) Color.White else Color.Gray,
                                fontSize = 13.sp
                            )
                        }

                        // List/Inbox Category component
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(width = 0.5.dp, color = Color(0xFF4D4D54), shape = RoundedCornerShape(6.dp))
                                    .clickable { showListDropdownMenu = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Inbox",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = targetCategory,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            DropdownMenu(
                                expanded = showListDropdownMenu,
                                onDismissRequest = { showListDropdownMenu = false },
                                modifier = Modifier
                                    .width(180.dp)
                                    .background(Color(0xFF1E1E20))
                            ) {
                                (listOf("Inbox") + allLists.map { it.name }).forEach { lstName ->
                                    DropdownMenuItem(
                                        text = { Text(lstName, color = Color.White) },
                                        onClick = {
                                            targetCategory = lstName
                                            showListDropdownMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Title ("What would you like to do?")
                    val activeTitleQuery = remember(title) { com.example.util.MentionParser.getActiveMentionQuery(title) }
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = {
                            Text(
                                text = "What would you like to do?",
                                color = Color(0xFF5D5D62),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            errorContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("editor_task_title").padding(horizontal = 0.dp)
                    )

                    if (activeTitleQuery != null) {
                        MentionAutocompleteBox(
                            query = activeTitleQuery,
                            allUsers = allUsersState,
                            folders = foldersState,
                            contacts = contactsState,
                            myNickname = viewModel.getMyNickname(),
                            onSelect = { selected ->
                                title = com.example.util.MentionParser.replaceActiveQuery(title, selected)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Description/Notes
                    val activeDescQuery = remember(cleanDescription) { com.example.util.MentionParser.getActiveMentionQuery(cleanDescription) }
                    TextField(
                        value = cleanDescription,
                        onValueChange = { cleanDescription = it },
                        placeholder = {
                            Text(
                                text = "Add outline details and notes for this agenda item...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.LightGray,
                            fontSize = 14.sp
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            errorContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (task != null) 0.5f else 1f)
                            .padding(horizontal = 0.dp)
                    )

                    if (activeDescQuery != null) {
                        MentionAutocompleteBox(
                            query = activeDescQuery,
                            allUsers = allUsersState,
                            folders = foldersState,
                            contacts = contactsState,
                            myNickname = viewModel.getMyNickname(),
                            onSelect = { selected ->
                                cleanDescription = com.example.util.MentionParser.replaceActiveQuery(cleanDescription, selected)
                            }
                        )
                    }

                    if (taskAttachment != "None" && taskAttachment.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val previewType = remember(taskAttachment) {
                            val nameLower = taskAttachment.lowercase()
                            if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".webp") || nameLower.contains("image") || nameLower.contains("photo") || nameLower.contains("drive.google.com") || nameLower.startsWith("http")) {
                                if (nameLower.contains(".mp4") || nameLower.contains("video") || nameLower.contains(".mov")) "video"
                                else if (nameLower.contains(".mp3") || nameLower.contains("audio") || nameLower.contains(".wav")) "audio"
                                else "image"
                            } else if (nameLower.endsWith(".mp4") || nameLower.endsWith(".mov") || nameLower.endsWith(".3gp") || nameLower.endsWith(".mkv")) {
                                "video"
                            } else if (nameLower.endsWith(".mp3") || nameLower.endsWith(".m4a") || nameLower.endsWith(".wav") || nameLower.endsWith(".aac")) {
                                "audio"
                            } else {
                                "others"
                            }
                        }

                        Text(
                            text = "ATTACHED FILE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            MediaPreviewBox(
                                pathOrName = taskAttachment,
                                type = previewType,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Column {
                                Text(
                                    text = taskAttachment,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap attachment menu to change or remove",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (task != null) {
                        val subtasks = remember(tasks, task.id) {
                            tasks.filter { it.parentTaskId == task.id }
                        }
                        
                        if (subtasks.isNotEmpty()) {
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                            
                            Text(
                                text = "SUBTASKS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            
                            var subtaskToManage by remember { mutableStateOf<Task?>(null) }
                            var editingSubtaskTitle by remember { mutableStateOf("") }

                            if (subtaskToManage != null) {
                                AlertDialog(
                                    onDismissRequest = { subtaskToManage = null },
                                    title = { Text("Manage Subtask", color = Color.White) },
                                    text = {
                                        Column {
                                            OutlinedTextField(
                                                value = editingSubtaskTitle,
                                                onValueChange = { editingSubtaskTitle = it },
                                                label = { Text("Subtask Title") },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedLabelColor = Color(0xFF2E6FF3),
                                                    unfocusedLabelColor = Color.Gray,
                                                    focusedBorderColor = Color(0xFF2E6FF3),
                                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        Row {
                                            TextButton(
                                                onClick = {
                                                    subtaskToManage?.let { currentSub ->
                                                        val cleanDesc = currentSub.description.replace("[WontDo]", "").trim()
                                                        val newDesc = if (cleanDesc.isEmpty()) "[WontDo]" else "$cleanDesc\n[WontDo]"
                                                        viewModel.updateTask(currentSub.copy(isCompleted = true, description = newDesc))
                                                    }
                                                    subtaskToManage = null
                                                }
                                            ) {
                                                Text("Won't Do", color = Color(0xFFF9325D))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            TextButton(
                                                onClick = {
                                                    subtaskToManage?.let { currentSub ->
                                                        if (editingSubtaskTitle.isNotBlank()) {
                                                            viewModel.updateTask(currentSub.copy(title = editingSubtaskTitle.trim()))
                                                        }
                                                    }
                                                    subtaskToManage = null
                                                }
                                            ) {
                                                Text("Save", color = Color(0xFF2E6FF3))
                                            }
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = {
                                                subtaskToManage?.let { currentSub ->
                                                    viewModel.deleteTask(currentSub)
                                                }
                                                subtaskToManage = null
                                            }
                                        ) {
                                            Text("Delete", color = Color.Gray)
                                        }
                                    },
                                    containerColor = Color(0xFF1C1C1E),
                                    iconContentColor = Color.White,
                                    titleContentColor = Color.White
                                )
                            }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.5f)
                                    .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(subtasks, key = { it.id }) { sub ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Black.copy(alpha = 0.2f))
                                                    .combinedClickable(
                                                        onClick = { viewModel.toggleTaskCompletion(sub) },
                                                        onLongClick = {
                                                            subtaskToManage = sub
                                                            editingSubtaskTitle = sub.title
                                                        }
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    val isSubWontDo = sub.description.contains("[WontDo]")
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .border(
                                                                width = 1.2.dp,
                                                                color = if (isSubWontDo) Color(0xFFF9325D) else if (sub.isCompleted) Color.Gray else Color(0xFF2E6FF3),
                                                                shape = RoundedCornerShape(4.dp)
                                                            )
                                                            .background(if (isSubWontDo) Color(0xFFF9325D).copy(alpha = 0.15f) else if (sub.isCompleted) Color(0xFF2E6FF3).copy(alpha = 0.15f) else Color.Transparent),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSubWontDo) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Won't Do",
                                                                tint = Color(0xFFF9325D),
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                        } else if (sub.isCompleted) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = Color(0xFF2E6FF3),
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                        }
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    
                                                    Text(
                                                        text = sub.title,
                                                        color = if (sub.isCompleted) Color.Gray else Color.White,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else null
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Picker Overlay (Screenshot 2)
                if (showDateReminderPicker) {
                    val mContext = androidx.compose.ui.platform.LocalContext.current
                    val dialogContext = remember(mContext) {
                        var cur = mContext
                        while (cur is android.content.ContextWrapper) {
                            if (cur is android.app.Activity) {
                                return@remember cur
                            }
                            cur = cur.baseContext
                        }
                        mContext
                    }
                    var pickerCalendar by remember { mutableStateOf(Calendar.getInstance()) }
                    var tempSelectedDate by remember { mutableStateOf(taskDueDate.ifEmpty { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }) }
                    var tempTime by remember { mutableStateOf(taskTime) }
                    var tempReminders by remember { mutableStateOf(taskReminders) }
                    var tempRepeat by remember { mutableStateOf(taskRepeat) }
                    var tempDuration by remember { mutableStateOf(taskDuration) }

                    // Sub-dialog indicators
                    var showAddReminderDialog by remember { mutableStateOf(false) }
                    var showRepeatDialog by remember { mutableStateOf(false) }
                    var showDurationDialog by remember { mutableStateOf(false) }

                    Dialog(
                        onDismissRequest = { showDateReminderPicker = false }
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Header controls row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(onClick = { showDateReminderPicker = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Gray)
                                    }

                                    Text(
                                        text = "Set Date & Time",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    IconButton(
                                        onClick = {
                                            taskDueDate = tempSelectedDate
                                            taskTime = tempTime
                                            taskReminders = tempReminders
                                            taskRepeat = tempRepeat
                                            taskDuration = tempDuration
                                            showDateReminderPicker = false
                                        }
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Confirm", tint = Color(0xFF2E6FF3))
                                    }
                                }

                                // Month picker Row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                                    Text(
                                        text = monthFormat.format(pickerCalendar.time),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(
                                            onClick = {
                                                val clone = pickerCalendar.clone() as Calendar
                                                clone.add(Calendar.MONTH, -1)
                                                pickerCalendar = clone
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = Color.LightGray)
                                        }

                                        IconButton(
                                            onClick = {
                                                val clone = pickerCalendar.clone() as Calendar
                                                clone.add(Calendar.MONTH, 1)
                                                pickerCalendar = clone
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray)
                                        }
                                    }
                                }

                                // Custom calendar grid calculations
                                val tempCal = pickerCalendar.clone() as Calendar
                                tempCal.set(Calendar.DAY_OF_MONTH, 1)
                                val firstWeekDay = tempCal.get(Calendar.DAY_OF_WEEK)
                                val totalDaysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                                val emptyCells = firstWeekDay - 1
                                val totalCells = emptyCells + totalDaysInMonth
                                val rowsCount = (totalCells + 6) / 7

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Weekdays row
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { wd ->
                                            Text(
                                                text = wd,
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    for (r in 0 until rowsCount) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            for (c in 0 until 7) {
                                                val cellIdx = r * 7 + c
                                                if (cellIdx < emptyCells || cellIdx >= totalCells) {
                                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                                } else {
                                                    val dayNum = cellIdx - emptyCells + 1

                                                    val dayCal = pickerCalendar.clone() as Calendar
                                                    dayCal.set(Calendar.DAY_OF_MONTH, dayNum)
                                                    val cellStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dayCal.time)

                                                    val isSelected = tempSelectedDate == cellStr
                                                    val isToday = todayStr == cellStr

                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .aspectRatio(1.1f)
                                                            .padding(2.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isSelected) Color(0xFF2E6FF3)
                                                                else if (isToday) Color.DarkGray.copy(alpha = 0.5f)
                                                                else Color.Transparent
                                                            )
                                                            .clickable {
                                                                tempSelectedDate = cellStr
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = dayNum.toString(),
                                                            color = if (isSelected) Color.White else Color.LightGray,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)

                                // Sub settings list items clickable
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Time row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val calendar = Calendar.getInstance()
                                                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                                                val minute = calendar.get(Calendar.MINUTE)
                                                android.app.TimePickerDialog(dialogContext, { _, h, m ->
                                                    val amPm = if (h < 12) "AM" else "PM"
                                                    val displayHour = when {
                                                        h == 0 -> 12
                                                        h > 12 -> h - 12
                                                        else -> h
                                                    }
                                                    tempTime = String.format(Locale.US, "%d:%02d %s", displayHour, m, amPm)
                                                }, hour, minute, false).show()
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Time", color = Color.White, fontSize = 14.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(tempTime, color = Color.Gray, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // Reminder row (with options to add multiple and Plus symbol)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showAddReminderDialog = true }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Reminder", color = Color.White, fontSize = 14.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { showAddReminderDialog = true },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Add reminder", tint = Color(0xFF2E6FF3), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                                            val activeRemList = tempReminders.filter { it != "None" && it.isNotEmpty() }
                                            Text(
                                                text = if (activeRemList.isEmpty()) "None" else activeRemList.joinToString(", "),
                                                color = Color.Gray,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // Display removable reminder chips below row
                                    val filterRem = tempReminders.filter { it != "None" && it.isNotEmpty() }
                                    if (filterRem.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            filterRem.forEach { rem ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF161618))
                                                        .clickable {
                                                            val updated = tempReminders.toMutableList()
                                                            updated.remove(rem)
                                                            if (updated.isEmpty()) updated.add("None")
                                                            tempReminders = updated
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(rem, color = Color.LightGray, fontSize = 11.sp)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Repeat Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showRepeatDialog = true }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Repeat", color = Color.White, fontSize = 14.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(tempRepeat, color = Color.Gray, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // Duration Row below Repeat Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showDurationDialog = true }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Duration (mins)", color = Color.White, fontSize = 14.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (tempDuration == "None") "None" else "$tempDuration mins", color = Color.Gray, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Add custom reminder Dialog
                    if (showAddReminderDialog) {
                        Dialog(onDismissRequest = { showAddReminderDialog = false }) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Add custom reminder", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                                    var inputNum by remember { mutableStateOf("15") }
                                    val units = listOf("mins", "hours", "days")
                                    var selectedUnitIndex by remember { mutableStateOf(0) }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Box 1: type or scroll numbers
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .background(Color(0xFF161618))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val current = inputNum.toIntOrNull() ?: 1
                                                    if (current > 1) inputNum = (current - 1).toString()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Text("-", color = Color.LightGray, fontWeight = FontWeight.Bold)
                                            }
                                            TextField(
                                                value = inputNum,
                                                onValueChange = { inputNum = it.filter { c -> c.isDigit() } },
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, textAlign = TextAlign.Center),
                                                modifier = Modifier.weight(1f),
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
                                                )
                                            )
                                            IconButton(
                                                onClick = {
                                                    val current = inputNum.toIntOrNull() ?: 0
                                                    inputNum = (current + 1).toString()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Text("+", color = Color.LightGray, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Box 2: scroll only options
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(90.dp)
                                                .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .background(Color(0xFF161618))
                                                .padding(vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                itemsIndexed(units) { index, unit ->
                                                    val isSel = selectedUnitIndex == index
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { selectedUnitIndex = index }
                                                            .background(if (isSel) Color(0xFF2E6FF3).copy(alpha = 0.15f) else Color.Transparent)
                                                            .padding(vertical = 6.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = unit,
                                                            color = if (isSel) Color(0xFF2E6FF3) else Color.Gray,
                                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { showAddReminderDialog = false }) {
                                            Text("Cancel", color = Color.Gray)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                val num = inputNum.ifEmpty { "15" }
                                                val unit = units[selectedUnitIndex]
                                                val newReminder = "$num $unit before"
                                                val currentList = tempReminders.toMutableList()
                                                if (currentList.contains("None") || currentList.contains("none")) {
                                                    currentList.remove("None")
                                                    currentList.remove("none")
                                                }
                                                if (!currentList.contains(newReminder)) {
                                                    currentList.add(newReminder)
                                                }
                                                tempReminders = currentList
                                                showAddReminderDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF3))
                                        ) {
                                            Text("Add", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Custom Repeat settings Dialog
                    if (showRepeatDialog) {
                        Dialog(onDismissRequest = { showRepeatDialog = false }) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Repeat Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                                    // Simple Presets
                                    Text("Presets", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("None", "Daily", "Weekly", "Monthly").forEach { choice ->
                                            val isSel = tempRepeat == choice
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSel) Color(0xFF2E6FF3) else Color(0xFF161618))
                                                    .clickable { tempRepeat = choice; showRepeatDialog = false }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(choice, color = if (isSel) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.12f))

                                    // Custom Interval
                                    Text("Custom Days Interval", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    var customDaysInput by remember { mutableStateOf("5") }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Every", color = Color.White, fontSize = 14.sp)
                                        TextField(
                                            value = customDaysInput,
                                            onValueChange = { customDaysInput = it.filter { c -> c.isDigit() } },
                                            modifier = Modifier.width(60.dp).height(48.dp),
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, textAlign = TextAlign.Center),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color(0xFF161618),
                                                unfocusedContainerColor = Color(0xFF161618),
                                                focusedIndicatorColor = Color(0xFF2E6FF3)
                                            )
                                        )
                                        Text("days", color = Color.White, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Button(
                                            onClick = {
                                                val num = customDaysInput.ifEmpty { "1" }
                                                tempRepeat = "Every $num days"
                                                showRepeatDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF3)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Set", color = Color.White, fontSize = 12.sp)
                                        }
                                    }

                                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.12f))

                                    // Specific Weekday selection
                                    Text("Day of Week Options", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    val daysOfWeek = remember { listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday") }
                                    val ordinals = remember { listOf("Every", "1st", "2nd", "3rd", "4th", "last") }

                                    var selectedOrdinal by remember {
                                        val initialOrdinal = when {
                                            tempRepeat.contains("1st") -> "1st"
                                            tempRepeat.contains("2nd") -> "2nd"
                                            tempRepeat.contains("3rd") -> "3rd"
                                            tempRepeat.contains("4th") -> "4th"
                                            tempRepeat.contains("last") -> "last"
                                            else -> "Every"
                                        }
                                        mutableStateOf(initialOrdinal)
                                    }

                                    var selectedDayOfWeek by remember {
                                        val day = daysOfWeek.firstOrNull { tempRepeat.contains(it) } ?: "Monday"
                                        mutableStateOf(day)
                                    }

                                    var ordinalExpanded by remember { mutableStateOf(false) }
                                    var dayExpanded by remember { mutableStateOf(false) }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 1st Box: Select Number / Ordinal
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color(0xFF161618), RoundedCornerShape(8.dp))
                                                .clickable { ordinalExpanded = true }
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(selectedOrdinal, color = Color.White, fontSize = 14.sp)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                                            }

                                            DropdownMenu(
                                                expanded = ordinalExpanded,
                                                onDismissRequest = { ordinalExpanded = false },
                                                modifier = Modifier.background(Color(0xFF1E1E20))
                                            ) {
                                                ordinals.forEach { ord ->
                                                    DropdownMenuItem(
                                                        text = { Text(ord, color = Color.White) },
                                                        onClick = {
                                                            selectedOrdinal = ord
                                                            ordinalExpanded = false
                                                            // Auto-update tempRepeat
                                                            tempRepeat = if (ord == "Every") {
                                                                "On $selectedDayOfWeek"
                                                            } else {
                                                                "On $ord $selectedDayOfWeek"
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // 2nd Box: Select Week Day
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color(0xFF161618), RoundedCornerShape(8.dp))
                                                .clickable { dayExpanded = true }
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(selectedDayOfWeek, color = Color.White, fontSize = 14.sp)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                                            }

                                            DropdownMenu(
                                                expanded = dayExpanded,
                                                onDismissRequest = { dayExpanded = false },
                                                modifier = Modifier.background(Color(0xFF1E1E20)).heightIn(max = 240.dp)
                                            ) {
                                                daysOfWeek.forEach { day ->
                                                    DropdownMenuItem(
                                                        text = { Text(day, color = Color.White) },
                                                        onClick = {
                                                            selectedDayOfWeek = day
                                                            dayExpanded = false
                                                            // Auto-update tempRepeat
                                                            tempRepeat = if (selectedOrdinal == "Every") {
                                                                "On $day"
                                                            } else {
                                                                "On $selectedOrdinal $day"
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { showRepeatDialog = false }) {
                                            Text("Cancel", color = Color.Gray)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                tempRepeat = if (selectedOrdinal == "Every") {
                                                    "On $selectedDayOfWeek"
                                                } else {
                                                    "On $selectedOrdinal $selectedDayOfWeek"
                                                }
                                                showRepeatDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF3)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Apply", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Duration sub settings dialog
                    if (showDurationDialog) {
                        Dialog(onDismissRequest = { showDurationDialog = false }) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Set Duration (in mins)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                                    var inputDurationNum by remember { mutableStateOf(tempDuration) }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .background(Color(0xFF161618))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        IconButton(onClick = {
                                            val current = inputDurationNum.toIntOrNull() ?: 5
                                            if (current > 5) inputDurationNum = (current - 5).toString()
                                        }) {
                                            Text("-5", color = Color.LightGray, fontWeight = FontWeight.Bold)
                                        }
                                        TextField(
                                            value = inputDurationNum,
                                            onValueChange = { inputDurationNum = it.filter { c -> c.isDigit() } },
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, textAlign = TextAlign.Center),
                                            modifier = Modifier.width(80.dp),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            )
                                        )
                                        IconButton(onClick = {
                                            val current = inputDurationNum.toIntOrNull() ?: 0
                                            inputDurationNum = (current + 5).toString()
                                        }) {
                                            Text("+5", color = Color.LightGray, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Pick presets option
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("None", "15", "30", "45", "60").forEach { p ->
                                            val isSel = inputDurationNum == p
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSel) Color(0xFF2E6FF3) else Color(0xFF161618))
                                                    .clickable { inputDurationNum = p }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text(p, color = if (isSel) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { showDurationDialog = false }) {
                                            Text("Cancel", color = Color.Gray)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                tempDuration = inputDurationNum.ifEmpty { "None" }
                                                showDurationDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF3))
                                        ) {
                                            Text("OK", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Menu sheet overlay (Screenshot 4)
                if (showActionMenuDialog) {
                    Dialog(
                        onDismissRequest = { showActionMenuDialog = false }
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { showActionMenuDialog = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Action Menu", tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Action Menu", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ActionGridItem(Icons.Default.Close, "Won't Do", Color(0xFF2E6FF3)) {
                                        showActionMenuDialog = false
                                        isWontDoState = true
                                        onSaveWithMeta()
                                    }
                                    ActionGridItem(Icons.Default.Delete, "Delete", Color(0xFFF9325D)) {
                                        showActionMenuDialog = false
                                        onDelete()
                                    }
                                }

                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)

                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        Icons.Default.List to "Add Subtask",
                                        Icons.Default.ExitToApp to "Link Parent Task",
                                        // removed

                                        Icons.Default.Add to "Duplicate"
                                    ).forEach { (icon, text) ->
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        showActionMenuDialog = false
                                                        if (text == "Duplicate") {
                                                            val metaBlock = StringBuilder()
                                                            if (taskTime != "None" && taskTime.isNotEmpty()) {
                                                                metaBlock.append("\n[Time: $taskTime]")
                                                            }
                                                            val remindersFiltered = taskReminders.filter { it != "None" && it.isNotEmpty() }
                                                            if (remindersFiltered.isNotEmpty()) {
                                                                metaBlock.append("\n[Reminders: ${remindersFiltered.joinToString(",")}]")
                                                            }
                                                            if (taskRepeat != "None" && taskRepeat.isNotEmpty()) {
                                                                metaBlock.append("\n[Repeat: $taskRepeat]")
                                                            }
                                                            if (taskDuration.isNotEmpty() && taskDuration != "None") {
                                                                metaBlock.append("\n[Duration: $taskDuration]")
                                                            }
                                                            if (taskAttachment != "None" && taskAttachment.isNotEmpty()) {
                                                                metaBlock.append("\n[Attachment: $taskAttachment]")
                                                            }
                                                            if (taskLocation != "None" && taskLocation.isNotEmpty()) {
                                                                metaBlock.append("\n[Location: $taskLocation]")
                                                            }
                                                            val finalDesc = (cleanDescription.trim() + metaBlock.toString()).trim()
                                                            onSave(title + " (Copy)", finalDesc, priority, taskDueDate, targetCategory, parentTaskIdState)
                                                        } else if (text == "Add Subtask") {
                                                            showAddSubtaskDialog = true
                                                        } else if (text == "Link Parent Task") {
                                                            showLinkParentDialog = true
                                                        } else if (false) {
                                                            showAttachmentDialog = true
                                                        }
                                                    }
                                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text, color = Color.White, fontSize = 14.sp)
                                                if (icon is androidx.compose.ui.graphics.vector.ImageVector) {
                                                     Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                 } else if (icon is Int) {
                                                     Icon(painterResource(id = icon), contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                 }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Interactive Dialogs for the remaining action menu items
                if (showAttachmentDialog) {
                    var inputAttachment by remember { mutableStateOf(if (taskAttachment == "None") "" else taskAttachment) }
                    var uploadedFiles by remember {
                        mutableStateOf<List<String>>(com.example.util.StorageHelper.getAppFilesDir(context).listFiles()?.map { it.name } ?: emptyList())
                    }
                    Dialog(onDismissRequest = { showAttachmentDialog = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Text("Add Task Attachment", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                TextField(
                                    value = inputAttachment,
                                    onValueChange = { inputAttachment = it },
                                    placeholder = { Text("E.g. agenda_notes.pdf", color = Color.Gray) },
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color(0xFF141416),
                                        unfocusedContainerColor = Color(0xFF141416)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Button(
                                    onClick = {
                                        onFilePickedCallback.value = { fileName ->
                                            inputAttachment = fileName
                                            uploadedFiles = com.example.util.StorageHelper.getAppFilesDir(context).listFiles()?.map { it.name } ?: emptyList()
                                        }
                                        filePickerLauncher.launch("*/*")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF3)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_attachment),
                                            contentDescription = "Upload Any File",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Upload / Pick Any File", color = Color.White, fontSize = 14.sp)
                                    }
                                }
                                
                                if (uploadedFiles.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Uploaded Files:", color = Color.Gray, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        uploadedFiles.take(5).forEach { name ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.White.copy(alpha = 0.1f))
                                                    .clickable { inputAttachment = name }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = if (inputAttachment == name) Color(0xFF2E6FF3) else Color.Gray,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = name,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Presets:", color = Color.Gray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("receipt.pdf", "photo.png", "notes.txt").forEach { p ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.1f))
                                                .clickable { inputAttachment = p }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(p, color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { 
                                        taskAttachment = "None"
                                        showAttachmentDialog = false 
                                    }) {
                                        Text("Clear", color = Color.LightGray)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = {
                                        taskAttachment = inputAttachment.ifEmpty { "None" }
                                        showAttachmentDialog = false
                                    }) {
                                        Text("Save", color = Color(0xFF2E6FF3))
                                    }
                                }
                            }
                        }
                    }
                }

                if (showLocationDialog) {
                    var inputLocation by remember { mutableStateOf(if (taskLocation == "None") "" else taskLocation) }
                    Dialog(onDismissRequest = { showLocationDialog = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Text("Add Task Location", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                TextField(
                                    value = inputLocation,
                                    onValueChange = { inputLocation = it },
                                    placeholder = { Text("E.g. Main Office", color = Color.Gray) },
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color(0xFF141416),
                                        unfocusedContainerColor = Color(0xFF141416)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Presets:", color = Color.Gray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("Home", "Office", "Supermarket", "Gym").forEach { loc ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.1f))
                                                .clickable { inputLocation = loc }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(loc, color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { 
                                        taskLocation = "None"
                                        showLocationDialog = false 
                                    }) {
                                        Text("Clear", color = Color.LightGray)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = {
                                        taskLocation = inputLocation.ifEmpty { "None" }
                                        showLocationDialog = false
                                    }) {
                                        Text("Save", color = Color(0xFF2E6FF3))
                                    }
                                }
                            }
                        }
                    }
                }

                if (showLinkParentDialog) {
                    val potentialParents = remember(tasks, task?.id) {
                        tasks.filter { 
                            it.id != (task?.id ?: -1) && !it.isCompleted && it.parentTaskId == null
                        }
                    }
                    
                    Dialog(onDismissRequest = { showLinkParentDialog = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Text("Select Parent Task", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                if (potentialParents.isEmpty()) {
                                    Text("No eligible parent tasks found.", color = Color.Gray, fontSize = 13.sp)
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.heightIn(max = 200.dp)
                                    ) {
                                        items(potentialParents) { pTask ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (parentTaskIdState == pTask.id) Color(0xFF2E6FF3).copy(alpha = 0.2f) else Color.Transparent)
                                                    .clickable {
                                                        parentTaskIdState = pTask.id
                                                        showLinkParentDialog = false
                                                    }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(pTask.title, color = Color.White, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    if (parentTaskIdState != null) {
                                        TextButton(onClick = {
                                            parentTaskIdState = null
                                            showLinkParentDialog = false
                                        }) {
                                            Text("Unlink Parent", color = Color(0xFFF9325D))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    TextButton(onClick = { showLinkParentDialog = false }) {
                                        Text("Close", color = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }

                if (showAddSubtaskDialog) {
                    var subtaskTitle by remember { mutableStateOf("") }
                    Dialog(onDismissRequest = { showAddSubtaskDialog = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Text("Create New Subtask", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                TextField(
                                    value = subtaskTitle,
                                    onValueChange = { subtaskTitle = it },
                                    placeholder = { Text("Subtask name", color = Color.Gray) },
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color(0xFF141416),
                                        unfocusedContainerColor = Color(0xFF141416)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { showAddSubtaskDialog = false }) {
                                        Text("Cancel", color = Color.LightGray)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = {
                                        if (subtaskTitle.isNotEmpty()) {
                                            if (task == null) {
                                                // Save the parent task first and then create is handled. But can show a hint or prompt to save task first.
                                                // E.g. save the parent, then allow editing it. Simple & safe UX:
                                                onSaveWithMeta()
                                            } else {
                                                viewModel.createTask(
                                                    title = subtaskTitle,
                                                    description = "",
                                                    estMin = 25,
                                                    category = targetCategory,
                                                    parentId = task.id
                                                )
                                            }
                                            showAddSubtaskDialog = false
                                        }
                                    }) {
                                        Text("Create", color = Color(0xFF2E6FF3))
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

@Composable
fun ActionGridItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = text, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text, color = Color.Gray, fontSize = 9.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun FlagIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        drawRect(
            color = color,
            topLeft = Offset(w * 0.18f, h * 0.1f),
            size = androidx.compose.ui.geometry.Size(w * 0.08f, h * 0.8f)
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.26f, h * 0.1f)
            lineTo(w * 0.85f, h * 0.28f)
            lineTo(w * 0.26f, h * 0.46f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
fun FinancialTrackerTasksIntegration(viewModel: AppViewModel) {
    val transactions by viewModel.financeTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.financeCategories.collectAsStateWithLifecycle()
    
    val totalIncome = remember(transactions) {
        transactions.filter { it.type.uppercase() == "INCOME" }.sumOf { it.amount }
    }
    val totalExpense = remember(transactions) {
        transactions.filter { it.type.uppercase() == "EXPENSE" }.sumOf { it.amount }
    }
    val balance = totalIncome - totalExpense

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Colorful Summary Dashboard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E1E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E3535))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "FINANCIAL SUMMARY INTEGRATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00BFA5)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Net Balance", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = String.format("$%.2f", balance),
                            color = if (balance >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    
                    Button(
                        onClick = { viewModel.navigateTo(Screen.FINANCES) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("OPEN LEDGER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                HorizontalDivider(color = Color(0xFF1E3535))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total Income", color = Color.Gray, fontSize = 10.sp)
                        Text(
                            text = String.format("+$%.2f", totalIncome),
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total Expenses", color = Color.Gray, fontSize = 10.sp)
                        Text(
                            text = String.format("-$%.2f", totalExpense),
                            color = Color(0xFFF44336),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // 2. Transactions header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT TRANSACTIONS (${transactions.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }
        
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C), RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No financial records documented. Click 'Open Ledger' to get started.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions.take(20)) { tx ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val typeColor = when (tx.type.uppercase()) {
                                "INCOME" -> Color(0xFF4CAF50)
                                "EXPENSE" -> Color(0xFFF44336)
                                else -> WaterBlue
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(typeColor.copy(alpha = 0.15f))
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (tx.type.uppercase() == "INCOME") Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = tx.type,
                                    tint = typeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (tx.note.isNotEmpty()) tx.note else "Financial Transaction",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "${tx.type} • ${tx.fromCategory ?: tx.toCategory ?: "General"}",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = String.format("$%.2f", tx.amount),
                                color = typeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


// ==================== CONSOLIDATED CALENDAR & TASK COMPONENTS ====================



// ==================== CONSOLIDATED FROM: CalendarOptimizationOnboardingView.kt ====================



@Composable
fun CalendarOptimizationOnboardingView(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var wakeHour by remember { mutableStateOf(7) }
    var wakeMin by remember { mutableStateOf(0) }

    var sleepHour by remember { mutableStateOf(22) }
    var sleepMin by remember { mutableStateOf(0) }

    val formattedWake = String.format("%02d:%02d", wakeHour, wakeMin)
    val formattedSleep = String.format("%02d:%02d", sleepHour, sleepMin)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Premium visual header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(WaterBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Calendar Optimization",
                    tint = WaterBlue,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CALENDAR & NOTIFICATION",
                color = WaterBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Time-Window Optimisation",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Establish your active daily boundaries. Life OS uses these parameters to filter notifications and optimize schedule layouts.",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Explanation Card of the Two Core Optimizations
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "WHAT THIS CONFIGURATION DOES:",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Feature 1",
                            tint = WaterBlue,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "1. Calendar Range Optimization",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hides dormant earlier and later hours in your Day View. Hitting 'Show' reveals extended blocks when required.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Feature 2",
                            tint = WaterBlue,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "2. Smart Sleep Silent Mode",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Blocks pings, system alarms, friend focus alerts, and water reminders during your Sleep Time window automatically.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Wake-Up Time Picker
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "WAKE-UP TIME START (DAY VIEW START)",
                        color = WaterBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stepper Widget
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                wakeHour = if (wakeHour == 0) 23 else wakeHour - 1
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E22))
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Minus Hour", tint = Color.White)
                        }

                        Text(
                            text = formattedWake,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        IconButton(
                            onClick = {
                                wakeHour = if (wakeHour == 23) 0 else wakeHour + 1
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E22))
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Plus Hour", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        listOf(5, 6, 7, 8).forEach { hr ->
                            val active = wakeHour == hr && wakeMin == 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) WaterBlue else Color(0xFF141416))
                                    .border(0.5.dp, if (active) WaterBlue else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        wakeHour = hr
                                        wakeMin = 0
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = String.format("%02d:00", hr),
                                    color = if (active) Color.Black else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sleep Time Picker
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SLEEP TIME START (SILENT MODE DEPLOYMENT)",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stepper Widget
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                sleepHour = if (sleepHour == 0) 23 else sleepHour - 1
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E22))
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Minus Hour", tint = Color.White)
                        }

                        Text(
                            text = formattedSleep,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        IconButton(
                            onClick = {
                                sleepHour = if (sleepHour == 23) 0 else sleepHour + 1
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E22))
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Plus Hour", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        listOf(21, 22, 23, 0).forEach { hr ->
                            val active = sleepHour == hr && sleepMin == 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) Color(0xFFFF8A80) else Color(0xFF141416))
                                    .border(0.5.dp, if (active) Color(0xFFFF8A80) else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        sleepHour = hr
                                        sleepMin = 0
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = String.format("%02d:00", hr),
                                    color = if (active) Color.Black else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save and Proceed Button
            Button(
                onClick = {
                    SleepTimeHelper.setWakeUpTime(context, formattedWake)
                    SleepTimeHelper.setSleepTime(context, formattedSleep)
                    viewModel.navigateTo(viewModel.getDefaultScreen())
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Apply Configurations")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Apply Optimizations & Enter",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}



// ==================== CONSOLIDATED FROM: CalendarView.kt ====================



enum class CalendarViewMode {
    YEAR, MONTH, WEEK, DAY
}

fun getTaskHourPrefix(description: String): String? {
    val regex = Regex("""\[Time: (\d{1,2}):(\d{2})\]""")
    val match = regex.find(description)
    if (match != null) {
        val hr = match.groupValues[1]
        val hourInt = hr.toIntOrNull() ?: return null
        return String.format(Locale.US, "%02d:00", hourInt)
    }
    
    val stringRegex = Regex("""\[Time: ([^\]]+)\]""")
    val stringMatch = stringRegex.find(description)
    if (stringMatch != null) {
        val timeStr = stringMatch.groupValues[1].trim()
        val hourMatch = Regex("""^(\d{1,2})""").find(timeStr)
        if (hourMatch != null) {
            val hourInt = hourMatch.groupValues[1].toIntOrNull()
            if (hourInt != null) {
                var finalHour = hourInt
                if (timeStr.uppercase(Locale.US).contains("PM") && finalHour < 12) {
                    finalHour += 12
                } else if (timeStr.uppercase(Locale.US).contains("AM") && finalHour == 12) {
                    finalHour = 0
                }
                return String.format(Locale.US, "%02d:00", finalHour)
            }
        }
    }
    return null
}

fun parseTaskTime(description: String): Pair<Int, Int>? {
    val amPmRegex = Regex("""\[Time:\s*(\d{1,2}):(\d{2})\s*(AM|PM)\]""", RegexOption.IGNORE_CASE)
    val amPmMatch = amPmRegex.find(description)
    if (amPmMatch != null) {
        var hour = amPmMatch.groupValues[1].toIntOrNull() ?: 0
        val minute = amPmMatch.groupValues[2].toIntOrNull() ?: 0
        val ampm = amPmMatch.groupValues[3].uppercase(Locale.US)
        if (ampm == "PM" && hour < 12) {
            hour += 12
        } else if (ampm == "AM" && hour == 12) {
            hour = 0
        }
        return Pair(hour, minute)
    }

    val stdRegex = Regex("""\[Time:\s*(\d{1,2}):(\d{2})\]""")
    val stdMatch = stdRegex.find(description)
    if (stdMatch != null) {
        val hour = stdMatch.groupValues[1].toIntOrNull() ?: 0
        val minute = stdMatch.groupValues[2].toIntOrNull() ?: 0
        return Pair(hour, minute)
    }

    val stringRegex = Regex("""\[Time:\s*([^\]]+)\]""")
    val stringMatch = stringRegex.find(description)
    if (stringMatch != null) {
        val timeStr = stringMatch.groupValues[1].trim()
        val match = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)?""", RegexOption.IGNORE_CASE).find(timeStr)
        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: 0
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val ampm = match.groupValues[3].uppercase(Locale.US)
            if (ampm == "PM" && hour < 12) {
                hour += 12
            } else if (ampm == "AM" && hour == 12) {
                hour = 0
            }
            return Pair(hour, minute)
        }
    }
    return null
}

fun parseTaskDuration(description: String): Int {
    val regex = Regex("""\[Duration:\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
    val match = regex.find(description)
    if (match != null) {
        val durationStr = match.groupValues[1].trim().lowercase(Locale.US)
        
        // Check for hour/hours/hr/hrs/h
        if (durationStr.contains("hour") || durationStr.contains("hr") || durationStr.contains("h")) {
            // Find the decimal number or integer before/in the unit
            val numRegex = Regex("""(\d+\.?\d*)""")
            val numMatch = numRegex.find(durationStr)
            if (numMatch != null) {
                val numFloat = numMatch.groupValues[1].toFloatOrNull()
                if (numFloat != null && numFloat > 0f) {
                    return (numFloat * 60).toInt()
                }
            }
        }
        
        // Otherwise, try to extract minutes
        val digits = durationStr.filter { it.isDigit() }
        val durationInt = digits.toIntOrNull()
        if (durationInt != null && durationInt > 0) {
            return durationInt
        }
    }
    return 15
}

fun formatTimeRange(startHour: Int, startMinute: Int, durationMins: Int, isAmPm: Boolean): String {
    val startTotal = startHour * 60 + startMinute
    val endTotal = (startTotal + durationMins) % (24 * 60)
    val endHour = endTotal / 60
    val endMinute = endTotal % 60

    return if (isAmPm) {
        val startAmPm = if (startHour >= 12) "PM" else "AM"
        val startDisp = when {
            startHour == 0 -> 12
            startHour > 12 -> startHour - 12
            else -> startHour
        }
        val endAmPm = if (endHour >= 12) "PM" else "AM"
        val endDisp = when {
            endHour == 0 -> 12
            endHour > 12 -> endHour - 12
            else -> endHour
        }
        String.format(Locale.US, "%d:%02d %s - %d:%02d %s", startDisp, startMinute, startAmPm, endDisp, endMinute, endAmPm)
    } else {
        String.format(Locale.US, "%02d:%02d - %02d:%02d", startHour, startMinute, endHour, endMinute)
    }
}

@Composable
fun CalendarView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val tasksAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.syncGoogleTasks(context) { }
        }
    }

    val systemCalendarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = (permissions[android.Manifest.permission.READ_CALENDAR] ?: false) &&
                          (permissions[android.Manifest.permission.WRITE_CALENDAR] ?: false)
            if (granted) {
                viewModel.syncGoogleCalendar(context)
            }
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            val hasTasksScope = account != null && com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(account, com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/tasks"))
            if (!hasTasksScope) {
                coroutineScope.launch {
                    com.example.util.GoogleTasksSyncManager.getAccessToken(context) { intent ->
                        tasksAuthLauncher.launch(intent)
                    }
                }
            }
        }
    )

    val calPrefs = remember { context.getSharedPreferences("app_calendar_prefs", android.content.Context.MODE_PRIVATE) }
    var showConnectBanner by remember {
        mutableStateOf(
            !calPrefs.getBoolean("gcal_connect_banner_dismissed", false) &&
            (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
             com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.let { account ->
                 !com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(account, com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/tasks"))
             } ?: true)
        )
    }

    val tasks by viewModel.tasks.collectAsState()
    val journalEntries by viewModel.journalEntries.collectAsState()

    val calendarViewModeStr by viewModel.calendarViewModeStr.collectAsState()
    val currentViewMode = when (calendarViewModeStr) {
        "Year" -> CalendarViewMode.YEAR
        "Week" -> CalendarViewMode.WEEK
        "Day" -> CalendarViewMode.DAY
        else -> CalendarViewMode.MONTH
    }
    
    var selectedMonthCalendar by remember {
        mutableStateOf(Calendar.getInstance())
    }

    val currentYear = selectedMonthCalendar.get(Calendar.YEAR)
    val currentMonth = selectedMonthCalendar.get(Calendar.MONTH) // 0-indexed

    // Generate dynamic header title based on view mode
    val headerText = remember(selectedMonthCalendar, currentViewMode) {
        when (currentViewMode) {
            CalendarViewMode.YEAR -> {
                val sdf = SimpleDateFormat("yyyy", Locale.getDefault())
                sdf.format(selectedMonthCalendar.time)
            }
            CalendarViewMode.MONTH -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                sdf.format(selectedMonthCalendar.time)
            }
            CalendarViewMode.WEEK -> {
                val startCal = Calendar.getInstance().apply {
                    time = selectedMonthCalendar.time
                    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                }
                val endCal = Calendar.getInstance().apply {
                    time = startCal.time
                    add(Calendar.DAY_OF_MONTH, 6)
                }
                val sdfStr = SimpleDateFormat("MMM d", Locale.getDefault())
                val sdfYear = SimpleDateFormat("yyyy", Locale.getDefault())
                "${sdfStr.format(startCal.time)} - ${sdfStr.format(endCal.time)}, ${sdfYear.format(endCal.time)}"
            }
            CalendarViewMode.DAY -> {
                val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                sdf.format(selectedMonthCalendar.time)
            }
        }
    }

    // Helper function for navigating date forward/backward
    fun navigate(delta: Int) {
        val newCal = Calendar.getInstance().apply {
            time = selectedMonthCalendar.time
            when (currentViewMode) {
                CalendarViewMode.YEAR -> add(Calendar.YEAR, delta)
                CalendarViewMode.MONTH -> add(Calendar.MONTH, delta)
                CalendarViewMode.WEEK -> add(Calendar.WEEK_OF_YEAR, delta)
                CalendarViewMode.DAY -> add(Calendar.DAY_OF_YEAR, delta)
            }
        }
        selectedMonthCalendar = newCal
    }

    // Generate month view grid days (42 cells starting from padded previous month)
    val gridDates = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Offset for Sunday start: Sunday is 1, so offset = firstDayOfWeek - 1
        val prevMonthPadding = firstDayOfWeek - 1
        
        val gridCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -prevMonthPadding)
        }
        
        List(42) {
            val d = gridCal.time
            gridCal.add(Calendar.DAY_OF_MONTH, 1)
            d
        }
    }

    // Generate week view days (7 days Sunday - Saturday)
    val weekDates = remember(selectedMonthCalendar) {
        val cal = Calendar.getInstance().apply {
            time = selectedMonthCalendar.time
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        }
        List(7) {
            val d = cal.time
            cal.add(Calendar.DAY_OF_MONTH, 1)
            d
        }
    }

    // Modal dialog state for viewing and managing tasks of a specific clicked day
    var selectedDayForDetail by remember { mutableStateOf<Date?>(null) }
    var selectedTaskForEdit by remember { mutableStateOf<Task?>(null) }

    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp)
            .pointerInput(currentViewMode) {
                detectDragGestures(
                    onDragEnd = {
                        if (totalDragAmount < -60f) {
                            navigate(1)
                        } else if (totalDragAmount > 60f) {
                            navigate(-1)
                        }
                        totalDragAmount = 0f
                    },
                    onDragCancel = {
                        totalDragAmount = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragAmount += dragAmount.x
                    }
                )
            }
    ) {
        
        // Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // VIEW DROPDOWN BETWEEN MONTH YEAR AND TODAY
            var expanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier.wrapContentSize(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF151515))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = calendarViewModeStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select View Mode",
                        tint = WaterBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF222225))
                ) {
                    listOf("Year", "Month", "Week", "Day").forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode,
                                    color = if (calendarViewModeStr == mode) WaterBlue else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            },
                            onClick = {
                                viewModel.setCalendarViewModeStr(mode)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navigate(-1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Previous",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(2.dp))

                Button(
                    onClick = { selectedMonthCalendar = Calendar.getInstance() },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Today", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(
                    onClick = { navigate(1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Next",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Automatic and silent Google Calendar and Google Tasks Sync when screen is opened
        LaunchedEffect(Unit) {
            val hasRead = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasWrite = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasRead && hasWrite) {
                viewModel.syncGoogleCalendar(context)
            }
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            val hasTasksScope = account != null && com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(account, com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/tasks"))
            if (hasTasksScope) {
                viewModel.syncGoogleTasks(context) { }
            }
        }

        if (showConnectBanner) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, WaterBlue.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Sync",
                                tint = WaterBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Connect Google Calendar & Tasks",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                calPrefs.edit().putBoolean("gcal_connect_banner_dismissed", true).apply()
                                showConnectBanner = false
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect to view and manage your timeline directly synchronized with Google Calendar and tasks with Google Tasks.",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                calPrefs.edit().putBoolean("gcal_connect_banner_dismissed", true).apply()
                                showConnectBanner = false
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("No thanks", color = Color.Gray, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                calPrefs.edit().putBoolean("gcal_connect_banner_dismissed", true).apply()
                                showConnectBanner = false
                                systemCalendarLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_CALENDAR,
                                        android.Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Connect", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Main Calendar content container card
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                when (currentViewMode) {
                    CalendarViewMode.YEAR -> {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val isCompact = maxWidth < 480.dp
                            val isLarge = maxWidth >= 840.dp
                            val columnsCount = when {
                                isCompact -> 1
                                isLarge -> 4
                                else -> 3
                            }
                            val rowsCount = (12 + columnsCount - 1) / columnsCount

                            var totalDragX by remember { mutableStateOf(0f) }
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .pointerInput(currentYear) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { totalDragX = 0f },
                                            onDragEnd = {
                                                if (totalDragX > 150) {
                                                    navigate(-1)
                                                } else if (totalDragX < -150) {
                                                    navigate(1)
                                                }
                                            },
                                            onDragCancel = { totalDragX = 0f },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                totalDragX += dragAmount
                                            }
                                        )
                                    },
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (rowIdx in 0 until rowsCount) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        for (colIdx in 0 until columnsCount) {
                                            val monthIndex = rowIdx * columnsCount + colIdx
                                            if (monthIndex < 12) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    MiniMonthGrid(
                                                        year = currentYear,
                                                        month = monthIndex,
                                                        tasks = tasks,
                                                        onMonthClick = {
                                                            selectedMonthCalendar = Calendar.getInstance().apply {
                                                                set(Calendar.YEAR, currentYear)
                                                                set(Calendar.MONTH, monthIndex)
                                                                set(Calendar.DAY_OF_MONTH, 1)
                                                            }
                                                            viewModel.setCalendarViewModeStr("Month")
                                                        },
                                                        onDayClick = { day ->
                                                            selectedMonthCalendar = Calendar.getInstance().apply {
                                                                set(Calendar.YEAR, currentYear)
                                                                set(Calendar.MONTH, monthIndex)
                                                                set(Calendar.DAY_OF_MONTH, day)
                                                            }
                                                            viewModel.setCalendarViewModeStr("Day")
                                                        }
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // MONTH VIEW
                    CalendarViewMode.MONTH -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Days of Week Header Indicators
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { wd ->
                                    Text(
                                        text = wd,
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Grid Weeks (6 rows)
                            val chunkedWeeks = gridDates.chunked(7)
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                chunkedWeeks.forEach { week ->
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        week.forEach { date ->
                                            val cellCal = Calendar.getInstance().apply { time = date }
                                            val cellYear = cellCal.get(Calendar.YEAR)
                                            val cellMonth = cellCal.get(Calendar.MONTH)
                                            val cellDay = cellCal.get(Calendar.DAY_OF_MONTH)

                                            val isInCurrentMonth = cellMonth == currentMonth && cellYear == currentYear
                                            
                                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                                            val dayTasks = tasks.filter { it.dueDateString == dateStr }

                                            val matchingJournalEntry = journalEntries.find { it.dateString == dateStr }
                                            val photoUrl = matchingJournalEntry?.attachmentsJson
                                                ?.split("|")
                                                ?.find { it.trim().startsWith("photo:") }
                                                ?.replace("photo:", "")
                                                ?.trim()
                                            
                                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                            val isToday = dateStr == todayStr

                                            val cellBg = when {
                                                isToday -> WaterBlue.copy(alpha = 0.15f)
                                                !isInCurrentMonth -> Color(0xFF141416)
                                                else -> SurfaceCard
                                            }
                                            val borderClr = if (isToday) WaterBlue else Color.Transparent

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .border(1.dp, borderClr, RoundedCornerShape(6.dp))
                                                    .background(cellBg)
                                                    .clickable {
                                                        val targetCal = Calendar.getInstance().apply { time = date }
                                                        selectedMonthCalendar = targetCal
                                                        viewModel.setCalendarViewModeStr("Day")
                                                    }
                                                    .padding(4.dp)
                                            ) {
                                                if (!photoUrl.isNullOrEmpty()) {
                                                    AsyncImage(
                                                        model = photoUrl,
                                                        contentDescription = "Journal Day photo background",
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color.Black.copy(alpha = 0.5f))
                                                    )
                                                }
                                                Column(modifier = Modifier.fillMaxSize()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = cellDay.toString(),
                                                            color = if (isInCurrentMonth) Color.White else Color.DarkGray,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (dayTasks.isNotEmpty()) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(WaterBlue)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        val displayedTasks = dayTasks.take(2)
                                                        displayedTasks.forEach { task ->
                                                            val priorityColor = when (task.priority.uppercase()) {
                                                                "HIGH" -> Color(0xFFF44336)
                                                                "LOW" -> Color(0xFF4CAF50)
                                                                else -> WaterBlue
                                                            }
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clip(RoundedCornerShape(3.dp))
                                                                    .background(priorityColor.copy(alpha = 0.1f))
                                                                    .padding(horizontal = 3.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = task.title,
                                                                    color = if (task.isCompleted) Color.Gray else Color.White,
                                                                    fontSize = 8.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                                                    )
                                                                )
                                                            }
                                                        }
                                                        if (dayTasks.size > 2) {
                                                            Text(
                                                                text = "+${dayTasks.size - 2} more",
                                                                color = Color.Gray,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 2.dp)
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

                    // WEEK VIEW - Scrolling column of days with clean visual schedules
                    CalendarViewMode.WEEK -> {
                        val hourHeight = 60.dp
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0C0C0D))
                        ) {
                            // Header Row containing Sunday to Saturday days
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0C0C0D))
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Spacer with GMT indicator
                                Box(
                                    modifier = Modifier.width(70.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "GMT+05:30",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // 7 column headers
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    weekDates.forEach { date ->
                                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                                        val isToday = dateStr == SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        val dayName = SimpleDateFormat("EEE", Locale.US).format(date).uppercase()
                                        val dayNum = SimpleDateFormat("d", Locale.US).format(date)
                                        val hasTasks = tasks.any { it.dueDateString == dateStr }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedDayForDetail = date },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = dayName,
                                                color = if (isToday) WaterBlue else Color.Gray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isToday) WaterBlue else Color.Transparent),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dayNum,
                                                    color = if (isToday) Color.Black else Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            if (hasTasks) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(WaterBlue)
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.height(5.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Divider(color = Color(0xFF222225), thickness = 0.5.dp)

                            // Main Scrollable Grid area
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Left hours index column (12 AM to 11 PM or 24 hours)
                                    Column(
                                        modifier = Modifier.width(70.dp)
                                    ) {
                                        (0..23).forEach { hour ->
                                            Box(
                                                modifier = Modifier
                                                    .height(hourHeight)
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp),
                                                contentAlignment = Alignment.TopCenter
                                            ) {
                                                val label = when {
                                                    hour == 0 -> "12 AM"
                                                    hour == 12 -> "12 PM"
                                                    hour < 12 -> "$hour AM"
                                                    else -> "${hour - 12} PM"
                                                }
                                                Text(
                                                    text = label,
                                                    color = Color.Gray,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }

                                    // Right side schedule timeline with columns and task cards
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(hourHeight * 24)
                                    ) {
                                        // Hourly grid lines
                                        Column {
                                            (0..23).forEach { _ ->
                                                Box(
                                                    modifier = Modifier
                                                        .height(hourHeight)
                                                        .fillMaxWidth()
                                                ) {
                                                    Divider(
                                                        color = Color(0xFF222225),
                                                        thickness = 0.5.dp,
                                                        modifier = Modifier.align(Alignment.BottomStart)
                                                    )
                                                }
                                            }
                                        }

                                        // Vertical day separator grid lines
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            (0..6).forEach { _ ->
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                ) {
                                                    Divider(
                                                        color = Color(0xFF222225),
                                                        thickness = 0.5.dp,
                                                        modifier = Modifier.align(Alignment.CenterEnd)
                                                    )
                                                }
                                            }
                                        }

                                        // Day Tasks mapping onto the grid columns
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            weekDates.forEachIndexed { dayIdx, date ->
                                                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                                                val dayTasks = tasks.filter { it.dueDateString == dateStr }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                ) {
                                                    dayTasks.forEach { task ->
                                                        val timeParts = parseTaskTime(task.description)
                                                        if (timeParts != null) {
                                                            val (startHour, startMinute) = timeParts
                                                            val durationMins = parseTaskDuration(task.description)

                                                            val startMinutesOfDay = startHour * 60 + startMinute
                                                            val topOffsetDp = (startMinutesOfDay / 60f) * hourHeight.value
                                                            val heightDp = (durationMins / 60f) * hourHeight.value

                                                            val categoryColor = when (task.priority.uppercase()) {
                                                                "HIGH" -> Color(0xFFD32F2F)
                                                                "LOW" -> Color(0xFF388E3C)
                                                                else -> WaterBlue
                                                            }

                                                            Card(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 2.dp)
                                                                    .offset(y = topOffsetDp.dp)
                                                                    .height(heightDp.dp)
                                                                    .clickable { selectedTaskForEdit = task },
                                                                colors = CardDefaults.cardColors(
                                                                    containerColor = categoryColor.copy(alpha = 0.25f)
                                                                ),
                                                                border = BorderStroke(0.5.dp, categoryColor.copy(alpha = 0.8f)),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .padding(4.dp),
                                                                    verticalArrangement = Arrangement.Center
                                                                ) {
                                                                    Text(
                                                                        text = task.title,
                                                                        color = Color.White,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                    if (heightDp >= 30) {
                                                                        val timeLabel = String.format(Locale.US, "%02d:%02d", if (startHour > 12) startHour - 12 else if (startHour == 0) 12 else startHour, startMinute) + (if (startHour >= 12) " PM" else " AM")
                                                                        Text(
                                                                            text = timeLabel,
                                                                            color = Color.LightGray,
                                                                            fontSize = 8.sp,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Current Time Line across the active day
                                        val now = Calendar.getInstance()
                                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
                                        val todayIdx = weekDates.indexOfFirst { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) == todayStr }
                                        if (todayIdx != -1) {
                                            val currentHour = now.get(Calendar.HOUR_OF_DAY)
                                            val currentMinute = now.get(Calendar.MINUTE)
                                            val nowMinutesOfDay = currentHour * 60 + currentMinute
                                            val indicatorYOffsetDp = (nowMinutesOfDay / 60f) * hourHeight.value

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(y = indicatorYOffsetDp.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Align with today's column precisely
                                                    Spacer(modifier = Modifier.weight(todayIdx.toFloat() + 0.05f))

                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFFF5252))
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .weight(0.9f)
                                                            .height(1.5.dp)
                                                            .background(Color(0xFFFF5252))
                                                    )

                                                    Spacer(modifier = Modifier.weight((6 - todayIdx).toFloat() + 0.05f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // DAY VIEW - Detailed schedule timeline with hourly blocks. Interlinked with task creation.
                    CalendarViewMode.DAY -> {
                        val activeDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedMonthCalendar.time)
                        val dayTasks = tasks.filter { it.dueDateString == activeDateStr }
                        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val isToday = activeDateStr == todayStr

                        // Day Header info matching screenshot 1 (TUE above, large 16 circle)
                        val dayOfWeekLabel = SimpleDateFormat("EEE", Locale.getDefault()).format(selectedMonthCalendar.time).uppercase()
                        val dayNumLabel = SimpleDateFormat("d", Locale.getDefault()).format(selectedMonthCalendar.time)

                        val scrollState = rememberScrollState()

                        // Calendar Optimization & Notification settings variables
                        val wakeUpTime = com.example.util.SleepTimeHelper.getWakeUpTime(context) ?: "07:00"
                        val sleepTime = com.example.util.SleepTimeHelper.getSleepTime(context) ?: "22:00"
                        val wakeUpHour = wakeUpTime.split(":").firstOrNull()?.toIntOrNull() ?: 7
                        val sleepHour = sleepTime.split(":").firstOrNull()?.toIntOrNull() ?: 22

                        var showEarlyHours by remember { mutableStateOf(false) }
                        var showLateHours by remember { mutableStateOf(false) }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Day Identifier block
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = dayOfWeekLabel,
                                        color = if (isToday) WaterBlue else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(if (isToday) WaterBlue else SurfaceCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNumLabel,
                                            color = if (isToday) Color.Black else Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val readableDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(selectedMonthCalendar.time)
                                    Text(
                                        text = "DAILY TIMELINE",
                                        color = WaterBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.2.sp
                                    )
                                    Text(
                                        text = readableDate,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Horizontal Divider
                            Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 1.dp)

                            // Calendar list of hours
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                ) {
                                    // Header of hour table GMT indicator
                                    val tzOffset = remember {
                                        try {
                                            val tz = java.util.TimeZone.getDefault()
                                            val shortName = tz.getDisplayName(false, java.util.TimeZone.SHORT)
                                            shortName
                                        } catch (e: Exception) {
                                            "GMT-04"
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 1. "Above Wake-Up Time" button if there are early hours and they are not shown yet
                                    if (!showEarlyHours && wakeUpHour > 0) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                                .clickable { showEarlyHours = true },
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                                            border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "Show earlier hours",
                                                    tint = WaterBlue,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Show hours before Wake-Up Time ($wakeUpTime) [ABOVE ALL TIMES]",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    // Generate active hours based on sleep optimization settings
                                    val earlyHours = if (showEarlyHours) (0 until wakeUpHour).toList() else emptyList()
                                    val coreHours = (wakeUpHour..sleepHour).toList()
                                    val lateHours = if (showLateHours) ((sleepHour + 1)..23).toList() else emptyList()
                                    val hoursToRender = earlyHours + coreHours + lateHours

                                    val hourHeight = 64.dp
                                    val totalGridHeight = hourHeight * hoursToRender.size

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(totalGridHeight)
                                    ) {
                                        // 1. Column of Background Hourly Rows (keeps clickability and time labels!)
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            hoursToRender.forEach { hrInt ->
                                                val hrStr = String.format(Locale.US, "%02d:00", hrInt)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(hourHeight)
                                                        .clickable {
                                                            viewModel.triggerTaskCreationRedirect(activeDateStr, hrStr, "Inbox")
                                                        },
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    // Time Label on Left
                                                    Box(
                                                        modifier = Modifier
                                                            .width(64.dp)
                                                            .fillMaxHeight()
                                                            .padding(end = 8.dp, top = 4.dp),
                                                        contentAlignment = Alignment.TopEnd
                                                    ) {
                                                        Text(
                                                            text = if (hrInt == 0) tzOffset else hrStr,
                                                            color = Color.Gray,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (hrInt == 0) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                    }

                                                    // Timeline track background on Right
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight()
                                                    ) {
                                                        Divider(
                                                            color = Color.Gray.copy(alpha = 0.15f),
                                                            thickness = 1.dp,
                                                            modifier = Modifier.align(Alignment.TopStart)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 2. Overlay of Scheduled Task Cards drawn with absolute offsets and heights based on duration
                                        BoxWithConstraints(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(start = 64.dp)
                                        ) {
                                            val firstRenderedHour = hoursToRender.firstOrNull() ?: 0

                                            var draggingTaskId by remember { mutableStateOf<Int?>(null) }
                                            var dragOffsetPx by remember { mutableStateOf(0f) }

                                            // Extract and parse valid timed tasks
                                            val activeDateTasksWithTimes = dayTasks.mapNotNull { task ->
                                                val timeParts = parseTaskTime(task.description)
                                                if (timeParts != null) {
                                                    val (startHour, startMinute) = timeParts
                                                    if (startHour in hoursToRender) {
                                                        val durationMins = parseTaskDuration(task.description)
                                                        val startMinutes = startHour * 60 + startMinute
                                                        val endMinutes = startMinutes + durationMins
                                                        Triple(task, startMinutes, endMinutes)
                                                    } else null
                                                } else null
                                            }.sortedBy { it.second }

                                            // Assign tasks to horizontal columns to handle overlaps gracefully
                                            val columns = mutableListOf<MutableList<Triple<Task, Int, Int>>>()
                                            val taskToColumn = mutableMapOf<Task, Int>()

                                            activeDateTasksWithTimes.forEach { triple ->
                                                val (task, start, end) = triple
                                                var placed = false
                                                for (colIdx in columns.indices) {
                                                    val colTasks = columns[colIdx]
                                                    val hasOverlap = colTasks.any { (_, s, e) ->
                                                        maxOf(start, s) < minOf(end, e)
                                                    }
                                                    if (!hasOverlap) {
                                                        colTasks.add(triple)
                                                        taskToColumn[task] = colIdx
                                                        placed = true
                                                        break
                                                    }
                                                }
                                                if (!placed) {
                                                    columns.add(mutableListOf(triple))
                                                    taskToColumn[task] = columns.size - 1
                                                }
                                            }

                                            // Render each card
                                            activeDateTasksWithTimes.forEach { triple ->
                                                val (task, start, end) = triple
                                                val startHour = start / 60
                                                val startMinute = start % 60
                                                val durationMins = end - start

                                                val colIdx = taskToColumn[task] ?: 0
                                                val overlappingTasks = activeDateTasksWithTimes.filter { (_, s, e) ->
                                                    maxOf(start, s) < minOf(end, e)
                                                }
                                                val maxColAmongOverlaps = overlappingTasks.map { taskToColumn[it.first] ?: 0 }.maxOrNull() ?: 0
                                                val numSlices = maxColAmongOverlaps + 1

                                                val trackWidth = maxWidth - 24.dp
                                                val cardWidth = trackWidth / numSlices
                                                val leftOffset = 8.dp + cardWidth * colIdx

                                                val relativeStartMinutes = (startHour - firstRenderedHour) * 60 + startMinute
                                                val topOffsetDp = (relativeStartMinutes / 60f) * hourHeight.value
                                                val heightDp = (durationMins / 60f) * hourHeight.value

                                                val isDragging = task.id == draggingTaskId
                                                val dragOffsetDp = if (isDragging) {
                                                    with(androidx.compose.ui.platform.LocalDensity.current) { dragOffsetPx.toDp() }
                                                } else {
                                                    0.dp
                                                }
                                                val finalTopOffset = topOffsetDp.dp + dragOffsetDp

                                                val isAmPm = task.description.contains(Regex("""\[Time:\s*\d{1,2}:\d{2}\s*(AM|PM)\]""", RegexOption.IGNORE_CASE))

                                                val liveDeltaMinutes = if (isDragging) {
                                                    val density = androidx.compose.ui.platform.LocalDensity.current
                                                    val hourHeightPx = with(density) { hourHeight.toPx() }
                                                    val minutesPerPx = 60f / hourHeightPx
                                                    val deltaMins = (dragOffsetPx * minutesPerPx).toInt()
                                                    Math.round(deltaMins / 5f) * 5
                                                } else {
                                                    0
                                                }

                                                val liveStartMinutes = (startHour * 60 + startMinute + liveDeltaMinutes).coerceIn(0, 23 * 60 + 55)
                                                val liveStartHour = liveStartMinutes / 60
                                                val liveStartMinute = liveStartMinutes % 60

                                                val liveTimeRangeText = formatTimeRange(liveStartHour, liveStartMinute, durationMins, isAmPm)

                                                Card(
                                                    modifier = Modifier
                                                        .width(cardWidth)
                                                        .offset(x = leftOffset, y = finalTopOffset)
                                                        .height(heightDp.dp)
                                                        .shadow(
                                                            elevation = if (isDragging) 8.dp else 0.dp,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .pointerInput(task.id) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = {
                                                                    draggingTaskId = task.id
                                                                    dragOffsetPx = 0f
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    dragOffsetPx += dragAmount.y
                                                                },
                                                                onDragEnd = {
                                                                    val density = this
                                                                    val hourHeightPx = density.run { hourHeight.toPx() }
                                                                    val minutesPerPx = 60f / hourHeightPx
                                                                    val deltaMinutes = (dragOffsetPx * minutesPerPx).toInt()
                                                                    val snappedDeltaMinutes = Math.round(deltaMinutes / 5f) * 5

                                                                    val currentStartMinutes = startHour * 60 + startMinute
                                                                    val newStartMinutes = (currentStartMinutes + snappedDeltaMinutes).coerceIn(0, 23 * 60 + 55)
                                                                    val newHour = newStartMinutes / 60
                                                                    val newMinute = newStartMinutes % 60

                                                                    val isAmPm = task.description.contains(Regex("""\[Time:\s*\d{1,2}:\d{2}\s*(AM|PM)\]""", RegexOption.IGNORE_CASE))
                                                                    val newTimeStr = if (isAmPm) {
                                                                        val ampm = if (newHour >= 12) "PM" else "AM"
                                                                        val displayHour = when {
                                                                            newHour == 0 -> 12
                                                                            newHour > 12 -> newHour - 12
                                                                            else -> newHour
                                                                        }
                                                                        String.format(Locale.US, "%d:%02d %s", displayHour, newMinute, ampm)
                                                                    } else {
                                                                        String.format(Locale.US, "%02d:%02d", newHour, newMinute)
                                                                    }

                                                                    val timeRegex = Regex("""\[Time:\s*[^\]]+\]""")
                                                                    var updatedDesc = task.description
                                                                    if (updatedDesc.contains(timeRegex)) {
                                                                        updatedDesc = updatedDesc.replace(timeRegex, "[Time: $newTimeStr]")
                                                                    } else {
                                                                        updatedDesc = updatedDesc.trim() + "\n[Time: $newTimeStr]"
                                                                    }

                                                                    viewModel.updateTask(task.copy(description = updatedDesc))

                                                                    draggingTaskId = null
                                                                    dragOffsetPx = 0f
                                                                },
                                                                onDragCancel = {
                                                                    draggingTaskId = null
                                                                    dragOffsetPx = 0f
                                                                }
                                                            )
                                                        }
                                                        .clickable(enabled = !isDragging) {
                                                            selectedTaskForEdit = task
                                                        },
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isDragging) {
                                                            Color(0xFF202025)
                                                        } else if (task.isCompleted) {
                                                            Color(0xFF141416)
                                                        } else {
                                                            SurfaceCard
                                                        }
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = if (isDragging) {
                                                        BorderStroke(1.5.dp, WaterBlue)
                                                    } else if (task.isCompleted) {
                                                        null
                                                     } else {
                                                         BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f))
                                                     }
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    when (task.priority.uppercase()) {
                                                                        "HIGH" -> Color(0xFFF44336)
                                                                        "LOW" -> Color(0xFF4CAF50)
                                                                        else -> WaterBlue
                                                                    }
                                                                )
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            if (heightDp >= 32) {
                                                                Text(
                                                                    text = liveTimeRangeText,
                                                                    color = WaterBlue.copy(alpha = 0.9f),
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 10.sp,
                                                                    modifier = Modifier.padding(bottom = 1.dp)
                                                                )
                                                            }
                                                            Text(
                                                                text = task.title,
                                                                color = if (task.isCompleted) Color.Gray else Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp,
                                                                maxLines = if (heightDp >= 50) 2 else 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                                                )
                                                            )
                                                            if (task.description.isNotEmpty() && heightDp >= 50) {
                                                                Text(
                                                                    text = task.description.replace(Regex("""\[[^\]]+\]"""), "").trim(),
                                                                    color = Color.Gray,
                                                                    fontSize = 10.sp,
                                                                    maxLines = if (heightDp >= 60) 2 else 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // 3. Current relative time pointer red line precisely overlayed if today
                                            if (isToday) {
                                                val c = Calendar.getInstance()
                                                val nowHr = c.get(Calendar.HOUR_OF_DAY)
                                                val nowMin = c.get(Calendar.MINUTE)

                                                if (nowHr in hoursToRender) {
                                                    val relativeNowMinutes = (nowHr - firstRenderedHour) * 60 + nowMin
                                                    val indicatorYOffsetDp = (relativeNowMinutes / 60f) * hourHeight.value

                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .offset(y = indicatorYOffsetDp.dp)
                                                    ) {
                                                        Divider(
                                                            color = Color(0xFFEA4335),
                                                            thickness = 1.8.dp,
                                                            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart)
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(7.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFFEA4335))
                                                                .align(Alignment.CenterStart)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 3. "Below Sleep Time" button if there are late hours and they are not shown yet
                                    if (!showLateHours && sleepHour < 23) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                                .clickable { showLateHours = true },
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                                            border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Show later hours",
                                                    tint = WaterBlue,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Show hours after Sleep Time ($sleepTime) [MORE THAN END TIME]",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }

                            Button(
                                onClick = { viewModel.triggerTaskCreationRedirect(activeDateStr, null, "Inbox") },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Task for $activeDateStr", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Day Detail Dialog - Opens on clicking day elements
    selectedDayForDetail?.let { date ->
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        val formattedHeader = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(date)
        val dayTasks = tasks.filter { it.dueDateString == dateStr }

        AlertDialog(
            onDismissRequest = { selectedDayForDetail = null },
            title = {
                Column {
                    Text("Day Details", fontSize = 12.sp, color = WaterBlue, fontWeight = FontWeight.Bold)
                    Text(formattedHeader, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (dayTasks.isEmpty()) {
                         Box(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(vertical = 12.dp),
                             contentAlignment = Alignment.Center
                         ) {
                             Text("No tasks scheduled for this day.", color = Color.Gray, fontSize = 13.sp)
                         }
                    } else {
                        dayTasks.forEach { task ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedTaskForEdit = task
                                },
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = if (task.isCompleted) null else BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = task.isCompleted,
                                            onCheckedChange = { checked ->
                                                viewModel.updateTask(task.copy(isCompleted = checked))
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = WaterBlue,
                                                uncheckedColor = Color.Gray
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = task.title,
                                                color = if (task.isCompleted) Color.Gray else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                                )
                                            )
                                            if (task.description.isNotEmpty()) {
                                                Text(
                                                    text = task.description,
                                                    color = Color.Gray,
                                                    fontSize = 11.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    
                                    val priorityLabel = task.priority.uppercase()
                                    val priorityColor = when (priorityLabel) {
                                        "HIGH" -> Color(0xFFF44336)
                                        "LOW" -> Color(0xFF4CAF50)
                                        else -> WaterBlue
                                    }
                                    Box(
                                         modifier = Modifier
                                             .clip(RoundedCornerShape(4.dp))
                                             .background(priorityColor.copy(alpha = 0.15f))
                                             .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(priorityLabel, color = priorityColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Quick add task button redirecting
                    Button(
                        onClick = {
                            viewModel.triggerTaskCreationRedirect(dateStr, null, "Inbox")
                            selectedDayForDetail = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Task for This Day", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDayForDetail = null }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    selectedTaskForEdit?.let { task ->
        TaskDetailsAndEditDialog(
            task = task,
            onDismiss = { selectedTaskForEdit = null },
            onSave = { updatedTask ->
                viewModel.updateTask(updatedTask)
                selectedTaskForEdit = null
                // Force selectedDayForDetail update if it was open to show latest changes
                if (selectedDayForDetail != null) {
                    val currentSelectedDay = selectedDayForDetail
                    selectedDayForDetail = null
                    selectedDayForDetail = currentSelectedDay
                }
            },
            onDelete = { taskToDelete ->
                viewModel.deleteTask(taskToDelete)
                selectedTaskForEdit = null
                // Force selectedDayForDetail update if it was open
                if (selectedDayForDetail != null) {
                    val currentSelectedDay = selectedDayForDetail
                    selectedDayForDetail = null
                    selectedDayForDetail = currentSelectedDay
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailsAndEditDialog(
    task: Task,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (Task) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    
    // Extract metadata from description
    val originalCleanDesc = remember(task.description) {
        task.description
            .replace(Regex("""\[Time:\s*[^\]]+\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[Duration:\s*[^\]]+\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[Reminders:\s*[^\]]+\]""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
    var cleanDescription by remember { mutableStateOf(originalCleanDesc) }
    
    val parsedTime = remember(task.description) { parseTaskTime(task.description) ?: Pair(9, 0) }
    var hour by remember { mutableStateOf(parsedTime.first) }
    var minute by remember { mutableStateOf(parsedTime.second) }
    
    val parsedDuration = remember(task.description) { parseTaskDuration(task.description) }
    var durationMinutes by remember { mutableStateOf(parsedDuration) }
    
    var priority by remember { mutableStateOf(task.priority.uppercase()) }
    var isCompleted by remember { mutableStateOf(task.isCompleted) }
    var dueDateString by remember { mutableStateOf(task.dueDateString) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Task Details", fontWeight = FontWeight.Bold, color = Color.White)
        },
        containerColor = Color(0xFF141419),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = WaterBlue,
                        unfocusedLabelColor = Color.Gray
                    )
                )
                
                OutlinedTextField(
                    value = cleanDescription,
                    onValueChange = { cleanDescription = it },
                    label = { Text("Description") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = WaterBlue,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                // Date field
                OutlinedTextField(
                    value = dueDateString,
                    onValueChange = { dueDateString = it },
                    label = { Text("Due Date (YYYY-MM-DD)") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = WaterBlue,
                        unfocusedLabelColor = Color.Gray
                    )
                )
                
                // Status Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCompleted = !isCompleted }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = isCompleted,
                        onCheckedChange = { isCompleted = it },
                        colors = CheckboxDefaults.colors(checkedColor = WaterBlue, uncheckedColor = Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Completed", color = Color.White, fontSize = 14.sp)
                }
                
                Divider(color = Color(0xFF222225), thickness = 0.5.dp)

                // Time picker fields
                Text("Start Time & Timing", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var hourText by remember { mutableStateOf(hour.toString()) }
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { newValue ->
                            val clean = newValue.filter { it.isDigit() }
                            hourText = clean
                            clean.toIntOrNull()?.let {
                                hour = it.coerceIn(0, 23)
                            }
                        },
                        label = { Text("Hour (24h)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    var minuteText by remember { mutableStateOf(String.format(Locale.US, "%02d", minute)) }
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { newValue ->
                            val clean = newValue.filter { it.isDigit() }
                            minuteText = clean
                            clean.toIntOrNull()?.let {
                                minute = it.coerceIn(0, 59)
                            }
                        },
                        label = { Text("Minute") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }

                // Duration Selector
                var durationText by remember { mutableStateOf(durationMinutes.toString()) }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { newValue ->
                        val clean = newValue.filter { it.isDigit() }
                        durationText = clean
                        clean.toIntOrNull()?.let {
                            durationMinutes = it.coerceAtLeast(15)
                        }
                    },
                    label = { Text("Duration (minutes)") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Divider(color = Color(0xFF222225), thickness = 0.5.dp)

                // Priority Selection
                Text("Priority Level", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val priorities = listOf("LOW", "MEDIUM", "HIGH")
                    priorities.forEach { p ->
                        val isSel = p == priority
                        val btnBg = when (p) {
                            "HIGH" -> if (isSel) Color(0xFFF44336) else Color(0xFFF44336).copy(alpha = 0.15f)
                            "LOW" -> if (isSel) Color(0xFF4CAF50) else Color(0xFF4CAF50).copy(alpha = 0.15f)
                            else -> if (isSel) WaterBlue else WaterBlue.copy(alpha = 0.15f)
                        }
                        val textClr = if (isSel) Color.Black else Color.White
                        Button(
                            onClick = { priority = p },
                            modifier = Modifier.weight(1f).height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = btnBg),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(p, color = textClr, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete Button on the left
                IconButton(
                    onClick = { onDelete(task) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF44336))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White)
                    }
                    Button(
                        onClick = {
                            val timeTag = "[Time: ${String.format(Locale.US, "%02d:%02d", hour, minute)}]"
                            val durationTag = "[Duration: ${durationMinutes}m]"
                            val finalDesc = if (cleanDescription.isEmpty()) {
                                "$timeTag $durationTag"
                            } else {
                                "$cleanDescription\n$timeTag $durationTag"
                            }
                            onSave(
                                task.copy(
                                    title = title,
                                    description = finalDesc,
                                    priority = priority,
                                    isCompleted = isCompleted,
                                    dueDateString = dueDateString,
                                    estimatedMinutes = durationMinutes
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

@Composable
fun MiniMonthGrid(
    year: Int,
    month: Int,
    tasks: List<Task>,
    onMonthClick: () -> Unit,
    onDayClick: (Int) -> Unit
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, etc.
    val prevPadding = firstDayDayOfWeek - 1 // Sunday start offset
    
    val totalCells = prevPadding + daysInMonth
    val weeksCount = (totalCells + 6) / 7
    
    val monthName = remember(month) {
        val formatter = SimpleDateFormat("MMMM", Locale.getDefault())
        cal.set(Calendar.MONTH, month)
        formatter.format(cal.time)
    }
    
    val todayCal = remember { Calendar.getInstance() }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMonthClick() },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, Color(0xFF222225)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Month Heading
            Text(
                text = monthName.uppercase(),
                color = WaterBlue,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // Days of week initials (S M T W T F S)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        color = Color.Gray,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // 5 or 6 rows of mini dates
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                for (weekIdx in 0 until weeksCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        for (dayOfWeekIdx in 0..6) {
                            val cellIndex = weekIdx * 7 + dayOfWeekIdx
                            val dayNumber = cellIndex - prevPadding + 1
                            
                            if (dayNumber in 1..daysInMonth) {
                                val isToday = todayCal.get(Calendar.YEAR) == year &&
                                        todayCal.get(Calendar.MONTH) == month &&
                                        todayCal.get(Calendar.DAY_OF_MONTH) == dayNumber
                                        
                                val cellDateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayNumber)
                                val hasTasks = remember(cellDateStr, tasks) {
                                    tasks.any { it.dueDateString == cellDateStr }
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isToday -> WaterBlue.copy(alpha = 0.35f)
                                                hasTasks -> WaterBlue.copy(alpha = 0.25f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isToday) 0.5.dp else 0.dp,
                                            color = if (isToday) WaterBlue else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { onDayClick(dayNumber) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            color = if (isToday) WaterBlue else if (hasTasks) Color.White else Color.LightGray,
                                            fontSize = 6.5.sp,
                                            fontWeight = if (isToday || hasTasks) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center
                                        )
                                        if (hasTasks) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(2.5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isToday) WaterBlue else Color(0xFF2E6FF3))
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
