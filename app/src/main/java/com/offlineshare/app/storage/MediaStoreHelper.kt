package com.offlineshare.app.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.offlineshare.app.model.FileCategory
import java.io.File
import java.io.FileInputStream

object MediaStoreHelper {
    fun classifyFile(mimeType: String, fileName: String): FileCategory {
        val lowerMime = mimeType.lowercase()
        val lowerName = fileName.lowercase()
        return when {
            lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".png") -> FileCategory.PHOTOS
            lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") -> FileCategory.VIDEOS
            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".flac") -> FileCategory.AUDIO
            lowerName.endsWith(".apk") -> FileCategory.APKS
            else -> FileCategory.DOCUMENTS
        }
    }

    fun saveFileToMediaStore(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String,
        category: FileCategory
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                val relPath = when (category) {
                    FileCategory.PHOTOS -> Environment.DIRECTORY_PICTURES + "/OfflineShare"
                    FileCategory.VIDEOS -> Environment.DIRECTORY_MOVIES + "/OfflineShare"
                    FileCategory.AUDIO -> Environment.DIRECTORY_MUSIC + "/OfflineShare"
                    else -> Environment.DIRECTORY_DOWNLOADS + "/OfflineShare"
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            }
        }
        val targetUri = when (category) {
            FileCategory.PHOTOS -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            FileCategory.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            FileCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(targetUri, contentValues) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(sourceFile).use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        return uri
    }
}