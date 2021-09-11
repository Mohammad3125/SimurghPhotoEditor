package ir.maneditor.mananpiclibrary.views

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.updateLayoutParams
import ir.maneditor.mananpiclibrary.properties.Scalable

class MananImageView(context: Context, attr: AttributeSet?) : AppCompatImageView(context, attr),
    Scalable {
    constructor(context: Context) : this(context, null)

    private var isInitialScaling = true
    private var maximumScalingWidth = 0
    private var maximumScalingHeight = 0
    private var minimumScalingWidth = 0
    private var minimumScalingHeight = 0
    private lateinit var imageParent: ViewGroup

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        imageParent = parent as ViewGroup
    }

    override fun applyScale(scaleFactor: Float) {
        if (isInitialScaling) {
            maximumScalingWidth = imageParent.width
            maximumScalingHeight = imageParent.height
            minimumScalingWidth = width / 4
            minimumScalingHeight = height / 4
            isInitialScaling = false
        }

        var widthToScale = (width.toFloat() * scaleFactor).toInt()
        var heightToScale = (height.toFloat() * scaleFactor).toInt()

        if (widthToScale > maximumScalingWidth) widthToScale = maximumScalingWidth
        if (heightToScale > maximumScalingHeight) heightToScale = maximumScalingHeight

        if (widthToScale < minimumScalingWidth) widthToScale = minimumScalingWidth
        if (heightToScale < minimumScalingHeight) heightToScale = minimumScalingHeight

        updateLayoutParams {
            width = widthToScale
            height = heightToScale
        }
    }

}