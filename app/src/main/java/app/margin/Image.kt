package app.margin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Shrinks a camera photo and applies its EXIF rotation so handwriting is upright. */
fun prepareImage(src: File, dst: File): File {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(src.absolutePath, bounds)
    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / sample > 2000) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return src
    val orientation = ExifInterface(src.absolutePath)
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val out = if (rotation != 0f) {
        val m = Matrix()
        m.postRotate(rotation)
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    } else {
        bmp
    }
    dst.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return dst
}
