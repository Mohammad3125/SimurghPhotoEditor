package ir.manan.mananpic.components.imageviews

import android.content.Context
import android.graphics.ColorFilter
import android.util.AttributeSet
import ir.manan.mananpic.properties.EditableComponent
import ir.manan.mananpic.properties.Filterable

class MananMatrixImageView(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet), EditableComponent, Filterable {

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
}