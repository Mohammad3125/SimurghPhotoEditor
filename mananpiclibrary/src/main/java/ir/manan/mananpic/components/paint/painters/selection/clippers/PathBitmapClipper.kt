package ir.manan.mananpic.components.paint.painters.selection.clippers

import android.graphics.*
import androidx.core.graphics.toRect
import ir.manan.mananpic.components.paint.painters.brushpaint.BrushPreview
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.smoothers.BasicSmoother

class PathBitmapClipper(
    path: Path? = null,
    bitmap: Bitmap? = null,
    isInverse: Boolean = false,
    var edgeBrush: Brush,
) : PathClipper(path, bitmap, isInverse) {

    constructor(edgeBrush: Brush) : this(null, null, false, edgeBrush)

    private val canvas by lazy {
        Canvas()
    }

    private val pathCopy by lazy {
        Path()
    }

    private val lassoFillPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    private val basicLineSmoother by lazy {
        BasicSmoother()
    }

    private val dstOutBitmapPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }

    private val bitmapRectangle by lazy {
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

    private inline fun doIfPathAndBitmapNotNull(function: (bitmap: Bitmap, path: Path) -> Unit) {
        bitmap?.let { bit ->
            path?.let { p ->
                function(bit, p)
            }
        }
    }

    private fun drawBitmapOnMask(bitmap: Bitmap): Bitmap {
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

    private fun clip(targetBitmap: Bitmap) {
        val smoothEdgesBitmap =
            createSmoothEdgesBitmap(targetBitmap.width, targetBitmap.height)

        maskOutBitmap(smoothEdgesBitmap, targetBitmap)
    }

    private fun setRectangleBounds(bitmap: Bitmap) {
        if (isInverse) {
            bitmapRectangle.set(
                0f,
                0f,
                bitmap.width.toFloat(),
                bitmap.height.toFloat()
            )
        } else {
            path!!.computeBounds(bitmapRectangle, true)
        }
    }

    private fun createMaskedBitmap(bitmap: Bitmap): Bitmap {
        return bitmap.copy(bitmap.config, true)
    }

    private fun createSmoothEdgesBitmap(width: Int, height: Int): Bitmap {
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

    private fun maskOutBitmap(smoothEdgesBitmap: Bitmap, bitmap: Bitmap) {
        canvas.apply {
            setBitmap(smoothEdgesBitmap)

            drawPath(pathCopy, lassoFillPaint)

            setBitmap(bitmap)

            drawBitmap(smoothEdgesBitmap, 0f, 0f, dstOutBitmapPaint)
        }
    }
}