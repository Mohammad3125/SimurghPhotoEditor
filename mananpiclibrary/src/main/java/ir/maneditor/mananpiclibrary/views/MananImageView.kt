package ir.maneditor.mananpiclibrary.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.updateLayoutParams
import ir.maneditor.mananpiclibrary.properties.Filterable
import ir.maneditor.mananpiclibrary.properties.Scalable
import ir.maneditor.mananpiclibrary.properties.Texturable
import ir.maneditor.mananpiclibrary.utils.invalidateAfter

class MananImageView(context: Context, attr: AttributeSet?) : AppCompatImageView(context, attr),
    Scalable, Filterable, Texturable {
    constructor(context: Context) : this(context, null)

    private var isInitialScaling = true
    private var maximumScalingWidth = 0
    private var maximumScalingHeight = 0
    private var minimumScalingWidth = 0
    private var minimumScalingHeight = 0
    private lateinit var imageParent: ViewGroup

    private var bitmapTexture: Bitmap? = null

    private val bitmapTexturePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG)
    }

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

    override fun applyFilter(colorFilter: ColorFilter) {
        this.colorFilter = colorFilter
    }

    override fun removeFilter() {
        clearColorFilter()
    }

    override fun applyTexture(bitmap: Bitmap, opacity: Float) {
        applyTexture(bitmap, Shader.TileMode.REPEAT, opacity)
    }

    override fun applyTexture(bitmap: Bitmap, tileMode: Shader.TileMode, opacity: Float) {
        invalidateAfter {
            bitmapTexturePaint.colorFilter =
                ColorMatrixColorFilter(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        0f, 0f, 0f, opacity, 0f
                    )
                )
            bitmapTexture = bitmap
        }
    }

    override fun removeTexture() {
        invalidateAfter {
            bitmapTexturePaint.apply {
                colorFilter = null
                bitmapTexture = null
            }
        }
    }


    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)
        if (bitmapTexture != null)
            canvas!!.apply {
                drawBitmap(bitmapTexture!!, 0f, 0f, bitmapTexturePaint)
            }
    }


}