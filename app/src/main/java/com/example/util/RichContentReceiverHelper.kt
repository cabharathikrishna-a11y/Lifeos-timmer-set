package com.example.util

import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat

object RichContentReceiverHelper {

    val SUPPORTED_MIME_TYPES = arrayOf(
        "image/*",
        "video/*",
        "audio/*",
        "application/pdf",
        "text/*"
    )

    interface OnRichContentCallback {
        fun onContentReceived(uris: List<Uri>, text: String?, source: Int)
    }

    class AppRichContentListener(
        private val callback: OnRichContentCallback
    ) : OnReceiveContentListener {

        override fun onReceiveContent(view: View, payload: ContentInfoCompat): ContentInfoCompat? {
            // Partition payload into items with URIs (rich content) and without URIs (text/other)
            val split = payload.partition { item -> item.uri != null }
            val uriContent = split.first
            val remaining = split.second

            if (uriContent != null) {
                val clipData = uriContent.clip
                val uris = mutableListOf<Uri>()
                var extractedText: String? = null

                for (i in 0 until clipData.itemCount) {
                    val item = clipData.getItemAt(i)
                    if (item.uri != null) {
                        uris.add(item.uri)
                        Log.d("RichContentReceiver", "Received rich content URI: ${item.uri} via source: ${payload.source}")
                    }
                    if (item.text != null && extractedText == null) {
                        extractedText = item.text.toString()
                    }
                }

                if (uris.isNotEmpty()) {
                    callback.onContentReceived(uris, extractedText, payload.source)
                }
            }

            // Return remaining data to preserve default platform handling for text and non-URI content
            return remaining
        }
    }

    fun attachToView(
        view: View,
        mimeTypes: Array<String> = SUPPORTED_MIME_TYPES,
        callback: OnRichContentCallback
    ) {
        val validMimeTypes = mimeTypes
            .filter { type ->
                !type.startsWith("*") && type != "*/*" && type.contains("/") && !type.substringBefore("/").contains("*")
            }
            .toTypedArray()
        val finalTypes = if (validMimeTypes.isNotEmpty()) validMimeTypes else arrayOf("image/*", "video/*", "text/*")
        try {
            val listener = AppRichContentListener(callback)
            ViewCompat.setOnReceiveContentListener(view, finalTypes, listener)
        } catch (e: Exception) {
            Log.e("RichContentReceiver", "Failed to setOnReceiveContentListener", e)
        }
    }
}

@Composable
fun RichContentInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Type a message, paste image, or drop content...",
    maxLines: Int = 4,
    textColor: Color = Color.White,
    backgroundColor: Color = Color(0xFF16161F),
    borderColor: Color = Color(0xFF38BDF8).copy(alpha = 0.3f),
    onSend: (() -> Unit)? = null,
    onRichContentReceived: (uris: List<Uri>, text: String?, source: Int) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                EditText(ctx).apply {
                    setText(value)
                    setHint(hint)
                    setHintTextColor(Color.Gray.toArgb())
                    setTextColor(textColor.toArgb())
                    textSize = 14f
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    this.maxLines = maxLines

                    if (onSend != null) {
                        imeOptions = EditorInfo.IME_ACTION_SEND
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_SEND) {
                                onSend()
                                true
                            } else false
                        }
                    }

                    // Attach the Unified OnReceiveContentListener (API 31 / AndroidX Core)
                    RichContentReceiverHelper.attachToView(
                        view = this,
                        mimeTypes = RichContentReceiverHelper.SUPPORTED_MIME_TYPES,
                        callback = object : RichContentReceiverHelper.OnRichContentCallback {
                            override fun onContentReceived(uris: List<Uri>, text: String?, source: Int) {
                                onRichContentReceived(uris, text, source)
                            }
                        }
                    )

                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            val newText = s?.toString() ?: ""
                            if (newText != value) {
                                onValueChange(newText)
                            }
                        }
                        override fun afterTextChanged(s: android.text.Editable?) {}
                    })
                }
            },
            update = { editText ->
                if (editText.text.toString() != value) {
                    editText.setText(value)
                    editText.setSelection(value.length)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
