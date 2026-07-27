package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.Charcoal
import com.example.ui.theme.MonospaceNumbers
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class CombinedHistoryItem(
    val id: String,
    val timestamp: Long,
    val title: String,
    val type: String,
    val subtitle: String,
    val amount: Double,
    val note: String,
    val isAssetImpact: Boolean,
    val detailString: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinancialLedgerView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val familyMembers by viewModel.familyMembers.collectAsStateWithLifecycle()
    val accounts by viewModel.financialAccounts.collectAsStateWithLifecycle()
    val logs by viewModel.financialLogs.collectAsStateWithLifecycle()
    val txs by viewModel.financeTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.financeCategories.collectAsStateWithLifecycle()

    // Screen State
    var selectedMemberId by remember { mutableStateOf<Int?>(null) } // null means "All Members"
    
    // Account details expansion states
    var expandedCategory by remember { mutableStateOf<String?>(null) } // "LONG_TERM_ASSETS", "CURRENT_ASSETS", etc.

    // Modal forms expansion states
    var showRecordExpense by remember { mutableStateOf(false) }
    var showRecordIncome by remember { mutableStateOf(false) }
    var showRecordTransfer by remember { mutableStateOf(false) }

    // Manual Adjustments Modals
    var activeAdjustmentAccount by remember { mutableStateOf<FinancialAccount?>(null) }
    var activeAdjustmentType by remember { mutableStateOf("") } // "APPRECIATION", "DEPRECIATION", "INTEREST_ACCRUED", "PAID"
    var showAdjustmentDialog by remember { mutableStateOf(false) }

    // Range Query States
    var queryStartYear by remember { mutableIntStateOf(2026) }
    var queryStartMonth by remember { mutableIntStateOf(1) }
    var queryStartDay by remember { mutableIntStateOf(1) }
    var queryEndYear by remember { mutableIntStateOf(2026) }
    var queryEndMonth by remember { mutableIntStateOf(12) }
    var queryEndDay by remember { mutableIntStateOf(31) }
    var showQueryResults by remember { mutableStateOf(false) }
    var queryTypeRequested by remember { mutableStateOf("") } // "INCOME" or "EXPENSE"
    var showFilteredHistoryDialog by remember { mutableStateOf(false) }

    // AI advisor state
    var aiReportText by remember { mutableStateOf("") }
    var isGeneratingAiReport by remember { mutableStateOf(false) }
    var showTransactionHistory by remember { mutableStateOf(false) }

    // Helper: Compute balance for an individual account
    fun getAccountBalance(a: FinancialAccount): Double {
        val initial = a.openingValue
        val adjustments = logs.filter { it.accountId == a.id }.sumOf { l ->
            when (l.logType) {
                "APPRECIATION", "INTEREST_ACCRUED" -> l.amount
                "DEPRECIATION", "PAID" -> -l.amount
                else -> 0.0
            }
        }
        var txAdjust = 0.0
        txs.forEach { t ->
            if (t.fromAccountId == a.id) {
                if (a.categoryType.contains("ASSET")) {
                    txAdjust -= t.amount
                } else {
                    txAdjust += t.amount
                }
            }
            if (t.toAccountId == a.id) {
                if (a.categoryType.contains("ASSET")) {
                    txAdjust += t.amount
                } else {
                    txAdjust -= t.amount
                }
            }
        }
        return initial + adjustments + txAdjust
    }

    // Filter accounts based on selected family member
    val activeAccounts = remember(accounts, selectedMemberId) {
        if (selectedMemberId == null) {
            accounts // Merged/all
        } else {
            accounts.filter { it.memberId == selectedMemberId }
        }
    }

    // Calculations based on the active set of accounts
    val computedLongTermAssets = remember(activeAccounts, logs, txs) {
        activeAccounts.filter { it.categoryType == "LONG_TERM_ASSETS" }.sumOf { getAccountBalance(it) }
    }
    val computedCurrentAssets = remember(activeAccounts, logs, txs) {
        activeAccounts.filter { it.categoryType == "CURRENT_ASSETS" }.sumOf { getAccountBalance(it) }
    }
    val computedCurrentLiabilities = remember(activeAccounts, logs, txs) {
        activeAccounts.filter { it.categoryType == "CURRENT_LIABILITIES" }.sumOf { getAccountBalance(it) }
    }
    val computedLongTermLiabilities = remember(activeAccounts, logs, txs) {
        activeAccounts.filter { it.categoryType == "LONG_TERM_LIABILITIES" }.sumOf { getAccountBalance(it) }
    }

    val totalAssets = computedLongTermAssets + computedCurrentAssets
    val totalLiabilities = computedCurrentLiabilities + computedLongTermLiabilities
    val netWorth = totalAssets - totalLiabilities

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- PART 1: FAMILY MEMBERS TABS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "FAMILY ACCOUNTS radar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All Members" primary trigger
                    val isAllSelected = selectedMemberId == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isAllSelected) WaterBlue else Charcoal)
                            .border(
                                width = 1.dp,
                                color = if (isAllSelected) WaterBlue else Color.Gray.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedMemberId = null
                                expandedCategory = null
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "All",
                                tint = if (isAllSelected) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "All Members",
                                color = if (isAllSelected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Individual members
                    familyMembers.forEach { member ->
                        val isSelected = selectedMemberId == member.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) WaterBlue else Charcoal)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) WaterBlue else Color.Gray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    selectedMemberId = member.id
                                    expandedCategory = null
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = member.name,
                                    tint = if (isSelected) Color.Black else Color.LightGray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = member.name,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    
                    if (familyMembers.isEmpty()) {
                        Text(
                            "💡 Tip: Go to Settings -> Financial Ledger to add Family Members!",
                            color = WaterBlue,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // --- PART 2: NET WORTH DASHBOARD CARD ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val entityLabel = remember(selectedMemberId, familyMembers) {
                        if (selectedMemberId == null) "FAMILY CONSOLIDATED NET WORTH"
                        else "${familyMembers.find { it.id == selectedMemberId }?.name?.uppercase() ?: "MEMBER"}'S NET WORTH"
                    }
                    Text(
                        text = entityLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    
                    Text(
                        text = "₹${String.format("%,.2f", netWorth)}",
                        style = MonospaceNumbers.copy(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold),
                        color = if (netWorth >= 0) WaterBlue else AlertRed,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CONSOLIDATED ASSETS", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                text = "+₹${String.format("%,.2f", totalAssets)}",
                                style = MonospaceNumbers.copy(fontSize = 14.sp),
                                color = SuccessGreen
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CONSOLIDATED LIABILITIES", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                text = "-₹${String.format("%,.2f", totalLiabilities)}",
                                style = MonospaceNumbers.copy(fontSize = 14.sp),
                                color = AlertRed
                            )
                        }
                    }
                }
            }
        }

        // --- PART 3: THE 4 SEPARATE CATEGORY CARDS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ASSET & LIABILITY SEGMENTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                // Category parameters mapping
                val categoriesMap = listOf(
                    Triple("LONG_TERM_ASSETS", "Long Term Assets", computedLongTermAssets),
                    Triple("CURRENT_ASSETS", "Current Assets", computedCurrentAssets),
                    Triple("CURRENT_LIABILITIES", "Current Liability", computedCurrentLiabilities),
                    Triple("LONG_TERM_LIABILITIES", "Long Term Liability", computedLongTermLiabilities)
                )

                categoriesMap.forEach { (typeCode, displayName, totalValue) ->
                    val isExpanded = expandedCategory == typeCode
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header Row clickable
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategory = if (isExpanded) null else typeCode
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand",
                                        tint = WaterBlue
                                    )
                                    Text(
                                        text = displayName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                
                                Text(
                                    text = "₹${String.format("%,.2f", totalValue)}",
                                    style = MonospaceNumbers.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = if (typeCode.contains("ASSET")) SuccessGreen else AlertRed
                                )
                            }

                            // Nested expanded form & account listings
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                                    // Create account nested section
                                    if (selectedMemberId == null) {
                                        Text(
                                            "⚠️ Select a specific family member tab to add accounts to this category.",
                                            fontSize = 11.sp,
                                            color = WaterBlue
                                        )
                                    } else {
                                        var accountName by remember { mutableStateOf("") }
                                        var openingValText by remember { mutableStateOf("") }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextField(
                                                value = accountName,
                                                onValueChange = { accountName = it },
                                                placeholder = { Text("Account Name", fontSize = 11.sp) },
                                                colors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.LightGray,
                                                    focusedContainerColor = SurfaceCard,
                                                    unfocusedContainerColor = SurfaceCard
                                                ),
                                                modifier = Modifier.weight(1.5f),
                                                singleLine = true
                                            )

                                            TextField(
                                                value = openingValText,
                                                onValueChange = { openingValText = it },
                                                placeholder = { Text("Opening Val", fontSize = 11.sp) },
                                                colors = TextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.LightGray,
                                                    focusedContainerColor = SurfaceCard,
                                                    unfocusedContainerColor = SurfaceCard
                                                ),
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )

                                            Button(
                                                onClick = {
                                                    val opVal = openingValText.toDoubleOrNull() ?: 0.0
                                                    if (accountName.isNotBlank() && selectedMemberId != null) {
                                                        viewModel.createFinancialAccount(
                                                            memberId = selectedMemberId!!,
                                                            name = accountName.trim(),
                                                            categoryType = typeCode,
                                                            openingValue = opVal
                                                        )
                                                        accountName = ""
                                                        openingValText = ""
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // List existing accounts
                                    val catAccounts = activeAccounts.filter { it.categoryType == typeCode }
                                    if (catAccounts.isEmpty()) {
                                        Text("No accounts registered under this segment.", fontSize = 12.sp, color = Color.Gray)
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            catAccounts.forEach { account ->
                                                val accountBalance = getAccountBalance(account)
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            val originLabel = remember(selectedMemberId, familyMembers) {
                                                                if (selectedMemberId == null) {
                                                                    val mName = familyMembers.find { it.id == account.memberId }?.name ?: "All"
                                                                    "${account.name} [$mName]"
                                                                } else account.name
                                                            }
                                                            Text(originLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                Text(
                                                                    text = "₹${String.format("%,.2f", accountBalance)}",
                                                                    style = MonospaceNumbers.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                                                    color = if (typeCode.contains("ASSET")) SuccessGreen else AlertRed
                                                                )
                                                                IconButton(
                                                                    onClick = { viewModel.deleteFinancialAccount(account) },
                                                                    modifier = Modifier.size(20.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                                }
                                                            }
                                                        }

                                                        // Modification buttons nested under custom conditions
                                                        if (typeCode == "LONG_TERM_ASSETS") {
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Button(
                                                                    onClick = {
                                                                        activeAdjustmentAccount = account
                                                                        activeAdjustmentType = "APPRECIATION"
                                                                        showAdjustmentDialog = true
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen.copy(alpha = 0.2f), contentColor = SuccessGreen),
                                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Appreciation", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                }

                                                                Button(
                                                                    onClick = {
                                                                        activeAdjustmentAccount = account
                                                                        activeAdjustmentType = "DEPRECIATION"
                                                                        showAdjustmentDialog = true
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.2f), contentColor = AlertRed),
                                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Icon(Icons.Default.TrendingDown, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Depreciation", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }

                                                        if (typeCode == "LONG_TERM_LIABILITIES") {
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Button(
                                                                    onClick = {
                                                                        activeAdjustmentAccount = account
                                                                        activeAdjustmentType = "INTEREST_ACCRUED"
                                                                        showAdjustmentDialog = true
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.2f), contentColor = AlertRed),
                                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Interest Accrued", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                }

                                                                Button(
                                                                    onClick = {
                                                                        activeAdjustmentAccount = account
                                                                        activeAdjustmentType = "PAID"
                                                                        showAdjustmentDialog = true
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen.copy(alpha = 0.2f), contentColor = SuccessGreen),
                                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Paid", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    }
                }
            }
        }

        // --- PART 4: THE 3 CORE FINANCIAL TRANSACTION TRIGGERS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "REGISTER BOOK TRANSACTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showRecordExpense = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.15f), contentColor = AlertRed),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Expense", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showRecordIncome = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen.copy(alpha = 0.15f), contentColor = SuccessGreen),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Income", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showRecordTransfer = true },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f), contentColor = WaterBlue),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Transfer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- PART 4B: TRANSACTION HISTORY TRIGGER ---
        item {
            Button(
                onClick = { showTransactionHistory = true },
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f), contentColor = WaterBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Transaction History", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- PART 5: AI ADVISOR REPORT COMPONENT ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = WaterBlue)
                            Text("Deepa AI Ledger Intelligence", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                    }
                    Text("Unlock predictive financial models, budget safety recommendations, and liquid asset analysis using secure local AI intelligence.", fontSize = 11.sp, color = Color.LightGray)
                    
                    Button(
                        onClick = {
                            viewModel.runAdvancedAIFinancialAudit()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("Run Advanced AI Financial Audit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- PART 6: EXPENSE & INCOME RANGE QUERIES ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "RANGE CASHFLOW ANALYTICS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue
                    )
                    Text("Select a custom date range and query cumulative breakdowns instantly with zero mathematical error.", fontSize = 11.sp, color = Color.Gray)

                    // SIMPLE, ELEGANT INTERACTIVE DATE PICKER SELECTORS FOR START AND END DATE
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Start Date Card
                        Card(
                            onClick = {
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        queryStartYear = year
                                        queryStartMonth = month + 1
                                        queryStartDay = dayOfMonth
                                    },
                                    queryStartYear,
                                    queryStartMonth - 1,
                                    queryStartDay
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("START DATE", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = WaterBlue)
                                    Text(
                                        text = String.format(Locale.US, "%04d-%02d-%02d", queryStartYear, queryStartMonth, queryStartDay),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // End Date Card
                        Card(
                            onClick = {
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        queryEndYear = year
                                        queryEndMonth = month + 1
                                        queryEndDay = dayOfMonth
                                    },
                                    queryEndYear,
                                    queryEndMonth - 1,
                                    queryEndDay
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("END DATE", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = WaterBlue)
                                    Text(
                                        text = String.format(Locale.US, "%04d-%02d-%02d", queryEndYear, queryEndMonth, queryEndDay),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                queryTypeRequested = "EXPENSE"
                                showQueryResults = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Total Expenses", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                queryTypeRequested = "INCOME"
                                showQueryResults = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Total Incomes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            showFilteredHistoryDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("View Range Transactions", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showQueryResults) {
                        // Extract timestamps
                        val sCal = Calendar.getInstance().apply {
                            set(queryStartYear, queryStartMonth - 1, queryStartDay, 0, 0, 0)
                        }
                        val eCal = Calendar.getInstance().apply {
                            set(queryEndYear, queryEndMonth - 1, queryEndDay, 23, 59, 59)
                        }
                        val startTs = sCal.timeInMillis
                        val endTs = eCal.timeInMillis

                        // Filtered transaction list
                        val inRangeTxs = txs.filter { t ->
                            t.timestamp in startTs..endTs && t.type == queryTypeRequested &&
                            (selectedMemberId == null || t.memberId == selectedMemberId)
                        }

                        // Compute category aggregations
                        val categorySums = remember(inRangeTxs) {
                            val map = mutableMapOf<String, Double>()
                            inRangeTxs.forEach { t ->
                                val categoryName = if (t.type == "EXPENSE") t.toCategory else t.fromCategory
                                if (categoryName != null) {
                                    map[categoryName] = (map[categoryName] ?: 0.0) + t.amount
                                }
                            }
                            map.toList().sortedByDescending { it.second }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                                    Text(
                                        text = "${queryTypeRequested} (${dateFmt.format(sCal.time)} - ${dateFmt.format(eCal.time)})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                    IconButton(onClick = { showQueryResults = false }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Close query", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                    }
                                }

                                if (categorySums.isEmpty()) {
                                    Text("No entries registered within this custom range.", fontSize = 12.sp, color = Color.Gray)
                                } else {
                                    val totalSum = categorySums.sumOf { it.second }
                                    Text(
                                        text = "Total $queryTypeRequested sum: ₹${String.format("%,.2f", totalSum)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = if (queryTypeRequested == "INCOME") SuccessGreen else AlertRed
                                    )

                                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                                    categorySums.forEach { (catName, sumAmount) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(catName, color = Color.White, fontSize = 12.sp)
                                            Text(
                                                text = "₹${String.format("%,.2f", sumAmount)}",
                                                style = MonospaceNumbers.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                                color = Color.White
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

        // Space at the bottom
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ====================================================
    // MANUAL PORTFOLIO VALUE ADJUSTMENT DIALOG (Appreciation/Depreciation etc.)
    // ====================================================
    if (showAdjustmentDialog && activeAdjustmentAccount != null) {
        var adjAmountText by remember { mutableStateOf("") }
        val dialogTitle = when (activeAdjustmentType) {
            "APPRECIATION" -> "Log Appreciation (Asset up)"
            "DEPRECIATION" -> "Log Depreciation (Asset down)"
            "INTEREST_ACCRUED" -> "Log Interest Accrued (Liability up)"
            "PAID" -> "Log Cash Paid (Liability down)"
            else -> "Log Adjustment"
        }

        AlertDialog(
            onDismissRequest = { showAdjustmentDialog = false },
            title = { Text(dialogTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Account: ${activeAdjustmentAccount!!.name}",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    TextField(
                        value = adjAmountText,
                        onValueChange = { adjAmountText = it },
                        placeholder = { Text("Enter modification amount (₹)") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = adjAmountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            viewModel.logAssetAdjustment(activeAdjustmentAccount!!.id, activeAdjustmentType, amt)
                        }
                        showAdjustmentDialog = false
                        adjAmountText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Confirm Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustmentDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    // ====================================================
    // RECORD EXPENSE MODAL SUBPANEL
    // ====================================================
    if (showRecordExpense) {
        var expenseMemberId by remember { mutableStateOf<Int?>(selectedMemberId ?: familyMembers.firstOrNull()?.id) }
        var expenseFromAccountId by remember { mutableStateOf<Int?>(null) }
        var expenseCategorySelection by remember { mutableStateOf("") }
        var expenseAmountText by remember { mutableStateOf("") }
        var expenseNoteText by remember { mutableStateOf("") }
        val isSavedState = remember { mutableStateOf(false) }

        val calendarNow = remember { Calendar.getInstance() }
        val currentYearStr = remember(calendarNow) { calendarNow.get(Calendar.YEAR).toString() }
        val currentMonthStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.MONTH) + 1) }
        val currentDayStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.DAY_OF_MONTH)) }
        val currentHourStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.HOUR_OF_DAY)) }
        val currentMinuteStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.MINUTE)) }

        LaunchedEffect(Unit) {
            val draft = viewModel.getTransactionDraft()
            if (draft != null && draft.type == "EXPENSE") {
                if (draft.memberId != -1) expenseMemberId = draft.memberId
                expenseCategorySelection = draft.toCategory
                expenseAmountText = if (draft.amount > 0.0) draft.amount.toString() else ""
                expenseNoteText = draft.note
                if (draft.fromAccountId != -1) expenseFromAccountId = draft.fromAccountId
                viewModel.clearTransactionDraft()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                if (!isSavedState.value && (expenseAmountText.isNotEmpty() || expenseNoteText.isNotEmpty())) {
                    viewModel.saveTransactionDraft(
                        memberId = expenseMemberId ?: -1,
                        type = "EXPENSE",
                        amount = expenseAmountText.toDoubleOrNull() ?: 0.0,
                        note = expenseNoteText,
                        fromCategory = "",
                        toCategory = expenseCategorySelection,
                        fromAccountId = expenseFromAccountId ?: -1,
                        toAccountId = -1
                    )
                }
            }
        }

        // Date selection States initialized to current local time
        var expYear by remember { mutableStateOf(currentYearStr) }
        var expMonth by remember { mutableStateOf(currentMonthStr) }
        var expDay by remember { mutableStateOf(currentDayStr) }
        var expHour by remember { mutableStateOf(currentHourStr) }
        var expMinute by remember { mutableStateOf(currentMinuteStr) }

        val memberAccounts = accounts.filter { it.memberId == expenseMemberId }
        val expenseCats = categories.filter { it.type == "EXPENSE" }

        // Auto-select first account if none is chosen
        LaunchedEffect(expenseMemberId, memberAccounts) {
            if (expenseFromAccountId == null || expenseFromAccountId !in memberAccounts.map { it.id }) {
                expenseFromAccountId = memberAccounts.firstOrNull()?.id
            }
        }

        // Auto-select first category if none is chosen
        LaunchedEffect(expenseCats) {
            if (expenseCategorySelection.isEmpty() || expenseCategorySelection !in expenseCats.map { it.name }) {
                expenseCategorySelection = expenseCats.firstOrNull()?.name ?: ""
            }
        }

        var showUnsavedDialog by remember { mutableStateOf(false) }

        val handleDismissAttempt = {
            if (expenseAmountText.isNotEmpty() || expenseNoteText.isNotEmpty() || expenseCategorySelection.isNotEmpty()) {
                showUnsavedDialog = true
            } else {
                showRecordExpense = false
            }
        }

        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                title = { Text("Unsaved Changes", color = Color.White) },
                text = { Text("You have unsaved changes. Do you want to discard them?", color = Color.LightGray) },
                containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                    }) {
                        Text("Resume Editing", color = WaterBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.clearTransactionDraft()
                        showUnsavedDialog = false
                        showRecordExpense = false
                    }) {
                        Text("Discard", color = Color(0xFFF9325D))
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { handleDismissAttempt() },
            title = { Text("Record Custom Expense Bookflow", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Member dropdown trigger
                    Text("RESPONSIBLE FAMILY MEMBER", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        familyMembers.forEach { fm ->
                            val isChosen = expenseMemberId == fm.id
                            FilterChip(
                                selected = isChosen,
                                onClick = {
                                    expenseMemberId = fm.id
                                    expenseFromAccountId = null
                                },
                                label = { Text(fm.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Account selections
                    Text("SOURCE ACCOUNT", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (memberAccounts.isEmpty()) {
                        Text("⚠️ No accounts created for this member yet.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            memberAccounts.forEach { acc ->
                                val isChosen = expenseFromAccountId == acc.id
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { expenseFromAccountId = acc.id },
                                    label = { Text("${acc.name} (₹${String.format("%,.2f", getAccountBalance(acc))})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Categories dropdown
                    Text("DESTINATION EXPENSE CATEGORY", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (expenseCats.isEmpty()) {
                        Text("No custom Expense Categories. Add some in settings first.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            expenseCats.forEach { ec ->
                                val isChosen = expenseCategorySelection == ec.name
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { expenseCategorySelection = ec.name },
                                    label = { Text(ec.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Amount Text
                    TextField(
                        value = expenseAmountText,
                        onValueChange = { expenseAmountText = it },
                        placeholder = { Text("Expense Amount (₹)") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Note Text
                    TextField(
                        value = expenseNoteText,
                        onValueChange = { expenseNoteText = it },
                        placeholder = { Text("Notes / Tags") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date & Time configuration fields
                    Text("BOOKFLOW TIME (YEAR-MONTH-DAY HOUR:MINUTE)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextField(value = expYear, onValueChange = { expYear = it }, modifier = Modifier.weight(1.5f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = expMonth, onValueChange = { expMonth = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = expDay, onValueChange = { expDay = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = expHour, onValueChange = { expHour = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = expMinute, onValueChange = { expMinute = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = expenseAmountText.toDoubleOrNull() ?: 0.0
                        if (expenseMemberId == null) {
                            Toast.makeText(context, "Please select a family member first.", Toast.LENGTH_LONG).show()
                        } else if (expenseFromAccountId == null) {
                            Toast.makeText(context, "Please select a source account first.", Toast.LENGTH_LONG).show()
                        } else if (expenseCategorySelection.isEmpty()) {
                            Toast.makeText(context, "Please select an expense category first.", Toast.LENGTH_LONG).show()
                        } else if (amt <= 0.0) {
                            Toast.makeText(context, "Please enter an expense amount greater than ₹0.00.", Toast.LENGTH_LONG).show()
                        } else {
                            isSavedState.value = true
                            viewModel.clearTransactionDraft()
                            val calendar = Calendar.getInstance()
                            calendar.set(
                                expYear.toIntOrNull() ?: calendar.get(Calendar.YEAR),
                                (expMonth.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1,
                                expDay.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH),
                                expHour.toIntOrNull() ?: calendar.get(Calendar.HOUR_OF_DAY),
                                expMinute.toIntOrNull() ?: calendar.get(Calendar.MINUTE)
                            )
                            viewModel.recordFinanceTransaction(
                                memberId = expenseMemberId!!,
                                type = "EXPENSE",
                                fromAccountId = expenseFromAccountId,
                                fromCategory = null,
                                toAccountId = null,
                                toCategory = expenseCategorySelection,
                                amount = amt,
                                note = expenseNoteText.trim(),
                                timestamp = calendar.timeInMillis
                            )
                            showRecordExpense = false
                            Toast.makeText(context, "Expense successfully recorded!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Commit Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { handleDismissAttempt() }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    // ====================================================
    // RECORD INCOME MODAL SUBPANEL
    // ====================================================
    if (showRecordIncome) {
        var incomeMemberId by remember { mutableStateOf<Int?>(selectedMemberId ?: familyMembers.firstOrNull()?.id) }
        var incomeCategorySelection by remember { mutableStateOf("") }
        var incomeToAccountId by remember { mutableStateOf<Int?>(null) }
        var incomeAmountText by remember { mutableStateOf("") }
        var incomeNoteText by remember { mutableStateOf("") }

        val calendarNow = remember { Calendar.getInstance() }
        val currentYearStr = remember(calendarNow) { calendarNow.get(Calendar.YEAR).toString() }
        val currentMonthStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.MONTH) + 1) }
        val currentDayStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.DAY_OF_MONTH)) }
        val currentHourStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.HOUR_OF_DAY)) }
        val currentMinuteStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.MINUTE)) }

        // Date Picker states initialized to current local time
        var incYear by remember { mutableStateOf(currentYearStr) }
        var incMonth by remember { mutableStateOf(currentMonthStr) }
        var incDay by remember { mutableStateOf(currentDayStr) }
        var incHour by remember { mutableStateOf(currentHourStr) }
        var incMinute by remember { mutableStateOf(currentMinuteStr) }

        val memberAccounts = accounts.filter { it.memberId == incomeMemberId }
        val incomeCats = categories.filter { it.type == "INCOME" }

        // Auto-select first available account
        LaunchedEffect(incomeMemberId, memberAccounts) {
            if (incomeToAccountId == null || incomeToAccountId !in memberAccounts.map { it.id }) {
                incomeToAccountId = memberAccounts.firstOrNull()?.id
            }
        }

        // Auto-select first available income category
        LaunchedEffect(incomeCats) {
            if (incomeCategorySelection.isEmpty() || incomeCategorySelection !in incomeCats.map { it.name }) {
                incomeCategorySelection = incomeCats.firstOrNull()?.name ?: ""
            }
        }

        var showUnsavedDialog by remember { mutableStateOf(false) }

        val handleDismissAttempt = {
            if (incomeAmountText.isNotEmpty() || incomeNoteText.isNotEmpty() || incomeCategorySelection.isNotEmpty()) {
                showUnsavedDialog = true
            } else {
                showRecordIncome = false
            }
        }

        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                title = { Text("Unsaved Changes", color = Color.White) },
                text = { Text("You have unsaved changes. Do you want to discard them?", color = Color.LightGray) },
                containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                    }) {
                        Text("Resume Editing", color = WaterBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        showRecordIncome = false
                    }) {
                        Text("Discard", color = Color(0xFFF9325D))
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { handleDismissAttempt() },
            title = { Text("Record Custom Income Bookflow", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Member selection
                    Text("RESPONSIBLE FAMILY MEMBER", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        familyMembers.forEach { fm ->
                            val isChosen = incomeMemberId == fm.id
                            FilterChip(
                                selected = isChosen,
                                onClick = {
                                    incomeMemberId = fm.id
                                    incomeToAccountId = null
                                },
                                label = { Text(fm.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Category dropdown
                    Text("SOURCE INCOME CATEGORY", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (incomeCats.isEmpty()) {
                        Text("No custom Income Categories. Add some in settings first.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            incomeCats.forEach { ic ->
                                val isChosen = incomeCategorySelection == ic.name
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { incomeCategorySelection = ic.name },
                                    label = { Text(ic.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Destination account selections
                    Text("DESTINATION ACCOUNT", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (memberAccounts.isEmpty()) {
                        Text("No accounts custom configured.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            memberAccounts.forEach { acc ->
                                val isChosen = incomeToAccountId == acc.id
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { incomeToAccountId = acc.id },
                                    label = { Text("${acc.name} (₹${String.format("%,.2f", getAccountBalance(acc))})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Amount Text
                    TextField(
                        value = incomeAmountText,
                        onValueChange = { incomeAmountText = it },
                        placeholder = { Text("Income Value / Profit (₹)") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Note Text
                    TextField(
                        value = incomeNoteText,
                        onValueChange = { incomeNoteText = it },
                        placeholder = { Text("Notes / Comments") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date Selection Input
                    Text("BOOKFLOW TIME (YEAR-MONTH-DAY HOUR:MINUTE)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextField(value = incYear, onValueChange = { incYear = it }, modifier = Modifier.weight(1.5f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = incMonth, onValueChange = { incMonth = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = incDay, onValueChange = { incDay = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = incHour, onValueChange = { incHour = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = incMinute, onValueChange = { incMinute = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = incomeAmountText.toDoubleOrNull() ?: 0.0
                        if (incomeMemberId == null) {
                            Toast.makeText(context, "Please select a family member first.", Toast.LENGTH_LONG).show()
                        } else if (incomeToAccountId == null) {
                            Toast.makeText(context, "Please select a destination account first.", Toast.LENGTH_LONG).show()
                        } else if (incomeCategorySelection.isEmpty()) {
                            Toast.makeText(context, "Please select an income category first.", Toast.LENGTH_LONG).show()
                        } else if (amt <= 0.0) {
                            Toast.makeText(context, "Please enter an income amount greater than ₹0.00.", Toast.LENGTH_LONG).show()
                        } else {
                            val calendar = Calendar.getInstance()
                            calendar.set(
                                incYear.toIntOrNull() ?: calendar.get(Calendar.YEAR),
                                (incMonth.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1,
                                incDay.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH),
                                incHour.toIntOrNull() ?: calendar.get(Calendar.HOUR_OF_DAY),
                                incMinute.toIntOrNull() ?: calendar.get(Calendar.MINUTE)
                            )
                            viewModel.recordFinanceTransaction(
                                memberId = incomeMemberId!!,
                                type = "INCOME",
                                fromAccountId = null,
                                fromCategory = incomeCategorySelection,
                                toAccountId = incomeToAccountId,
                                toCategory = null,
                                amount = amt,
                                note = incomeNoteText.trim(),
                                timestamp = calendar.timeInMillis
                            )
                            showRecordIncome = false
                            Toast.makeText(context, "Income successfully recorded!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Commit Income")
                }
            },
            dismissButton = {
                TextButton(onClick = { handleDismissAttempt() }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    // ====================================================
    // RECORD TRANSFER MODAL SUBPANEL
    // ====================================================
    if (showRecordTransfer) {
        var transferMemberId by remember { mutableStateOf<Int?>(selectedMemberId ?: familyMembers.firstOrNull()?.id) }
        var transferFromAccountId by remember { mutableStateOf<Int?>(null) }
        var transferToAccountId by remember { mutableStateOf<Int?>(null) }
        var transferAmountText by remember { mutableStateOf("") }
        var transferNoteText by remember { mutableStateOf("") }

        val calendarNow = remember { Calendar.getInstance() }
        val currentYearStr = remember(calendarNow) { calendarNow.get(Calendar.YEAR).toString() }
        val currentMonthStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.MONTH) + 1) }
        val currentDayStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.DAY_OF_MONTH)) }
        val currentHourStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.HOUR_OF_DAY)) }
        val currentMinuteStr = remember(calendarNow) { String.format("%02d", calendarNow.get(Calendar.MINUTE)) }

        // Date selection
        var trsfYear by remember { mutableStateOf(currentYearStr) }
        var trsfMonth by remember { mutableStateOf(currentMonthStr) }
        var trsfDay by remember { mutableStateOf(currentDayStr) }
        var trsfHour by remember { mutableStateOf(currentHourStr) }
        var trsfMinute by remember { mutableStateOf(currentMinuteStr) }

        val memberAccounts = accounts.filter { it.memberId == transferMemberId }

        // Auto-select source and destination accounts
        LaunchedEffect(transferMemberId, memberAccounts) {
            if (transferFromAccountId == null || transferFromAccountId !in memberAccounts.map { it.id }) {
                transferFromAccountId = memberAccounts.firstOrNull()?.id
            }
            if (transferToAccountId == null || transferToAccountId !in memberAccounts.map { it.id }) {
                transferToAccountId = memberAccounts.getOrNull(1)?.id ?: memberAccounts.firstOrNull()?.id
            }
        }

        AlertDialog(
            onDismissRequest = { showRecordTransfer = false },
            title = { Text("Transfer Funds Internally", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Member choice
                    Text("RESPONSIBLE FAMILY MEMBER", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        familyMembers.forEach { fm ->
                            val isChosen = transferMemberId == fm.id
                            FilterChip(
                                selected = isChosen,
                                onClick = {
                                    transferMemberId = fm.id
                                    transferFromAccountId = null
                                    transferToAccountId = null
                                },
                                label = { Text(fm.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Source account choices
                    Text("FROM ACCOUNT", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (memberAccounts.isEmpty()) {
                        Text("No accounts custom configured.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            memberAccounts.forEach { acc ->
                                val isChosen = transferFromAccountId == acc.id
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { transferFromAccountId = acc.id },
                                    label = { Text("${acc.name} (₹${String.format("%,.2f", getAccountBalance(acc))})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Destination account choices
                    Text("TO ACCOUNT", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    if (memberAccounts.isEmpty()) {
                        Text("No accounts custom configured.", color = AlertRed, fontSize = 11.sp)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            memberAccounts.forEach { acc ->
                                val isChosen = transferToAccountId == acc.id
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { transferToAccountId = acc.id },
                                    label = { Text("${acc.name} (₹${String.format("%,.2f", getAccountBalance(acc))})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Amount Text
                    TextField(
                        value = transferAmountText,
                        onValueChange = { transferAmountText = it },
                        placeholder = { Text("Value to Transfer (₹)") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Note text
                    TextField(
                        value = transferNoteText,
                        onValueChange = { transferNoteText = it },
                        placeholder = { Text("Transfer details") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date select input
                    Text("BOOKFLOW TIME (YEAR-MONTH-DAY HOUR:MINUTE)", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextField(value = trsfYear, onValueChange = { trsfYear = it }, modifier = Modifier.weight(1.5f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = trsfMonth, onValueChange = { trsfMonth = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = trsfDay, onValueChange = { trsfDay = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = trsfHour, onValueChange = { trsfHour = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                        TextField(value = trsfMinute, onValueChange = { trsfMinute = it }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceCard, unfocusedContainerColor = SurfaceCard))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = transferAmountText.toDoubleOrNull() ?: 0.0
                        if (transferMemberId == null) {
                            Toast.makeText(context, "Please select a family member first.", Toast.LENGTH_LONG).show()
                        } else if (transferFromAccountId == null) {
                            Toast.makeText(context, "Please select a source account first.", Toast.LENGTH_LONG).show()
                        } else if (transferToAccountId == null) {
                            Toast.makeText(context, "Please select a destination account first.", Toast.LENGTH_LONG).show()
                        } else if (transferFromAccountId == transferToAccountId) {
                            Toast.makeText(context, "Source and destination accounts must be different.", Toast.LENGTH_LONG).show()
                        } else if (amt <= 0.0) {
                            Toast.makeText(context, "Please enter a transfer amount greater than ₹0.00.", Toast.LENGTH_LONG).show()
                        } else {
                            val calendar = Calendar.getInstance()
                            calendar.set(
                                trsfYear.toIntOrNull() ?: calendar.get(Calendar.YEAR),
                                (trsfMonth.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1,
                                trsfDay.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH),
                                trsfHour.toIntOrNull() ?: calendar.get(Calendar.HOUR_OF_DAY),
                                trsfMinute.toIntOrNull() ?: calendar.get(Calendar.MINUTE)
                            )
                            viewModel.recordFinanceTransaction(
                                memberId = transferMemberId!!,
                                type = "TRANSFER",
                                fromAccountId = transferFromAccountId,
                                fromCategory = null,
                                toAccountId = transferToAccountId,
                                toCategory = null,
                                amount = amt,
                                note = transferNoteText.trim(),
                                timestamp = calendar.timeInMillis
                            )
                            showRecordTransfer = false
                            Toast.makeText(context, "Transfer successfully executed!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Execute Transfer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordTransfer = false }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    if (showTransactionHistory) {
        val combinedList = remember(txs, logs, accounts, familyMembers) {
            val list = mutableListOf<CombinedHistoryItem>()

            // 1. Transactions
            txs.forEach { t ->
                val memberName = familyMembers.find { it.id == t.memberId }?.name ?: "Unknown"
                val fromAccName = accounts.find { it.id == t.fromAccountId }?.name ?: t.fromCategory ?: "None"
                val toAccName = accounts.find { it.id == t.toAccountId }?.name ?: t.toCategory ?: "None"

                val (title, colorHex, isImpact) = when (t.type) {
                    "EXPENSE" -> Triple("Expense: $fromAccName ➔ $toAccName", AlertRed, false)
                    "INCOME" -> Triple("Income: $fromAccName ➔ $toAccName", SuccessGreen, true)
                    else -> Triple("Transfer: $fromAccName ➔ $toAccName", WaterBlue, true)
                }

                list.add(
                    CombinedHistoryItem(
                        id = "tx_${t.id}",
                        timestamp = t.timestamp,
                        title = title,
                        type = t.type,
                        subtitle = "Member: $memberName",
                        amount = t.amount,
                        note = t.note,
                        isAssetImpact = isImpact,
                        detailString = t.type
                    )
                )
            }

            // 2. Logs / Adjustments
            logs.forEach { l ->
                val acc = accounts.find { it.id == l.accountId }
                val accName = acc?.name ?: "Unknown Account"
                val memberName = acc?.let { a -> familyMembers.find { it.id == a.memberId }?.name } ?: "Unknown"

                val (title, colorHex, isImpact) = when (l.logType) {
                    "INITIAL" -> Triple("Account Created: $accName", WaterBlue, true)
                    "APPRECIATION" -> Triple("Adjustment Check-In: $accName (Value Appreciated)", SuccessGreen, true)
                    "DEPRECIATION" -> Triple("Adjustment Check-In: $accName (Value Depreciated)", AlertRed, false)
                    "INTEREST_ACCRUED" -> Triple("Adjustment Check-In: $accName (Interest Accrued)", SuccessGreen, true)
                    "PAID" -> Triple("Adjustment Check-In: $accName (Amortization Payment)", AlertRed, false)
                    else -> Triple("Log: $accName (${l.logType})", Color.Gray, true)
                }

                list.add(
                    CombinedHistoryItem(
                        id = "log_${l.id}",
                        timestamp = l.timestamp,
                        title = title,
                        type = l.logType,
                        subtitle = "Member: $memberName",
                        amount = l.amount,
                        note = "Account manual audit & check-in",
                        isAssetImpact = isImpact,
                        detailString = l.logType
                    )
                )
            }

            list.sortByDescending { it.timestamp }
            list
        }

        AlertDialog(
            onDismissRequest = { showTransactionHistory = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "CORE LEDGER HISTORY",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue
                    )
                    IconButton(onClick = { showTransactionHistory = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Unified log of all cash flows, adjustments, audits, and account creation values in reverse chronological order.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    
                    if (combinedList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No transactions logged yet.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(combinedList, key = { it.id }) { item ->
                                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = if (item.isAssetImpact) "+₹${String.format("%.2f", item.amount)}" else "-₹${String.format("%.2f", item.amount)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.isAssetImpact) SuccessGreen else AlertRed
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.subtitle,
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = dateStr,
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        if (item.note.isNotEmpty()) {
                                            Text(
                                                text = "Note: ${item.note}",
                                                fontSize = 10.sp,
                                                color = Color.LightGray.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showFilteredHistoryDialog) {
        val sCal = Calendar.getInstance().apply {
            set(queryStartYear, queryStartMonth - 1, queryStartDay, 0, 0, 0)
        }
        val eCal = Calendar.getInstance().apply {
            set(queryEndYear, queryEndMonth - 1, queryEndDay, 23, 59, 59)
        }
        val startTs = sCal.timeInMillis
        val endTs = eCal.timeInMillis

        val filteredTxs = remember(txs, startTs, endTs, selectedMemberId) {
            txs.filter { t ->
                t.timestamp in startTs..endTs &&
                (selectedMemberId == null || t.memberId == selectedMemberId)
            }.sortedByDescending { it.timestamp }
        }

        AlertDialog(
            onDismissRequest = { showFilteredHistoryDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "RANGE TRANSACTION HISTORY",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterBlue
                    )
                    IconButton(onClick = { showFilteredHistoryDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                    Text(
                        text = "Transactions logged between ${dateFmt.format(sCal.time)} and ${dateFmt.format(eCal.time)}.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    
                    if (filteredTxs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No transactions logged in this range.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredTxs, key = { it.id }) { t ->
                                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(t.timestamp))
                                val memberName = familyMembers.find { it.id == t.memberId }?.name ?: "Unknown"
                                val fromAccName = accounts.find { it.id == t.fromAccountId }?.name ?: t.fromCategory ?: "None"
                                val toAccName = accounts.find { it.id == t.toAccountId }?.name ?: t.toCategory ?: "None"

                                val (title, isAssetImpact) = when (t.type) {
                                    "EXPENSE" -> "Expense: $fromAccName ➔ $toAccName" to false
                                    "INCOME" -> "Income: $fromAccName ➔ $toAccName" to true
                                    else -> "Transfer: $fromAccName ➔ $toAccName" to true
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = if (isAssetImpact) "+₹${String.format("%,.2f", t.amount)}" else "-₹${String.format("%,.2f", t.amount)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAssetImpact) SuccessGreen else AlertRed
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Member: $memberName | Type: ${t.type}",
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = dateStr,
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        if (!t.note.isNullOrBlank()) {
                                            Text(
                                                text = "Note: ${t.note}",
                                                fontSize = 10.sp,
                                                color = Color.LightGray.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
