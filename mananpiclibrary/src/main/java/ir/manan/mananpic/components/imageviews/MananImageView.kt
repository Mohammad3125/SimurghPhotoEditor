package ir.manan.mananpic.components.imageviews

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import ir.manan.mananpic.properties.Filterable
import ir.manan.mananpic.properties.MananComponent
import ir.manan.mananpic.utils.MananFactory

class MananImageView(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet), MananComponent, Filterable {

    constructor(context: Context) : this(context, null)

    override fun applyScale(scaleFactor: Float) {
        postScale(scaleFactor, imagePivotX, imagePivotY)
    }

    override fun applyRotation(degree: Float) {
        postRotate(degree - imageRotation, imagePivotX, imagePivotY)
    }

    override fun applyMovement(dx: Float, dy: Float) {
        postTranslate(dx, dy)
    }

    override fun applyFilter(colorFilter: ColorFilter) {
        this.colorFilter = colorFilter
    }

    override fun removeFilter() {
        clearColorFilter()
    }

    override fun reportBound(): RectF {
        return boundsRectangle
    }

    override fun reportRotation(): Float {
        return imageRotation
    }

    override fun reportBoundPivotX(): Float {
        return leftEdge
    }

    override fun reportBoundPivotY(): Float {
        return topEdge
    }

    override fun reportPivotX(): Float {
        return imagePivotX
    }

    override fun reportPivotY(): Float {
        return imagePivotY
    }

    override fun clone(): View {
        return MananFactory.createImageView(context, toBitmap()).apply {
            colorFilter = this@MananImageView.colorFilter
        }
    }

}