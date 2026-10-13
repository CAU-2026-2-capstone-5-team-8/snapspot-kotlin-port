package com.capstone.snapspot.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.capstone.snapspot.data.image.AndroidImageSource
import com.capstone.snapspot.data.image.ImagePreprocessor
import com.capstone.snapspot.data.image.ImageStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageTest {
    private lateinit var context: Context
    private lateinit var store: ImageStore
    private lateinit var pre: ImagePreprocessor
    private lateinit var work: File
    private lateinit var imagesDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        store = ImageStore(context)
        pre = ImagePreprocessor(context)
        work = File(context.cacheDir, "image-test").apply { deleteRecursively(); mkdirs() }
        imagesDir = File(context.filesDir, "images").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        work.deleteRecursively()
        imagesDir.deleteRecursively()
    }

    private fun png(name: String, w: Int, h: Int, fill: Int): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(fill) }
        return File(work, name).also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun decode(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun uri(f: File) = Uri.fromFile(f)

    // --- ImagePreprocessor ---

    @Test
    fun aWideScreenshotIsReducedToWidth1024() = runBlocking {
        val out = pre.toJpeg(uri(png("big.png", 2000, 1000, Color.BLUE)))
        val bmp = decode(out)
        assertEquals(1024, bmp.width)
        assertEquals(512, bmp.height)
    }

    @Test
    fun aSmallImageIsNotEnlarged() = runBlocking {
        val bmp = decode(pre.toJpeg(uri(png("small.png", 400, 300, Color.RED))))
        assertEquals(400, bmp.width)
        assertEquals(300, bmp.height)
    }

    @Test
    fun theResultIsAJpeg() = runBlocking {
        val out = pre.toJpeg(uri(png("a.png", 100, 100, Color.GREEN)))
        assertEquals(0xFF.toByte(), out[0])
        assertEquals(0xD8.toByte(), out[1])
    }

    @Test
    fun aTransparentPngBecomesWhiteNotBlack() = runBlocking {
        val bmp = decode(pre.toJpeg(uri(png("clear.png", 200, 200, Color.TRANSPARENT))))
        val p = bmp.getPixel(100, 100)
        assertTrue("pixel was ${Integer.toHexString(p)}", Color.red(p) > 240 && Color.green(p) > 240 && Color.blue(p) > 240)
    }

    @Test
    fun aPhotoTurnedByExifComesOutUpright() = runBlocking {
        val bmp = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val f = File(work, "rotated.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(f.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val out = decode(pre.toJpeg(uri(f)))
        assertEquals(300, out.width)
        assertEquals(600, out.height)
    }

    @Test
    fun aFileThatIsNotAnImageFails() {
        val junk = File(work, "junk.png").also { it.writeBytes(byteArrayOf(1, 2, 3, 4)) }
        try {
            runBlocking { pre.toJpeg(uri(junk)) }
            fail("expected an exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("이미지를 열 수 없어요.", e.message)
        }
    }

    // --- ImageStore ---

    @Test
    fun importCopiesTheFileIntoAppStorage() = runBlocking {
        val src = png("shot.png", 300, 200, Color.CYAN)
        val path = store.import(uri(src))
        assertTrue(File(path).exists())
        assertEquals(imagesDir.canonicalPath, File(path).parentFile!!.canonicalPath)
        assertArrayEquals(src.readBytes(), File(path).readBytes())
    }

    @Test
    fun twoImportsOfTheSamePictureAreTwoFiles() = runBlocking {
        val src = png("shot.png", 50, 50, Color.CYAN)
        assertFalse(store.import(uri(src)) == store.import(uri(src)))
    }

    @Test
    fun importOfAMissingFileFailsAndLeavesNothing() {
        try {
            runBlocking { store.import(uri(File(work, "nope.png"))) }
            fail("expected an exception")
        } catch (_: Exception) {
        }
        assertEquals(0, imagesDir.listFiles().orEmpty().size)
    }

    @Test
    fun deleteRemovesAnImportedImageButNotAFileOutsideTheFolder() = runBlocking {
        val path = store.import(uri(png("x.png", 10, 10, Color.BLACK)))
        store.delete(path)
        assertFalse(File(path).exists())

        val outside = File(work, "keep.txt").also { it.writeText("keep") }
        store.delete(outside.absolutePath)
        assertTrue(outside.exists())
        store.delete(null) // nothing to do, no crash
    }

    @Test
    fun sweepKeepsUsedAndYoungImagesAndRemovesTheRest() = runBlocking {
        val used = File(store.import(uri(png("a.png", 10, 10, Color.RED))))
        val orphanOld = File(store.import(uri(png("b.png", 10, 10, Color.RED))))
        val orphanYoung = File(store.import(uri(png("c.png", 10, 10, Color.RED))))
        val now = System.currentTimeMillis()
        used.setLastModified(now - 3_600_000)
        orphanOld.setLastModified(now - 3_600_000)

        val removed = store.sweep(setOf(used.absolutePath, null), minAgeMs = 600_000, now = now)

        assertEquals(1, removed)
        assertTrue(used.exists())
        assertFalse(orphanOld.exists())
        assertTrue("a draft being made right now must survive", orphanYoung.exists())
    }

    // --- AndroidImageSource ---

    @Test
    fun prepareGivesTheStoredCopyAndASmallJpeg() = runBlocking {
        val source = AndroidImageSource(store, pre)
        val prepared = source.prepare(uri(png("wide.png", 3000, 1500, Color.YELLOW)).toString())
        assertTrue(File(prepared.path).exists())
        assertEquals(3000, BitmapFactory.decodeFile(prepared.path).width) // the copy is the original
        assertEquals(1024, decode(prepared.jpeg).width) // the analyzer's version is small
        source.delete(prepared.path)
        assertFalse(File(prepared.path).exists())
    }

    @Test
    fun prepareOfSomethingUnreadableLeavesNoCopyBehind() {
        val junk = File(work, "junk.png").also { it.writeBytes(byteArrayOf(9, 9, 9)) }
        try {
            runBlocking { AndroidImageSource(store, pre).prepare(uri(junk).toString()) }
            fail("expected an exception")
        } catch (_: Exception) {
        }
        assertEquals(0, imagesDir.listFiles().orEmpty().size)
        assertNotNull(junk)
    }
}
