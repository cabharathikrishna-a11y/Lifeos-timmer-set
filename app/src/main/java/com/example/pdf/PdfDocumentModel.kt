package com.example.pdf

import android.net.Uri

data class PdfDocumentItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val uriString: String,
    val filePath: String? = null,
    val pageCount: Int = 0,
    val fileSizeFormatted: String = "0 KB",
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val lastPageRead: Int = 0,
    val isFavorite: Boolean = false,
    val isSample: Boolean = false
)

data class PdfBookmark(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pdfId: String,
    val pageIndex: Int,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class ReadingMode {
    CONTINUOUS_VERTICAL,
    PAGE_BY_PAGE
}

enum class ColorFilterMode {
    NORMAL,
    NIGHT_MODE,
    SEPIA,
    EYE_CARE
}

data class DrawingPathPoint(
    val x: Float,
    val y: Float
)

data class DrawingStroke(
    val points: List<DrawingPathPoint>,
    val colorHex: Long,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false
)
