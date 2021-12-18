package ir.maneditor.mananpiclibrary.components.imageviews

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
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
    private var imageRatio = 0f

    override fun setImageDrawable(drawable: Drawable?) {

        if (drawable !is BitmapDrawable) throw IllegalArgumentException(
            "Type of drawable should only be BitmapDrawable"
        )
        super.setImageDrawable(drawable)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        if (bm == null) return

        // If current drawable is not null that means we already had image in image view
        // and we should not update the bounds and ratio.
        if (drawable != null) {
            // Update aspect ratio.
            imageRatio = bm.width.toFloat() / bm.height.toFloat()
            // Resize to fit into new aspect ratio,
            applyScale(1f, bm.width, bm.height)
        }

        super.setImageBitmap(bm)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (isInitialScaling && drawable != null) {

            minimumScalingWidth = width / 4
            minimumScalingHeight = height / 4

            val viewDrawable = drawable
            imageRatio =
                viewDrawable.intrinsicWidth.toFloat() / viewDrawable.intrinsicHeight.toFloat()

            isInitialScaling = false
        }
    }

    override fun applyScale(scaleFactor: Float, widthLimit: Int, heightLimit: Int) {

        var widthToScale: Int
        var heightToScale: Int

        // If ratio is less than 1 then height of view is greater than it's width.
        if (imageRatio < 1f) {
            heightToScale = (height.toFloat() * scaleFactor).toInt()

            if (heightToScale > heightLimit)
                heightToScale = heightLimit

            if (heightToScale < minimumScalingHeight)
                heightToScale = minimumScalingHeight


            widthToScale = (heightToScale.toFloat() * imageRatio).toInt()

        } else {
            widthToScale = (width.toFloat() * scaleFactor).toInt()

            if (widthToScale > widthLimit)
                widthToScale = widthLimit

            if (widthToScale < minimumScalingWidth)
                widthToScale = minimumScalingWidth

            heightToScale = (widthToScale.toFloat() / imageRatio).toInt()
        }
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

    /**
     * Returns original bitmap/drawing of current imageview.
     * This is helpful if user wants to perform processing on image with higher quality (even if imageview is scaled down.)
     * @throws IllegalStateException if drawable is null.
     */
    fun getOriginalBitmap(
    ): Bitmap {
        if (drawable == null) throw IllegalStateException("Drawable should not be null")

        return (drawable as BitmapDrawable).bitmap
    }
}