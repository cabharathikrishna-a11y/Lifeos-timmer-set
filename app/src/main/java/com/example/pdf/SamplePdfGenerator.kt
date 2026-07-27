package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object SamplePdfGenerator {

    fun generateSamplePdfsIfNeeded(context: Context): List<PdfDocumentItem> {
        val createdList = mutableListOf<PdfDocumentItem>()
        val filesDir = context.filesDir
        
        val welcomeFile = File(filesDir, "Welcome_User_Guide.pdf")
        if (!welcomeFile.exists() || welcomeFile.length() == 0L) {
            createWelcomePdf(welcomeFile)
        }
        if (welcomeFile.exists()) {
            createdList.add(
                PdfDocumentItem(
                    id = "sample_welcome_guide",
                    title = "Welcome & User Guide.pdf",
                    uriString = welcomeFile.toURI().toString(),
                    filePath = welcomeFile.absolutePath,
                    pageCount = 3,
                    fileSizeFormatted = formatFileSize(welcomeFile.length()),
                    isSample = true
                )
            )
        }

        val studyGuideFile = File(filesDir, "Sample_Study_Notes.pdf")
        if (!studyGuideFile.exists() || studyGuideFile.length() == 0L) {
            createStudyNotesPdf(studyGuideFile)
        }
        if (studyGuideFile.exists()) {
            createdList.add(
                PdfDocumentItem(
                    id = "sample_study_notes",
                    title = "Sample Study Notes & Reference.pdf",
                    uriString = studyGuideFile.toURI().toString(),
                    filePath = studyGuideFile.absolutePath,
                    pageCount = 2,
                    fileSizeFormatted = formatFileSize(studyGuideFile.length()),
                    isSample = true
                )
            )
        }

        return createdList
    }

    private fun createWelcomePdf(outputFile: File) {
        val document = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points

        // PAGE 1: Welcome
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // Background
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint().apply {
            color = Color.parseColor("#B71C1C")
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#37474F")
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#263238")
            textSize = 12f
            isAntiAlias = true
        }

        val bannerPaint = Paint().apply {
            color = Color.parseColor("#FFEBEE")
            style = Paint.Style.FILL
        }

        // Top Banner
        canvas.drawRoundRect(RectF(30f, 30f, (pageWidth - 30).toFloat(), 130f), 12f, 12f, bannerPaint)
        canvas.drawText("Offline PDF Viewer", 50f, 75f, titlePaint)
        canvas.drawText("High Performance Fast Reader for Android", 50f, 105f, subtitlePaint)

        var yPos = 170f
        canvas.drawText("1. Welcome to your Offline PDF Reader", 40f, yPos, subtitlePaint)
        yPos += 25f
        canvas.drawText("This application allows you to seamlessly open, read, search, and annotate", 40f, yPos, bodyPaint)
        yPos += 18f
        canvas.drawText("PDF documents directly on your device without requiring an internet connection.", 40f, yPos, bodyPaint)

        yPos += 40f
        canvas.drawText("2. Key Features & Capabilities", 40f, yPos, subtitlePaint)
        yPos += 25f

        val features = listOf(
            "• Smooth Zooming & Panning: Pinch or double-tap to zoom up to 500%",
            "• Reading Modes: Switch between Continuous Vertical Scroll & Page-by-Page",
            "• Night & Sepia Reading: High-contrast color inverted mode for dark environments",
            "• Text Search & Navigation: Quick search with result matching and page jumps",
            "• Stylus & Touch Annotations: Draw, highlight, and write notes on any page",
            "• Print & Share: Send PDFs directly to printers or share via Android system sheet"
        )

        for (feat in features) {
            canvas.drawText(feat, 50f, yPos, bodyPaint)
            yPos += 22f
        }

        // Draw decorative box
        val boxPaint = Paint().apply {
            color = Color.parseColor("#ECEFF1")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(40f, yPos + 20f, (pageWidth - 40).toFloat(), yPos + 120f), 8f, 8f, boxPaint)
        
        val boxTextPaint = Paint().apply {
            color = Color.parseColor("#455A64")
            textSize = 11f
            isAntiAlias = true
        }
        canvas.drawText("PRIVACY & OFFLINE GUARANTEE:", 60f, yPos + 50f, Paint(boxTextPaint).apply { isFakeBoldText = true })
        canvas.drawText("All your documents remain strictly on your local device storage. No cloud server uploads", 60f, yPos + 70f, boxTextPaint)
        canvas.drawText("or external network calls are performed while viewing your sensitive documents.", 60f, yPos + 88f, boxTextPaint)

        // Footer
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }
        canvas.drawText("Page 1 of 3", (pageWidth / 2 - 20).toFloat(), (pageHeight - 30).toFloat(), footerPaint)
        document.finishPage(page)

        // PAGE 2: Gesture Controls & Annotations
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        page = document.startPage(pageInfo)
        canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        canvas.drawText("Interactive Tools & Touch Gestures", 40f, 60f, titlePaint)
        yPos = 100f

        canvas.drawText("Annotation & Drawing Mode", 40f, yPos, subtitlePaint)
        yPos += 25f
        canvas.drawText("Tap the Pen Icon in the top toolbar to toggle drawing mode.", 40f, yPos, bodyPaint)
        yPos += 20f
        canvas.drawText("You can select pen colors, highlighter mode, stroke width, and erase marks.", 40f, yPos, bodyPaint)

        yPos += 40f
        canvas.drawText("Supported Touch Shortcuts", 40f, yPos, subtitlePaint)
        yPos += 25f

        val shortcuts = listOf(
            "• Double Tap: Quickly toggle zoom between 100% and 250%",
            "• Pinch Gesture: Scale page fluidly with smooth multi-touch response",
            "• Bottom Scrubber: Drag the slider at the bottom to jump across pages",
            "• Thumbnail Drawer: Tap grid button to view visual thumbnail list of pages"
        )
        for (s in shortcuts) {
            canvas.drawText(s, 50f, yPos, bodyPaint)
            yPos += 24f
        }

        canvas.drawText("Page 2 of 3", (pageWidth / 2 - 20).toFloat(), (pageHeight - 30).toFloat(), footerPaint)
        document.finishPage(page)

        // PAGE 3: Settings & Specs
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
        page = document.startPage(pageInfo)
        canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        canvas.drawText("Technical Details & Performance", 40f, 60f, titlePaint)
        yPos = 100f

        canvas.drawText("PDF Engine Architecture", 40f, yPos, subtitlePaint)
        yPos += 25f
        canvas.drawText("Powered by Android's native PdfRenderer engine, ensuring optimal battery life,", 40f, yPos, bodyPaint)
        yPos += 20f
        canvas.drawText("instant rendering speed, and zero native memory leaks.", 40f, yPos, bodyPaint)

        yPos += 50f
        canvas.drawText("Thank you for using Offline PDF Viewer!", 40f, yPos, titlePaint)

        canvas.drawText("Page 3 of 3", (pageWidth / 2 - 20).toFloat(), (pageHeight - 30).toFloat(), footerPaint)
        document.finishPage(page)

        try {
            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document.close()
        }
    }

    private fun createStudyNotesPdf(outputFile: File) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint().apply {
            color = Color.parseColor("#0D47A1")
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val headingPaint = Paint().apply {
            color = Color.parseColor("#1565C0")
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#212121")
            textSize = 12f
            isAntiAlias = true
        }

        canvas.drawText("Sample Study Notes: Mobile Software Architecture", 40f, 60f, titlePaint)
        var yPos = 100f

        canvas.drawText("1. Clean Architecture Principles", 40f, yPos, headingPaint)
        yPos += 22f
        canvas.drawText("Clean architecture separates code into distinct layers: UI (Presentation), Domain, and Data.", 40f, yPos, bodyPaint)
        yPos += 18f
        canvas.drawText("This decoupling allows high testability, maintainability, and clean state flows.", 40f, yPos, bodyPaint)

        yPos += 40f
        canvas.drawText("2. Reactive UI with Jetpack Compose", 40f, yPos, headingPaint)
        yPos += 22f
        canvas.drawText("Jetpack Compose uses declarative UI functions that automatically recompose when", 40f, yPos, bodyPaint)
        yPos += 18f
        canvas.drawText("underlying StateFlow values change.", 40f, yPos, bodyPaint)

        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }
        canvas.drawText("Page 1 of 2", (pageWidth / 2 - 20).toFloat(), (pageHeight - 30).toFloat(), footerPaint)
        document.finishPage(page)

        // PAGE 2
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        page = document.startPage(pageInfo)
        canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        canvas.drawText("3. Offline Local Persistence", 40f, 60f, headingPaint)
        yPos = 85f
        canvas.drawText("Local databases such as SQLite / Room provide instant access to user data offline.", 40f, yPos, bodyPaint)

        canvas.drawText("Page 2 of 2", (pageWidth / 2 - 20).toFloat(), (pageHeight - 30).toFloat(), footerPaint)
        document.finishPage(page)

        try {
            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document.close()
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.1f MB", mb)
    }
}
