package ir.manan.mananpic.components.paint.smoothers

import android.graphics.Path
import android.graphics.PathMeasure
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.utils.gesture.GestureUtils
import kotlin.math.atan2
import kotlin.math.floor

class BezierLineSmoother : LineSmoother() {

    private var perv1x = 0f
    private var perv1y = 0f

    private var mid1x = 0f
    private var mid1y = 0f

    private var mid2x = 0f
    private var mid2y = 0f

    private var perv2x = 0f
    private var perv2y = 0f

    private var curX = 0f
    private var curY = 0f

    private var distance = 0f

    private var isFirstThreeCreated = false

    private var counter = 0

    private val pointHolder = floatArrayOf(0f, 0f)
    private val tanHolder = floatArrayOf(0f, 0f)

    private val path = Path()
    private val pathMeasure = PathMeasure()

    override fun setFirstPoint(ex: Float, ey: Float, brush: Brush) {
        isFirstThreeCreated = false

        perv2x = ex
        perv2y = ey

        counter = 0
        counter++
    }

    override fun addPoints(ex: Float, ey: Float, brush: Brush) {
        if (!isFirstThreeCreated) {

            when (counter) {
                0 -> {
                    perv2x = ex
                    perv2y = ey
                }
                1 -> {
                    perv1x = ex
                    perv1y = ey
                }
                2 -> {
                    curX = ex
                    curY = ey

                    calculateQuadAndDraw(brush)

                    counter = 0

                    isFirstThreeCreated = true

                    return
                }
            }

            counter++
        } else {
            perv2x = perv1x
            perv2y = perv1y

            perv1x = curX
            perv1y = curY

            curX = ex
            curY = ey

            calculateQuadAndDraw(brush)

        }
    }

    override fun setLastPoint(ex: Float, ey: Float, brush: Brush) {

        if (isFirstThreeCreated) {

            perv2x = perv1x
            perv2y = perv1y

            perv1x = curX
            perv1y = curY

            curX = ex
            curY = ey

            calculateQuadAndDraw(brush)

            isFirstThreeCreated = false
        } else {
            onDrawPoint?.onDrawPoint(ex, ey, 0f)
        }

        distance = 0f
        path.rewind()
    }

    private fun calculateQuadAndDraw(brush: Brush) {

        val spacedWidth = brush.spacedWidth
        val smoothness = brush.smoothness

        val isListenerNull = onDrawPoint == null

        mid1x = (perv1x + perv2x) * 0.5f
        mid1y = (perv1y + perv2y) * 0.5f

        val smoothnessInverse = 1f - smoothness

        curX = curX * smoothnessInverse + (perv1x * smoothness)
        curY = curY * smoothnessInverse + (perv1y * smoothness)

        mid2x = (curX + perv1x) * 0.5f
        mid2y = (curY + perv1y) * 0.5f

        if (path.isEmpty) {
            path.moveTo(mid1x, mid1y)
        }

        path.quadTo(perv1x, perv1y, mid2x, mid2y)

        pathMeasure.setPath(path, false)

        val width = (pathMeasure.length)

        val total = floor((width - distance) / spacedWidth).toInt()

        repeat(total) {

            distance += spacedWidth


            pathMeasure.getPosTan(
                distance,
                pointHolder,
                tanHolder
            )

            val degree = if (brush.autoRotate) {
                GestureUtils.mapTo360(
                    -(Math.toDegrees(
                        (atan2(
                            tanHolder[0].toDouble(),
                            tanHolder[1].toDouble()
                        ))
                    ).toFloat() - 180f) - 90f
                )
            } else {
                0f
            }

            if (!isListenerNull) {
                onDrawPoint!!.onDrawPoint(pointHolder[0], pointHolder[1], degree)
            }
        }
    }
}