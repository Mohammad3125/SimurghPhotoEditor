package ir.manan.mananpic.components.paint.engines

import android.graphics.Canvas
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.utils.gesture.GestureUtils
import kotlin.random.Random

class CachedCanvasEngine : DrawingEngine {

    var cachedScatterX = 0f
    var cachedScatterY = 0f
    var cachedScale = 0f
    var cachedRotation = 0f

    private var taperSizeHolder = 0

    override fun onMoveBegin(ex: Float, ey: Float, brush: Brush) {
        taperSizeHolder = if (brush.startTaperSize == 0) brush.size else brush.startTaperSize
    }

    override fun onMove(ex: Float, ey: Float, brush: Brush) {
    }

    override fun onMoveEnded(ex: Float, ey: Float, brush: Brush) {
    }

    override fun draw(ex: Float, ey: Float, canvas: Canvas, brush: Brush) {
        brush.apply {
            canvas.save()

            val scatterSize = scatter

            if (scatterSize > 0f) {
                val brushSize = size

                val rx = (brushSize * (scatterSize * cachedScatterX)).toInt()
                val ry = (brushSize * (scatterSize * cachedScatterY)).toInt()

                canvas.translate(
                    ex + rx,
                    ey + ry
                )
            } else {
                canvas.translate(ex, ey)
            }

            val angleJitter = angleJitter

            val fixedAngle = angle

            if (angleJitter > 0f && fixedAngle > 0f || angleJitter > 0f && fixedAngle == 0f) {

                val rot = GestureUtils.mapTo360(
                    fixedAngle + (360f * (angleJitter * cachedRotation))
                )

                canvas.rotate(rot)
            } else if (angleJitter == 0f && fixedAngle > 0f) {
                canvas.rotate(fixedAngle)
            }

            val squish = 1f - squish

            val sizeJitter = sizeJitter

            val jitterNumber = sizeJitter * cachedScale
            val finalScale = (1f + jitterNumber)
            canvas.scale(finalScale * squish, finalScale)

            val brushOpacity = if (opacityJitter > 0f) {
                Random.nextInt(0, (255f * opacityJitter).toInt())
            } else  {
                (opacity * 255).toInt()
            }

            val llSize = size

            if (startTaperSpeed > 0 && startTaperSize != size) {
                if (startTaperSize < size) {
                    taperSizeHolder += startTaperSpeed
                    taperSizeHolder = taperSizeHolder.coerceAtMost(size)
                } else {
                    taperSizeHolder -= startTaperSpeed
                    taperSizeHolder = taperSizeHolder.coerceAtLeast(size)
                }
                size = taperSizeHolder
            }

            draw(canvas, brushOpacity)

            if(size != llSize) {
                size = llSize
            }

            canvas.restore()
        }
    }
}