package com.example.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.util.Log

class LifeOsCloudMediaProvider : ContentProvider() {

    companion object {
        private const val TAG = "LifeOsCloudMedia"
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "Initializing LifeOsCloudMediaProvider (Safe ContentProvider)")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        return MatrixCursor(projection ?: arrayOf("_id"))
    }

    override fun getType(uri: Uri): String? {
        return "vnd.android.cursor.dir/media"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }
}
