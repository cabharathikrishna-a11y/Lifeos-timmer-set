package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.theme.WaterBlue
import android.widget.Toast

private val CardColorsList = listOf(
    Color(0xFFFF5252), // Red
    Color(0xFF448AFF), // Blue
    Color(0xFF66BB6A), // Green
    Color(0xFFFFA726), // Orange
    Color(0xFFEC407A), // Pink
    Color(0xFFAB47BC), // Purple
    Color(0xFF26C6DA), // Cyan
    Color(0xFFFFCA28)  // Yellow
)

data class FlexStudioItem(
    val id: Int,
    val title: String,
    val color: Color,
    var basis: FlexBasis = FlexBasis.Auto,
    var grow: Float = 0f,
    var shrink: Float = 1f,
    var alignSelf: FlexAlignSelf = FlexAlignSelf.Auto,
    var order: Int = 0
)

@OptIn(ExperimentalFlexBoxApi::class, ExperimentalGridApi::class)
@Composable
fun FlexGridStudioView(viewModel: AppViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedStudioTab by remember { mutableIntStateOf(0) } // 0: FlexBox, 1: Grid, 2: Presets

    // =========================================================================
    // FLEXBOX STATE
    // =========================================================================
    var flexDirection by remember { mutableStateOf(FlexDirection.Row) }
    var flexWrap by remember { mutableStateOf(FlexWrap.Wrap) }
    var flexJustify by remember { mutableStateOf(FlexJustifyContent.Start) }
    var flexAlignItems by remember { mutableStateOf(FlexAlignItems.Start) }
    var flexAlignContent by remember { mutableStateOf(FlexAlignContent.Start) }
    var flexGapDp by remember { mutableFloatStateOf(8f) }

    val flexItems = remember {
        mutableStateListOf(
            FlexStudioItem(1, "Red Box", CardColorsList[0], grow = 0f, basis = FlexBasis.FixedDp(80.dp)),
            FlexStudioItem(2, "Blue Box", CardColorsList[1], grow = 0f, basis = FlexBasis.FixedDp(80.dp)),
            FlexStudioItem(3, "Green Box", CardColorsList[2], grow = 1f, basis = FlexBasis.Auto),
            FlexStudioItem(4, "Orange Box", CardColorsList[3], grow = 1f, basis = FlexBasis.Auto),
            FlexStudioItem(5, "Pink Box", CardColorsList[4], grow = 1f, basis = FlexBasis.Auto)
        )
    }
    var selectedItemIndex by remember { mutableIntStateOf(0) }

    // =========================================================================
    // GRID STATE
    // =========================================================================
    var gridColCount by remember { mutableIntStateOf(3) }
    var gridRowCount by remember { mutableIntStateOf(2) }
    var gridGapDp by remember { mutableFloatStateOf(8f) }
    var gridFlow by remember { mutableStateOf(GridFlow.Row) }
    var colSizeType by remember { mutableStateOf("Flex (1.fr)") }
    var rowSizeType by remember { mutableStateOf("Fixed (80.dp)") }

    // =========================================================================
    // MEDIA QUERY SIMULATOR STATE
    // =========================================================================
    var simWindowWidthDp by remember { mutableFloatStateOf(412f) }
    var simWindowHeightDp by remember { mutableFloatStateOf(892f) }
    var simPosture by remember { mutableStateOf(UiMediaScope.Posture.Flat) }
    var simPointer by remember { mutableStateOf(UiMediaScope.PointerPrecision.Coarse) }
    var simKeyboard by remember { mutableStateOf(UiMediaScope.KeyboardKind.Virtual) }
    var simHasCamera by remember { mutableStateOf(true) }
    var simHasMic by remember { mutableStateOf(true) }
    var simViewingDist by remember { mutableStateOf(UiMediaScope.ViewingDistance.Near) }

    var copyNotificationToast by remember { mutableStateOf<String?>(null) }

    if (copyNotificationToast != null) {
        LaunchedEffect(copyNotificationToast) {
            Toast.makeText(context, copyNotificationToast, Toast.LENGTH_SHORT).show()
            copyNotificationToast = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F141C), Color(0xFF080B10))
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Studio Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(WaterBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = "Studio Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "LAYOUT STUDIO",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Experimental FlexBox & 2D Grid engine",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            // Mode Selector Switcher
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(3.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf("FlexBox", "Grid 2D", "Presets", "mediaQuery", "Canonical", "Custom Layout", "Desktop & Displays")
                tabs.forEachIndexed { index, title ->
                    val selected = selectedStudioTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) WaterBlue else Color.Transparent)
                            .clickable { selectedStudioTab = index }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (selected) Color.Black else Color.White,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Studio Main Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedStudioTab) {
                0 -> {
                    // =========================================================
                    // FLEXBOX PLAYGROUND
                    // =========================================================
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Live Preview Canvas
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp, max = 320.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161D2A)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A364F))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "LIVE FLEXBOX CONTAINER",
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "${flexItems.size} items | gap: ${flexGapDp.toInt()}dp",
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Render FlexBox layout
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0B0F17))
                                        .padding(8.dp)
                                ) {
                                    FlexBox(
                                        modifier = Modifier.fillMaxSize(),
                                        config = {
                                            direction(flexDirection)
                                            wrap(flexWrap)
                                            justifyContent(flexJustify)
                                            alignItems(flexAlignItems)
                                            alignContent(flexAlignContent)
                                            gap(flexGapDp.dp)
                                        }
                                    ) {
                                        flexItems.forEachIndexed { idx, item ->
                                            val isSelected = idx == selectedItemIndex
                                            Box(
                                                modifier = Modifier
                                                    .flex {
                                                        basis(item.basis)
                                                        grow(item.grow)
                                                        shrink(item.shrink)
                                                        alignSelf(item.alignSelf)
                                                        order(item.order)
                                                    }
                                                    .height(64.dp)
                                                    .widthIn(min = 60.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(item.color.copy(alpha = if (isSelected) 1f else 0.85f))
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) Color.White else item.color,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable { selectedItemIndex = idx }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = item.title,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Text(
                                                        text = "g:${item.grow} | s:${item.shrink}",
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Container Properties Controls
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "CONTAINER CONFIG (config)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Direction
                                PropertySelectorRow(
                                    label = "direction",
                                    options = listOf("Row", "RowReverse", "Column", "ColumnReverse"),
                                    selected = flexDirection.name,
                                    onSelect = { name ->
                                        flexDirection = FlexDirection.valueOf(name)
                                    }
                                )

                                // Wrap
                                PropertySelectorRow(
                                    label = "wrap",
                                    options = listOf("NoWrap", "Wrap", "WrapReverse"),
                                    selected = flexWrap.name,
                                    onSelect = { name ->
                                        flexWrap = FlexWrap.valueOf(name)
                                    }
                                )

                                // JustifyContent
                                PropertySelectorRow(
                                    label = "justifyContent",
                                    options = listOf("Start", "Center", "End", "SpaceBetween", "SpaceAround", "SpaceEvenly"),
                                    selected = flexJustify.name,
                                    onSelect = { name ->
                                        flexJustify = FlexJustifyContent.valueOf(name)
                                    }
                                )

                                // AlignItems
                                PropertySelectorRow(
                                    label = "alignItems",
                                    options = listOf("Start", "Center", "End", "Stretch", "Baseline"),
                                    selected = flexAlignItems.name,
                                    onSelect = { name ->
                                        flexAlignItems = FlexAlignItems.valueOf(name)
                                    }
                                )

                                // Gap slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "gap: ${flexGapDp.toInt()}dp",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Slider(
                                        value = flexGapDp,
                                        onValueChange = { flexGapDp = it },
                                        valueRange = 0f..32f,
                                        steps = 15,
                                        modifier = Modifier.width(180.dp)
                                    )
                                }
                            }
                        }

                        // Item Properties Controls
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ITEM PROPERTIES (Modifier.flex)",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                if (flexItems.size < 10) {
                                                    val newId = flexItems.size + 1
                                                    val color = CardColorsList[(newId - 1) % CardColorsList.size]
                                                    flexItems.add(
                                                        FlexStudioItem(
                                                            id = newId,
                                                            title = "Box $newId",
                                                            color = color,
                                                            grow = 1f,
                                                            basis = FlexBasis.Auto
                                                        )
                                                    )
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                                        ) {
                                            Text("+ Add Item", fontSize = 10.sp, color = Color.Black)
                                        }

                                        if (flexItems.size > 1) {
                                            Button(
                                                onClick = {
                                                    flexItems.removeAt(flexItems.size - 1)
                                                    if (selectedItemIndex >= flexItems.size) {
                                                        selectedItemIndex = flexItems.size - 1
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                                            ) {
                                                Text("- Remove", fontSize = 10.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                // Item Picker Tabs
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    flexItems.forEachIndexed { index, item ->
                                        val isSel = index == selectedItemIndex
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) item.color else Color.White.copy(alpha = 0.08f))
                                                .clickable { selectedItemIndex = index }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = item.title,
                                                color = if (isSel) Color.White else Color.Gray,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                if (selectedItemIndex in flexItems.indices) {
                                    val currentItem = flexItems[selectedItemIndex]

                                    // Grow control
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "grow (${currentItem.grow})",
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(0f, 1f, 2f, 3f).forEach { g ->
                                                val sel = currentItem.grow == g
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(if (sel) WaterBlue else Color.White.copy(alpha = 0.1f))
                                                        .clickable {
                                                            flexItems[selectedItemIndex] = currentItem.copy(grow = g)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = g.toInt().toString(),
                                                        color = if (sel) Color.Black else Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Basis control
                                    PropertySelectorRow(
                                        label = "basis",
                                        options = listOf("Auto", "80dp", "140dp", "30%"),
                                        selected = when (val b = currentItem.basis) {
                                            is FlexBasis.Auto -> "Auto"
                                            is FlexBasis.FixedDp -> "${b.value.value.toInt()}dp"
                                            is FlexBasis.Percentage -> "${(b.value * 100).toInt()}%"
                                        },
                                        onSelect = { option ->
                                            val newBasis = when (option) {
                                                "80dp" -> FlexBasis.FixedDp(80.dp)
                                                "140dp" -> FlexBasis.FixedDp(140.dp)
                                                "30%" -> FlexBasis.Percentage(0.3f)
                                                else -> FlexBasis.Auto
                                            }
                                            flexItems[selectedItemIndex] = currentItem.copy(basis = newBasis)
                                        }
                                    )

                                    // AlignSelf
                                    PropertySelectorRow(
                                        label = "alignSelf",
                                        options = listOf("Auto", "Start", "Center", "End", "Stretch"),
                                        selected = currentItem.alignSelf.name,
                                        onSelect = { name ->
                                            flexItems[selectedItemIndex] = currentItem.copy(alignSelf = FlexAlignSelf.valueOf(name))
                                        }
                                    )
                                }
                            }
                        }

                        // Generated Code Snippet
                        val codeSnippet = remember(flexDirection, flexWrap, flexJustify, flexAlignItems, flexGapDp, flexItems.size) {
                            """
                            FlexBox(
                                config = {
                                    direction(FlexDirection.$flexDirection)
                                    wrap(FlexWrap.$flexWrap)
                                    justifyContent(FlexJustifyContent.$flexJustify)
                                    alignItems(FlexAlignItems.$flexAlignItems)
                                    gap(${flexGapDp.toInt()}.dp)
                                }
                            ) {
                                // ${flexItems.size} items inside
                                Item1(modifier = Modifier.flex { grow(1f) })
                                Item2(modifier = Modifier.flex { basis(100.dp) })
                            }
                            """.trimIndent()
                        }

                        CodeSnippetCard(
                            code = codeSnippet,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(codeSnippet))
                                copyNotificationToast = "FlexBox Kotlin code copied!"
                            }
                        )
                    }
                }

                1 -> {
                    // =========================================================
                    // GRID 2D PLAYGROUND
                    // =========================================================
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Live Grid Canvas
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161D2A)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A364F))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "LIVE 2D GRID CONTAINER",
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "$gridColCount cols x $gridRowCount rows | flow: ${gridFlow.name}",
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0B0F17))
                                        .padding(8.dp)
                                ) {
                                    Grid(
                                        modifier = Modifier.fillMaxSize(),
                                        config = {
                                            repeat(gridColCount) {
                                                when (colSizeType) {
                                                    "Fixed (100.dp)" -> column(100.dp)
                                                    "Percentage (30%)" -> column(0.30f)
                                                    else -> column(1.fr)
                                                }
                                            }
                                            repeat(gridRowCount) {
                                                when (rowSizeType) {
                                                    "Fixed (80.dp)" -> row(80.dp)
                                                    else -> row(1.fr)
                                                }
                                            }
                                            gap(gridGapDp.dp)
                                            flow = gridFlow
                                        }
                                    ) {
                                        val totalCells = gridColCount * gridRowCount
                                        for (i in 1..totalCells) {
                                            val c = CardColorsList[(i - 1) % CardColorsList.size]
                                            Card(
                                                modifier = Modifier.fillMaxSize(),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.85f))
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = "Cell $i",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp
                                                        )
                                                        Text(
                                                            text = "Grid Track",
                                                            color = Color.White.copy(alpha = 0.7f),
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Grid Controls
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "GRID CONFIGURATION (GridConfigurationScope)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Columns count
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Columns Count ($gridColCount)", color = Color.LightGray, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(1, 2, 3, 4, 5).forEach { num ->
                                            val sel = gridColCount == num
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(if (sel) WaterBlue else Color.White.copy(alpha = 0.1f))
                                                    .clickable { gridColCount = num },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(num.toString(), color = if (sel) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Rows count
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Rows Count ($gridRowCount)", color = Color.LightGray, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(1, 2, 3, 4).forEach { num ->
                                            val sel = gridRowCount == num
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(if (sel) WaterBlue else Color.White.copy(alpha = 0.1f))
                                                    .clickable { gridRowCount = num },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(num.toString(), color = if (sel) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Column Sizing Type
                                PropertySelectorRow(
                                    label = "Column Track Size",
                                    options = listOf("Flex (1.fr)", "Fixed (100.dp)", "Percentage (30%)"),
                                    selected = colSizeType,
                                    onSelect = { colSizeType = it }
                                )

                                // Flow direction
                                PropertySelectorRow(
                                    label = "flow",
                                    options = listOf("Row", "Column"),
                                    selected = gridFlow.name,
                                    onSelect = { name -> gridFlow = GridFlow.valueOf(name) }
                                )

                                // Gap
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("gap: ${gridGapDp.toInt()}dp", color = Color.LightGray, fontSize = 11.sp)
                                    Slider(
                                        value = gridGapDp,
                                        onValueChange = { gridGapDp = it },
                                        valueRange = 0f..24f,
                                        steps = 11,
                                        modifier = Modifier.width(180.dp)
                                    )
                                }
                            }
                        }

                        // Generated Grid Code
                        val gridCode = remember(gridColCount, gridRowCount, gridGapDp, colSizeType, gridFlow) {
                            """
                            Grid(
                                config = {
                                    repeat($gridColCount) {
                                        column(${if (colSizeType.contains("Fixed")) "100.dp" else "1.fr"})
                                    }
                                    repeat($gridRowCount) {
                                        row(1.fr)
                                    }
                                    gap(${gridGapDp.toInt()}.dp)
                                    flow = GridFlow.$gridFlow
                                }
                            ) {
                                Card1()
                                Card2()
                                Card3()
                            }
                            """.trimIndent()
                        }

                        CodeSnippetCard(
                            code = gridCode,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(gridCode))
                                copyNotificationToast = "Grid 2D Kotlin code copied!"
                            }
                        )
                    }
                }

                2 -> {
                    // =========================================================
                    // PRESETS SHOWCASE
                    // =========================================================
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "PRACTICAL LAYOUT TEMPLATES",
                            color = WaterBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // Preset 1: Adaptive Tag Cloud
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Dynamic Tag Cloud (FlexBox Wrap)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Items wrap smoothly when container shrinks", color = Color.Gray, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(10.dp))

                                FlexBox(
                                    modifier = Modifier.fillMaxWidth(),
                                    config = {
                                        wrap(FlexWrap.Wrap)
                                        gap(6.dp)
                                    }
                                ) {
                                    listOf("Jetpack Compose", "FlexBox", "2D Grid", "Material 3", "Kotlin Coroutines", "Room DB", "Android 15", "Life OS", "Flow").forEach { tag ->
                                        Box(
                                            modifier = Modifier
                                                .flex { grow(1f) }
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(WaterBlue.copy(alpha = 0.15f))
                                                .border(1.dp, WaterBlue.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(tag, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }

                        // Preset 2: Dashboard Metric Cards
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Dashboard Widget Grid (Grid 2D)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Equal track sizing with fractional Fr unit", color = Color.Gray, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(10.dp))

                                Grid(
                                    modifier = Modifier.fillMaxWidth().height(140.dp),
                                    config = {
                                        repeat(2) { column(1.fr) }
                                        repeat(2) { row(1.fr) }
                                        gap(8.dp)
                                    }
                                ) {
                                    val metrics = listOf("Focus Time" to "4h 20m", "Tasks Done" to "12/15", "Habit Streak" to "14 Days", "Energy" to "92%")
                                    metrics.forEach { (label, value) ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2536)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize().padding(10.dp),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(label, color = Color.Gray, fontSize = 10.sp)
                                                Text(value, color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // =========================================================
                    // ADAPTIVE MEDIA QUERY PLAYGROUND
                    // =========================================================
                    val currentSimScope = remember(
                        simWindowWidthDp, simWindowHeightDp, simPosture, simPointer,
                        simKeyboard, simHasCamera, simHasMic, simViewingDist
                    ) {
                        MutableUiMediaScope(
                            windowWidth = simWindowWidthDp.dp,
                            windowHeight = simWindowHeightDp.dp,
                            windowPosture = simPosture,
                            pointerPrecision = simPointer,
                            keyboardKind = simKeyboard,
                            hasCamera = simHasCamera,
                            hasMicrophone = simHasMic,
                            viewingDistance = simViewingDist
                        )
                    }

                    CompositionLocalProvider(LocalUiMediaScope provides currentSimScope) {
                        MediaQueryPlaygroundContent(
                            simScope = currentSimScope,
                            onUpdateWidth = { simWindowWidthDp = it },
                            onUpdateHeight = { simWindowHeightDp = it },
                            onUpdatePosture = { simPosture = it },
                            onUpdatePointer = { simPointer = it },
                            onUpdateKeyboard = { simKeyboard = it },
                            onUpdateCamera = { simHasCamera = it },
                            onUpdateMic = { simHasMic = it },
                            onUpdateViewingDist = { simViewingDist = it },
                            onCopyCode = { code ->
                                clipboardManager.setText(AnnotatedString(code))
                                copyNotificationToast = "mediaQuery code copied!"
                            }
                        )
                    }
                }

                4 -> {
                    // =========================================================
                    // CANONICAL LAYOUTS PLAYGROUND (List-Detail, Feed, Supporting Pane, Navigation Suite)
                    // =========================================================
                    CanonicalLayoutsPlaygroundContent(
                        onCopyCode = { code ->
                            clipboardManager.setText(AnnotatedString(code))
                            copyNotificationToast = "Canonical layout code copied!"
                        }
                    )
                }

                5 -> {
                    // =========================================================
                    // CUSTOM COMPOSE LAYOUTS PLAYGROUND (Layout composable, layout modifier, 3-step measure pass)
                    // =========================================================
                    CustomLayoutPlaygroundContent(
                        onCopyCode = { code ->
                            clipboardManager.setText(AnnotatedString(code))
                            copyNotificationToast = "Custom layout code copied!"
                        }
                    )
                }

                6 -> {
                    // =========================================================
                    // DESKTOP WINDOWING & CONNECTED DISPLAYS PLAYGROUND
                    // =========================================================
                    DesktopAndDisplaysPlaygroundContent(
                        onCopyCode = { code ->
                            clipboardManager.setText(AnnotatedString(code))
                            copyNotificationToast = "Desktop & Displays code copied!"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaQueryPlaygroundContent(
    simScope: MutableUiMediaScope,
    onUpdateWidth: (Float) -> Unit,
    onUpdateHeight: (Float) -> Unit,
    onUpdatePosture: (UiMediaScope.Posture) -> Unit,
    onUpdatePointer: (UiMediaScope.PointerPrecision) -> Unit,
    onUpdateKeyboard: (UiMediaScope.KeyboardKind) -> Unit,
    onUpdateCamera: (Boolean) -> Unit,
    onUpdateMic: (Boolean) -> Unit,
    onUpdateViewingDist: (UiMediaScope.ViewingDistance) -> Unit,
    onCopyCode: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device Presets Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "DEVICE SIMULATION PRESETS",
                    color = WaterBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "Phone Portrait" to {
                            onUpdateWidth(390f); onUpdateHeight(844f)
                            onUpdatePosture(UiMediaScope.Posture.Flat)
                            onUpdatePointer(UiMediaScope.PointerPrecision.Coarse)
                            onUpdateKeyboard(UiMediaScope.KeyboardKind.Virtual)
                            onUpdateViewingDist(UiMediaScope.ViewingDistance.Near)
                        },
                        "Foldable Tabletop" to {
                            onUpdateWidth(700f); onUpdateHeight(600f)
                            onUpdatePosture(UiMediaScope.Posture.Tabletop)
                            onUpdatePointer(UiMediaScope.PointerPrecision.Coarse)
                            onUpdateKeyboard(UiMediaScope.KeyboardKind.Virtual)
                            onUpdateViewingDist(UiMediaScope.ViewingDistance.Near)
                        },
                        "Tablet Landscape" to {
                            onUpdateWidth(1024f); onUpdateHeight(768f)
                            onUpdatePosture(UiMediaScope.Posture.Flat)
                            onUpdatePointer(UiMediaScope.PointerPrecision.Fine)
                            onUpdateKeyboard(UiMediaScope.KeyboardKind.Physical)
                            onUpdateViewingDist(UiMediaScope.ViewingDistance.Medium)
                        },
                        "Desktop / Far Display" to {
                            onUpdateWidth(1600f); onUpdateHeight(900f)
                            onUpdatePosture(UiMediaScope.Posture.Flat)
                            onUpdatePointer(UiMediaScope.PointerPrecision.Fine)
                            onUpdateKeyboard(UiMediaScope.KeyboardKind.Physical)
                            onUpdateViewingDist(UiMediaScope.ViewingDistance.Far)
                        }
                    )

                    presets.forEach { (name, action) ->
                        Button(
                            onClick = action,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Text(name, fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // Environment & Device Capability Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "DEVICE CAPABILITIES & ENVIRONMENT CONFIG",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // Width & Height Sliders
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "windowWidth: ${simScope.windowWidth.value.toInt()} dp (${WindowSizeClass.getWidthCategory(simScope.windowWidth)})",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                    Slider(
                        value = simScope.windowWidth.value,
                        onValueChange = onUpdateWidth,
                        valueRange = 320f..1600f,
                        steps = 63
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "windowHeight: ${simScope.windowHeight.value.toInt()} dp (${WindowSizeClass.getHeightCategory(simScope.windowHeight)})",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                    Slider(
                        value = simScope.windowHeight.value,
                        onValueChange = onUpdateHeight,
                        valueRange = 300f..1200f,
                        steps = 44
                    )
                }

                // Window Posture
                PropertySelectorRow(
                    label = "windowPosture",
                    options = listOf("Flat", "Tabletop", "Book"),
                    selected = simScope.windowPosture.name,
                    onSelect = { onUpdatePosture(UiMediaScope.Posture.valueOf(it)) }
                )

                // Pointer Precision
                PropertySelectorRow(
                    label = "pointerPrecision",
                    options = listOf("Fine", "Coarse", "Blunt", "None"),
                    selected = simScope.pointerPrecision.name,
                    onSelect = { onUpdatePointer(UiMediaScope.PointerPrecision.valueOf(it)) }
                )

                // Keyboard Kind
                PropertySelectorRow(
                    label = "keyboardKind",
                    options = listOf("Physical", "Virtual", "None"),
                    selected = simScope.keyboardKind.name,
                    onSelect = { onUpdateKeyboard(UiMediaScope.KeyboardKind.valueOf(it)) }
                )

                // Viewing Distance
                PropertySelectorRow(
                    label = "viewingDistance",
                    options = listOf("Near", "Medium", "Far"),
                    selected = simScope.viewingDistance.name,
                    onSelect = { onUpdateViewingDist(UiMediaScope.ViewingDistance.valueOf(it)) }
                )

                // Camera & Mic toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("hasCamera: ", color = Color.LightGray, fontSize = 11.sp)
                        Switch(
                            checked = simScope.hasCamera,
                            onCheckedChange = onUpdateCamera,
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("hasMicrophone: ", color = Color.LightGray, fontSize = 11.sp)
                        Switch(
                            checked = simScope.hasMicrophone,
                            onCheckedChange = onUpdateMic,
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }
        }

        // Live Evaluated Scope Inspector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161D2A)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A364F))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "EVALUATED UiMediaScope VALUES (mediaQuery { ... })",
                    color = WaterBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                val evaluatedPosture = mediaQuery { windowPosture }
                val evaluatedPointer = mediaQuery { pointerPrecision }
                val evaluatedKeyboard = mediaQuery { keyboardKind }
                val evaluatedCam = mediaQuery { hasCamera }
                val evaluatedMic = mediaQuery { hasMicrophone }
                val evaluatedDist = mediaQuery { viewingDistance }

                val narrowerThanMedium by derivedMediaQuery {
                    windowWidth < WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp
                }
                val narrowerThanExpanded by derivedMediaQuery {
                    windowWidth < WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND.dp
                }

                val evalItems = listOf(
                    "windowPosture" to evaluatedPosture.name,
                    "pointerPrecision" to evaluatedPointer.name,
                    "keyboardKind" to evaluatedKeyboard.name,
                    "hasCamera" to evaluatedCam.toString(),
                    "hasMicrophone" to evaluatedMic.toString(),
                    "viewingDistance" to evaluatedDist.name,
                    "narrowerThanMedium (<600dp)" to narrowerThanMedium.toString(),
                    "narrowerThanExpanded (<840dp)" to narrowerThanExpanded.toString()
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    evalItems.forEach { (key, valStr) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(key, color = Color.Gray, fontSize = 11.sp)
                            Text(valStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Live Adaptive UI Preview Components
        Text(
            text = "LIVE ADAPTIVE COMPOSABLE PREVIEWS",
            color = WaterBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        // 1. Adaptive Video Player (Tabletop / Book / Flat)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("1. VideoPlayer Layout (windowPosture)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Adapts automatically when posture is Tabletop, Book, or Flat", color = Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(10.dp))

                val isTabletop = mediaQuery { windowPosture == UiMediaScope.Posture.Tabletop }
                val isBook = mediaQuery { windowPosture == UiMediaScope.Posture.Book }

                when {
                    isTabletop -> {
                        // Tabletop Layout
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = WaterBlue)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tabletop Top Half: Video Screen", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF334155)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.FastRewind, contentDescription = null, tint = Color.White)
                                    Icon(Icons.Default.Pause, contentDescription = null, tint = WaterBlue)
                                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White)
                                    Text("Tabletop Control Deck", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    isBook -> {
                        // Book Layout
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Left Page Video", color = Color.White, fontSize = 11.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF334155)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Right Page Chapters", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                    else -> {
                        // Flat Layout
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Standard Flat Fullscreen Video Player", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // 2. Adaptive Button Size (pointerPrecision) & Viewing Distance Typography
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("2. Pointer Precision & Viewing Distance Adaptation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                val isBluntPointer = mediaQuery { pointerPrecision == UiMediaScope.PointerPrecision.Blunt }
                val currentViewingDist = mediaQuery { viewingDistance }

                val dynamicFontSize = when (currentViewingDist) {
                    UiMediaScope.ViewingDistance.Far -> 20.sp
                    UiMediaScope.ViewingDistance.Medium -> 18.sp
                    UiMediaScope.ViewingDistance.Near -> 15.sp
                }

                Text(
                    text = "Dynamic Font Size (${dynamicFontSize.value.toInt()}sp for $currentViewingDist viewing distance)",
                    color = WaterBlue,
                    fontSize = dynamicFontSize,
                    fontWeight = FontWeight.Bold
                )

                if (isBluntPointer) {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("LARGE TOUCH TARGET BUTTON (Blunt Pointer Detected)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    Button(
                        onClick = {},
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Normal Button (${simScope.pointerPrecision.name} Precision)", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // 3. Smart Hardware Capabilities Row (hasCamera / hasMicrophone)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("3. Smart Media Input Row (hasCamera & hasMicrophone)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Enter prompt...", color = Color.Gray, fontSize = 11.sp)
                    }

                    if (mediaQuery { hasMicrophone }) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(WaterBlue)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (mediaQuery { hasCamera }) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEC407A))
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 4. Adaptive Multi-Pane Layout (windowWidth & WindowSizeClass)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("4. Adaptive Multi-Pane Breakdown (derivedMediaQuery)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Changes structure according to window size classes", color = Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(8.dp))

                val narrowerThanMedium by derivedMediaQuery {
                    windowWidth < WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp
                }
                val narrowerThanExpanded by derivedMediaQuery {
                    windowWidth < WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND.dp
                }

                when {
                    narrowerThanMedium -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SinglePaneLayout (Compact Screen < 600dp)", color = Color.White, fontSize = 11.sp)
                        }
                    }
                    narrowerThanExpanded -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Pane 1 (List)", color = Color.White, fontSize = 11.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(WaterBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Pane 2 (Detail)", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Navigation", color = Color.White, fontSize = 10.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF334155)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("List Pane", color = Color.White, fontSize = 10.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(WaterBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Detail Pane", color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Code Generator Card
        val sampleCode = """
            // Enable integration in Application class
            ComposeUiFlags.isMediaQueryIntegrationEnabled = true

            @Composable
            fun AdaptiveScreen() {
                val isTabletop = mediaQuery { windowPosture == UiMediaScope.Posture.Tabletop }
                val hasMic = mediaQuery { hasMicrophone }
                
                val narrowerThanMedium by derivedMediaQuery {
                    windowWidth < WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp
                }

                if (isTabletop) {
                    TabletopLayout()
                } else if (narrowerThanMedium) {
                    SinglePaneLayout()
                } else {
                    TwoPaneLayout()
                }
            }
        """.trimIndent()

        CodeSnippetCard(
            code = sampleCode,
            onCopy = { onCopyCode(sampleCode) }
        )
    }
}

@Composable
private fun PropertySelectorRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val isSel = selected.equals(option, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) WaterBlue else Color.White.copy(alpha = 0.08f))
                        .clickable { onSelect(option) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = option,
                        color = if (isSel) Color.Black else Color.White,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeSnippetCard(
    code: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090D14)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A2333))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GENERATED KOTLIN CODE",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = WaterBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = code,
                color = Color(0xFF38BDF8),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

// ============================================================================
// CANONICAL LAYOUTS PLAYGROUND COMPOSABLE
// ============================================================================

@Composable
private fun CanonicalLayoutsPlaygroundContent(
    onCopyCode: (String) -> Unit
) {
    var selectedCanonicalTab by remember { mutableIntStateOf(0) } // 0: List-Detail, 1: Feed, 2: Supporting Pane, 3: Nav Suite

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Canonical Switcher Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "MATERIAL 3 CANONICAL LAYOUTS",
                    color = WaterBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Aesthetic, functional layouts proven across phones, tablets, foldables & ChromeOS",
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F19))
                        .padding(3.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val canonicalTabs = listOf("List-Detail", "Feed Grid", "Supporting Pane", "Nav Suite")
                    canonicalTabs.forEachIndexed { idx, title ->
                        val isSel = selectedCanonicalTab == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) WaterBlue else Color.Transparent)
                                .clickable { selectedCanonicalTab = idx }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSel) Color.Black else Color.White,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        when (selectedCanonicalTab) {
            0 -> ListDetailCanonicalSection(onCopyCode = onCopyCode)
            1 -> FeedGridCanonicalSection(onCopyCode = onCopyCode)
            2 -> SupportingPaneCanonicalSection(onCopyCode = onCopyCode)
            3 -> NavSuiteCanonicalSection(onCopyCode = onCopyCode)
        }
    }
}

// ----------------------------------------------------------------------------
// 1. LIST-DETAIL CANONICAL SECTION
// ----------------------------------------------------------------------------
@Composable
private fun ListDetailCanonicalSection(onCopyCode: (String) -> Unit) {
    var isExpandedWidth by remember { mutableStateOf(true) }
    var selectedItemId by remember { mutableIntStateOf(1) }
    var isShowingDetailInCompact by remember { mutableStateOf(false) }

    val sampleMessages = listOf(
        Triple(1, "Design Review Briefing", "Team Standup • 10:30 AM\nHey team, let's review the new M3 adaptive layout specs for tablet and foldable posture."),
        Triple(2, "Compose 1.8 & Adaptive 1.1", "Android Dev • Yesterday\nNavigableListDetailPaneScaffold brings predictive back gestures and shared element transitions."),
        Triple(3, "FlexGrid Engine Update", "System • 2 days ago\nFlexBox wrap logic and 2D Grid span calculations are fully synced.")
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("List-Detail Pattern Simulator", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("ListDetailPaneScaffold / NavigableListDetailPaneScaffold", color = Color.Gray, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Viewport: ", color = Color.Gray, fontSize = 10.sp)
                        Switch(
                            checked = isExpandedWidth,
                            onCheckedChange = {
                                isExpandedWidth = it
                                isShowingDetailInCompact = false
                            },
                            modifier = Modifier.scale(0.8f)
                        )
                        Text(if (isExpandedWidth) "Expanded (≥840dp)" else "Compact (<600dp)", color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Simulator Window Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F19))
                        .padding(8.dp)
                ) {
                    if (isExpandedWidth) {
                        // Expanded Dual-Pane Layout
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // List Pane (35%)
                            Column(
                                modifier = Modifier
                                    .weight(0.35f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF161E2E))
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("List Pane (Select item)", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                sampleMessages.forEach { (id, title, _) ->
                                    val isSelected = selectedItemId == id
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) WaterBlue else Color(0xFF222E42))
                                            .clickable { selectedItemId = id }
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = title,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Detail Pane (65%)
                            Column(
                                modifier = Modifier
                                    .weight(0.65f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A2436))
                                    .padding(10.dp)
                            ) {
                                val item = sampleMessages.first { it.first == selectedItemId }
                                Text("Detail Pane", color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.second, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(item.third, color = Color.LightGray, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }
                    } else {
                        // Compact Single-Pane Layout with Back Handler Simulation
                        if (!isShowingDetailInCompact) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF161E2E))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("List Pane (Compact Mode)", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                sampleMessages.forEach { (id, title, snippet) ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedItemId = id
                                                isShowingDetailInCompact = true
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222E42)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text(snippet.take(40) + "...", color = Color.Gray, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            val item = sampleMessages.first { it.first == selectedItemId }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A2436))
                                    .padding(10.dp)
                            ) {
                                Button(
                                    onClick = { isShowingDetailInCompact = false },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                                ) {
                                    Text("← Back to List", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(item.second, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(item.third, color = Color.LightGray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        val code = """
            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            @Composable
            fun MyListDetailPaneScaffold() {
                val navigator = rememberListDetailPaneScaffoldNavigator<String>()

                NavigableListDetailPaneScaffold(
                    navigator = navigator,
                    listPane = {
                        AnimatedPane {
                            MyListContent(
                                onSelect = { itemId ->
                                    scope.launch {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, itemId)
                                    }
                                }
                            )
                        }
                    },
                    detailPane = {
                        AnimatedPane {
                            val selectedId = navigator.currentDestination?.contentKey
                            MyDetailContent(
                                id = selectedId,
                                onBack = {
                                    scope.launch {
                                        navigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)
                                    }
                                }
                            )
                        }
                    }
                )
            }
        """.trimIndent()

        CodeSnippetCard(code = code, onCopy = { onCopyCode(code) })
    }
}

// ----------------------------------------------------------------------------
// 2. FEED GRID CANONICAL SECTION
// ----------------------------------------------------------------------------
@Composable
private fun FeedGridCanonicalSection(onCopyCode: (String) -> Unit) {
    var minSizeDp by remember { mutableFloatStateOf(120f) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Feed Layout (LazyVerticalGrid + GridCells.Adaptive)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Grid items adapt fluidly across column spans with maxLineSpan header support", color = Color.Gray, fontSize = 10.sp)

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("GridCells.Adaptive(minSize = ${minSizeDp.toInt()}.dp)", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = minSizeDp,
                    onValueChange = { minSizeDp = it },
                    valueRange = 80f..220f,
                    steps = 14
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Simulated Feed Grid Box
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = minSizeDp.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F19))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF0284C7)))),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("  FULL-WIDTH FEATURE BANNER (maxLineSpan)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    items(8) { idx ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .padding(8.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Column {
                                Text("Feed Item #${idx + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Adaptive card content", color = Color.Gray, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        val code = """
            @Composable
            fun MyFeed(items: List<FeedItem>) {
                LazyVerticalGrid(
                    // Automatically fits as many columns as fit with min 180dp each
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HeaderBanner("Featured Stories")
                    }

                    items(items) { item ->
                        FeedCardItem(item)
                    }
                }
            }
        """.trimIndent()

        CodeSnippetCard(code = code, onCopy = { onCopyCode(code) })
    }
}

// ----------------------------------------------------------------------------
// 3. SUPPORTING PANE CANONICAL SECTION
// ----------------------------------------------------------------------------
@Composable
private fun SupportingPaneCanonicalSection(onCopyCode: (String) -> Unit) {
    var isSupportingPaneVisible by remember { mutableStateOf(true) }
    var isExpandedWidth by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Supporting Pane Layout Simulator", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Primary content (70%) + Supplementary tools/comments (30%)", color = Color.Gray, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Viewport: ", color = Color.Gray, fontSize = 10.sp)
                        Switch(
                            checked = isExpandedWidth,
                            onCheckedChange = { isExpandedWidth = it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { isSupportingPaneVisible = !isSupportingPaneVisible },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSupportingPaneVisible) Color(0xFFEC407A) else WaterBlue)
                ) {
                    Text(if (isSupportingPaneVisible) "Dismiss Supporting Pane" else "Show Supporting Pane", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Simulated Supporting Pane Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F19))
                        .padding(8.dp)
                ) {
                    if (isExpandedWidth) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Main Pane (70%)
                            Box(
                                modifier = Modifier
                                    .weight(if (isSupportingPaneVisible) 0.7f else 1.0f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF161E2E))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("Primary Main Pane (~70%)", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Main Document / Video Player / Core Workspace", color = Color.White, fontSize = 11.sp)
                                }
                            }

                            if (isSupportingPaneVisible) {
                                // Supporting Pane (30%)
                                Box(
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF222E42))
                                    .padding(8.dp)
                                ) {
                                    Column {
                                        Text("Supporting Pane (~30%)", color = Color(0xFFEC407A), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Reviewer comments, tool parameters, or related items.", color = Color.LightGray, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Compact Mode
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF161E2E))
                                    .padding(8.dp)
                            ) {
                                Text("Primary Main Content (Compact View)", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            if (isSupportingPaneVisible) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF222E42))
                                        .padding(8.dp)
                                ) {
                                    Text("Supporting Sheet Overlay (Compact)", color = Color(0xFFEC407A), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        val code = """
            @OptIn(ExperimentalMaterial3AdaptiveApi::class)
            @Composable
            fun MySupportingPaneScaffold() {
                val scaffoldNavigator = rememberSupportingPaneScaffoldNavigator()

                NavigableSupportingPaneScaffold(
                    navigator = scaffoldNavigator,
                    mainPane = {
                        AnimatedPane {
                            MainWorkspaceContent(
                                onToggleTools = {
                                    scope.launch {
                                        scaffoldNavigator.navigateTo(SupportingPaneScaffoldRole.Supporting)
                                    }
                                }
                            )
                        }
                    },
                    supportingPane = {
                        AnimatedPane {
                            SupportingToolsContent(
                                onClose = {
                                    scope.launch {
                                        scaffoldNavigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)
                                    }
                                }
                            )
                        }
                    }
                )
            }
        """.trimIndent()

        CodeSnippetCard(code = code, onCopy = { onCopyCode(code) })
    }
}

// ----------------------------------------------------------------------------
// 4. NAVIGATION SUITE CANONICAL SECTION
// ----------------------------------------------------------------------------
@Composable
private fun NavSuiteCanonicalSection(onCopyCode: (String) -> Unit) {
    var simWidthDp by remember { mutableFloatStateOf(412f) }
    var selectedNavIndex by remember { mutableIntStateOf(0) }

    val navItems = listOf("Home", "Feed", "Analytics", "Profile")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("NavigationSuiteScaffold Simulator", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Dynamically switches between NavigationBar (bottom), NavigationRail (left), and PermanentNavigationDrawer", color = Color.Gray, fontSize = 10.sp)

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val navTypeStr = when {
                        simWidthDp < 600f -> "Navigation Bar (Bottom)"
                        simWidthDp < 1200f -> "Navigation Rail (Left)"
                        else -> "Navigation Drawer (Permanent Left)"
                    }
                    Text("Width: ${simWidthDp.toInt()}dp -> $navTypeStr", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = simWidthDp,
                    onValueChange = { simWidthDp = it },
                    valueRange = 360f..1400f,
                    steps = 51
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Simulated Navigation Suite Window
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F19))
                        .padding(6.dp)
                ) {
                    val isBottomBar = simWidthDp < 600f
                    val isRail = simWidthDp in 600f..1199f
                    val isDrawer = simWidthDp >= 1200f

                    if (isBottomBar) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Main Content Area
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF161E2E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Active Destination: ${navItems[selectedNavIndex]}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // Navigation Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B)),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                navItems.forEachIndexed { i, label ->
                                    Text(
                                        text = label,
                                        color = if (selectedNavIndex == i) WaterBlue else Color.Gray,
                                        fontWeight = if (selectedNavIndex == i) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp,
                                        modifier = Modifier.clickable { selectedNavIndex = i }
                                    )
                                }
                            }
                        }
                    } else if (isRail) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Navigation Rail
                            Column(
                                modifier = Modifier
                                    .width(70.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                navItems.forEachIndexed { i, label ->
                                    Text(
                                        text = label.take(4),
                                        color = if (selectedNavIndex == i) WaterBlue else Color.Gray,
                                        fontWeight = if (selectedNavIndex == i) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp,
                                        modifier = Modifier.clickable { selectedNavIndex = i }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // Content
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF161E2E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Active Destination: ${navItems[selectedNavIndex]}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    } else {
                        // Permanent Drawer
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .width(130.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("App Drawer", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                navItems.forEachIndexed { i, label ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (selectedNavIndex == i) WaterBlue else Color.Transparent)
                                            .clickable { selectedNavIndex = i }
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (selectedNavIndex == i) Color.Black else Color.White,
                                            fontWeight = if (selectedNavIndex == i) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF161E2E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Expanded Desktop Workspace: ${navItems[selectedNavIndex]}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        val code = """
            @Composable
            fun MyAdaptiveApp() {
                var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        AppDestinations.entries.forEach { dest ->
                            item(
                                icon = { Icon(dest.icon, contentDescription = null) },
                                label = { Text(dest.title) },
                                selected = dest == currentDestination,
                                onClick = { currentDestination = dest }
                            )
                        }
                    }
                ) {
                    when (currentDestination) {
                        AppDestinations.HOME -> HomeScreen()
                        AppDestinations.FEED -> FeedScreen()
                        AppDestinations.ANALYTICS -> AnalyticsScreen()
                        AppDestinations.PROFILE -> ProfileScreen()
                    }
                }
            }
        """.trimIndent()

        CodeSnippetCard(code = code, onCopy = { onCopyCode(code) })
    }
}

// ============================================================================
// CUSTOM COMPOSE LAYOUTS PLAYGROUND COMPOSABLE
// ============================================================================

@Composable
private fun CustomLayoutPlaygroundContent(
    onCopyCode: (String) -> Unit
) {
    var itemCount by remember { mutableIntStateOf(3) }
    var verticalSpacingDp by remember { mutableFloatStateOf(12f) }
    var baselineOffsetDp by remember { mutableFloatStateOf(24f) }
    var activeStep by remember { mutableIntStateOf(1) } // 1: Measure Children, 2: Decide Size, 3: Place Children

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "CUSTOM COMPOSE LAYOUT ENGINE",
                    color = WaterBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Single-pass 3-step measurement cycle & custom layout modifiers (MeasureScope & PlacementScope)",
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive 3-Step Process Stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val steps = listOf("1. Measure Children", "2. Decide Size", "3. Place Children")
                    steps.forEachIndexed { idx, label ->
                        val stepNum = idx + 1
                        val isSelected = activeStep == stepNum
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) WaterBlue else Color(0xFF0B0F19))
                                .clickable { activeStep = stepNum }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Live Custom Layout Inspector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Interactive Custom Layout Parameters", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Children Count: $itemCount", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Baseline Offset: ${baselineOffsetDp.toInt()}dp", color = Color(0xFFEC407A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Slider(
                        value = itemCount.toFloat(),
                        onValueChange = { itemCount = it.toInt() },
                        valueRange = 1f..6f,
                        steps = 4,
                        modifier = Modifier.weight(1f)
                    )
                    Slider(
                        value = baselineOffsetDp,
                        onValueChange = { baselineOffsetDp = it },
                        valueRange = 8f..48f,
                        steps = 19,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Canvas Container simulating single-pass measurement output
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F19))
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(verticalSpacingDp.dp)
                    ) {
                        repeat(itemCount) { idx ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when (activeStep) {
                                            1 -> Color(0xFF1E293B)
                                            2 -> Color(0xFF0284C7).copy(alpha = 0.8f)
                                            else -> Color(0xFF10B981)
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Node #${idx + 1}: Measurable -> Placeable",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = when (activeStep) {
                                            1 -> "Pass 1: measure(constraints)"
                                            2 -> "Pass 2: layout(w, h)"
                                            else -> "Pass 3: placeRelative(x=0, y=${(idx * (30 + verticalSpacingDp.toInt()))}px)"
                                        },
                                        color = if (activeStep == 3) Color.Black else Color.LightGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Multi-window & Bubbles Guidance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(WaterBlue)
                    )
                    Text("Floating Bubbles & Multi-Window Windowing Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Bubbles offer a floating UI experience anchored on phones, foldables, and tablets. Multi-window compliance ensures custom layouts automatically adjust incoming Constraints without multi-pass measurement violations.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // Production Kotlin Code Snippet Card
        val code = """
            // 1. Custom Layout Modifier (e.g. Baseline to Top Padding)
            fun Modifier.firstBaselineToTop(
                firstBaselineToTop: Dp
            ) = layout { measurable, constraints ->
                // Step 1: Measure child
                val placeable = measurable.measure(constraints)

                // Check baseline presence
                check(placeable[FirstBaseline] != AlignmentLine.Unspecified)
                val firstBaseline = placeable[FirstBaseline]

                // Step 2: Compute dimensions
                val placeableY = firstBaselineToTop.roundToPx() - firstBaseline
                val height = placeable.height + placeableY

                // Step 3: Layout & place child
                layout(placeable.width, height) {
                    placeable.placeRelative(0, placeableY)
                }
            }

            // 2. Custom Layout Composable (Single-Pass Column)
            @Composable
            fun MyBasicColumn(
                modifier: Modifier = Modifier,
                content: @Composable () -> Unit
            ) {
                Layout(
                    modifier = modifier,
                    content = content
                ) { measurables, constraints ->
                    // Step 1: Measure all children
                    val placeables = measurables.map { it.measure(constraints) }

                    // Step 2 & 3: Decide container size & place children
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        var yPosition = 0
                        placeables.forEach { placeable ->
                            placeable.placeRelative(x = 0, y = yPosition)
                            yPosition += placeable.height
                        }
                    }
                }
            }
        """.trimIndent()

        CodeSnippetCard(code = code, onCopy = { onCopyCode(code) })
    }
}

// ============================================================================
// DESKTOP WINDOWING & CONNECTED DISPLAYS PLAYGROUND COMPOSABLE
// ============================================================================

@Composable
private fun DesktopAndDisplaysPlaygroundContent(
    onCopyCode: (String) -> Unit
) {
    var selectedDesktopSubTab by remember { mutableIntStateOf(0) } // 0: Connected Displays, 1: Desktop Windowing, 2: Multi-Instance Drag, 3: ChromeOS Quality

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "DESKTOP WINDOWING & CONNECTED DISPLAYS",
                    color = WaterBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "External monitors, multi-instance drag-and-drop, custom caption bars & ChromeOS desktop guidelines",
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F19))
                        .padding(3.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val subTabs = listOf("Connected Displays", "Desktop Windowing", "Multi-Instance Drag", "ChromeOS & Quality")
                    subTabs.forEachIndexed { idx, title ->
                        val isSel = selectedDesktopSubTab == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) WaterBlue else Color.Transparent)
                                .clickable { selectedDesktopSubTab = idx }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSel) Color.Black else Color.White,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        when (selectedDesktopSubTab) {
            0 -> ConnectedDisplaysSection(onCopyCode = onCopyCode)
            1 -> DesktopWindowingSection(onCopyCode = onCopyCode)
            2 -> MultiInstanceDragSection(onCopyCode = onCopyCode)
            3 -> ChromeOsQualitySection(onCopyCode = onCopyCode)
        }
    }
}

// ----------------------------------------------------------------------------
// 1. CONNECTED DISPLAYS SECTION
// ----------------------------------------------------------------------------
@Composable
private fun ConnectedDisplaysSection(onCopyCode: (String) -> Unit) {
    var isExternalConnected by remember { mutableStateOf(true) }
    var activeDisplayMode by remember { mutableIntStateOf(0) } // 0: Phone + External Desktop, 1: Tablet Extended Workspace

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Connected Display Topology Simulator", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("DisplayManager API & ActivityOptions.launchDisplayId", color = Color.Gray, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("External Display: ", color = Color.Gray, fontSize = 10.sp)
                        Switch(
                            checked = isExternalConnected,
                            onCheckedChange = { isExternalConnected = it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activeDisplayMode == 0,
                        onClick = { activeDisplayMode = 0 },
                        label = { Text("Phone + Independent Desktop", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = WaterBlue, selectedLabelColor = Color.Black)
                    )
                    FilterChip(
                        selected = activeDisplayMode == 1,
                        onClick = { activeDisplayMode = 1 },
                        label = { Text("Tablet Dual Extended Canvas", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = WaterBlue, selectedLabelColor = Color.Black)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Simulated Displays Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F19))
                        .padding(10.dp)
                ) {
                    if (!isExternalConnected) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No External Display Connected\nSingle Built-in Screen (DEFAULT_DISPLAY = 0)", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Primary Device Screen (built-in)
                            Column(
                                modifier = Modifier
                                    .width(100.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF161E2E))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Display ID: 0", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                Text(if (activeDisplayMode == 0) "Phone Screen\n(Mobile Context)" else "Primary Tablet\n(Touch UI)", color = Color.White, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(WaterBlue))
                            }

                            // Secondary External Display
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(10.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Display ID: 2 (External Monitor)", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Text("launchDisplayId = 2", color = WaterBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0F172A))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = if (activeDisplayMode == 0) "Blank Desktop Session Started\nIndependent Windowing Environment" else "Continuous Extended Desktop Session\nWindows & Cursor Move Freely Across Screens",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Density & Resolution metrics query via LocalConfiguration.current & activity Context", color = Color.LightGray, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val code = """
            // Querying External Displays with DisplayManager
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = displayManager.displays

            // Filter out default primary screen (ID 0)
            val externalDisplay = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

            // Launch Activity targeted at connected external display
            if (externalDisplay != null) {
                val intent = Intent(this, SecondaryWorkspaceActivity::class.java)
                val options = ActivityOptions.makeBasic().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        launchDisplayId = externalDisplay.displayId
                    }
                }
                startActivity(intent, options.toBundle())
            }
        """.trimIndent()

        CodeSnippetCard(code = code, onCopy = { onCopyCode(code) })
    }
}

// ----------------------------------------------------------------------------
// 2. DESKTOP WINDOWING SECTION
// ----------------------------------------------------------------------------
@Composable
private fun DesktopWindowingSection(onCopyCode: (String) -> Unit) {
    var isCaptionTransparent by remember { mutableStateOf(true) }
    var captionTitle by remember { mutableStateOf("FlexGrid Studio - Custom Header Bar") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Desktop Windowing & Custom Header Bar (Caption Insets)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND & WindowInsets.captionBar", color = Color.Gray, fontSize = 10.sp)

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transparent Caption Bar:", color = Color.White, fontSize = 11.sp)
                    Switch(
                        checked = isCaptionTransparent,
                        onCheckedChange = { isCaptionTransparent = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Simulated Desktop Window
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Custom Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(if (isCaptionTransparent) Color(0xFF0284C7).copy(alpha = 0.3f) else Color.DarkGray)
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                Text(captionTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Text("[ System Controls: _ □ X ]", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // Window Workspace Content
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Window Engagement Mode: PRECISE_POINTER", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("The app window is freely resizable in Desktop Windowing mode, even if locked to portrait. Use WindowInsets.captionBar to measure system controls safely.", color = Color.LightGray, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        val code = """
            // 1. Set transparent caption bar in Activity
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND,
                WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND
            )

            // 2. Custom Compose Caption Bar with Insets
            @Composable
            fun CustomCaptionHeader() {
                if (WindowInsets.isCaptionBarVisible) {
                    Row(
                        modifier = Modifier
                            .windowInsetsTopHeight(WindowInsets.captionBar)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("My Custom App Title", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        """.trimIndent()

        CodeSnippetCard(code = code, onCopy = { onCopyCode(code) })
    }
}

// ----------------------------------------------------------------------------
// 3. MULTI-INSTANCE & DRAG-AND-DROP SECTION
// ----------------------------------------------------------------------------
@Composable
private fun MultiInstanceDragSection(onCopyCode: (String) -> Unit) {
    var droppedItemsCount by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Multi-Instance Drag & Drop Simulator (Android 15+)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("DRAG_FLAG_GLOBAL_SAME_APPLICATION & DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG", color = Color.Gray, fontSize = 10.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Drag Source Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.2f))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DRAG SOURCE", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Drag Tab / Document out of Window to spawn new instance or drop on target", color = Color.LightGray, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { droppedItemsCount++ },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                            ) {
                                Text("Simulate Drop", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Drag Target Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.2f))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DROP TARGET", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Received Drops: $droppedItemsCount items", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("dragAndDropTarget + requestDragAndDropPermissions", color = Color.Gray, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }

        val code = """
            // 1. Drag Source enabling Multi-Instance spawn
            Modifier.dragAndDropSource { _ ->
                val intent = Intent.makeMainActivity(activity.componentName).apply {
                    putExtra("EXTRA_DOC_ID", "doc_101")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                }
                val pendingIntent = PendingIntent.getActivity(activity, 0, intent, PendingIntent.FLAG_IMMUTABLE)

                val clipData = ClipData(
                    "Doc doc_101",
                    arrayOf(ClipDescription.MIMETYPE_TEXT_INTENT),
                    ClipData.Item.Builder().setIntentSender(pendingIntent.intentSender).build()
                )

                DragAndDropTransferData(
                    clipData = clipData,
                    flags = View.DRAG_FLAG_GLOBAL_SAME_APPLICATION or
                            View.DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG
                )
            }

            // 2. Drag Target receiving dropped payload
            Modifier.dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.toAndroidDragEvent().clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                },
                target = object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        requestDragAndDropPermissions(activity, event.toAndroidDragEvent())
                        val clipData = event.toAndroidDragEvent().clipData
                        val text = clipData?.getItemAt(0)?.text
                        // Process cross-window payload...
                        return text != null
                    }
                }
            )
        """.trimIndent()

        CodeSnippetCard(code = code, onCopy = { onCopyCode(code) })
    }
}

// ----------------------------------------------------------------------------
// 4. CHROMEOS & DESKTOP QUALITY CHECKLIST SECTION
// ----------------------------------------------------------------------------
@Composable
private fun ChromeOsQualitySection(onCopyCode: (String) -> Unit) {
    val checklist = listOf(
        "T-Scrollbar_Display: App displays visible scrollbars during mouse / trackpad scroll pass",
        "T-Hover_Parity: UI elements display fly-out tooltips & preview cards on cursor hover",
        "T-Keyboard_Navigation: Full Tab / Arrow key navigation with high-contrast focus rings",
        "T-Keyboard_Parity: Standard Ctrl+C, Ctrl+V, Ctrl+Z and Ctrl+F shortcuts supported",
        "T-Input_Combinations: Ctrl+Click & Shift+Click multi-selection for range items",
        "T-Custom_Cursors: I-beam for text fields, resize handles for borders, pointer for links",
        "T-Multi-Instance: App opens multiple windows for side-by-side document comparison"
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222E42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("ChromeOS & Desktop Quality Guidelines Checklist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Ensure mouse, keyboard, and external peripheral readiness across DeX & ChromeOS", color = Color.Gray, fontSize = 10.sp)

                Spacer(modifier = Modifier.height(10.dp))

                checklist.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(item, color = Color.LightGray, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }

        val code = """
            // Manifest declaration for Desktop Multi-Instance UI support
            <application>
                <property
                    android:name="android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI"
                    android:value="true" />
            </application>

            // Window Engagement Listener for Precise Pointer (Mouse / Trackpad)
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    windowInfoTracker.windowEngagementInfo(this@MyActivity)
                        .collect { info ->
                            if (info.hasEngagementMode(WindowEngagementInfo.EngagementMode.PRECISE_POINTER)) {
                                showDesktopOptimizedUI()
                            } else {
                                showTouchOptimizedUI()
                            }
                        }
                }
            }
        """.trimIndent()

        CodeSnippetCard(code = code, onCopy = { onCopyCode(code) })
    }
}

