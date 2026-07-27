package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.util.rememberVideoThumbnail
import com.example.util.rememberPdfFirstPagePreview
import androidx.compose.foundation.Image
import com.example.data.Contact
import com.example.ui.AppViewModel
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue
import com.example.util.MediaCompressionHelper
import java.io.File

// Premium avatar constants
val AVATAR_OPTIONS = listOf(
    "https://api.dicebear.com/7.x/bottts/svg?seed=Felix",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Anya",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Leo",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Dana",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Jack"
)

enum class ContactScreen {
    LIST, ADD, EDIT
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val contacts by viewModel.contacts.collectAsState()
    val foldersList by viewModel.contactFolders.collectAsState()
    val context = LocalContext.current

    // Navigation and selection state
    var screenState by remember { mutableStateOf(ContactScreen.LIST) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    // Dialog triggering states for Folders
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var renameFolderOldName by remember { mutableStateOf("") }
    var renameFolderNewName by remember { mutableStateOf("") }

    var showDeleteFolderDialog by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf("") }

    // Dialog triggering states for Contacts
    var showContactActionDialog by remember { mutableStateOf(false) }
    var longPressedContact by remember { mutableStateOf<Contact?>(null) }
    var showConfirmDeleteContactDialog by remember { mutableStateOf(false) }

