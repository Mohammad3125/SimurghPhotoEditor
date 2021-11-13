package ir.maneditor.mananpiclibrary.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.BitmapDrawable
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
    private var imageRatio = 0f
    private lateinit var imageParent: ViewGroup

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        imageParent = parent as ViewGroup
    }

    override fun setImageBitmap(bm: Bitmap?) {
        // Update aspect ratio.
        imageRatio = bm!!.width.toFloat() / bm.height.toFloat()

        // Resize to fit into new aspect ratio,
        applyScale(1f, bm.width, bm.height)
        super.setImageBitmap(bm)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (isInitialScaling) {
            minimumScalingWidth = measuredWidth / 4
            minimumScalingHeight = measuredHeight / 4
            imageRatio = measuredWidth.toFloat() / measuredHeight.toFloat()
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

    fun drawContentToBitmap(
        bitmapConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {

        val mDrawable = drawable
        if (mDrawable is BitmapDrawable)
            return mDrawable.bitmap

        val bitmap =
            Bitmap.createBitmap(mDrawable.intrinsicWidth, mDrawable.intrinsicHeight, bitmapConfig)

        mDrawable.draw(Canvas(bitmap))

        return bitmap
    }
}