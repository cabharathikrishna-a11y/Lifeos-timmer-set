package com.example.pdf

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PdfStorageRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("pdf_viewer_library_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSavedPdfs(): List<PdfDocumentItem> {
        val json = prefs.getString("saved_pdf_items_v1", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PdfDocumentItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePdfs(items: List<PdfDocumentItem>) {
        val json = gson.toJson(items)
        prefs.edit().putString("saved_pdf_items_v1", json).apply()
    }

    fun addOrUpdatePdf(item: PdfDocumentItem) {
        val current = getSavedPdfs().toMutableList()
        val index = current.indexOfFirst { it.uriString == item.uriString || it.id == item.id }
        if (index >= 0) {
            current[index] = item.copy(lastOpenedTimestamp = System.currentTimeMillis())
        } else {
            current.add(0, item.copy(lastOpenedTimestamp = System.currentTimeMillis()))
        }
        savePdfs(current)
    }

    fun updateProgress(pdfId: String, pageIndex: Int) {
        val current = getSavedPdfs().toMutableList()
        val index = current.indexOfFirst { it.id == pdfId }
        if (index >= 0) {
            current[index] = current[index].copy(
                lastPageRead = pageIndex,
                lastOpenedTimestamp = System.currentTimeMillis()
            )
            savePdfs(current)
        }
    }

    fun toggleFavorite(pdfId: String): Boolean {
        val current = getSavedPdfs().toMutableList()
        val index = current.indexOfFirst { it.id == pdfId }
        if (index >= 0) {
            val newFav = !current[index].isFavorite
            current[index] = current[index].copy(isFavorite = newFav)
            savePdfs(current)
            return newFav
        }
        return false
    }

    fun deletePdf(pdfId: String) {
        val current = getSavedPdfs().toMutableList()
        current.removeAll { it.id == pdfId }
        savePdfs(current)
    }

    // Bookmarks Persistence
    fun getBookmarks(pdfId: String): List<PdfBookmark> {
        val json = prefs.getString("bookmarks_pdf_$pdfId", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PdfBookmark>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addBookmark(bookmark: PdfBookmark) {
        val current = getBookmarks(bookmark.pdfId).toMutableList()
        if (current.none { it.pageIndex == bookmark.pageIndex }) {
            current.add(bookmark)
            prefs.edit().putString("bookmarks_pdf_${bookmark.pdfId}", gson.toJson(current)).apply()
        }
    }

    fun removeBookmark(pdfId: String, pageIndex: Int) {
        val current = getBookmarks(pdfId).toMutableList()
        current.removeAll { it.pageIndex == pageIndex }
        prefs.edit().putString("bookmarks_pdf_$pdfId", gson.toJson(current)).apply()
    }

    // Annotations Persistence
    fun getAnnotations(pdfId: String, pageIndex: Int): List<DrawingStroke> {
        val json = prefs.getString("annotations_${pdfId}_p$pageIndex", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DrawingStroke>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAnnotations(pdfId: String, pageIndex: Int, strokes: List<DrawingStroke>) {
        val json = gson.toJson(strokes)
        prefs.edit().putString("annotations_${pdfId}_p$pageIndex", json).apply()
    }
}
