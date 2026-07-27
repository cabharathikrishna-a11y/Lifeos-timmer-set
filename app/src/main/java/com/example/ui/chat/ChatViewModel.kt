package com.example.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.model.ChatMessage
import com.example.model.UserPresence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId

enum class ChatDateRangeFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    LAST_30_DAYS("30 Days")
}

enum class ChatOptionType {
    STUDY_GROUP,
    DIRECT_MESSAGE
}

data class ChatOption(
    val id: String,
    val name: String,
    val type: ChatOptionType,
    val description: String,
    val memberUserId: String? = null,
    val iconEmoji: String = "🎓",
    val memberCount: Int = 0,
    val isOnline: Boolean = false,
    val roleTitle: String = ""
)

class ChatViewModel(
    private val repository: ChatRepository = ChatRepository(),
    val currentUserId: String = "user_me"
) : ViewModel() {

    private val localRepository = try {
        val app = com.example.MainApplication.instance
        val db = com.example.data.AppDatabase.getInstance(app)
        com.example.data.LocalRepository(db, app)
    } catch (e: Exception) {
        null
    }

    private val initialStudyGroups = listOf(
        ChatOption(
            id = "group_main",
            name = "Study Group",
            type = ChatOptionType.STUDY_GROUP,
            description = "Main Study Group Community",
            iconEmoji = "🎓",
            memberCount = 1
        )
    )

    private val initialDirectMessageMembers = emptyList<ChatOption>()

    private val _studyGroups = MutableStateFlow(initialStudyGroups)
    val studyGroups: StateFlow<List<ChatOption>> = _studyGroups.asStateFlow()

    private val _directMessageMembers = MutableStateFlow(initialDirectMessageMembers)
    val directMessageMembers: StateFlow<List<ChatOption>> = _directMessageMembers.asStateFlow()

    private val _selectedChatOption = MutableStateFlow(initialStudyGroups[0])
    val selectedChatOption: StateFlow<ChatOption> = _selectedChatOption.asStateFlow()

    private val _showChatOptionsSheet = MutableStateFlow(false)
    val showChatOptionsSheet: StateFlow<Boolean> = _showChatOptionsSheet.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSenderFilter = MutableStateFlow<String?>(null)
    val selectedSenderFilter: StateFlow<String?> = _selectedSenderFilter.asStateFlow()

    private val _selectedDateRangeFilter = MutableStateFlow(ChatDateRangeFilter.ALL)
    val selectedDateRangeFilter: StateFlow<ChatDateRangeFilter> = _selectedDateRangeFilter.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _presenceMap = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
    val presenceMap: StateFlow<Map<String, UserPresence>> = _presenceMap.asStateFlow()

    private val _replyingToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyingToMessage: StateFlow<ChatMessage?> = _replyingToMessage.asStateFlow()

    private val _selectedMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMessageIds: StateFlow<Set<Long>> = _selectedMessageIds.asStateFlow()

    private val _isMultiSelectActive = MutableStateFlow(false)
    val isMultiSelectActive: StateFlow<Boolean> = _isMultiSelectActive.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    private var currentLimit = 100
    private var canLoadMore = true

    val filteredMessages: StateFlow<List<ChatMessage>> = combine(
        _messages ?: MutableStateFlow(emptyList()),
        _searchQuery ?: MutableStateFlow(""),
        _selectedSenderFilter ?: MutableStateFlow(null),
        _selectedDateRangeFilter ?: MutableStateFlow(ChatDateRangeFilter.ALL),
        _selectedChatOption ?: MutableStateFlow(initialStudyGroups[0])
    ) { msgList, query, sender, dateFilter, option ->
        val trimmedQuery = query.trim().lowercase()
        val now = ZonedDateTime.now(ZoneId.systemDefault())

        msgList.filter { msg ->
            // 0. Channel / Direct Message Isolation
            val matchesOption = if (option.type == ChatOptionType.STUDY_GROUP) {
                if (option.id == "group_main") {
                    !msg.text.contains("[GROUP:") && !msg.text.contains("[DM:")
                } else {
                    msg.text.contains("[GROUP:${option.id}]")
                }
            } else {
                val targetMember = option.memberUserId ?: ""
                msg.text.contains("[DM:$targetMember]") ||
                        (msg.senderId == targetMember && (msg.replyToSender == currentUserId || msg.text.contains("[DM:$currentUserId]") || msg.text.contains("[DM:$targetMember]"))) ||
                        (msg.senderId == currentUserId && (msg.replyToSender == targetMember || msg.text.contains("[DM:$targetMember]")))
            }

            // 1. Keyword search (matches message text or sender name)
            val matchesKeyword = if (trimmedQuery.isEmpty()) true else {
                msg.text.lowercase().contains(trimmedQuery) ||
                        msg.senderId.lowercase().replace("_", " ").contains(trimmedQuery)
            }

            // 2. Sender filter
            val matchesSender = if (sender.isNullOrEmpty()) true else {
                msg.senderId.equals(sender, ignoreCase = true)
            }

            // 3. Date range filter
            val matchesDate = when (dateFilter) {
                ChatDateRangeFilter.ALL -> true
                ChatDateRangeFilter.TODAY -> isSameDay(msg.createdAt, now)
                ChatDateRangeFilter.LAST_7_DAYS -> isWithinDays(msg.createdAt, now, 7)
                ChatDateRangeFilter.LAST_30_DAYS -> isWithinDays(msg.createdAt, now, 30)
            }

            matchesOption && matchesKeyword && matchesSender && matchesDate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableSenders: StateFlow<List<String>> = (_messages ?: MutableStateFlow(emptyList())).map { list ->
        list.map { it.senderId }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedMessages: StateFlow<List<ChatMessage>> = (_messages ?: MutableStateFlow(emptyList())).map { list ->
        list.filter { it.isPinned }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun isSelf(userId: String): Boolean {
        val s = userId.trim().lowercase()
        if (s.isBlank()) return true
        if (s == "user_me" || s == "user me" || s == "me" || s == "myself") return true
        if (s == currentUserId.trim().lowercase()) return true
        val context = com.example.MainApplication.instance
        val appPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val myEmail = appPrefs.getString("user_email", "")?.trim()?.lowercase() ?: ""
        val myUsername = appPrefs.getString("logged_in_username", "")?.trim()?.lowercase() ?: ""
        val emailPrefix = if (myEmail.contains("@")) myEmail.substringBefore("@") else ""
        if (myEmail.isNotBlank() && s == myEmail) return true
        if (myUsername.isNotBlank() && s == myUsername) return true
        if (emailPrefix.isNotBlank() && s == emailPrefix) return true
        return false
    }

    val onlineUsers: StateFlow<List<String>> = (_presenceMap ?: MutableStateFlow(emptyMap())).map { map ->
        map.filter { (id, presence) -> !isSelf(id) && presence.isOnline }.keys.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val typingUsers: StateFlow<List<String>> = (_presenceMap ?: MutableStateFlow(emptyMap())).map { map ->
        map.filter { (id, presence) -> !isSelf(id) && presence.isOnline && presence.isTyping }.keys.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun observePeerLiveSphereForMembers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.api.PeerLiveSphereManager.peerLiveStates.collect { peerMap ->
                    val memberDms = peerMap.filter { (email, _) ->
                        !isSelf(email)
                    }.map { (email, peer) ->
                        val peerName = peer.displayName.ifBlank { email.substringBefore("@") }
                        val emoji = peer.customEmoji?.ifBlank { "🧑‍💻" } ?: "🧑‍💻"
                        val isUserOnline = peer.status.isNotBlank() && !peer.status.equals("idle", ignoreCase = true)
                        ChatOption(
                            id = "dm_peer_${email.replace(".", "_")}",
                            name = peerName,
                            type = ChatOptionType.DIRECT_MESSAGE,
                            description = if (peer.status.isNotBlank()) "Study Group • ${peer.status}" else "Study Group Member",
                            memberUserId = email,
                            iconEmoji = emoji,
                            isOnline = isUserOnline,
                            roleTitle = "Study Group Member"
                        )
                    }

                    // Only Study Group Focus Locker members shown in Live Sphere are available for DMs
                    _directMessageMembers?.value = memberDms.distinctBy { it.id }

                    // Sync main study group member count: actual distinct peers (excluding self) + 1 (for self)
                    val currentGroups = _studyGroups?.value ?: initialStudyGroups
                    val totalMembers = memberDms.size + 1
                    val updatedGroups = currentGroups.map { grp ->
                        if (grp.id == "group_main") {
                            grp.copy(memberCount = totalMembers)
                        } else {
                            grp
                        }
                    }
                    _studyGroups?.value = updatedGroups
                }
            } catch (e: Throwable) {
                android.util.Log.e("ChatViewModel", "Error observing PeerLiveSphere members for chat", e)
            }
        }
    }

    private fun observeRoomDatabaseFlow() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.getAllCachedMessagesFlow().collect { roomList ->
                    if (roomList.isNotEmpty()) {
                        val currentList = _messages?.value ?: emptyList()
                        val mergedMap = LinkedHashMap<Long, ChatMessage>()
                        for (msg in roomList) {
                            val key = if (msg.id != 0L) msg.id else msg.hashCode().toLong()
                            mergedMap[key] = msg
                        }
                        for (msg in currentList) {
                            val key = if (msg.id != 0L) msg.id else msg.hashCode().toLong()
                            if (!mergedMap.containsKey(key)) {
                                mergedMap[key] = msg
                            }
                        }
                        _messages?.value = mergedMap.values.sortedBy { it.id }
                        _isLoading?.value = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error observing Room database flow", e)
            }
        }
    }

    init {
        observeRoomDatabaseFlow()
        loadInitialMessagesAndSubscribe()
        listenToRtdbChatHistory()
        observePeerLiveSphereForMembers()
    }

    fun createCustomStudyGroup(name: String, description: String, iconEmoji: String = "🎓") {
        if (name.isBlank()) return
        val newGroup = ChatOption(
            id = "group_custom_${System.currentTimeMillis()}",
            name = name.trim(),
            type = ChatOptionType.STUDY_GROUP,
            description = description.ifBlank { "Custom Study Group" },
            iconEmoji = iconEmoji.ifBlank { "🎓" },
            memberCount = 1
        )
        _studyGroups?.value = (_studyGroups?.value ?: emptyList()) + newGroup
        selectChatOption(newGroup)
    }

    fun createCustomContactDM(firstName: String, lastName: String, email: String = "", phone: String = "") {
        val targetEmail = email.trim().ifBlank { phone.trim() }
        if (isSelf(targetEmail)) return
        val name = "${firstName.trim()} ${lastName.trim()}".trim().ifBlank { targetEmail.substringBefore("@") }
        if (targetEmail.isBlank() && name.isBlank()) return

        val newDm = ChatOption(
            id = "dm_peer_${targetEmail.replace(".", "_")}",
            name = name.ifBlank { targetEmail },
            type = ChatOptionType.DIRECT_MESSAGE,
            description = "Study Group Focus Locker Member",
            memberUserId = if (targetEmail.contains("@")) targetEmail else "$targetEmail@gmail.com",
            iconEmoji = "🧑‍💻",
            isOnline = true,
            roleTitle = "Study Group Member"
        )
        val currentDms = _directMessageMembers?.value ?: emptyList()
        val updated = (currentDms + newDm).distinctBy { it.id }
        _directMessageMembers?.value = updated
        selectChatOption(newDm)
    }

    fun selectChatOption(option: ChatOption) {
        _selectedChatOption?.value = option
        _showChatOptionsSheet?.value = false
        if (option.type == ChatOptionType.STUDY_GROUP) {
            syncGroupMembersToDms(option)
        }
    }

    fun openChatOptionsSheet() {
        _showChatOptionsSheet?.value = true
    }

    fun closeChatOptionsSheet() {
        _showChatOptionsSheet?.value = false
    }

    private fun syncGroupMembersToDms(group: ChatOption) {
        viewModelScope.launch(Dispatchers.IO) {
            val peerStates = com.example.api.PeerLiveSphereManager.peerLiveStates.value
            val memberDms = peerStates.filter { (email, _) ->
                !isSelf(email)
            }.map { (email, peer) ->
                val peerName = peer.displayName.ifBlank { email.substringBefore("@") }
                val emoji = peer.customEmoji?.ifBlank { "🧑‍💻" } ?: "🧑‍💻"
                val isUserOnline = peer.status.isNotBlank() && !peer.status.equals("idle", ignoreCase = true)
                ChatOption(
                    id = "dm_peer_${email.replace(".", "_")}",
                    name = peerName,
                    type = ChatOptionType.DIRECT_MESSAGE,
                    description = "Study Group Member • ${group.name}",
                    memberUserId = email,
                    iconEmoji = emoji,
                    isOnline = isUserOnline,
                    roleTitle = "Study Group Member"
                )
            }

            _directMessageMembers?.value = memberDms.distinctBy { it.id }
        }
    }

    private fun ensureSampleMessagesForOption(option: ChatOption) {
        // Strictly no sample or fake trial messages
    }


    fun setReplyingTo(message: ChatMessage?) {
        _replyingToMessage?.value = message
    }

    fun cancelReply() {
        _replyingToMessage?.value = null
    }

    fun toggleMessageSelection(messageId: Long) {
        val current = _selectedMessageIds?.value ?: emptySet()
        if (current.contains(messageId)) {
            val updated = current - messageId
            _selectedMessageIds?.value = updated
            if (updated.isEmpty()) {
                _isMultiSelectActive?.value = false
            }
        } else {
            _selectedMessageIds?.value = current + messageId
            _isMultiSelectActive?.value = true
        }
    }

    fun startMultiSelect(initialMessageId: Long? = null) {
        _isMultiSelectActive?.value = true
        _selectedMessageIds?.value = if (initialMessageId != null) setOf(initialMessageId) else emptySet()
    }

    fun clearSelection() {
        _selectedMessageIds?.value = emptySet()
        _isMultiSelectActive?.value = false
    }

    fun deleteSelectedMessages() {
        val idsToDelete = _selectedMessageIds?.value ?: emptySet()
        if (idsToDelete.isEmpty()) return
        val currentList = _messages?.value ?: emptyList()
        _messages?.value = currentList.filter { it.id !in idsToDelete }
        clearSelection()

        viewModelScope.launch(Dispatchers.IO) {
            idsToDelete.forEach { id ->
                try {
                    repository.deleteMessage(id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getSelectedMessagesText(context: android.content.Context): String {
        val ids = _selectedMessageIds?.value ?: emptySet()
        return (_messages?.value ?: emptyList())
            .filter { it.id in ids }
            .joinToString("\n---\n") { "${it.senderId.replace("_", " ")}: ${it.text}" }
    }

    fun onSendMediaMessage(mediaType: String, mediaPathOrUri: String, caption: String = "") {
        val cleanType = mediaType.lowercase()
        val fileId = "drive_${cleanType}_${System.currentTimeMillis()}"
        val driveShareUrl = "https://drive.google.com/file/d/$fileId/view?usp=sharing"
        val driveDirectUrl = "https://drive.google.com/uc?export=download&id=$fileId"

        val typeTag = when (cleanType) {
            "voice", "audio" -> "VOICE"
            "image", "photo" -> "IMAGE"
            "video" -> "VIDEO"
            else -> "FILE"
        }

        val typeLabel = when (typeTag) {
            "VOICE" -> "🎙️ Voice Recording"
            "IMAGE" -> "📷 Photo Attachment"
            "VIDEO" -> "🎥 Video Attachment"
            else -> "📁 File Attachment"
        }

        val captionText = if (caption.isNotBlank()) "\n$caption" else ""
        val fullFormattedText = "$typeLabel (Google Drive link: $driveShareUrl)$captionText\n[$typeTag:$mediaPathOrUri|$driveDirectUrl]"

        onSendMessage(fullFormattedText)
    }

    fun loadInitialMessagesAndSubscribe() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading?.value = true
                _errorMessage?.value = null
                currentLimit = 100
                canLoadMore = true

                // 1. Load initial 100 locally cached Room messages first for immediate display
                try {
                    val cached = repository.getRecentCachedMessages(100)
                    if (cached.isNotEmpty()) {
                        _messages?.value = cached
                        _isLoading?.value = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. Fetch last 100 messages from Supabase and sync with Room
                try {
                    val synced = repository.fetchAndCacheRecentMessages(100)
                    if (synced.isNotEmpty()) {
                        _messages?.value = synced
                    }
                    // Mark unread incoming messages as READ when recipient opens chat
                    markAllUnreadIncomingMessagesAsRead()
                } catch (e: Exception) {
                    if ((_messages?.value ?: emptyList()).isEmpty()) {
                        _errorMessage?.value = "Unable to fetch Supabase messages: ${e.localizedMessage ?: "Connection failed"}"
                    }
                } finally {
                    _isLoading?.value = false
                }

                // 3. Connect Realtime WebSocket subscription for live messages & status updates
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        repository.subscribeToPresence(currentUserId).collect { presenceMap ->
                            _presenceMap?.value = presenceMap
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                try {
                    repository.subscribeToNewMessages().collect { realtimeMsg ->
                        if (realtimeMsg.text == "__DELETED__") {
                            _messages?.value = (_messages?.value ?: emptyList()).filter { it.id != realtimeMsg.id }
                            if (_editingMessage?.value?.id == realtimeMsg.id) {
                                _editingMessage?.value = null
                            }
                        } else {
                            val existingList = _messages?.value ?: emptyList()
                            val existingIndex = existingList.indexOfFirst { (it.id != 0L && it.id == realtimeMsg.id) }

                            if (existingIndex != -1) {
                                // Message status or text update (e.g., status changed to READ)
                                val updatedList = existingList.toMutableList()
                                updatedList[existingIndex] = realtimeMsg
                                _messages?.value = updatedList
                            } else {
                                // New incoming message
                                val isFromOther = realtimeMsg.senderId != currentUserId
                                val finalMsg = if (isFromOther) realtimeMsg.copy(status = "READ") else realtimeMsg
                                
                                _messages?.value = (existingList + finalMsg).distinctBy { if (it.id != 0L) it.id else it.hashCode() }

                                if (isFromOther) {
                                    sendIncomingMessageNotification(realtimeMsg)
                                    // Emit READ receipt update via Supabase & Room
                                    viewModelScope.launch(Dispatchers.IO) {
                                        try {
                                            repository.markMessagesAsRead(listOf(realtimeMsg.id))
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Throwable) {
                android.util.Log.e("ChatViewModel", "Error in loadInitialMessagesAndSubscribe", e)
            }
        }
    }

    private fun setupPresenceAndTyping() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = com.example.MainApplication.instance
                val dbUrl = com.example.api.FirebaseConfig.getChatDatabaseUrl(context)
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)

                val statusRef = database.getReference("status").child(currentUserId)
                statusRef.child("state").onDisconnect().setValue("offline")
                statusRef.child("last_changed").onDisconnect().setValue(com.google.firebase.database.ServerValue.TIMESTAMP)

                val onlineStatus = mapOf(
                    "state" to "online",
                    "last_changed" to System.currentTimeMillis()
                )
                statusRef.setValue(onlineStatus)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error in setupPresenceAndTyping", e)
            }
        }
    }

    fun setTypingState(chatId: String, isTyping: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = com.example.MainApplication.instance
                val dbUrl = com.example.api.FirebaseConfig.getChatDatabaseUrl(context)
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                val typingRef = database.getReference("typing").child(chatId).child(currentUserId)
                if (isTyping) {
                    typingRef.setValue(true)
                    typingRef.onDisconnect().removeValue()
                } else {
                    typingRef.removeValue()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error setting typing state", e)
            }
        }
    }

    private fun listenToRtdbChatHistory() {
        setupPresenceAndTyping()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = com.example.MainApplication.instance
                // Explicitly restricted to Chat Messages & History URL: https://cloud-storage-f8ab3-default-rtdb.asia-southeast1.firebasedatabase.app/
                val dbUrl = com.example.api.FirebaseConfig.getChatDatabaseUrl(context)
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                
                // Connection rule: Limit active real-time listener to last 30 messages
                val chatQuery = database.getReference("community_chat/messages").limitToLast(30)

                chatQuery.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        if (snapshot.exists()) {
                            val rtdbList = mutableListOf<ChatMessage>()
                            for (child in snapshot.children) {
                                try {
                                    val id = child.child("id").getValue(Long::class.java) ?: (child.key?.toLongOrNull() ?: 0L)
                                    val senderId = child.child("senderId").getValue(String::class.java) 
                                        ?: child.child("sender").getValue(String::class.java) ?: "community_user"
                                    val text = child.child("text").getValue(String::class.java) ?: ""
                                    val createdAt = child.child("createdAt").getValue(String::class.java) ?: "Just now"
                                    val status = child.child("status").getValue(String::class.java) ?: "SENT"
                                    val isPinned = child.child("isPinned").getValue(Boolean::class.java) ?: false
                                    val reactions = child.child("reactions").getValue(String::class.java) ?: ""
                                    val replyToId = child.child("replyToId").getValue(Long::class.java)
                                    val replyToText = child.child("replyToText").getValue(String::class.java)
                                    val replyToSender = child.child("replyToSender").getValue(String::class.java)

                                    var type = child.child("type").getValue(String::class.java) ?: "text"
                                    val rawContent = child.child("content").getValue(String::class.java)
                                        ?: child.child("content_url").getValue(String::class.java) ?: ""
                                    var contentUrl = if (rawContent.isNotBlank()) com.example.util.DriveUrlUtil.toDirectDownloadUrl(rawContent) else null

                                    val metaChild = child.child("metadata")
                                    var fileName = child.child("fileName").getValue(String::class.java)
                                        ?: metaChild.child("file_name").getValue(String::class.java)
                                    var fileSizeKb = child.child("fileSizeKb").getValue(Int::class.java)
                                        ?: metaChild.child("file_size_kb").getValue(Int::class.java)
                                    val mimeType = child.child("mimeType").getValue(String::class.java)
                                        ?: metaChild.child("mime_type").getValue(String::class.java)
                                    var durationSec = child.child("durationSec").getValue(Int::class.java)
                                        ?: metaChild.child("duration_sec").getValue(Int::class.java)
                                    val ts = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                                    // Parse formatted text payload if present (e.g. [ATTACHMENT:voice|url|filename|duration|size])
                                    val parsedFormatted = com.example.util.DriveUrlUtil.parseAttachmentText(text)
                                    if (parsedFormatted != null) {
                                        type = parsedFormatted.type
                                        contentUrl = parsedFormatted.directUrl
                                        fileName = parsedFormatted.fileName
                                        durationSec = parsedFormatted.durationSec
                                        fileSizeKb = parsedFormatted.fileSizeKb
                                    }

                                    if ((text.isNotBlank() || !contentUrl.isNullOrBlank()) && id != 0L) {
                                        val msg = ChatMessage(
                                            id = id,
                                            senderId = senderId,
                                            text = text,
                                            createdAt = createdAt,
                                            status = status,
                                            isPinned = isPinned,
                                            reactions = reactions,
                                            replyToId = replyToId,
                                            replyToText = replyToText,
                                            replyToSender = replyToSender,
                                            type = type,
                                            contentUrl = contentUrl,
                                            fileName = fileName,
                                            fileSizeKb = fileSizeKb,
                                            mimeType = mimeType,
                                            durationSec = durationSec,
                                            timestamp = ts
                                        )
                                        rtdbList.add(msg)

                                        // Media Auto-Download Engine: If media/doc/voice link present, download silently to local storage
                                        if (!contentUrl.isNullOrBlank() && type != "text") {
                                            viewModelScope.launch(Dispatchers.IO) {
                                                val downloaded = com.example.util.DriveUrlUtil.downloadMediaToLocal(context, contentUrl, fileName)
                                                if (downloaded != null) {
                                                    val localMsg = msg.copy(localPath = downloaded.absolutePath)
                                                    repository.insertMessageToCache(localMsg)
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatViewModel", "Error parsing RTDB chat msg", e)
                                }
                            }

                            if (rtdbList.isNotEmpty()) {
                                val currentList = _messages?.value ?: emptyList()
                                val mapById = LinkedHashMap<Long, ChatMessage>()
                                for (msg in currentList) {
                                    val key = if (msg.id != 0L) msg.id else msg.hashCode().toLong()
                                    mapById[key] = msg
                                }
                                for (rtdbMsg in rtdbList) {
                                    val rtdbKey = if (rtdbMsg.id != 0L) rtdbMsg.id else rtdbMsg.hashCode().toLong()
                                    // Match existing message by key or by senderId + text if sent close in time
                                    val existingKey = mapById.keys.find { k ->
                                        k == rtdbKey || mapById[k]?.let { existing ->
                                            existing.senderId == rtdbMsg.senderId &&
                                            existing.text.trim() == rtdbMsg.text.trim() &&
                                            Math.abs(existing.timestamp - rtdbMsg.timestamp) < 15000L
                                        } == true
                                    } ?: rtdbKey

                                    val existing = mapById[existingKey]
                                    if (existing != null) {
                                        mapById[existingKey] = existing.copy(
                                            id = if (rtdbMsg.id != 0L) rtdbMsg.id else existing.id,
                                            isPinned = if (rtdbMsg.isPinned) true else existing.isPinned,
                                            reactions = if (rtdbMsg.reactions.isNotBlank()) rtdbMsg.reactions else existing.reactions,
                                            text = if (rtdbMsg.text.isNotBlank()) rtdbMsg.text else existing.text,
                                            status = if (rtdbMsg.status.isNotBlank()) rtdbMsg.status else existing.status,
                                            type = rtdbMsg.type,
                                            contentUrl = rtdbMsg.contentUrl ?: existing.contentUrl,
                                            localPath = existing.localPath ?: rtdbMsg.localPath
                                        )
                                    } else {
                                        mapById[rtdbKey] = rtdbMsg
                                    }
                                }
                                _messages?.value = mapById.values.toList()

                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        repository.insertMessagesToCache(rtdbList)
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatViewModel", "Failed caching RTDB messages", e)
                                    }
                                }
                            }
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        android.util.Log.e("ChatViewModel", "RTDB Chat listener error: ${error.message}")
                    }
                })

                val pinnedRef = database.getReference("community_chat/pinned_messages")
                pinnedRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(pinnedSnapshot: com.google.firebase.database.DataSnapshot) {
                        if (pinnedSnapshot.exists()) {
                            val pinnedFromRtdb = mutableListOf<ChatMessage>()
                            for (pChild in pinnedSnapshot.children) {
                                try {
                                    val id = pChild.child("id").getValue(Long::class.java) ?: (pChild.key?.toLongOrNull() ?: 0L)
                                    val senderId = pChild.child("senderId").getValue(String::class.java) ?: "user"
                                    val text = pChild.child("text").getValue(String::class.java) ?: ""
                                    val createdAt = pChild.child("createdAt").getValue(String::class.java) ?: "Just now"
                                    val status = pChild.child("status").getValue(String::class.java) ?: "SENT"
                                    val reactions = pChild.child("reactions").getValue(String::class.java) ?: ""

                                    if (text.isNotBlank() && id != 0L) {
                                        pinnedFromRtdb.add(
                                            ChatMessage(
                                                id = id,
                                                senderId = senderId,
                                                text = text,
                                                createdAt = if (!createdAt.isNullOrBlank() && createdAt != "Just now") createdAt else java.time.Instant.ofEpochMilli(if (id > 1000000000000L) id else System.currentTimeMillis()).toString(),
                                                timestamp = if (id > 1000000000000L) id else System.currentTimeMillis(),
                                                status = status,
                                                isPinned = true,
                                                reactions = reactions
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatViewModel", "Error parsing RTDB pinned msg", e)
                                }
                            }

                            if (pinnedFromRtdb.isNotEmpty()) {
                                val currentList = _messages?.value ?: emptyList()
                                val mapById = LinkedHashMap<Long, ChatMessage>()
                                for (msg in currentList) {
                                    val key = if (msg.id != 0L) msg.id else msg.hashCode().toLong()
                                    mapById[key] = msg
                                }
                                for (pMsg in pinnedFromRtdb) {
                                    val key = if (pMsg.id != 0L) pMsg.id else pMsg.hashCode().toLong()
                                    val existing = mapById[key]
                                    if (existing != null) {
                                        mapById[key] = existing.copy(isPinned = true)
                                    } else {
                                        mapById[key] = pMsg
                                    }
                                }
                                _messages?.value = mapById.values.toList()
                                viewModelScope.launch(Dispatchers.IO) {
                                    repository.insertMessagesToCache(pinnedFromRtdb)
                                }
                            }
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                })
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed setting up RTDB chat listener", e)
            }
        }
    }

    private fun syncMessageToRtdb(message: ChatMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = com.example.MainApplication.instance
                // Explicitly restricted to Chat Messages & History URL: https://cloud-storage-f8ab3-default-rtdb.asia-southeast1.firebasedatabase.app/
                val dbUrl = com.example.api.FirebaseConfig.getChatDatabaseUrl(context)
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                val msgKey = if (message.id != 0L) message.id.toString() else System.currentTimeMillis().toString()
                val directUrl = message.contentUrl?.let { com.example.util.DriveUrlUtil.toDirectDownloadUrl(it) }

                val payload = mapOf(
                    "id" to (if (message.id != 0L) message.id else msgKey.toLongOrNull() ?: 0L),
                    "sender" to message.senderId,
                    "senderId" to message.senderId,
                    "text" to message.text,
                    "createdAt" to (message.createdAt.takeIf { !it.isNullOrBlank() && it != "Just now" } ?: java.time.Instant.ofEpochMilli(if (message.timestamp > 0) message.timestamp else System.currentTimeMillis()).toString()),
                    "timestamp" to (if (message.timestamp > 0) message.timestamp else System.currentTimeMillis()),
                    "status" to message.status,
                    "isPinned" to message.isPinned,
                    "reactions" to message.reactions,
                    "replyToId" to message.replyToId,
                    "replyToText" to message.replyToText,
                    "replyToSender" to message.replyToSender,
                    "type" to message.type,
                    "content" to (directUrl ?: message.text),
                    "content_url" to (directUrl ?: message.text),
                    "metadata" to mapOf(
                        "file_name" to (message.fileName ?: ""),
                        "file_size_kb" to (message.fileSizeKb ?: 0),
                        "mime_type" to (message.mimeType ?: ""),
                        "duration_sec" to (message.durationSec ?: 0)
                    ),
                    "timestamp" to message.timestamp
                )

                // 1. Write to main community_chat/messages branch
                database.getReference("community_chat/messages").child(msgKey).setValue(payload)

                // 2. Also write to user_chats inbox node
                val selectedOpt = _selectedChatOption?.value
                val chatId = selectedOpt?.id ?: "group_main"
                val userChatSummary = mapOf(
                    "other_user" to (selectedOpt?.memberUserId ?: "community"),
                    "last_message" to message.text.ifBlank { "Media attached" },
                    "updated_at" to System.currentTimeMillis(),
                    "unread_count" to 0
                )
                database.getReference("user_chats").child(currentUserId).child(chatId).setValue(userChatSummary)

                // 3. Write to /messages/{chatId}/{messageId} branch
                database.getReference("messages").child(chatId).child(msgKey).setValue(payload)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed syncing message to RTDB", e)
            }
        }
    }

    private fun sendIncomingMessageNotification(message: ChatMessage) {
        try {
            val context = com.example.MainApplication.instance
            com.example.util.ChatNotificationHelper.sendNotification(context, message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadMoreMessages() {
        if ((_isLoadingMore?.value == true) || !canLoadMore) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore?.value = true
            val nextLimit = currentLimit + 1000
            try {
                val expanded = repository.fetchAndCacheRecentMessages(nextLimit)
                if (expanded.size <= (_messages?.value?.size ?: 0)) {
                    canLoadMore = false
                } else {
                    _messages?.value = expanded
                    currentLimit = nextLimit
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingMore?.value = false
            }
        }
    }


    fun startEditingMessage(message: ChatMessage) {
        if (message.senderId == currentUserId) {
            _editingMessage?.value = message
        }
    }

    fun cancelEditingMessage() {
        _editingMessage?.value = null
    }

    fun onEditMessage(messageId: Long, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return

        _editingMessage?.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Sync edit directly to RTDB for immediate real-time propagation across all devices
                try {
                    val context = com.example.MainApplication.instance
                    val dbUrl = com.example.api.FirebaseConfig.getChatDatabaseUrl(context)
                    val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                    database.getReference("community_chat/messages")
                        .child(messageId.toString())
                        .child("text")
                        .setValue(trimmed)
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error syncing edit to RTDB", e)
                }

                val updated = repository.editMessage(messageId, trimmed)
                if (updated != null) {
                    _messages?.value = (_messages?.value ?: emptyList()).map {
                        if (it.id == messageId) updated else it
                    }
                } else {
                    _messages?.value = (_messages?.value ?: emptyList()).map {
                        if (it.id == messageId) it.copy(text = trimmed) else it
                    }
                }
            } catch (e: Exception) {
                _messages?.value = (_messages?.value ?: emptyList()).map {
                    if (it.id == messageId) it.copy(text = trimmed) else it
                }
                _errorMessage?.value = "Failed to sync message edit (${e.localizedMessage ?: "Offline"})"
            }
        }
    }

    fun toggleReaction(messageId: Long, emoji: String) {
        val currentList = _messages?.value ?: emptyList()
        val targetMsg = currentList.find { it.id == messageId } ?: return
        val currentMap = targetMsg.parseReactions().toMutableMap()
        val usersForEmoji = (currentMap[emoji] ?: emptyList()).toMutableList()

        if (usersForEmoji.contains(currentUserId)) {
            usersForEmoji.remove(currentUserId)
        } else {
            usersForEmoji.add(currentUserId)
        }

        if (usersForEmoji.isEmpty()) {
            currentMap.remove(emoji)
        } else {
            currentMap[emoji] = usersForEmoji
        }

        val updatedReactionsStr = ChatMessage.formatReactions(currentMap)

        // Optimistic UI update
        _messages?.value = currentList.map {
            if (it.id == messageId) it.copy(reactions = updatedReactionsStr) else it
        }

        // Async sync to Supabase, Room, and RTDB
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateMessageReaction(messageId, updatedReactionsStr)
                val context = com.example.MainApplication.instance
                val dbUrl = com.example.api.FirebaseConfig.getChatDatabaseUrl(context)
                val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                database.getReference("community_chat/messages")
                    .child(messageId.toString())
                    .child("reactions")
                    .setValue(updatedReactionsStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePinMessage(messageId: Long) {
        val currentList = _messages?.value ?: emptyList()
        val targetMsg = currentList.find { it.id == messageId } ?: return
        val newPinnedState = !targetMsg.isPinned

        // Optimistic UI update
        _messages?.value = currentList.map {
            if (it.id == messageId) it.copy(isPinned = newPinnedState) else it
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Persist to Supabase & Room DB
                repository.pinMessage(messageId, newPinnedState)

                // 2. Realtime sync isPinned flag directly to RTDB node & pinned_messages node
                try {
                    val context = com.example.MainApplication.instance
                    // Explicitly restricted to Chat Messages & History URL: https://cloud-storage-f8ab3-default-rtdb.asia-southeast1.firebasedatabase.app/
                    val dbUrl = com.example.api.FirebaseConfig.getChatDatabaseUrl(context)
                    val database = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                    database.getReference("community_chat/messages")
                        .child(messageId.toString())
                        .child("isPinned")
                        .setValue(newPinnedState)

                    val pinnedNodeRef = database.getReference("community_chat/pinned_messages").child(messageId.toString())
                    if (newPinnedState) {
                        val payload = mapOf(
                            "id" to targetMsg.id,
                            "senderId" to targetMsg.senderId,
                            "text" to targetMsg.text,
                            "createdAt" to targetMsg.createdAt,
                            "status" to targetMsg.status,
                            "isPinned" to true,
                            "reactions" to targetMsg.reactions
                        )
                        pinnedNodeRef.setValue(payload)
                    } else {
                        pinnedNodeRef.removeValue()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error updating RTDB isPinned field", e)
                }

                // 3. Post automated system chat message referencing/replying to the target message
                val cleanSnippet = targetMsg.text.take(40).replace("\n", " ")
                val snippetSuffix = if (targetMsg.text.length > 40) "..." else ""
                val autoText = if (newPinnedState) {
                    "📌 Pinned a message: \"$cleanSnippet$snippetSuffix\""
                } else {
                    "📌 Unpinned a message"
                }

                val autoMsg = try {
                    repository.sendMessage(
                        text = autoText,
                        senderId = currentUserId,
                        replyToId = targetMsg.id,
                        replyToText = targetMsg.text,
                        replyToSender = targetMsg.senderId
                    )
                } catch (e: Exception) {
                    ChatMessage(
                        id = System.currentTimeMillis(),
                        senderId = currentUserId,
                        text = autoText,
                        status = "SENT",
                        createdAt = java.time.Instant.now().toString(),
                        timestamp = System.currentTimeMillis(),
                        replyToId = targetMsg.id,
                        replyToText = targetMsg.text,
                        replyToSender = targetMsg.senderId
                    )
                }

                _messages?.value = ((_messages?.value ?: emptyList()) + autoMsg).distinctBy { if (it.id != 0L) it.id else it.hashCode() }
                syncMessageToRtdb(autoMsg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        val currentList = _messages?.value ?: emptyList()
        _messages?.value = currentList.filter { it.id != messageId }
        if (_editingMessage?.value?.id == messageId) {
            _editingMessage?.value = null
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteMessage(messageId)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage?.value = "Failed to delete message (${e.localizedMessage ?: "Offline"})"
            }
        }
    }

    fun onSendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val replyMsg = _replyingToMessage?.value
        _replyingToMessage?.value = null

        val currentOption = _selectedChatOption?.value ?: initialStudyGroups[0]
        val formattedText = when {
            currentOption.type == ChatOptionType.STUDY_GROUP && currentOption.id != "group_main" -> {
                if (!trimmed.contains("[GROUP:")) "$trimmed\n[GROUP:${currentOption.id}]" else trimmed
            }
            currentOption.type == ChatOptionType.DIRECT_MESSAGE -> {
                val targetMember = currentOption.memberUserId ?: ""
                if (!trimmed.contains("[DM:")) "$trimmed\n[DM:$targetMember]" else trimmed
            }
            else -> trimmed
        }

        val targetRecipient = if (currentOption.type == ChatOptionType.DIRECT_MESSAGE) currentOption.memberUserId else replyMsg?.senderId

        viewModelScope.launch(Dispatchers.IO) {
            val localMsg = ChatMessage(
                id = System.currentTimeMillis(),
                senderId = currentUserId,
                text = formattedText,
                status = "SENT",
                createdAt = java.time.Instant.now().toString(),
                timestamp = System.currentTimeMillis(),
                replyToId = replyMsg?.id,
                replyToText = replyMsg?.text,
                replyToSender = targetRecipient
            )

            // 1. Immediately store in local Room DB for instant UI responsiveness
            try {
                repository.insertMessageToCache(localMsg)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Attempt remote send via Supabase / REST
            try {
                val sent = repository.sendMessage(
                    text = formattedText,
                    senderId = currentUserId,
                    replyToId = replyMsg?.id,
                    replyToText = replyMsg?.text,
                    replyToSender = targetRecipient
                )
                syncMessageToRtdb(sent)
            } catch (e: Exception) {
                // If remote fails, mark as PENDING for outbox drainer
                try {
                    repository.insertMessageToCache(localMsg.copy(status = "PENDING"))
                } catch (dbEx: Exception) {
                    dbEx.printStackTrace()
                }
                syncMessageToRtdb(localMsg)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery?.value = query
    }

    fun setSenderFilter(sender: String?) {
        _selectedSenderFilter?.value = sender
    }

    fun setDateRangeFilter(filter: ChatDateRangeFilter) {
        _selectedDateRangeFilter?.value = filter
    }

    fun toggleSearchActive(active: Boolean? = null) {
        val next = active ?: !(_isSearchActive?.value ?: false)
        _isSearchActive?.value = next
        if (!next) {
            clearSearchAndFilters()
        }
    }

    fun clearSearchAndFilters() {
        _searchQuery?.value = ""
        _selectedSenderFilter?.value = null
        _selectedDateRangeFilter?.value = ChatDateRangeFilter.ALL
    }

    private var typingJob: Job? = null
    private var isCurrentlyTyping = false

    fun onUserTypingChanged(isTyping: Boolean) {
        if (isTyping) {
            if (!isCurrentlyTyping) {
                isCurrentlyTyping = true
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updatePresenceStatus(currentUserId, true)
                }
            }
            typingJob?.cancel()
            typingJob = viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(3000)
                isCurrentlyTyping = false
                repository.updatePresenceStatus(currentUserId, false)
            }
        } else {
            if (isCurrentlyTyping) {
                isCurrentlyTyping = false
                typingJob?.cancel()
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updatePresenceStatus(currentUserId, false)
                }
            }
        }
    }

    fun clearError() {
        _errorMessage?.value = null
    }

    fun markAllUnreadIncomingMessagesAsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.markIncomingMessagesAsRead(currentUserId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isSameDay(isoDate: String?, now: ZonedDateTime): Boolean {
        if (isoDate.isNullOrBlank()) return false
        return try {
            val msgTime = ZonedDateTime.ofInstant(Instant.parse(isoDate), ZoneId.systemDefault())
            msgTime.toLocalDate() == now.toLocalDate()
        } catch (e: Exception) {
            false
        }
    }

    private fun isWithinDays(isoDate: String?, now: ZonedDateTime, days: Long): Boolean {
        if (isoDate.isNullOrBlank()) return false
        return try {
            val msgTime = ZonedDateTime.ofInstant(Instant.parse(isoDate), ZoneId.systemDefault())
            val threshold = now.minusDays(days)
            !msgTime.isBefore(threshold)
        } catch (e: Exception) {
            false
        }
    }
}
