package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.api.PeerLiveState
import com.example.data.Contact

object MentionParser {

    /**
     * Builds an AnnotatedString from task title or description, turning `@mention` words 
     * into styled pills.
     */
    fun buildMentionAnnotatedString(text: String, highlightColor: Color): AnnotatedString {
        return buildAnnotatedString {
            if (text.isEmpty()) return@buildAnnotatedString
            val words = text.split(" ")
            words.forEachIndexed { index, word ->
                // Clean punctuation from the word to check if it's a mention
                val isMention = word.startsWith("@") && word.length > 1
                if (isMention) {
                    val cleanWord = word.lowercase()
                    pushStyle(
                        SpanStyle(
                            color = highlightColor,
                            fontWeight = FontWeight.Bold,
                            background = highlightColor.copy(alpha = 0.15f)
                        )
                    )
                    append(cleanWord)
                    pop()
                } else {
                    append(word)
                }
                if (index < words.size - 1) {
                    append(" ")
                }
            }
        }
    }

    /**
     * Parse and resolve mention tags into a list of lowercase peer/user identifiers.
     */
    fun getActiveMentionQuery(text: String): String? {
        val lastIndex = text.lastIndexOf('@')
        if (lastIndex == -1) return null
        val sub = text.substring(lastIndex)
        if (sub.contains(' ')) return null
        return sub.removePrefix("@")
    }

    /**
     * Replaces the currently typed @query with the chosen suggestion, appending a space.
     */
    fun replaceActiveQuery(text: String, selection: String): String {
        val lastIndex = text.lastIndexOf('@')
        if (lastIndex == -1) return text + " @" + selection + " "
        return text.substring(0, lastIndex) + "@" + selection + " "
    }

    fun resolveMentions(
        text: String,
        allUsers: Map<String, PeerLiveState>,
        contactFolders: List<String>,
        contacts: List<Contact>,
        myNickname: String = ""
    ): Set<String> {
        val rawTags = Regex("""@\w+""").findAll(text)
            .map { it.value.lowercase().trim().replace("@", "") }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

        val resolved = mutableSetOf<String>()
        val myCleanNick = myNickname.lowercase().trim()

        for (tag in rawTags) {
            if ((tag == "me" || tag == "myself" || tag == myCleanNick) && myCleanNick.isNotBlank()) {
                resolved.add(myCleanNick)
                continue
            }

            // 1. Match against Peer Live State Display Name or userId (email prefix)
            val matchedPeer = allUsers.values.find {
                it.displayName.lowercase().trim() == tag ||
                it.userId.substringBefore("@").replace(".", "_").lowercase().trim() == tag
            }
            if (matchedPeer != null) {
                resolved.add(matchedPeer.userId.substringBefore("@").replace(".", "_").lowercase().trim())
                continue
            }

            // 2. Match against Contact folder / Study Group (with spaces removed)
            val matchedFolder = contactFolders.find {
                it.replace(" ", "").lowercase().trim() == tag
            }
            if (matchedFolder != null) {
                val folderContacts = contacts.filter { it.folder == matchedFolder }
                for (c in folderContacts) {
                    val memberNick = if (c.email.isNotEmpty()) {
                        c.email.substringBefore("@").replace(".", "_").lowercase().trim()
                    } else {
                        c.firstName.lowercase().trim()
                    }
                    if (memberNick.isNotEmpty()) {
                        resolved.add(memberNick)
                    }
                }
                continue
            }

            // 3. Fallback: Check local contacts list by email prefix or first/last names
            val matchedContact = contacts.find {
                it.firstName.lowercase().trim() == tag ||
                it.lastName.lowercase().trim() == tag ||
                (it.email.isNotEmpty() && it.email.substringBefore("@").replace(".", "_").lowercase().trim() == tag)
            }
            if (matchedContact != null) {
                val memberNick = if (matchedContact.email.isNotEmpty()) {
                    matchedContact.email.substringBefore("@").replace(".", "_").lowercase().trim()
                } else {
                    matchedContact.firstName.lowercase().trim()
                }
                if (memberNick.isNotEmpty()) {
                    resolved.add(memberNick)
                }
            } else {
                // If not resolved, just add the raw tag
                resolved.add(tag)
            }
        }

        return resolved
    }
}
