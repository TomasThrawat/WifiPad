package com.wifipad.receiver

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends timestamped lines to a single text file in the public Downloads
 * collection (MediaStore.Downloads). No storage permission is needed: on
 * API 29+ (this app's minSdk is 30) an app can contribute to well-defined
 * media collections such as MediaStore.Downloads without requesting any
 * storage-related permission — see "Storage updates in Android 11",
 * developer.android.com/about/versions/11/privacy/storage. Reading a file
 * an app didn't create out of Downloads still needs the Storage Access
 * Framework, but this file only ever writes/reads what it created itself.
 */
object FileLogger {
    private const val FILE_NAME = "wifipad_receiver_log.txt"
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun log(context: Context, message: String) {
        try {
            val resolver = context.contentResolver
            val uri = findExisting(resolver) ?: create(resolver) ?: return
            resolver.openOutputStream(uri, "wa")?.use { out ->
                out.write("${timeFormat.format(Date())}  $message\n".toByteArray())
            }
        } catch (e: Exception) {
            // Logging must never crash the app it's meant to help debug.
        }
    }

    private fun create(resolver: ContentResolver): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    private fun findExisting(resolver: ContentResolver): Uri? {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(FILE_NAME), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }
}
