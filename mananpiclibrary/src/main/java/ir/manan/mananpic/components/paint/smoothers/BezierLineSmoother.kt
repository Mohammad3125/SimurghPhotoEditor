package ir.manan.mananpic.components.paint.smoothers

import android.graphics.Path
import android.graphics.PathMeasure
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

    private val path = Path()
    private val pathMeasure = PathMeasure()

    override fun setFirstPoint(ex: Float, ey: Float, smoothness: Float, stampWidth: Float) {
        isFirstThreeCreated = false

        perv2x = ex
        perv2y = ey

        counter = 0
        counter++
    }

    override fun addPoints(ex: Float, ey: Float, smoothness: Float, stampWidth: Float) {
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

                    calculateQuadAndDraw(smoothness, stampWidth)

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

            calculateQuadAndDraw(smoothness, stampWidth)

        }
    }

    override fun setLastPoint(ex: Float, ey: Float, smoothness: Float, stampWidth: Float) {

        if (isFirstThreeCreated) {

            perv2x = perv1x
            perv2y = perv1y

            perv1x = curX
            perv1y = curY

            curX = ex
            curY = ey

            calculateQuadAndDraw(smoothness, stampWidth)

            isFirstThreeCreated = false
        } else {
            onDrawPoint?.onDrawPoint(ex, ey)
        }

        distance = 0f
        path.rewind()
    }

    private fun calculateQuadAndDraw(smoothness: Float, stampWidth: Float) {

        mid1x = (perv1x + perv2x) * 0.5f
        mid1y = (perv1y + perv2y) * 0.5f

        val smoothnessInverse = 1f - smoothness

        curX = curX * smoothness + (perv1x * smoothnessInverse)
        curY = curY * smoothness + (perv1y * smoothnessInverse)

        mid2x = (curX + perv1x) * 0.5f
        mid2y = (curY + perv1y) * 0.5f

        if (path.isEmpty) {
            path.moveTo(mid1x, mid1y)
        }

        path.quadTo(perv1x, perv1y, mid2x, mid2y)

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