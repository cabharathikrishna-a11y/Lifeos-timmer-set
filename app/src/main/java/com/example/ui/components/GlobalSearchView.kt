package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Contact
import com.example.data.Habit
import com.example.data.HabitCompletion
import com.example.data.Deadline
import com.example.data.JournalEntry
import com.example.data.Task
import com.example.data.KeepNote
import com.example.ui.AppViewModel
import com.example.ui.FocusRecord
import com.example.ui.Screen
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

enum class SearchFilter {
    ALL, TASKS, HABITS, JOURNALS, CONTACTS, FINANCES, NOTES
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalSearchView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val query by viewModel.globalSearchQuery.collectAsState()
    val results by viewModel.globalSearchResults.collectAsState()
    val history by viewModel.globalSearchHistory.collectAsState()

    var activeFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var inputQuery by remember { mutableStateOf(query) }

    // Sync state if vm query changes externally
    LaunchedEffect(query) {
        if (query != inputQuery) {
            inputQuery = query
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // 1. Search Bar Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = inputQuery,
                onValueChange = {
                    inputQuery = it
                    viewModel.setGlobalSearchQuery(it)
                },
                placeholder = { Text("Search tasks, habits, journal notebooks...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (inputQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            inputQuery = ""
                            viewModel.setGlobalSearchQuery("")
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (inputQuery.trim().isNotEmpty()) {
                            viewModel.addSearchHistory(inputQuery.trim())
                        }
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedContainerColor = Charcoal,
                    unfocusedContainerColor = Charcoal,
                    focusedIndicatorColor = WaterBlue,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .testTag("global_search_input")
            )
        }

        // 2. Filter Category Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchFilter.values().forEach { filter ->
                val isSelected = activeFilter == filter
                val label = filter.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) WaterBlue else Charcoal,
                    modifier = Modifier
                        .clickable { activeFilter = filter }
                        .testTag("search_filter_${filter.name.lowercase()}")
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Search Results or Empty State or History
        if (inputQuery.trim().isEmpty()) {
            // Show Search History Logs (Indices Database Cache)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT COMPILATIONS INDEX",
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearSearchHistory() }) {
                            Text("Clear logs", color = Color.Red, fontSize = 11.sp)
                        }
                    }
                }

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Query empty hint",
                                tint = Charcoal,
                                modifier = Modifier.size(52.dp)
                            )
                            Text(
                                text = "Start typing some keywords to seek entire Life OS database",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 40.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { logQuery ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Charcoal)
                                    .clickable {
                                        inputQuery = logQuery
                                        viewModel.setGlobalSearchQuery(logQuery)
                                        viewModel.addSearchHistory(logQuery)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Past query match",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = logQuery,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Run query",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val DateRegex = Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""")
            val dateMatch = DateRegex.matchEntire(inputQuery.trim())
            if (dateMatch != null) {
                val day = dateMatch.groupValues[1].toInt()
                val month = dateMatch.groupValues[2].toInt()
                val year = dateMatch.groupValues[3].toInt()
                val searchDateStr = String.format("%04d-%02d-%02d", year, month, day)

                // Render Dynamic Daily Overview Log Report
                DailyDateOverview(
                    searchDateStr = searchDateStr,
                    day = day,
                    month = month,
                    year = year,
                    viewModel = viewModel
                )
            } else {
                // Filter results depending on selected category chip
                val finalTasks = if (activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.TASKS) results.matchingTasks else emptyList()
                val finalHabits = if (activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.HABITS) results.matchingHabits else emptyList()
                val finalJournals = if (activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.JOURNALS) results.matchingJournals else emptyList()
                val finalContacts = if (activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.CONTACTS) results.matchingContacts else emptyList()
                val finalFinances = if (activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.FINANCES) results.matchingFinances else emptyList()
                val finalNotes = if (activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.NOTES) results.matchingNotes else emptyList()

                val totalCount = finalTasks.size + finalHabits.size + finalJournals.size + finalContacts.size + finalFinances.size + finalNotes.size

                if (totalCount == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "No results matches",
                                tint = Charcoal,
                                modifier = Modifier.size(62.dp)
                            )
                            Text(
                                text = "No matches indexing across database for \"$inputQuery\"",
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Check spelling or search for alternative tasks/journals",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // TASKS BLOCK
                        if (finalTasks.isNotEmpty()) {
                            item {
                                SectionHeader(title = "TASKS MATCHING", count = finalTasks.size)
                            }
                            items(finalTasks) { task ->
                                TaskSearchItem(task = task, onToggle = { viewModel.toggleTaskCompletion(task) })
                            }
                        }

                        // HABITS BLOCK
                        if (finalHabits.isNotEmpty()) {
                            item {
                                SectionHeader(title = "HABITS BOARD MATCHING", count = finalHabits.size)
                            }
                            items(finalHabits) { habit ->
                                HabitSearchItem(habit = habit)
                            }
                        }

                        // LIFE JOURNALS BLOCK
                        if (finalJournals.isNotEmpty()) {
                            item {
                                SectionHeader(title = "DAILY DIARY JOURNAL MATCHING", count = finalJournals.size)
                            }
                            items(finalJournals) { entry ->
                                JournalSearchItem(entry = entry, onOpen = {
                                    viewModel.navigateTo(Screen.JOURNAL)
                                })
                            }
                        }

                        // CONTACT NOTES BLOCK
                        if (finalContacts.isNotEmpty()) {
                            item {
                                SectionHeader(title = "CONTACTS DIRECTORY MATCHING", count = finalContacts.size)
                            }
                            items(finalContacts) { contact ->
                                ContactSearchItem(contact = contact, onOpen = {
                                    viewModel.navigateTo(Screen.CONTACTS)
                                })
                            }
                        }

                        // FINANCIAL TRANSACTIONS BLOCK
                        if (finalFinances.isNotEmpty()) {
                            item {
                                SectionHeader(title = "FINANCES MATCHING", count = finalFinances.size)
                            }
                            items(finalFinances) { transaction ->
                                FinanceSearchItem(transaction = transaction, onOpen = {
                                    viewModel.navigateTo(Screen.FINANCES)
                                })
                            }
                        }

                        // GOOGLE KEEP NOTES BLOCK
                        if (finalNotes.isNotEmpty()) {
                            item {
                                SectionHeader(title = "GOOGLE KEEP NOTES MATCHING", count = finalNotes.size)
                            }
                            items(finalNotes) { note ->
                                NoteSearchItem(note = note, onOpen = {
                                    viewModel.navigateTo(Screen.KEEP_NOTES)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = WaterBlue,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Charcoal)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$count Match",
                color = Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TaskSearchItem(task: Task, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = WaterBlue,
                    uncheckedColor = Color.Gray
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val priorityColor = when (task.priority.uppercase()) {
                        "HIGH" -> Color.Red
                        "MEDIUM" -> Color(0xFFFFB300)
                        else -> Color.Gray
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(priorityColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.priority,
                            color = priorityColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = task.listCategory,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = task.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                
                if (task.description.isNotEmpty()) {
                    Text(
                        text = task.description,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (task.dueDateString.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Due Date",
                        tint = WaterBlue,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = task.dueDateString,
                        color = Color.Gray,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
fun HabitSearchItem(habit: Habit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Habit check status",
                tint = WaterBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Group: ${habit.listCategory} • Frequency: ${habit.frequency}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E3A20))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "🔥 ${habit.streakCount} streak",
                    color = Color(0xFF81C784),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun JournalSearchItem(entry: JournalEntry, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = "Journal Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entry.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text(
                    text = entry.dateString,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.text,
                color = Color.LightGray,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ContactSearchItem(contact: Contact, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountBox,
                contentDescription = "Contact Info Icon",
                tint = WaterBlue,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${contact.firstName} ${contact.lastName}".trim(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (contact.jobTitle.isNotEmpty()) {
                    Text(
                        text = contact.jobTitle,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (contact.phone.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Phone icon",
                                tint = Color.Gray,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = contact.phone, color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                    if (contact.email.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email icon",
                                tint = Color.Gray,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = contact.email, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Navigate to Contact details page",
                tint = Color.Gray,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun DailyDateOverview(
    searchDateStr: String,
    day: Int,
    month: Int,
    year: Int,
    viewModel: AppViewModel
) {
    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val completions by viewModel.habitCompletions.collectAsState()
    val focusRecords by viewModel.focusRecords.collectAsState()
    val journals by viewModel.journalEntries.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val deadlines by viewModel.deadlines.collectAsState()

    val dateTasks = remember(tasks, searchDateStr) {
        tasks.filter { it.dueDateString == searchDateStr }
    }
    
    val completedTasks = remember(dateTasks) { dateTasks.filter { it.isCompleted } }
    val pendingTasks = remember(dateTasks) { dateTasks.filter { !it.isCompleted } }

    val dateFocusRecords = remember(focusRecords, searchDateStr) {
        focusRecords.filter { it.dateString == searchDateStr }
    }
    val totalFocusMins = dateFocusRecords.sumOf { it.durationMinutes }

    val dateJournals = remember(journals, searchDateStr) {
        journals.filter { it.dateString == searchDateStr }
    }

    // Celebrations & Birthdays
    val birthdays = remember(contacts) {
        contacts.filter { contact ->
            if (contact.dobString.isNotEmpty()) {
                val parsedCal = parseDateStringToCalendar(contact.dobString)
                parsedCal != null && parsedCal.get(Calendar.DAY_OF_MONTH) == day && parsedCal.get(Calendar.MONTH) + 1 == month
            } else false
        }
    }

    val anniversaries = remember(contacts) {
        contacts.filter { contact ->
            if (contact.anniversaryString.isNotEmpty()) {
                val parsedCal = parseDateStringToCalendar(contact.anniversaryString)
                parsedCal != null && parsedCal.get(Calendar.DAY_OF_MONTH) == day && parsedCal.get(Calendar.MONTH) + 1 == month
            } else false
        }
    }

    val activeDeadlines = remember(deadlines) {
        deadlines.filter { d ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val deadlineStr = sdf.format(Date(d.targetTimestamp))
            deadlineStr == searchDateStr
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Daily header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = WaterBlue.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, WaterBlue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📅 HISTORIC DAILY DIGEST",
                        color = WaterBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Detailed Overview for $day/${if (month < 10) "0$month" else "$month"}/$year",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
            }
        }

        // Celebrations & Birthdays Block
        if (birthdays.isNotEmpty() || anniversaries.isNotEmpty() || activeDeadlines.isNotEmpty()) {
            item {
                Text(
                    text = "🎉 CELEBRATIONS & EVENTS",
                    color = WaterBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    birthdays.forEach { contact ->
                        val parsedCal = parseDateStringToCalendar(contact.dobString)
                        val ageText = if (parsedCal != null && hasYearMentioned(contact.dobString)) {
                            " (Age: ${year - parsedCal.get(Calendar.YEAR)})"
                        } else ""
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.selectContact(contact.id) }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cake, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🎂 ${contact.firstName} ${contact.lastName}'s Birthday$ageText",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    anniversaries.forEach { contact ->
                        val parsedCal = parseDateStringToCalendar(contact.anniversaryString)
                        val diffYearsText = if (parsedCal != null && hasYearMentioned(contact.anniversaryString)) {
                            " (${year - parsedCal.get(Calendar.YEAR)} years)"
                        } else ""
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.selectContact(contact.id) }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "💑 ${contact.firstName} ${contact.lastName}'s Anniversary$diffYearsText",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    activeDeadlines.forEach { d ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.navigateTo(Screen.COUNTDOWN) }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🏁 Target Milestone: ${d.name}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tasks Block
        item {
            Text(
                text = "📋 TASKS REPORT (Total: ${dateTasks.size})",
                color = WaterBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (dateTasks.isEmpty()) {
                Text("No tasks scheduled for this day.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pendingTasks.isNotEmpty()) {
                        Text("⏳ PENDING TASKS:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        pendingTasks.forEach { task ->
                            TaskSearchItem(task = task, onToggle = { viewModel.toggleTaskCompletion(task) })
                        }
                    }
                    if (completedTasks.isNotEmpty()) {
                        Text("✓ COMPLETED TASKS:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        completedTasks.forEach { task ->
                            TaskSearchItem(task = task, onToggle = { viewModel.toggleTaskCompletion(task) })
                        }
                    }
                }
            }
        }

        // Habits Status Block
        item {
            Text(
                text = "🔥 HABITS STATUS TRACKER",
                color = WaterBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (habits.isEmpty()) {
                Text("No habits registered in database.", color = Color.Gray, fontSize = 12.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    habits.forEach { habit ->
                        val isDone = completions.any { it.habitId == habit.id && it.dateString == searchDateStr }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceCard)
                                .clickable { viewModel.toggleHabit(habit, searchDateStr) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(habit.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isDone) WaterBlue.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isDone) "🟢 COMPLETED" else "🔴 NOT COMPLETED",
                                    color = if (isDone) WaterBlue else Color.Gray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Focused Time details block
        item {
            Text(
                text = "⏱️ FOCUS TIME DETAILS",
                color = WaterBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Logged", color = Color.Gray, fontSize = 13.sp)
                        Text("$totalFocusMins Minutes", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    if (dateFocusRecords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = Color.Gray.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))
                        dateFocusRecords.forEach { record ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(record.taskTitle, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Duration: ${record.startTime} - ${record.endTime}", color = Color.Gray, fontSize = 10.sp)
                                }
                                Text(formatRecordDuration(record.durationSeconds, record.durationMinutes), color = WaterBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "No focused stopwatch/timer session logs on this day.",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Journal details block
        item {
            Text(
                text = "📖 JOURNAL NOTEBOOK DETAILED SESSIONS",
                color = WaterBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (dateJournals.isEmpty()) {
                Text("No journal entry saved on this template date.", color = Color.Gray, fontSize = 12.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dateJournals.forEach { entry ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.navigateTo(Screen.JOURNAL) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(entry.title, color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(entry.text, color = Color.White, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinanceSearchItem(transaction: com.example.data.FinanceTransaction, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val typeColor = when (transaction.type.uppercase()) {
                "INCOME" -> Color(0xFF4CAF50)
                "EXPENSE" -> Color(0xFFF44336)
                else -> WaterBlue
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(typeColor.copy(alpha = 0.2f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (transaction.type.uppercase() == "INCOME") Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = transaction.type,
                    tint = typeColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (transaction.note.isNotEmpty()) transaction.note else "Financial Transaction",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${transaction.type} • Class: ${transaction.fromCategory ?: transaction.toCategory ?: "General"}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Text(
                text = String.format("$%.2f", transaction.amount),
                color = typeColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun NoteSearchItem(note: KeepNote, onOpen: () -> Unit) {
    val cardColor = try {
        Color(android.graphics.Color.parseColor(note.colorHex))
    } catch (e: Exception) {
        Color(0xFF202124) // Fallback charcoal
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("note_search_item_${note.id}"),
        border = CardDefaults.outlinedCardBorder(true).copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.1f))
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Keep Note Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (note.title.isNotEmpty()) {
                        Text(
                            text = note.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
            if (note.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.content,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
