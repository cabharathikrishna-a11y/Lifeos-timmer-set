package com.example.api

enum class CaMasterySubject(
    val paperNumber: Int,
    val subjectName: String,
    val subTopics: List<String>
) {
    ADV_ACCOUNTING(
        1, 
        "Paper 1: Advanced Accounting", 
        listOf("Partnership Accounts", "Consolidated Financial Statements", "Buyback of Securities", "Amalgamation")
    ),
    CORP_LAWS(
        2, 
        "Paper 2: Corporate and Other Laws", 
        listOf("Prospectus and Allotment", "Share Capital and Debentures", "Management and Administration", "Foreign Companies")
    ),
    TAXATION(
        3, 
        "Paper 3: Taxation", 
        listOf("Capital Gains", "GST", "Salary Income", "House Property")
    ),
    COST_MGMT(
        4, 
        "Paper 4: Cost and Management Accounting", 
        listOf("Material Cost", "Labor Cost", "Overheads", "Standard Costing")
    ),
    AUDIT_ETHICS(
        5, 
        "Paper 5: Auditing and Ethics", 
        listOf("SA 240", "Company Audit", "Audit Documentation", "Internal Control")
    ),
    FIN_STRAT_MGMT(
        6, 
        "Paper 6: Financial Management and Strategic Management", 
        listOf("Cost of Capital", "Leverages", "Strategic Analysis", "Business Level Decisions")
    );

    companion object {
        fun fromTag(tag: String?): CaMasterySubject? {
            if (tag.isNullOrBlank()) return null
            val normalized = tag.lowercase().trim()
            
            if (normalized.startsWith("paper 1") || normalized.startsWith("paper1") || normalized.contains("accounting")) {
                return ADV_ACCOUNTING
            }
            if (normalized.startsWith("paper 2") || normalized.startsWith("paper2") || normalized.contains("law") || normalized.contains("corp")) {
                return CORP_LAWS
            }
            if (normalized.startsWith("paper 3") || normalized.startsWith("paper3") || normalized.contains("tax") || normalized.contains("gst")) {
                return TAXATION
            }
            if (normalized.startsWith("paper 4") || normalized.startsWith("paper4") || normalized.contains("cost") || normalized.contains("management accounting")) {
                return COST_MGMT
            }
            if (normalized.startsWith("paper 5") || normalized.startsWith("paper5") || normalized.contains("audit") || normalized.contains("ethics")) {
                return AUDIT_ETHICS
            }
            if (normalized.startsWith("paper 6") || normalized.startsWith("paper6") || normalized.contains("finance") || normalized.contains("strategic") || normalized.contains("fm") || normalized.contains("sm")) {
                return FIN_STRAT_MGMT
            }

            // Check direct paper number/name matching as fallback
            for (value in values()) {
                val cleanPaperName = value.subjectName.substringAfter(": ").lowercase().trim()
                if (normalized == "paper ${value.paperNumber}" ||
                    normalized == "paper${value.paperNumber}" ||
                    normalized == value.name.lowercase() ||
                    normalized.contains(cleanPaperName) ||
                    value.subjectName.lowercase().contains(normalized)
                ) {
                    return value
                }
            }
            return null
        }
    }
}

data class SubjectMasteryStats(
    val subjectName: String,
    val totalFocusMs: Long
)
