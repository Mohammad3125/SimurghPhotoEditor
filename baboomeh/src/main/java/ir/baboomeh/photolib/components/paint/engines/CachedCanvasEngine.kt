package ir.baboomeh.photolib.components.paint.engines

import android.graphics.Canvas
import androidx.core.graphics.withSave
import ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes.Brush
import ir.baboomeh.photolib.utils.gesture.GestureUtils
import ir.baboomeh.photolib.utils.gesture.TouchData
import kotlin.random.Random

open class CachedCanvasEngine : DrawingEngine {

    open var cachedScatterX = 0f
    open var cachedScatterY = 0f
    open var cachedScale = 0f
    open var cachedRotation = 0f

    private var taperSizeHolder = 0f

    override fun onMoveBegin(touchData: TouchData, brush: Brush) {
        taperSizeHolder = brush.startTaperSize
    }

    override fun onMove(touchData: TouchData, brush: Brush) {
    }

    override fun onMoveEnded(touchData: TouchData, brush: Brush) {
    }

    override fun draw(
        ex: Float,
        ey: Float,
        directionalAngle: Float,
        canvas: Canvas,
        brush: Brush,
        drawCount: Int
    ) {
        brush.apply {
            canvas.withSave {

                val scatterSize = scatter

                if (scatterSize > 0f) {
                    val brushSize = size

                    val rx = (brushSize * (scatterSize * cachedScatterX)).toInt()
                    val ry = (brushSize * (scatterSize * cachedScatterY)).toInt()

                    translate(ex + rx, ey + ry)
                } else {
                    translate(ex, ey)
                }

                val angleJitter = angleJitter

                val fixedAngle = angle

                if (angleJitter > 0f && (fixedAngle > 0f || directionalAngle > 0f) || angleJitter > 0f && fixedAngle == 0f) {

                    val rot = GestureUtils.mapTo360(
                        fixedAngle + (360f * (angleJitter * cachedRotation)) + directionalAngle
                    )

                    rotate(rot)
                } else if (angleJitter == 0f && (fixedAngle > 0f || directionalAngle > 0f)) {
                    rotate(fixedAngle + directionalAngle)
                }

                if (startTaperSpeed > 0 && startTaperSize != 1f && taperSizeHolder != 1f) {
                    if (startTaperSize < 1f) {
                        taperSizeHolder += startTaperSpeed
                        taperSizeHolder = taperSizeHolder.coerceAtMost(1f)
                    } else {
                        taperSizeHolder -= startTaperSpeed
                        taperSizeHolder = taperSizeHolder.coerceAtLeast(1f)
                    }
                }


                val squish = 1f - squish

                val sizeJitter = sizeJitter

                val jitterNumber = sizeJitter * cachedScale

                val finalTaperSize =
                    if (taperSizeHolder != 1f && startTaperSpeed > 0) taperSizeHolder else 1f

                val finalScale = (1f + jitterNumber) * finalTaperSize

                scale(finalScale * squish, finalScale)

                val brushOpacity = if (opacityJitter > 0f) {
                    Random.nextInt(0, (255f * opacityJitter).toInt())
                } else {
                    (opacity * 255).toInt()
                }


                draw(this, brushOpacity)
            }
        }
    }

    override fun isEraserModeEnabled(): Boolean {
        return false
    }

    override fun setEraserMode(isEnabled: Boolean) {

    }
}