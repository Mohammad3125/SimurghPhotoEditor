package ir.manan.mananpic.components.imageviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.shapes.Shape
import android.view.View
import ir.manan.mananpic.properties.Colorable
import ir.manan.mananpic.properties.MananComponent
import ir.manan.mananpic.properties.StrokeCapable

@SuppressLint("ViewConstructor")
class MananShapeView(
    context: Context,
    @Transient var shape: Shape,
    var shapeWidth: Int,
    var shapeHeight: Int
) : View(context), MananComponent, StrokeCapable, Colorable, java.io.Serializable {

    @Transient
    private val shapePaint = Paint()

    var shapeColor = Color.BLACK
        set(value) {
            field = value
            shapePaint.color = value
            invalidate()
        }

    private var strokeSize = 0f

    private var strokeColor = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    @Transient
    private val bounds = RectF()

    @Transient
    private val mappingMatrix = Matrix()
    override fun changeColor(color: Int) {
        shapeColor = color
    }

    override fun getColor(): Int {
        return shapeColor
    }

    override fun reportRotation(): Float {
        return rotation
    }

    override fun reportScaleX(): Float {
        return scaleX
    }

    override fun reportScaleY(): Float {
        return scaleY
    }

    override fun reportBoundPivotX(): Float {
        return bounds.centerX()
    }

    override fun reportBound(): RectF {
        bounds.set(
            x,
            y,
            width + x,
            height + y
        )
        mappingMatrix.run {
            setScale(scaleX, scaleY, bounds.centerX(), bounds.centerY())
            mapRect(bounds)
        }
        return bounds
    }

    override fun clone(): View {
        return this
    }

    override fun reportBoundPivotY(): Float {
        return bounds.centerY()
    }

    override fun reportPivotX(): Float {
        return pivotX
    }

    override fun reportPivotY(): Float {
        return pivotY
    }

    override fun applyRotation(degree: Float) {
        rotation = degree
    }

    override fun applyScale(scaleFactor: Float) {
        scaleX *= scaleFactor
        scaleY *= scaleFactor
    }

    override fun applyScale(xFactor: Float, yFactor: Float) {
        scaleX *= xFactor
        scaleY *= yFactor
    }

    override fun applyMovement(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        shape.resize(shapeWidth.toFloat(), shapeHeight.toFloat())

        setMeasuredDimension(
            (shapeWidth + strokeSize).toInt(),
            (shapeHeight + strokeSize).toInt()
        )
    }

    override fun setStroke(strokeRadiusPx: Float, strokeColor: Int) {
        strokeSize = strokeRadiusPx
        this.strokeColor = strokeColor
        requestLayout()
    }

    override fun getStrokeColor(): Int {
        return strokeColor
    }

    override fun getStrokeWidth(): Float {
        return strokeSize
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.run {
            val half = strokeSize * 0.5f
            translate(half, half)

            if (strokeSize > 0f) {
                val currentColor = shapeColor
                val currentStyle = shapePaint.style
                shapePaint.style = Paint.Style.STROKE
                shapePaint.strokeWidth = strokeSize
                val currentShader = shapePaint.shader
                shapePaint.shader = null
                shapePaint.color = strokeColor

                shape.draw(canvas, shapePaint)

                shapePaint.shader = currentShader
                shapePaint.style = currentStyle
                shapePaint.strokeWidth = 0f
                shapePaint.color = currentColor
            }
            shape.draw(canvas, shapePaint)
        }
    }
}