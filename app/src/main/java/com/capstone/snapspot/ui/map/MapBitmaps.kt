package com.capstone.snapspot.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.capstone.snapspot.ui.theme.SnapColor

/** Draws the map's markers as bitmaps: a screenshot pin, a brand badge, and the user's own position. */
object MapBitmaps {
    private fun argb(c: Color) = c.toArgb()

    /**
     * A pin: the screenshot in a rounded frame with a tail. The frame is yellow when the place is near,
     * ink and larger when it is selected, white otherwise. Anchor the pin at its bottom center.
     */
    fun pin(thumb: Bitmap?, near: Boolean, selected: Boolean, density: Float): Bitmap {
        val px = density * if (selected) 1.18f else 1f
        val w = (52 * px).toInt()
        val h = (66 * px).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val border = when {
            selected -> SnapColor.Ink
            near -> SnapColor.Highlight
            else -> SnapColor.Surface
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Tail first, so the frame covers its top half.
        paint.color = argb(border)
        c.save()
        c.rotate(45f, 26 * px, 60 * px)
        c.drawRoundRect(RectF(20 * px, 54 * px, 32 * px, 66 * px), 2 * px, 2 * px, paint)
        c.restore()

        val outer = RectF(3 * px, 0f, 49 * px, 58 * px)
        // A hairline outside the frame keeps the pin readable on pale map areas.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = argb(SnapColor.Line)
        c.drawRoundRect(outer, 9 * px, 9 * px, paint)
        paint.style = Paint.Style.FILL
        paint.color = argb(border)
        c.drawRoundRect(outer, 9 * px, 9 * px, paint)

        val inner = RectF(outer.left + 3 * px, outer.top + 3 * px, outer.right - 3 * px, outer.bottom - 3 * px)
        paint.color = argb(SnapColor.GreenSoft)
        c.drawRoundRect(inner, 6 * px, 6 * px, paint)
        if (thumb != null) {
            val clip = Path().apply { addRoundRect(inner, 6 * px, 6 * px, Path.Direction.CW) }
            c.save()
            c.clipPath(clip)
            c.drawBitmap(thumb, coverTop(thumb, inner), RectF(inner), Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
        return bmp
    }

    /** The part of [src] that fills [dst] without stretching: centered sideways, aligned to the top. */
    private fun coverTop(src: Bitmap, dst: RectF): Rect {
        val dstAspect = dst.width() / dst.height()
        val srcAspect = src.width.toFloat() / src.height
        return if (srcAspect > dstAspect) {
            val cropW = (src.height * dstAspect).toInt()
            val left = (src.width - cropW) / 2
            Rect(left, 0, left + cropW, src.height)
        } else {
            Rect(0, 0, src.width, (src.width / dstAspect).toInt().coerceAtMost(src.height))
        }
    }

    /** A small round badge with the brand's first letter, for a coupon branch in the watch plan. */
    fun badge(letter: String, near: Boolean, density: Float): Bitmap {
        val size = (26 * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        paint.color = argb(if (near) SnapColor.Highlight else SnapColor.Surface)
        c.drawCircle(r, r, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * density
        paint.color = argb(SnapColor.Ink)
        c.drawCircle(r, r, r - density, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12 * density
        paint.typeface = Typeface.DEFAULT_BOLD
        val y = r - (paint.descent() + paint.ascent()) / 2
        c.drawText(letter, r, y, paint)
        return bmp
    }

    /** The user's position: a green dot with a white ring and a soft halo. */
    fun meDot(density: Float): Bitmap {
        val size = (40 * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        paint.color = android.graphics.Color.argb(51, 45, 122, 87)
        c.drawCircle(r, r, 14 * density, paint)
        paint.color = argb(SnapColor.Surface)
        c.drawCircle(r, r, 8 * density, paint)
        paint.color = argb(SnapColor.Green)
        c.drawCircle(r, r, 5 * density, paint)
        return bmp
    }

    /** A small version of a screenshot for a pin, or null if the file cannot be read. */
    fun thumbnail(path: String, targetWidthPx: Int = 120): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
