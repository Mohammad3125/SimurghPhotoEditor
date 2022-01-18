package ir.manan.mananpic.components.imageviews

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector

class MananPerspectiveImageView(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet) {

    private var srcPoints: FloatArray? = null
    private var dstPoints: FloatArray? = null

    private var totalDxChange = 0f


    init {
        moveDetector = MoveDetector(1, this)
    }

    override fun onImageLaidOut() {
//        srcPoints = floatArrayOf(
//            0f, 0f,
//            0f, bitmapHeight,
//            bitmapWidth, bitmapHeight,
//            bitmapWidth, 0f
//        )

        val finalWidth = drawableWidth.toFloat()
        val finalHeight = drawableHeight.toFloat()

        srcPoints = floatArrayOf(
            0f, 0f,
            finalWidth, 0f,
            finalWidth, finalHeight,
            0f, finalHeight
        )

    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean {
        totalDxChange += dx

        val finalWidth = drawableWidth.toFloat()
        val finalHeight = drawableHeight.toFloat()

        dstPoints =
            floatArrayOf(
                0f, 0f,
                finalWidth, 0f,
                finalWidth, finalHeight,
                0f, finalHeight
            )

//        imageviewMatrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        postScale(
            initialScale / getMatrixValue(Matrix.MSCALE_Y, true),
            0f,
            0f
        )

        return true
    }
}
