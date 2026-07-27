package com.example.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfLibraryScreen(
    pdfItems: List<PdfDocumentItem>,
    onOpenPdf: (PdfDocumentItem) -> Unit,
    onPickFile: () -> Unit,
    onCreateSamplePdf: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeletePdf: (String) -> Unit,
    onShowDetails: (PdfDocumentItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // All, Recent, Favorites, Samples
    var isGridView by remember { mutableStateOf(false) }

    val filteredList = remember(pdfItems, searchQuery, selectedFilter) {
        pdfItems.filter { item ->
            val matchesSearch = searchQuery.isBlank() || item.title.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "Recent" -> true
                "Favorites" -> item.isFavorite
                "Samples" -> item.isSample
                else -> true
            }
            matchesSearch && matchesFilter
        }.sortedByDescending { it.lastOpenedTimestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFD32F2F),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Offline PDF Viewer",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${pdfItems.size} Documents Available",
                                fontSize = 11.sp,
                                color = Color(0xFFB0BEC5)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.testTag("toggle_grid_view_button")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = "Toggle View",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onCreateSamplePdf,
                        modifier = Modifier.testTag("create_sample_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Add Sample",
                            tint = Color(0xFFFF5252)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121218))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickFile,
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Black) },
                text = { Text("Open Local PDF", fontWeight = FontWeight.Bold, color = Color.Black) },
                containerColor = Color(0xFFFF5252),
                modifier = Modifier.testTag("open_local_pdf_fab")
            )
        },
        containerColor = Color(0xFF0D0E12)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Real-time Document Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search PDF files by title...", color = Color(0xFF78909C), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF90A4AE)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1C23),
                    unfocusedContainerColor = Color(0xFF16181F),
                    focusedBorderColor = Color(0xFFFF5252),
                    unfocusedBorderColor = Color(0xFF2C2F3A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Favorites", "Samples").forEach { category ->
                    val isSelected = selectedFilter == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = category },
                        label = { Text(category, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF5252),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E202B),
                            labelColor = Color(0xFFCFD8DC)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFF2A2D3B),
                            selectedBorderColor = Color(0xFFFF5252)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredList.isEmpty()) {
                EmptyLibraryState(
                    searchQuery = searchQuery,
                    onPickFile = onPickFile,
                    onCreateSamplePdf = onCreateSamplePdf
                )
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredList, key = { it.id }) { pdfItem ->
                        PdfGridCard(
                            item = pdfItem,
                            onClick = { onOpenPdf(pdfItem) },
                            onToggleFavorite = { onToggleFavorite(pdfItem.id) },
                            onShowDetails = { onShowDetails(pdfItem) },
                            onDelete = { onDeletePdf(pdfItem.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredList, key = { it.id }) { pdfItem ->
                        PdfListCard(
                            item = pdfItem,
                            onClick = { onOpenPdf(pdfItem) },
                            onToggleFavorite = { onToggleFavorite(pdfItem.id) },
                            onShowDetails = { onShowDetails(pdfItem) },
                            onDelete = { onDeletePdf(pdfItem.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfListCard(
    item: PdfDocumentItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd • HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("pdf_item_card_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181A22))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PDF Cover Icon Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2C1013),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.pageCount} Pages",
                        fontSize = 11.sp,
                        color = Color(0xFFFF8A80),
                        fontWeight = FontWeight.Medium
                    )
                    Text(" • ", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        text = item.fileSizeFormatted,
                        fontSize = 11.sp,
                        color = Color(0xFF90A4AE)
                    )
                    Text(" • ", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        text = dateFormat.format(Date(item.lastOpenedTimestamp)),
                        fontSize = 11.sp,
                        color = Color(0xFF78909C)
                    )
                }

                if (item.pageCount > 0 && item.lastPageRead > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val progress = (item.lastPageRead + 1).toFloat() / item.pageCount.toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                        color = Color(0xFFFF5252),
                        trackColor = Color(0xFF2A2D3A)
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (item.isFavorite) Color(0xFFFF5252) else Color.Gray
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.Gray)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF222530))
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Document", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White) },
                        onClick = { showMenu = false; onClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Details & Info", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) },
                        onClick = { showMenu = false; onShowDetails() }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove Document", color = Color(0xFFFF5252)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun PdfGridCard(
    item: PdfDocumentItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("pdf_grid_card_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181A22))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C1013)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(42.dp)
                )

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (item.isFavorite) Color(0xFFFF5252) else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${item.pageCount} Pages • ${item.fileSizeFormatted}",
                fontSize = 11.sp,
                color = Color(0xFF90A4AE)
            )
        }
    }
}

@Composable
fun EmptyLibraryState(
    searchQuery: String,
    onPickFile: () -> Unit,
    onCreateSamplePdf: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = Color(0xFF37474F),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (searchQuery.isNotEmpty()) {
                Text(
                    text = "No PDFs matching \"$searchQuery\"",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Text(
                    text = "Your Local PDF Library is Empty",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Open any PDF file from your device storage or generate sample offline documents to start reading.",
                    fontSize = 13.sp,
                    color = Color(0xFF90A4AE),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPickFile,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open PDF File", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onCreateSamplePdf,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sample PDF")
                    }
                }
            }
        }
    }
}
