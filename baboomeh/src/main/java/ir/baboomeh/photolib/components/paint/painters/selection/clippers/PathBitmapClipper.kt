package ir.baboomeh.photolib.components.paint.painters.selection.clippers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.toRect
import ir.baboomeh.photolib.components.paint.painters.brushpaint.BrushPreview
import ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes.Brush
import ir.baboomeh.photolib.components.paint.smoothers.BasicSmoother

open class PathBitmapClipper(
    var path: Path? = null,
    bitmap: Bitmap? = null,
    var isInverse: Boolean = false,
    var edgeBrush: Brush,
) : Clipper(bitmap) {

    constructor(edgeBrush: Brush) : this(null, null, false, edgeBrush)

    protected val canvas by lazy {
        Canvas()
    }

    protected  val pathCopy by lazy {
        Path()
    }

    protected  val lassoFillPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    protected  val basicLineSmoother by lazy {
        BasicSmoother()
    }

    protected  val dstOutBitmapPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }

    protected  val bitmapRectangle by lazy {
        RectF()
    }


    override fun clip() {
        doIfPathAndBitmapNotNull { b, p ->
            val fillType = path!!.fillType

            path!!.fillType =
                if (isInverse) Path.FillType.INVERSE_WINDING else Path.FillType.WINDING

            clip(b)

            path!!.fillType = fillType
        }
    }

    override fun copy(): Bitmap? {
        doIfPathAndBitmapNotNull { bitmap, path ->
            return drawBitmapOnMask(bitmap)
        }
        return null
    }

    override fun cut(): Bitmap? {
        doIfPathAndBitmapNotNull { bitmap, path ->
            val finalBitmap = drawBitmapOnMask(bitmap)
            clip()
            return finalBitmap
        }
        return null
    }

    protected inline fun doIfPathAndBitmapNotNull(function: (bitmap: Bitmap, path: Path) -> Unit) {
        bitmap?.let { bit ->
            path?.let { p ->
                function(bit, p)
            }
        }
    }

    protected fun drawBitmapOnMask(bitmap: Bitmap): Bitmap {
        setRectangleBounds(bitmap)

        val maskedBitmap = createMaskedBitmap(bitmap)

        val fillType = path!!.fillType

        path!!.fillType =
            if (isInverse) Path.FillType.WINDING else Path.FillType.INVERSE_WINDING

        clip(maskedBitmap)

        path!!.fillType = fillType

        val rect = bitmapRectangle.toRect()

        return Bitmap.createBitmap(
            maskedBitmap,
            rect.left,
            rect.top,
            rect.width(),
            rect.height()
        )
    }

    protected fun clip(targetBitmap: Bitmap) {
        val smoothEdgesBitmap =
            createSmoothEdgesBitmap(targetBitmap.width, targetBitmap.height)

        maskOutBitmap(smoothEdgesBitmap, targetBitmap)
    }

    protected fun setRectangleBounds(bitmap: Bitmap) {
        if (isInverse) {
            bitmapRectangle.set(
                0f,
                0f,
                bitmap.width.toFloat(),
                bitmap.height.toFloat()
            )
        } else {
            path!!.computeBounds(bitmapRectangle, true)

            bitmapRectangle.left = bitmapRectangle.left.coerceAtLeast(0f)
            bitmapRectangle.top = bitmapRectangle.top.coerceAtLeast(0f)
            bitmapRectangle.right = bitmapRectangle.right.coerceAtMost(bitmap.width.toFloat())
            bitmapRectangle.bottom = bitmapRectangle.bottom.coerceAtMost(bitmap.height.toFloat())
        }
    }

    override fun getClippingBounds(rect: RectF) {
        doIfPathAndBitmapNotNull { bitmap, path ->
            setRectangleBounds(bitmap)
            rect.set(bitmapRectangle)
            return
        }
        throw IllegalStateException("path and/or bitmap is null; cannot get the clipping bounds")
    }

    protected open fun createMaskedBitmap(bitmap: Bitmap): Bitmap {
        return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
    }

    protected open fun createSmoothEdgesBitmap(width: Int, height: Int): Bitmap {
        pathCopy.set(path!!)

        pathCopy.close()

        val b = BrushPreview.createBrushSnapshot(
            width,
            height,
            0f,
            0f,
            edgeBrush,
            resolution = 1024,
            basicLineSmoother,
            pathCopy
        )
        return b
    }

    protected open fun maskOutBitmap(smoothEdgesBitmap: Bitmap, bitmap: Bitmap) {
        canvas.apply {
            setBitmap(smoothEdgesBitmap)

            drawPath(pathCopy, lassoFillPaint)

            setBitmap(bitmap)

            drawBitmap(smoothEdgesBitmap, 0f, 0f, dstOutBitmapPaint)
        }
    }
}