package com.capstone.snapspot.data.image

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the screenshots of saved places and coupons in app storage. The picker's URI is temporary and
 * cache files get cleaned up, so each image is copied once when saved. The original is kept as is:
 * a coupon's barcode has to stay scannable.
 */
@Singleton
class ImageStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir: File get() = File(context.filesDir, "images").apply { mkdirs() }

    /** Copies [uri] into app storage and returns the file path to save with the place or coupon. */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO + NonCancellable) {
        // Not cancellable: a copy that finished must be returned so the caller can delete it, never lost.
        val ext = context.contentResolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: "jpg"
        val file = File(dir, "${UUID.randomUUID()}.$ext")
        val input = context.contentResolver.openInputStream(uri) ?: error("이미지를 열 수 없어요.")
        try {
            input.use { src -> file.outputStream().use { src.copyTo(it) } }
        } catch (e: Exception) {
            file.delete() // no half-written file left behind
            throw e
        }
        file.absolutePath
    }

    /** Removes an imported image. Paths outside the image folder are left alone. */
    suspend fun delete(path: String?) {
        if (path == null) return
        withContext(Dispatchers.IO + NonCancellable) {
            val file = File(path)
            if (file.parentFile?.canonicalPath == dir.canonicalPath) file.delete()
        }
    }

    /**
     * Deletes images no saved place or coupon uses, such as a draft lost when the app was killed.
     * Only files in the image folder are touched, and files newer than [minAgeMs] are spared so a draft
     * being made right now is never taken. Returns how many were deleted.
     */
    suspend fun sweep(referenced: Set<String?>, minAgeMs: Long = 10 * 60 * 1000L, now: Long = System.currentTimeMillis()): Int =
        withContext(Dispatchers.IO) {
            val keep = referenced.filterNotNull().map { File(it).canonicalPath }.toSet()
            dir.listFiles().orEmpty().count { f ->
                f.isFile && f.canonicalPath !in keep && now - f.lastModified() >= minAgeMs && f.delete()
            }
        }
}
