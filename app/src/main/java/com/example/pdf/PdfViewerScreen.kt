package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfItem: PdfDocumentItem,
    repository: PdfStorageRepository,
    rendererHelper: PdfRendererHelper,
    onBackToLibrary: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pageCount by remember { mutableStateOf(pdfItem.pageCount) }
    var currentPageIndex by remember { mutableStateOf(pdfItem.lastPageRead.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Reading & Visual Modes
    var colorFilterMode by remember { mutableStateOf(ColorFilterMode.NORMAL) }
    var readingMode by remember { mutableStateOf(ReadingMode.PAGE_BY_PAGE) }

    // Zoom & Pan State
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Search Mode
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatchCount by remember { mutableStateOf(0) }

    // Drawing / Annotation Mode
    var isAnnotationActive by remember { mutableStateOf(false) }
    var selectedPenColor by remember { mutableStateOf(Color(0xFFE53935)) } // Red default
    var selectedStrokeWidth by remember { mutableStateOf(4f) }
    var isHighlighterMode by remember { mutableStateOf(false) }
    var drawingStrokes by remember { mutableStateOf(repository.getAnnotations(pdfItem.id, currentPageIndex)) }
    var currentPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Bookmarks & Sheets
    var bookmarks by remember { mutableStateOf(repository.getBookmarks(pdfItem.id)) }
    val isCurrentPageBookmarked = remember(bookmarks, currentPageIndex) {
        bookmarks.any { it.pageIndex == currentPageIndex }
    }
    var showThumbnailSheet by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    // Auto-hide Controls State
    var areControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var wasControlsVisibleOnTouchDown by remember { mutableStateOf(true) }

    fun resetControlsTimer() {
        areControlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    // Auto hide controls after 5 seconds if screen is not touched
    LaunchedEffect(areControlsVisible, lastInteractionTime) {
        if (areControlsVisible) {
            kotlinx.coroutines.delay(5000L)
            areControlsVisible = false
        }
    }

    // Initialize & Load PDF Document
    LaunchedEffect(pdfItem.uriString) {
        isLoadingPage = true
        errorMessage = null
        try {
            val count = rendererHelper.openDocument(pdfItem.uriString)
            pageCount = count
            if (count > 0) {
                if (currentPageIndex >= count) currentPageIndex = 0
            } else {
                errorMessage = "This PDF document appears to be empty."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Failed to load PDF: ${e.localizedMessage}"
        } finally {
            isLoadingPage = false
        }
    }

    // Render Page whenever page index or color filter changes
    LaunchedEffect(currentPageIndex, pageCount, colorFilterMode) {
        if (pageCount > 0) {
            isLoadingPage = true
            val bitmap = rendererHelper.renderPage(currentPageIndex, 1080, colorFilterMode)
            currentBitmap = bitmap
            isLoadingPage = false
            repository.updateProgress(pdfItem.id, currentPageIndex)
            drawingStrokes = repository.getAnnotations(pdfItem.id, currentPageIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val isTouchDown = event.changes.any { it.pressed && !it.previousPressed }
                        val isPressed = event.changes.any { it.pressed }

                        if (isTouchDown) {
                            wasControlsVisibleOnTouchDown = areControlsVisible
                            areControlsVisible = true
                            lastInteractionTime = System.currentTimeMillis()
                        } else if (isPressed && areControlsVisible) {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    }
                }
            }
    ) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = areControlsVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = pdfItem.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (pageCount > 0) "Page ${currentPageIndex + 1} of $pageCount" else "Loading...",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF8A80)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBackToLibrary,
                                modifier = Modifier.testTag("pdf_back_button")
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            // Search Button
                            IconButton(onClick = {
                                resetControlsTimer()
                                isSearchActive = !isSearchActive
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Text",
                                    tint = if (isSearchActive) Color(0xFFFF5252) else Color.White
                                )
                            }

                            // Night/Dark Mode Toggle Button
                            IconButton(onClick = {
                                resetControlsTimer()
                                colorFilterMode = when (colorFilterMode) {
                                    ColorFilterMode.NORMAL -> ColorFilterMode.NIGHT_MODE
                                    ColorFilterMode.NIGHT_MODE -> ColorFilterMode.SEPIA
                                    ColorFilterMode.SEPIA -> ColorFilterMode.EYE_CARE
                                    ColorFilterMode.EYE_CARE -> ColorFilterMode.NORMAL
                                }
                            }) {
                                Icon(
                                    imageVector = when (colorFilterMode) {
                                        ColorFilterMode.NIGHT_MODE -> Icons.Default.DarkMode
                                        ColorFilterMode.SEPIA -> Icons.Default.WbSunny
                                        ColorFilterMode.EYE_CARE -> Icons.Default.Visibility
                                        else -> Icons.Default.LightMode
                                    },
                                    contentDescription = "Color Filter",
                                    tint = if (colorFilterMode != ColorFilterMode.NORMAL) Color(0xFFFF5252) else Color.White
                                )
                            }

                            // Annotation / Drawing Mode Toggle
                            IconButton(onClick = {
                                resetControlsTimer()
                                isAnnotationActive = !isAnnotationActive
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Annotate",
                                    tint = if (isAnnotationActive) Color(0xFFFF5252) else Color.White
                                )
                            }

                            // Bookmark Button
                            IconButton(onClick = {
                                resetControlsTimer()
                                if (isCurrentPageBookmarked) {
                                    repository.removeBookmark(pdfItem.id, currentPageIndex)
                                } else {
                                    repository.addBookmark(PdfBookmark(pdfId = pdfItem.id, pageIndex = currentPageIndex))
                                }
                                bookmarks = repository.getBookmarks(pdfItem.id)
                            }) {
                                Icon(
                                    imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (isCurrentPageBookmarked) Color(0xFFFF5252) else Color.White
                                )
                            }

                            // Thumbnail Sheet
                            IconButton(onClick = {
                                resetControlsTimer()
                                showThumbnailSheet = true
                            }) {
                                Icon(Icons.Default.GridView, contentDescription = "Thumbnails", tint = Color.White)
                            }

                            // Print Document Button
                            IconButton(onClick = {
                                resetControlsTimer()
                                try {
                                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                    val adapter = PdfPrintAdapter(pdfItem)
                                    printManager.print("PDF_Print_${pdfItem.title}", adapter, PrintAttributes.Builder().build())
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }) {
                                Icon(Icons.Default.Print, contentDescription = "Print", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121218))
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = areControlsVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Surface(
                        color = Color(0xFF161820),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (isAnnotationActive) {
                                // Drawing Bar Tools
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Drawing Tools:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)

                                    // Color Swatches
                                    listOf(
                                        Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFFFFD600),
                                        Color(0xFF43A047), Color(0xFF000000)
                                    ).forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    width = if (selectedPenColor == color && !isHighlighterMode) 2.dp else 0.dp,
                                                    color = Color.White,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    resetControlsTimer()
                                                    selectedPenColor = color
                                                    isHighlighterMode = false
                                                }
                                        )
                                    }

                                    // Highlighter Button
                                    FilterChip(
                                        selected = isHighlighterMode,
                                        onClick = {
                                            resetControlsTimer()
                                            isHighlighterMode = !isHighlighterMode
                                        },
                                        label = { Text("Highlighter", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFFFD600),
                                            selectedLabelColor = Color.Black
                                        )
                                    )

                                    // Clear Drawings
                                    TextButton(onClick = {
                                        resetControlsTimer()
                                        drawingStrokes = emptyList()
                                        repository.saveAnnotations(pdfItem.id, currentPageIndex, emptyList())
                                    }) {
                                        Text("Clear", color = Color(0xFFFF5252), fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Main Navigation Scrubber Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        resetControlsTimer()
                                        if (currentPageIndex > 0) currentPageIndex--
                                    },
                                    enabled = currentPageIndex > 0
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page", tint = Color.White)
                                }

                                if (pageCount > 1) {
                                    Slider(
                                        value = currentPageIndex.toFloat(),
                                        onValueChange = {
                                            resetControlsTimer()
                                            currentPageIndex = it.toInt()
                                        },
                                        valueRange = 0f..(pageCount - 1).toFloat(),
                                        steps = (pageCount - 2).coerceAtLeast(0),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("page_scrubber_slider"),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFFFF5252),
                                            activeTrackColor = Color(0xFFFF5252),
                                            inactiveTrackColor = Color(0xFF2C2F3A)
                                        )
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                IconButton(
                                    onClick = {
                                        resetControlsTimer()
                                        if (currentPageIndex < pageCount - 1) currentPageIndex++
                                    },
                                    enabled = currentPageIndex < pageCount - 1
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Page", tint = Color.White)
                                }

                                TextButton(onClick = {
                                    resetControlsTimer()
                                    showGoToPageDialog = true
                                }) {
                                    Text("${currentPageIndex + 1}/$pageCount", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF0A0B0E)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingPage) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFFF5252))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Rendering PDF page...", color = Color.LightGray, fontSize = 13.sp)
                    }
                } else if (errorMessage != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMessage ?: "Unknown error", color = Color.White, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackToLibrary, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))) {
                            Text("Back to Library", color = Color.Black)
                        }
                    }
                } else if (currentBitmap != null) {
                    val bitmap = currentBitmap!!
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (wasControlsVisibleOnTouchDown) {
                                            areControlsVisible = false
                                        } else {
                                            resetControlsTimer()
                                        }
                                    },
                                    onDoubleTap = {
                                        scale = if (scale > 1.5f) 1f else 2.5f
                                        offsetX = 0f
                                        offsetY = 0f
                                        resetControlsTimer()
                                    }
                                )
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPageIndex + 1}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clip(RoundedCornerShape(4.dp))
                        )

                        // Annotation Drawing Canvas Overlay
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isAnnotationActive, selectedPenColor, isHighlighterMode) {
                                    if (!isAnnotationActive) return@pointerInput
                                    detectTapGestures(
                                        onPress = { offset ->
                                            resetControlsTimer()
                                            currentPathPoints = listOf(offset)
                                            awaitRelease()
                                            if (currentPathPoints.isNotEmpty()) {
                                                val stroke = DrawingStroke(
                                                    points = currentPathPoints.map { DrawingPathPoint(it.x, it.y) },
                                                    colorHex = if (isHighlighterMode) 0x88FFD600 else selectedPenColor.value.toLong(),
                                                    strokeWidth = if (isHighlighterMode) 18f else selectedStrokeWidth,
                                                    isHighlighter = isHighlighterMode
                                                )
                                                val updated = drawingStrokes + stroke
                                                drawingStrokes = updated
                                                repository.saveAnnotations(pdfItem.id, currentPageIndex, updated)
                                                currentPathPoints = emptyList()
                                            }
                                        }
                                    )
                                }
                        ) {
                            // Render saved strokes
                            drawingStrokes.forEach { stroke ->
                                if (stroke.points.size > 1) {
                                    val path = Path()
                                    path.moveTo(stroke.points[0].x, stroke.points[0].y)
                                    for (i in 1 until stroke.points.size) {
                                        path.lineTo(stroke.points[i].x, stroke.points[i].y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(stroke.colorHex.toULong()),
                                        style = Stroke(
                                            width = stroke.strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Minimal floating page badge when controls are auto-hidden
                AnimatedVisibility(
                    visible = !areControlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { resetControlsTimer() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Page ${currentPageIndex + 1}/$pageCount • Touch screen to show controls",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Go To Page Dialog
    if (showGoToPageDialog) {
        var pageInput by remember { mutableStateOf((currentPageIndex + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showGoToPageDialog = false },
            title = { Text("Go to Page", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it },
                    label = { Text("Page Number (1 - $pageCount)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5252)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pageInput.toIntOrNull()
                        if (target != null && target in 1..pageCount) {
                            currentPageIndex = target - 1
                        }
                        showGoToPageDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Jump", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoToPageDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E202A)
        )
    }

    // Thumbnail Bottom Sheet
    if (showThumbnailSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThumbnailSheet = false },
            containerColor = Color(0xFF14161F)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Page Thumbnails",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(pageCount) { idx ->
                        val isSelected = idx == currentPageIndex
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFFFF5252) else Color(0xFF222532),
                            modifier = Modifier
                                .height(90.dp)
                                .clickable {
                                    currentPageIndex = idx
                                    showThumbnailSheet = false
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Page\n${idx + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Print Document Adapter for offline Android PrintManager
class PdfPrintAdapter(private val item: PdfDocumentItem) : android.print.PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: android.os.CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = android.print.PrintDocumentInfo.Builder(item.title)
            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(item.pageCount)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out android.print.PageRange>?,
        destination: android.os.ParcelFileDescriptor?,
        cancellationSignal: android.os.CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        // Simple file copy write stream
        try {
            val file = java.io.File(item.filePath ?: "")
            if (file.exists() && destination != null) {
                file.inputStream().use { input ->
                    java.io.FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            } else {
                callback?.onWriteFailed("File not found")
            }
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}
