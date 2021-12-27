package ir.manan.mananpic.components.imageviews

import android.content.Context
import android.util.AttributeSet
import ir.manan.mananpic.properties.EditableComponent

class MananMatrixImageView(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet), EditableComponent {

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

}