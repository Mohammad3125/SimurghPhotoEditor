package ir.manan.mananpic.components.paint.smoothers

import android.graphics.Path
import android.graphics.PathMeasure
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.utils.gesture.TouchData
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

    override fun setFirstPoint(touchData: TouchData, brush: Brush) {
        path.rewind()
        path.moveTo(touchData.ex, touchData.ey)
    }

    override fun addPoints(touchData: TouchData, brush: Brush) {
        path.lineTo(touchData.ex, touchData.ey)
        drawPoints(brush)
    }

    override fun setLastPoint(touchData: TouchData, brush: Brush) {
        path.lineTo(touchData.ex, touchData.ey)
        drawPoints(brush)
        distance = 0f
    }

    private fun drawPoints(brush: Brush) {

        val spacedWidth = brush.spacedWidth

        val isListenerNull = onDrawPoint == null

        pathMeasure.setPath(path, false)

        val width = (pathMeasure.length)

        val total = floor((width - distance) / spacedWidth).toInt()

        repeat(total) {

            distance += spacedWidth


            pathMeasure.getPosTan(
                distance,
                pointHolder,
                null
            )

            if (!isListenerNull) {
                onDrawPoint!!.onDrawPoint(pointHolder[0], pointHolder[1], 0f, 1, it == (total - 1))
            }
        }
    }
}