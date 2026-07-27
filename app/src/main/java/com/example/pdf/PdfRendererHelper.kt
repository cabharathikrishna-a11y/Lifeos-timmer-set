package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfRendererHelper(private val context: Context) {

    private var currentPfd: ParcelFileDescriptor? = null
    private var currentRenderer: PdfRenderer? = null
    private var activeUriString: String? = null

    // Cache rendered page bitmaps to ensure ultra-smooth scrolling and zero lag
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxMemoryKb / 6 // Use 1/6th of max memory for bitmap cache
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun openDocument(uriString: String): Int = withContext(Dispatchers.IO) {
        if (activeUriString == uriString && currentRenderer != null) {
            return@withContext currentRenderer?.pageCount ?: 0
        }

        closeDocument()

        try {
            val uri = Uri.parse(uriString)
            val pfd = when {
                uri.scheme == "content" -> {
                    context.contentResolver.openFileDescriptor(uri, "r")
                }
                uri.scheme == "file" || uri.path != null -> {
                    val file = File(uri.path ?: uriString.replace("file://", ""))
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
                else -> {
                    val file = File(uriString)
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            } ?: throw IllegalStateException("Could not open file descriptor for URI: $uriString")

            currentPfd = pfd
            val renderer = PdfRenderer(pfd)
            currentRenderer = renderer
            activeUriString = uriString
            bitmapCache.evictAll()
            return@withContext renderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
            closeDocument()
            throw e
        }
    }

    suspend fun renderPage(
        pageIndex: Int,
        renderWidth: Int = 1080,
        colorFilterMode: ColorFilterMode = ColorFilterMode.NORMAL
    ): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = currentRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        val cacheKey = "${activeUriString}_p${pageIndex}_w${renderWidth}_m${colorFilterMode.name}"
        bitmapCache.get(cacheKey)?.let { cachedBitmap ->
            if (!cachedBitmap.isRecycled) return@withContext cachedBitmap
        }

        try {
            val page = synchronized(this@PdfRendererHelper) {
                renderer.openPage(pageIndex)
            }
            val pageWidth = page.width
            val pageHeight = page.height

            val scale = renderWidth.toFloat() / pageWidth.toFloat()
            val bitmapWidth = renderWidth
            val bitmapHeight = (pageHeight * scale).toInt()

            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            
            // Draw background fill first
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            val matrix = Matrix()
            matrix.postScale(scale, scale)

            synchronized(this@PdfRendererHelper) {
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
            }

            val finalBitmap = applyColorFilterIfNeeded(bitmap, colorFilterMode)
            bitmapCache.put(cacheKey, finalBitmap)
            return@withContext finalBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun applyColorFilterIfNeeded(source: Bitmap, filterMode: ColorFilterMode): Bitmap {
        if (filterMode == ColorFilterMode.NORMAL) return source

        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        when (filterMode) {
            ColorFilterMode.NIGHT_MODE -> {
                // Invert colors matrix for comfortable dark reading
                val colorMatrix = ColorMatrix(
                    floatArrayOf(
                        -1f,  0f,  0f, 0f, 255f,
                         0f, -1f,  0f, 0f, 255f,
                         0f,  0f, -1f, 0f, 255f,
                         0f,  0f,  0f, 1f,   0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            ColorFilterMode.SEPIA -> {
                val colorMatrix = ColorMatrix().apply {
                    set(
                        floatArrayOf(
                            0.393f, 0.769f, 0.189f, 0f, 0f,
                            0.349f, 0.686f, 0.168f, 0f, 0f,
                            0.272f, 0.534f, 0.131f, 0f, 0f,
                            0f,     0f,     0f,     1f, 0f
                        )
                    )
                }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            ColorFilterMode.EYE_CARE -> {
                // Gentle warm tint
                val colorMatrix = ColorMatrix().apply {
                    set(
                        floatArrayOf(
                            1.0f, 0f,   0f,   0f, 10f,
                            0f,   0.95f,0f,   0f, 5f,
                            0f,   0f,   0.8f, 0f, -10f,
                            0f,   0f,   0f,   1f, 0f
                        )
                    )
                }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            else -> {}
        }

        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    @Synchronized
    fun closeDocument() {
        try {
            currentRenderer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentRenderer = null
        }

        try {
            currentPfd?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentPfd = null
        }

        activeUriString = null
    }
}
