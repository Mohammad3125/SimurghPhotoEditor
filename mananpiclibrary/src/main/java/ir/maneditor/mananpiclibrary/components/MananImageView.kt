package ir.maneditor.mananpiclibrary.components

import android.content.Context
import android.graphics.ColorFilter
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.updateLayoutParams
import ir.maneditor.mananpiclibrary.properties.Filterable
import ir.maneditor.mananpiclibrary.properties.Scalable

class MananImageView(context: Context, attr: AttributeSet?) : AppCompatImageView(context, attr),
    Scalable, Filterable {
    constructor(context: Context) : this(context, null)

    private var isInitialScaling = true
    private var minimumScalingWidth = 0
    private var minimumScalingHeight = 0
    private lateinit var imageParent: ViewGroup

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        imageParent = parent as ViewGroup
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (isInitialScaling) {
            minimumScalingWidth = measuredWidth / 4
            minimumScalingHeight = measuredHeight / 4
            isInitialScaling = false
        }
    }
    override fun applyScale(scaleFactor: Float, widthLimit: Int, heightLimit: Int) {
        var widthToScale = (width.toFloat() * scaleFactor).toInt()
        var heightToScale = (height.toFloat() * scaleFactor).toInt()

        if (widthToScale > widthLimit) widthToScale = widthLimit
        if (heightToScale > heightLimit) heightToScale = heightLimit

        if (widthToScale < minimumScalingWidth) widthToScale = minimumScalingWidth
        if (heightToScale < minimumScalingHeight) heightToScale = minimumScalingHeight

        updateLayoutParams {
            width = widthToScale
            height = heightToScale
        }
    }

    override fun applyFilter(colorFilter: ColorFilter) {
        this.colorFilter = colorFilter
    }

    override fun removeFilter() {
        clearColorFilter()
    }
}