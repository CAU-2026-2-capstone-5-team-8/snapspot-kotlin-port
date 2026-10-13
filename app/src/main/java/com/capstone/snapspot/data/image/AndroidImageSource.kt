package com.capstone.snapspot.data.image

import android.net.Uri
import com.capstone.snapspot.domain.draft.ImageSource
import com.capstone.snapspot.domain.draft.PreparedImage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Copies the picked photo into app storage, then makes the small JPEG the analyzer reads from that copy. */
class AndroidImageSource @Inject constructor(
    private val store: ImageStore,
    private val preprocessor: ImagePreprocessor,
) : ImageSource {

    override suspend fun prepare(uri: String): PreparedImage {
        val path = store.import(Uri.parse(uri))
        return try {
            PreparedImage(path, preprocessor.toJpeg(Uri.fromFile(File(path))))
        } catch (e: Throwable) {
            // Cancelled or unreadable: do not leave the copy behind.
            withContext(NonCancellable) { store.delete(path) }
            throw e
        }
    }

    override suspend fun delete(path: String) = store.delete(path)
}
