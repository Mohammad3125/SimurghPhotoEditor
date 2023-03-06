package ir.manan.mananpic.components.paint.smoothers

import android.graphics.Path
import android.graphics.PathMeasure
import kotlin.math.floor

class BasicSmoother : LineSmoother() {

    private val path by lazy {
        Path()
    }

    private val pathMeasure by lazy {
        PathMeasure()
    }

    private var distance = 0f

    private val pointHolder = FloatArray(2)

    override fun setFirstPoint(ex: Float, ey: Float, smoothness: Float, stampWidth: Float) {
        path.rewind()
        path.moveTo(ex,ey)
    }

    override fun addPoints(ex: Float, ey: Float, smoothness: Float, stampWidth: Float) {
        path.lineTo(ex,ey)
        drawPoints(stampWidth)
    }

    override fun setLastPoint(ex: Float, ey: Float, smoothness: Float, stampWidth: Float) {
        path.lineTo(ex,ey)
        drawPoints(stampWidth)
        distance = 0f
    }

    private fun drawPoints(stampWidth: Float) {
        pathMeasure.setPath(path, false)

        val width = (pathMeasure.length)

        val total = floor((width - distance) / stampWidth).toInt()

        repeat(total) {

            distance += stampWidth


            pathMeasure.getPosTan(
                distance,
                pointHolder,
                null
            )

            onDrawPoint?.onDrawPoint(pointHolder[0], pointHolder[1])
        }
    }
}