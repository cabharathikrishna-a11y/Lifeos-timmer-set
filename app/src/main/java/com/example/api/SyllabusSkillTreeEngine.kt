package com.example.api

import com.example.data.LocalHistoryVault

data class SubTopicMastery(
    val name: String,
    val totalFocusMs: Long,
    val isUnlocked: Boolean // Crossed threshold (e.g. 5 hours = 18,000,000 ms)
)

data class SubjectSkillNode(
    val subject: CaMasterySubject,
    val totalFocusMs: Long,
    val subTopics: List<SubTopicMastery>
)

object SyllabusSkillTreeEngine {

    fun calculateSyllabusMastery(
        records: List<LocalHistoryVault>,
        unlockThresholdMs: Long = 5 * 60 * 60 * 1000L // Default: 5 Hours
    ): List<SubjectSkillNode> {
        val subjectFocusMap = mutableMapOf<CaMasterySubject, Long>()
        val subTopicFocusMap = mutableMapOf<String, Long>()

        // Initialize maps
        for (subject in CaMasterySubject.values()) {
            subjectFocusMap[subject] = 0L
            for (subTopic in subject.subTopics) {
                subTopicFocusMap["${subject.name}_$subTopic"] = 0L
            }
        }

        // Parse records
        for (record in records) {
            val subject = CaMasterySubject.fromTag(record.subject) ?: continue
            val duration = record.total_focus_ms

            // Accumulate to subject
            subjectFocusMap[subject] = (subjectFocusMap[subject] ?: 0L) + duration

            // Accumulate to subtopic
            val taskTitle = record.task_title?.lowercase() ?: ""
            val subjectTag = record.subject.lowercase()

            var matchedSubTopic: String? = null
            for (subTopic in subject.subTopics) {
                val subTopicLower = subTopic.lowercase()
                // Check if task title or tag contains the subtopic name
                if (taskTitle.contains(subTopicLower) || subjectTag.contains(subTopicLower)) {
                    matchedSubTopic = subTopic
                    break
                }
                
                // Extra keyword mappings
                val keywords = getSubTopicKeywords(subTopic)
                if (keywords.any { taskTitle.contains(it) || subjectTag.contains(it) }) {
                    matchedSubTopic = subTopic
                    break
                }
            }

            if (matchedSubTopic != null) {
                val key = "${subject.name}_$matchedSubTopic"
                subTopicFocusMap[key] = (subTopicFocusMap[key] ?: 0L) + duration
            }
        }

        // Build result
        return CaMasterySubject.values().map { subject ->
            val totalSubjectMs = subjectFocusMap[subject] ?: 0L
            val subTopicMasteries = subject.subTopics.map { subTopicName ->
                val focusMs = subTopicFocusMap["${subject.name}_$subTopicName"] ?: 0L
                SubTopicMastery(
                    name = subTopicName,
                    totalFocusMs = focusMs,
                    isUnlocked = focusMs >= unlockThresholdMs
                )
            }
            SubjectSkillNode(
                subject = subject,
                totalFocusMs = totalSubjectMs,
                subTopics = subTopicMasteries
            )
        }
    }

    private fun getSubTopicKeywords(subTopic: String): List<String> {
        return when (subTopic) {
            "Capital Gains" -> listOf("capital", "gain", "cg")
            "GST" -> listOf("gst", "taxation", "indirect tax", "idt")
            "SA 240" -> listOf("sa 240", "sa240", "fraud")
            "Company Audit" -> listOf("company audit", "co audit", "caro")
            "Partnership Accounts" -> listOf("partnership", "partner", "dissolution")
            "Consolidated Financial Statements" -> listOf("consolidated", "consolidation", "cfs")
            "Buyback of Securities" -> listOf("buyback", "buy back", "equity")
            "Amalgamation" -> listOf("amalgamation", "merger", "absorption")
            "Prospectus and Allotment" -> listOf("prospectus", "allotment", "public offer")
            "Share Capital and Debentures" -> listOf("share capital", "debenture", "shares")
            "Management and Administration" -> listOf("management", "administration", "agm", "egm")
            "Foreign Companies" -> listOf("foreign co", "foreign company")
            "Salary Income" -> listOf("salary", "allowance", "perquisite")
            "House Property" -> listOf("house property", "hp", "rental")
            "Material Cost" -> listOf("material", "eoq", "inventory")
            "Labor Cost" -> listOf("labor", "labour", "wages")
            "Overheads" -> listOf("overhead", "absorption cost")
            "Standard Costing" -> listOf("standard cost", "variance")
            "Audit Documentation" -> listOf("documentation", "audit file", "sa 230")
            "Internal Control" -> listOf("internal control", "ics", "test of controls")
            "Cost of Capital" -> listOf("cost of capital", "wacc", "ke", "kd")
            "Leverages" -> listOf("leverage", "ol", "fl", "cl")
            "Strategic Analysis" -> listOf("strategic analysis", "swot", "pestel")
            "Business Level Decisions" -> listOf("business level", "decision", "strategy")
            else -> emptyList()
        }
    }
}