    // Form inputs state
    var firstName by remember { mutableStateOf("") }
    var middleName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var jobTitle by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dobString by remember { mutableStateOf("") }
    var anniversaryString by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf("") }
    var selectedFolderOption by remember { mutableStateOf("All") }

    val customFieldsList = remember { mutableStateListOf<Pair<String, String>>() }
    var customFieldNameDraft by remember { mutableStateOf("") }
    var customFieldValueDraft by remember { mutableStateOf("") }

    val customDatesList = remember { mutableStateListOf<Pair<String, String>>() }
    var customDateNameDraft by remember { mutableStateOf("") }
    var customDateValueDraft by remember { mutableStateOf("") }

    val externalSelectedId by viewModel.selectedContactId.collectAsState()
    LaunchedEffect(externalSelectedId) {
        externalSelectedId?.let { idVal ->
            val found = contacts.find { it.id == idVal }
            if (found != null) {
                selectedContact = found
                screenState = ContactScreen.LIST
                viewModel.clearSelectedContactId()
            }
        }
    }
    var selectedFolder by remember { mutableStateOf("All") }
    var isFoldersSidebarExpanded by remember { mutableStateOf(false) }

    val handleBackPress = {
        if ((screenState == ContactScreen.ADD || screenState == ContactScreen.EDIT) && firstName.trim().isNotEmpty()) {
            showUnsavedDialog = true
        } else if (screenState != ContactScreen.LIST) {
            screenState = ContactScreen.LIST
        } else if (selectedContact != null) {
            selectedContact = null
        }
    }

    androidx.activity.compose.BackHandler(enabled = screenState != ContactScreen.LIST || selectedContact != null) {
        handleBackPress()
    }
    
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved Changes", color = Color.White) },
            text = { Text("You have unsaved changes. Do you want to save or discard them?", color = Color.LightGray) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    if (firstName.trim().isNotEmpty()) {
                        val datesJson = customDatesList.joinToString(";") { "${it.first}:${it.second}" }
                        val fieldsJson = customFieldsList.joinToString(";") { "${it.first}:${it.second}" }
                        
                        if (screenState == ContactScreen.EDIT && selectedContact != null) {
                            // Update existing logic
                            val updated = selectedContact!!.copy(
                                firstName = firstName.trim(),
                                middleName = middleName.trim(),
                                lastName = lastName.trim(),
                                jobTitle = jobTitle.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                phone = phone.trim(),
                                dobString = dobString.trim(),
                                photoUri = selectedAvatar.takeIf { it.isNotEmpty() },
                                anniversaryString = anniversaryString.trim(),
                                additionalFieldsJson = fieldsJson,
                                additionalDatesJson = datesJson,
                                folder = selectedFolderOption
                            )
                            viewModel.updateContact(updated)
                        } else {
                            viewModel.createContact(
                                firstName = firstName.trim(),
                                middleName = middleName.trim(),
                                lastName = lastName.trim(),
                                jobTitle = jobTitle.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                phone = phone.trim(),
                                dobString = dobString.trim(),
                                photoUri = selectedAvatar.takeIf { it.isNotEmpty() },
                                anniversaryString = anniversaryString.trim(),
                                additionalFieldsJson = fieldsJson,
                                additionalDatesJson = datesJson,
                                folder = selectedFolderOption
                            )
                        }
                    }
                    screenState = ContactScreen.LIST
                }) {
                    Text("Save", color = WaterBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    screenState = ContactScreen.LIST
                }) {
                    Text("Discard", color = Color(0xFFF9325D))
                }
            }
        )
    }

    // Image Picker launcher (copy to files dir on pick)
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val destFile = File(com.example.util.StorageHelper.getAppFilesDir(context), "profile_${System.currentTimeMillis()}.jpg")
            val compressSuccess = MediaCompressionHelper.compressImageFromUri(context, uri, destFile)
            if (compressSuccess) {
                selectedAvatar = destFile.absolutePath
                Toast.makeText(context, "Premium profile photo compressed & configured!", Toast.LENGTH_SHORT).show()
            } else {
                val copied = copyUriToLocalFile(context, uri, destFile.name)
                if (copied != null) {
                    selectedAvatar = copied.absolutePath
                    Toast.makeText(context, "Profile photo attached!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val contactsPrefs = remember { context.getSharedPreferences("app_contacts_prefs", android.content.Context.MODE_PRIVATE) }
    val isAuthorized = remember(context) {
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
        val scopeContacts = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/contacts")
        account != null && com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(account, scopeContacts)
    }
    var showContactsBanner by remember {
        mutableStateOf(false)
    }

    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.syncGoogleContacts(context) { intent ->
                // Do not loop infinitely if second auth fails
            }
        }
    }

    LaunchedEffect(Unit) {
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
        val scopeContacts = com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/contacts")
        val isAuth = account != null && com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(account, scopeContacts)
        if (isAuth) {
            viewModel.syncGoogleContacts(context) { }
        }
    }

    // Main screen controller
    when (screenState) {
        ContactScreen.LIST -> {
            val isTablet = LocalConfiguration.current.screenWidthDp >= 600
            val contactListUI = @Composable {
                    val sortedFilteredContacts = remember(contacts, selectedFolder) {
                        val base = if (selectedFolder == "All") {
                            contacts
                        } else {
                            contacts.filter { it.folder == selectedFolder }
                        }
                        base.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { "${it.firstName} ${it.lastName}".trim() })
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header inside List View
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 3-line Toggle Menu Button to toggle the folders list drawer
                            IconButton(
                                onClick = { isFoldersSidebarExpanded = !isFoldersSidebarExpanded },
                                modifier = Modifier.testTag("contacts_sidebar_toggle").padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Toggle Folders Sidebar",
                                    tint = Color.White
                                )
                            }

                            Text(
                                text = if (selectedFolder == "All") "All Contacts" else "📁 $selectedFolder",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 18.sp,
                                modifier = Modifier.weight(1f)
                            )

                            val googleContactsSyncStatus by viewModel.googleContactsSyncStatus.collectAsState()

                            if (googleContactsSyncStatus == "Syncing...") {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp).padding(end = 12.dp),
                                    color = WaterBlue,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        viewModel.syncGoogleContacts(context) { intent ->
                                            authResolutionLauncher.launch(intent)
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync Google Contacts",
                                        tint = WaterBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    firstName = ""
                                    middleName = ""
                                    lastName = ""
                                    jobTitle = ""
                                    email = ""
                                    address = ""
                                    phone = ""
                                    dobString = ""
                                    anniversaryString = ""
                                    customFieldNameDraft = ""
                                    customFieldValueDraft = ""
                                    customFieldsList.clear()
                                    customDateNameDraft = ""
                                    customDateValueDraft = ""
                                    customDatesList.clear()
                                    selectedAvatar = AVATAR_OPTIONS.random()
                                    selectedFolderOption = if (selectedFolder == "All") "All" else selectedFolder
                                    screenState = ContactScreen.ADD
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(WaterBlue)
                                    .testTag("add_contact_fab")
                                    .size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Contact", tint = Color.Black, modifier = Modifier.size(20.dp))
                            }
                        }

                        if (showContactsBanner) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141419)),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, WaterBlue.copy(alpha = 0.3f)),
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
                                                text = "Connect Google Contacts",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                contactsPrefs.edit().putBoolean("contacts_connect_banner_dismissed", true).apply()
                                                showContactsBanner = false
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
                                        text = "Sync and back up all your custom contacts with the official Google Contacts Cloud API.",
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
                                                contactsPrefs.edit().putBoolean("contacts_connect_banner_dismissed", true).apply()
                                                showContactsBanner = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("No thanks", color = Color.Gray, fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                contactsPrefs.edit().putBoolean("contacts_connect_banner_dismissed", true).apply()
                                                showContactsBanner = false
                                                viewModel.syncGoogleContacts(context) { intent ->
                                                    authResolutionLauncher.launch(intent)
                                                }
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

                        if (sortedFilteredContacts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBox,
                                        contentDescription = null,
                                        tint = Color.Gray.copy(alpha = 0.6f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No contacts in fold.",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Click the '+' button above to register premium contacts.",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sortedFilteredContacts) { contact ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp))
                                            .combinedClickable(
                                                onClick = { selectedContact = contact },
                                                onLongClick = {
                                                    longPressedContact = contact
                                                    showContactActionDialog = true
                                                }
                                            ),
                                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Profile picture layout (Circle)
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(if (!contact.photoUri.isNullOrEmpty()) Color.Transparent else listOf(Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFF795548))[(contact.firstName.firstOrNull()?.code ?: 0) % 11].copy(alpha = 0.8f))
                                                    .border(1.dp, WaterBlue.copy(alpha = 0.5f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!contact.photoUri.isNullOrEmpty()) {
                                                    AsyncImage(
                                                        model = contact.photoUri,
                                                        contentDescription = "Profile Photo",
                                                        modifier = Modifier.clip(CircleShape).fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Text(
                                                        text = "${contact.firstName.firstOrNull()?.uppercaseChar() ?: '?'}",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${contact.firstName} ${if (contact.middleName.isNotEmpty()) contact.middleName + " " else ""}${contact.lastName}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (contact.folder != "All") {
                                                        Icon(Icons.Default.Folder, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(10.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(contact.folder, color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                    }
                                                    if (contact.jobTitle.isNotEmpty()) {
                                                        Text(contact.jobTitle, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                                                    }
                                                }
                                            }
                                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
            }

            val contactDetailsUI = @Composable { contact: Contact ->
                    // Show Contact Details View (Redesigned as separate Full-Screen View)
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        val attachedFiles = remember(contact.attachedFilesJson) {
                            val res = mutableListOf<String>()
                            if (contact.attachedFilesJson.isNotEmpty()) {
                                try {
                                    val arr = org.json.JSONArray(contact.attachedFilesJson)
                                    for (i in 0 until arr.length()) { res.add(arr.getString(i)) }
                                } catch (e: Exception) {}
                            }
                            res
                        }

                        // Top bar inside Detail Screen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedContact = null }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to list", tint = Color.White)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // EDIT button
                                IconButton(
                                    onClick = {
                                        longPressedContact = contact
                                        // Load form entries to go to edit screen
                                        firstName = contact.firstName
                                        middleName = contact.middleName
                                        lastName = contact.lastName
                                        jobTitle = contact.jobTitle
                                        email = contact.email
                                        address = contact.address
                                        phone = contact.phone
                                        dobString = contact.dobString
                                        anniversaryString = contact.anniversaryString
                                        selectedAvatar = contact.photoUri ?: ""
                                        selectedFolderOption = contact.folder

                                        customFieldsList.clear()
                                        if (contact.additionalFieldsJson.isNotEmpty()) {
                                            contact.additionalFieldsJson.split(";").forEach { pair ->
                                                val parts = pair.split(":")
                                                if (parts.size == 2) customFieldsList.add(parts[0] to parts[1])
                                            }
                                        }

                                        customDatesList.clear()
                                        if (contact.additionalDatesJson.isNotEmpty()) {
                                            contact.additionalDatesJson.split(";").forEach { pair ->
                                                val parts = pair.split(":")
                                                if (parts.size == 2) customDatesList.add(parts[0] to parts[1])
                                            }
                                        }

                                        screenState = ContactScreen.EDIT
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Contact", tint = WaterBlue)
                                }

                                // DELETE button
                                IconButton(
                                    onClick = {
                                        longPressedContact = contact
                                        showConfirmDeleteContactDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Contact", tint = Color.Red)
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                // Header Box (Profile Image + Quick Communication)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(if (!contact.photoUri.isNullOrEmpty()) Color.Transparent else listOf(Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFF795548))[(contact.firstName.firstOrNull()?.code ?: 0) % 11].copy(alpha = 0.8f))
                                            .border(2.dp, WaterBlue, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!contact.photoUri.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = contact.photoUri,
                                                contentDescription = "Profile Photo",
                                                modifier = Modifier.clip(CircleShape).fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = "${contact.firstName.firstOrNull()?.uppercaseChar() ?: '?'}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 24.sp
                                            )
                                        }
                                    }

                                    // OS direct Action triggers
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(
                                            onClick = {
                                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                                                context.startActivity(dialIntent)
                                            },
                                            modifier = Modifier.clip(CircleShape).background(WaterBlue.copy(alpha = 0.15f))
                                        ) {
                                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = WaterBlue)
                                        }

                                        IconButton(
                                            onClick = {
                                                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.phone}"))
                                                context.startActivity(smsIntent)
                                            },
                                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.1f))
                                        ) {
                                            Icon(Icons.Default.Email, contentDescription = "SMS", tint = Color.White)
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${contact.firstName} ${if (contact.middleName.isNotEmpty()) contact.middleName + " " else ""}${contact.lastName}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                                if (contact.jobTitle.isNotEmpty()) {
                                    Text(contact.jobTitle, color = WaterBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Divider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                            }

                            // Custom date-tags count detail rows
                            item { ContactDetailRow(Icons.Default.LocalAirport, "Folder", contact.folder) }
                            item { ContactDetailRow(Icons.Default.Phone, "Phone", contact.phone) }
                            item { ContactDetailRow(Icons.Default.Email, "Email", contact.email.ifEmpty { "Not added" }) }
                            item { ContactDetailRow(Icons.Default.LocationOn, "Address", contact.address.ifEmpty { "Not added" }) }
                            item { ContactDetailRow(Icons.Default.DateRange, "Birthday (DOB)", contact.dobString.ifEmpty { "Not added" }) }
                            item { ContactDetailRow(Icons.Default.Favorite, "Anniversary", contact.anniversaryString.ifEmpty { "Not added" }) }

                            if (contact.additionalDatesJson.isNotEmpty()) {
                                contact.additionalDatesJson.split(";").forEach { pair ->
                                    val parts = pair.split(":")
                                    if (parts.size == 2) {
                                        item { ContactDetailRow(Icons.Default.Event, parts[0], parts[1]) }
                                    }
                                }
                            }

                            if (contact.additionalFieldsJson.isNotEmpty()) {
                                contact.additionalFieldsJson.split(";").forEach { pair ->
                                    val parts = pair.split(":")
                                    if (parts.size == 2) {
                                        item { ContactDetailRow(Icons.Default.Info, parts[0], parts[1]) }
                                    }
                                }
                            }

                            // 4. Relevant Documents / Info File Attachments
                            item {
                                Divider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                                
                                val docPickerLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.GetContent()
                                ) { uri: Uri? ->
                                    if (uri != null) {
                                        val originalName = getFileName(context, uri)
                                        val localName = "doc_${System.currentTimeMillis()}_${originalName}"
                                        val copiedFile = copyUriToLocalFile(context, uri, localName)
                                        if (copiedFile != null) {
                                            val currentList = mutableListOf<String>()
                                            if (contact.attachedFilesJson.isNotEmpty()) {
                                                try {
                                                    val arr = org.json.JSONArray(contact.attachedFilesJson)
                                                    for (i in 0 until arr.length()) { currentList.add(arr.getString(i)) }
                                                } catch (e: Exception) {}
                                            }
                                            currentList.add(copiedFile.absolutePath)
                                            val updated = contact.copy(attachedFilesJson = org.json.JSONArray(currentList).toString())
                                            viewModel.updateContact(updated)
                                            selectedContact = updated
                                            Toast.makeText(context, "Document attached successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Relevant Documents & Info", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    IconButton(
                                        onClick = { docPickerLauncher.launch("*/*") },
                                        modifier = Modifier.size(28.dp).clip(CircleShape).background(WaterBlue.copy(alpha = 0.15f))
                                    ) {
                                        Icon(Icons.Default.AttachFile, contentDescription = "Add Document", tint = WaterBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (attachedFiles.isEmpty()) {
                                item {
                                    Text("No target documents uploaded.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp))
                                }
                            } else {
                                items(attachedFiles) { filePath ->
                                    val file = File(filePath)
                                    val displayName = file.name.substringAfter("doc_").substringAfter("_")
                                    val isPhoto = isPhotoFile(file)
                                    
                                    var showOptionsDialog by remember { mutableStateOf(false) }

                                    if (showOptionsDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showOptionsDialog = false },
                                            title = { Text("Attachment Options", color = Color.White) },
                                            text = { Text("Choose action for '$displayName':", color = Color.LightGray) },
                                            confirmButton = {
                                                Button(
                                                    onClick = {
                                                        showOptionsDialog = false
                                                        val success = saveFileToDownloads(context, file, displayName)
                                                        if (success) {
                                                            Toast.makeText(context, "Saved to Downloads folder!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                                                ) {
                                                    Text("Save to Device")
                                                }
                                            },
                                            dismissButton = {
                                                TextButton(
                                                    onClick = {
                                                        showOptionsDialog = false
                                                        val newList = attachedFiles.filter { it != filePath }
                                                        val updated = contact.copy(attachedFilesJson = org.json.JSONArray(newList).toString())
                                                        viewModel.updateContact(updated)
                                                        selectedContact = updated
                                                        try { file.delete() } catch (e: Exception) {}
                                                        Toast.makeText(context, "Attachment deleted!", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Text("Delete", color = Color.Red)
                                                }
                                            },
                                            containerColor = SurfaceCard
                                        )
                                    }

                                    val ext = file.extension.lowercase()
                                    val isVideo = ext == "mp4" || ext == "mov" || ext == "3gp" || ext == "mkv"
                                    val isPdf = ext == "pdf"

                                    if (isPhoto || isVideo || isPdf) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .combinedClickable(
                                                    onClick = {
                                                        try {
                                                            val authority = "${context.packageName}.fileprovider"
                                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                val mimeType = if (isPhoto) "image/*" else if (isVideo) "video/*" else "application/pdf"
                                                                setDataAndType(uri, mimeType)
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(Intent.createChooser(intent, "Open File"))
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Open failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    onLongClick = {
                                                        showOptionsDialog = true
                                                    }
                                                )
                                        ) {
                                            Column {
                                                if (isPhoto) {
                                                    AsyncImage(
                                                        model = file,
                                                        contentDescription = displayName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(180.dp)
                                                    )
                                                } else if (isVideo) {
                                                    val thumbnailBitmap = rememberVideoThumbnail(file.absolutePath)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(180.dp)
                                                            .background(Color.Black),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (thumbnailBitmap != null) {
                                                            Image(
                                                                bitmap = thumbnailBitmap,
                                                                contentDescription = "Video Thumbnail Preview",
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.Black.copy(alpha = 0.5f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = "Play",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(32.dp)
                                                            )
                                                        }
                                                    }
                                                } else if (isPdf) {
                                                    val pdfBitmap = rememberPdfFirstPagePreview(file.absolutePath)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(180.dp)
                                                            .background(Color(0xFF1C1B1F)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (pdfBitmap != null) {
                                                            Image(
                                                                bitmap = pdfBitmap,
                                                                contentDescription = "PDF Preview",
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        } else {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(
                                                                    imageVector = Icons.Default.InsertDriveFile,
                                                                    contentDescription = "PDF",
                                                                    tint = Color(0xFFE57373),
                                                                    modifier = Modifier.size(48.dp)
                                                                )
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text("PDF Document", color = Color.LightGray, fontSize = 11.sp)
                                                            }
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text("${file.length() / 1024} KB • Hold for options", color = Color.Gray, fontSize = 9.sp)
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            val newList = attachedFiles.filter { it != filePath }
                                                            val updated = contact.copy(attachedFilesJson = org.json.JSONArray(newList).toString())
                                                            viewModel.updateContact(updated)
                                                            selectedContact = updated
                                                            try { file.delete() } catch (e: Exception) {}
                                                            Toast.makeText(context, "Attachment removed!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Delete Attachment", tint = Color.Red, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .combinedClickable(
                                                    onClick = {
                                                        try {
                                                            val authority = "${context.packageName}.fileprovider"
                                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(Intent.createChooser(intent, "Open File"))
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Open failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    onLongClick = {
                                                        showOptionsDialog = true
                                                    }
                                                )
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.FileOpen, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("${file.length() / 1024} KB • Hold for options", color = Color.Gray, fontSize = 9.sp)
                                            }
                                            IconButton(
                                                onClick = {
                                                    val newList = attachedFiles.filter { it != filePath }
                                                    val updated = contact.copy(attachedFilesJson = org.json.JSONArray(newList).toString())
                                                    viewModel.updateContact(updated)
                                                    selectedContact = updated
                                                    try { file.delete() } catch (e: Exception) {}
                                                    Toast.makeText(context, "Attachment removed!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Delete Attachment", tint = Color.Red, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }

            Box(modifier = modifier.fillMaxSize()) {
                if (!isTablet) {
                    if (selectedContact == null) {
                        contactListUI()
                    } else {
                        contactDetailsUI(selectedContact!!)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                            contactListUI()
                        }
                        Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                            if (selectedContact != null) {
                                contactDetailsUI(selectedContact!!)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountBox,
                                                contentDescription = null,
                                                tint = WaterBlue.copy(alpha = 0.4f),
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Select a Contact",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Choose a contact from the list to view their details, files, and timeline.",
                                                color = Color.Gray,
                                                fontSize = 12.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            // Scrim background when folders sidebar is open to dismiss on clicking outside
            androidx.compose.animation.AnimatedVisibility(
                visible = isFoldersSidebarExpanded,
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
                            isFoldersSidebarExpanded = false
                        }
                )
            }

            // Floating Folders Sidebar Overlay (Renders on top aligned to Left)
            androidx.compose.animation.AnimatedVisibility(
                visible = isFoldersSidebarExpanded,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF2E2E30), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "FOLDERS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WaterBlue,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        // Default "All" Folder Row (unremovable & uneditable)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedFolder == "All") WaterBlue.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    selectedFolder = "All"
                                    isFoldersSidebarExpanded = false
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "All Contacts",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // User Created Folders list
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(foldersList) { folder ->
                                val isSelected = selectedFolder == folder
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) WaterBlue.copy(alpha = 0.15f) else Color.Transparent)
                                        .combinedClickable(
                                            onClick = {
                                                selectedFolder = folder
                                                isFoldersSidebarExpanded = false
                                            },
                                            onLongClick = {
                                                renameFolderOldName = folder
                                                renameFolderNewName = folder
                                                folderToDelete = folder
                                                showRenameFolderDialog = true
                                                isFoldersSidebarExpanded = false
                                            }
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = folder,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Create Folder Button inside Sidebar
                        Button(
                            onClick = {
                                newFolderName = ""
                                showCreateFolderDialog = true
                                isFoldersSidebarExpanded = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Folder", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Folder", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } // End of outer Box
    }

        ContactScreen.ADD, ContactScreen.EDIT -> {
            // Screen Title
            val isEdit = screenState == ContactScreen.EDIT
            
            Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
                // Form Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { handleBackPress() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WaterBlue)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEdit) "Edit Contact Details" else "New Premium Contact",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = {
                            if (firstName.trim().isNotEmpty()) {
                                val datesJson = customDatesList.joinToString(";") { "${it.first}:${it.second}" }
                                val fieldsJson = customFieldsList.joinToString(";") { "${it.first}:${it.second}" }
                                
                                if (isEdit && selectedContact != null) {
                                    val updated = selectedContact!!.copy(
                                        firstName = firstName.trim(),
                                        middleName = middleName.trim(),
                                        lastName = lastName.trim(),
                                        jobTitle = jobTitle.trim(),
                                        email = email.trim(),
                                        address = address.trim(),
                                        phone = phone.trim(),
                                        dobString = dobString.trim(),
                                        photoUri = selectedAvatar.takeIf { it.isNotEmpty() },
                                        anniversaryString = anniversaryString.trim(),
                                        additionalFieldsJson = fieldsJson,
                                        additionalDatesJson = datesJson,
                                        folder = selectedFolderOption
                                    )
                                    viewModel.updateContact(updated)
                                    selectedContact = updated
                                    Toast.makeText(context, "Contact details updated!", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.createContact(
                                        firstName = firstName.trim(),
                                        middleName = middleName.trim(),
                                        lastName = lastName.trim(),
                                        jobTitle = jobTitle.trim(),
                                        email = email.trim(),
                                        address = address.trim(),
                                        phone = phone.trim(),
                                        dobString = dobString.trim(),
                                        photoUri = selectedAvatar.takeIf { it.isNotEmpty() },
                                        anniversaryString = anniversaryString.trim(),
                                        additionalFieldsJson = fieldsJson,
                                        additionalDatesJson = datesJson,
                                        folder = selectedFolderOption
                                    )
                                    Toast.makeText(context, "Contact created in folder: $selectedFolderOption", Toast.LENGTH_SHORT).show()
                                }
                                screenState = ContactScreen.LIST
                            } else {
                                Toast.makeText(context, "First name is mandatory", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Details", fontWeight = FontWeight.Bold)
                    }
                }

                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Profile image configure column (Left inside Form)
                    Column(
                        modifier = Modifier.width(200.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("PROFILE PICTURE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
                        
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Charcoal)
                                .border(2.dp, WaterBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedAvatar.isNotEmpty()) {
                                AsyncImage(
                                    model = selectedAvatar,
                                    contentDescription = "Profile Pic Selector",
                                    modifier = Modifier.clip(CircleShape).fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            }
                        }

                        Button(
                            onClick = { galleryPickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f), contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("From Device", fontSize = 11.sp)
                        }

                        // Presets Row
                        Text("Select Preset Avatar:", fontSize = 10.sp, color = Color.Gray)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            items(AVATAR_OPTIONS) { url ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedAvatar == url) WaterBlue.copy(alpha = 0.3f) else Color.Transparent)
                                        .border(
                                            width = if (selectedAvatar == url) 2.dp else 1.dp,
                                            color = if (selectedAvatar == url) WaterBlue else Color.Gray.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedAvatar = url }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.clip(CircleShape).fillMaxSize())
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Folder selector dropdown/radio options
                        Text("ASSOCIATED FOLDER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
                        
                        var showFolderDropdown by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Charcoal)
                                .clickable { showFolderDropdown = true }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedFolderOption, color = Color.White, fontSize = 13.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = WaterBlue)
                            }

                            DropdownMenu(
                                expanded = showFolderDropdown,
                                onDismissRequest = { showFolderDropdown = false },
                                modifier = Modifier.background(SurfaceCard)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All", color = Color.White) },
                                    onClick = {
                                        selectedFolderOption = "All"
                                        showFolderDropdown = false
                                    }
                                )
                                foldersList.forEach { folder ->
                                    DropdownMenuItem(
                                        text = { Text(folder, color = Color.White) },
                                        onClick = {
                                            selectedFolderOption = folder
                                            showFolderDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Fields configure column
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            TextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                label = { Text("First Name *") },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("contact_first_name")
                            )
                        }
                        item {
                            TextField(
                                value = middleName,
                                onValueChange = { middleName = it },
                                label = { Text("Middle Name") },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            TextField(
                                value = lastName,
                                onValueChange = { lastName = it },
                                label = { Text("Last Name") },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            TextField(
                                value = jobTitle,
                                onValueChange = { jobTitle = it },
                                label = { Text("Job Title") },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            TextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone Number") },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            TextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address") },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            TextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Street Address") },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            TextField(
                                value = dobString,
                                onValueChange = { dobString = formatAutoDate(it, dobString) },
                                label = { Text("Date of Birth (DD/MM/YYYY)") },
                                placeholder = { Text("e.g. 20/06/1998") },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            TextField(
                                value = anniversaryString,
                                onValueChange = { anniversaryString = formatAutoDate(it, anniversaryString) },
                                label = { Text("Anniversary (DD/MM/YYYY)") },
                                placeholder = { Text("e.g. 15/08/2012") },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray,
                                    focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Custom Dates
                        item {
                            Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                            Text("Custom Dates to Remember", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        items(customDatesList.toList()) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${pair.first}: ${pair.second}", color = Color.White, fontSize = 12.sp)
                                IconButton(onClick = { customDatesList.remove(pair) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = customDateNameDraft,
                                    onValueChange = { customDateNameDraft = it },
                                    label = { Text("Label") },
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard),
                                    modifier = Modifier.weight(1f)
                                )
                                TextField(
                                    value = customDateValueDraft,
                                    onValueChange = { customDateValueDraft = formatAutoDate(it, customDateValueDraft) },
                                    label = { Text("DD/MM/YYYY") },
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard),
                                    modifier = Modifier.weight(1.2f)
                                )
                                IconButton(onClick = {
                                    if (customDateNameDraft.isNotBlank() && customDateValueDraft.isNotBlank()) {
                                        customDatesList.add(customDateNameDraft.trim() to customDateValueDraft.trim())
                                        customDateNameDraft = ""
                                        customDateValueDraft = ""
                                    }
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Custom Date", tint = WaterBlue)
                                }
                            }
                        }

                        // Custom Fields
                        item {
                            Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                            Text("Custom Info Fields", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        items(customFieldsList.toList()) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${pair.first}: ${pair.second}", color = Color.White, fontSize = 12.sp)
                                IconButton(onClick = { customFieldsList.remove(pair) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = customFieldNameDraft,
                                    onValueChange = { customFieldNameDraft = it },
                                    label = { Text("Field Name") },
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard),
                                    modifier = Modifier.weight(1f)
                                )
                                TextField(
                                    value = customFieldValueDraft,
                                    onValueChange = { customFieldValueDraft = it },
                                    label = { Text("Value") },
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard),
                                    modifier = Modifier.weight(1.2f)
                                )
                                IconButton(onClick = {
                                    if (customFieldNameDraft.isNotBlank() && customFieldValueDraft.isNotBlank()) {
                                        customFieldsList.add(customFieldNameDraft.trim() to customFieldValueDraft.trim())
                                        customFieldNameDraft = ""
                                        customFieldValueDraft = ""
                                    }
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Custom Field", tint = WaterBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS FOR FOLDER ACTIONS ---

    // 1. Create Folder
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                TextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray, focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.trim().isNotEmpty()) {
                            viewModel.createContactFolder(newFolderName.trim())
                            showCreateFolderDialog = false
                        } else {
                            Toast.makeText(context, "Folder name cannot be blank", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    // 2. Rename & Delete Folder Selector Dialog (triggered on folder long press)
    if (showRenameFolderDialog) {
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = false },
            title = { Text("Folder Options: $renameFolderOldName", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Rename Folder:", color = Color.Gray, fontSize = 12.sp)
                    TextField(
                        value = renameFolderNewName,
                        onValueChange = { renameFolderNewName = it },
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray, focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Danger Zone:", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            showRenameFolderDialog = false
                            showDeleteFolderDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Folder and reset contacts to All", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameFolderNewName.trim().isNotEmpty() && renameFolderNewName != renameFolderOldName) {
                            viewModel.renameContactFolder(renameFolderOldName, renameFolderNewName.trim())
                            if (selectedFolder == renameFolderOldName) {
                                selectedFolder = renameFolderNewName.trim()
                            }
                            showRenameFolderDialog = false
                            Toast.makeText(context, "Folder renamed to: $renameFolderNewName", Toast.LENGTH_SHORT).show()
                        } else {
                            showRenameFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Rename", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFolderDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    // 3. Delete Folder Confirmation
    if (showDeleteFolderDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFolderDialog = false },
            title = { Text("Delete Folder?", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Text(
                    "Are you sure you want to delete folder '$folderToDelete'? Contacts inside this folder will stay preserved, resetting back to the 'All' folder.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteContactFolder(folderToDelete)
                        if (selectedFolder == folderToDelete) {
                            selectedFolder = "All"
                        }
                        showDeleteFolderDialog = false
                        Toast.makeText(context, "Folder deleted successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) {
                    Text("Delete Folder", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFolderDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    // --- DIALOGS FOR CONTACT ACTIONS (LONG PRESS TARGETS) ---

    // 1. Long Press Main Actions
    if (showContactActionDialog && longPressedContact != null) {
        AlertDialog(
            onDismissRequest = { showContactActionDialog = false },
            title = {
                Text(
                    text = "Manage ${longPressedContact?.firstName} ${longPressedContact?.lastName}",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            showContactActionDialog = false
                            // Load form entries to go to edit screen
                            val c = longPressedContact!!
                            firstName = c.firstName
                            middleName = c.middleName
                            lastName = c.lastName
                            jobTitle = c.jobTitle
                            email = c.email
                            address = c.address
                            phone = c.phone
                            dobString = c.dobString
                            anniversaryString = c.anniversaryString
                            selectedAvatar = c.photoUri ?: ""
                            selectedFolderOption = c.folder

                            customFieldsList.clear()
                            if (c.additionalFieldsJson.isNotEmpty()) {
                                c.additionalFieldsJson.split(";").forEach { pair ->
                                    val parts = pair.split(":")
                                    if (parts.size == 2) customFieldsList.add(parts[0] to parts[1])
                                }
                            }

                            customDatesList.clear()
                            if (c.additionalDatesJson.isNotEmpty()) {
                                c.additionalDatesJson.split(";").forEach { pair ->
                                    val parts = pair.split(":")
                                    if (parts.size == 2) customDatesList.add(parts[0] to parts[1])
                                }
                            }

                            screenState = ContactScreen.EDIT
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Contact", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showContactActionDialog = false
                            showConfirmDeleteContactDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Contact", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showContactActionDialog = false }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    // 2. Confirm Delete Contact
    if (showConfirmDeleteContactDialog && longPressedContact != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteContactDialog = false },
            title = { Text("Delete Contact?", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Text(
                    "This action is permanent. Are you sure you want to securely delete ${longPressedContact?.firstName} ${longPressedContact?.lastName} from your address book?",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c = longPressedContact!!
                        viewModel.deleteContact(c)
                        if (selectedContact?.id == c.id) {
                            selectedContact = null
                        }
                        showConfirmDeleteContactDialog = false
                        Toast.makeText(context, "Contact deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                ) {
                    Text("Delete Details", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteContactDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun ContactDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 13.sp)
        }
    }
}

// Helper functions for file management
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
    return result ?: "unnamed_document"
}

private fun copyUriToLocalFile(context: android.content.Context, uri: Uri, destFileName: String): File? {
    return com.example.util.StorageHelper.copyFileToInternalSandbox(context, uri)
}

private fun isPhotoFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp" || ext == "gif"
}

private fun saveFileToDownloads(context: android.content.Context, sourceFile: File, displayName: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val mime = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "3gp" -> "video/3gp"
                "mkv" -> "video/x-matroska"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "m4a" -> "audio/mp4"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val downloadsUri = android.net.Uri.parse("content://media/external/downloads")
            resolver.insert(downloadsUri, contentValues)
        } else {
            null
        }
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            true
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, displayName)
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

