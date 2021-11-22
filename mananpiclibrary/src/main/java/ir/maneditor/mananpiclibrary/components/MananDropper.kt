package ir.maneditor.mananpiclibrary.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.updateLayoutParams
import ir.maneditor.mananpiclibrary.R
import ir.maneditor.mananpiclibrary.utils.dp
import kotlin.math.min

/**
 * Component that let user get color from a bitmap.
 */
class MananDropper(context: Context, attributeSet: AttributeSet?) :
    FrameLayout(context, attributeSet) {

    constructor(context: Context) : this(context, null)

    // Image that later we will pick color from.
    private val imageview by lazy {
        AppCompatImageView(context, null).apply {
            adjustViewBounds = true
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            )
        }
    }

    var imageviewGravity: Int = Gravity.CENTER
        set(value) {
            imageview.updateLayoutParams<LayoutParams> { gravity = value }
            field = value
        }


    // Paint for drawing enlarged bitmap.
    private val enlargedBitmapPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }

    // Matrix to later enlarge the bitmap with.
    private val enlargedBitmapMatrix by lazy { Matrix() }

    // Path to clip a circle and drawing enlarge bitmap in it.
    private val enlargedBitmapPath by lazy { Path() }

    // Ring around circle showing color of current pixel that user is pointing at.
    private val colorRingPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    // Stroke width of color ring.
    var colorRingStrokeWidth = 0f
        set(value) {
            colorRingPaint.strokeWidth = value
            field = value
        }

    // Inner circle radius for enlarged bitmap.
    var circlesRadius = 0f
        set(value) {
            field = value
            invalidate()
        }

    // Paint for drawing cross in center of circle for better indicating selected pixels.
    private val centerCrossPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    // Stroke width of center cross.
    var centerCrossStrokeWidth = 1.dp
        set(value) {
            centerCrossPaint.strokeWidth = value
            field = value
        }

    // Color of center cross.
    var centerCrossColor = Color.WHITE
        set(value) {
            centerCrossPaint.color = value
            field = value
        }

    // Determines the size of cross lines (not to be confused with stroke width).
    var centerCrossLineSize = 4.dp

    // Later indicating current position of user fingers.
    private var dropperXPosition = 0f
    private var dropperYPosition = 0f

    // Determines if view should view circle (when user lifts his/her finger, circle should disappear.)
    private var showCircle = false

    // This offset is used for times where current circle exceeds the bounds of image so we offset it.
    private var offsetY = 0f

    // This variable will later be used to determine how much the circle that shows enlarged bitmap
    // should offset from user finger.
    private var circleOffsetFromCenter = 0f

    // Lazily create bitmap to view inside the circle.
    private lateinit var bitmapToViewInCircle: Bitmap

    // Will later determine if user has inserted new bitmap in Dropper or not.
    private var isNewBitmap = false

    /**
     * The last color that was detected by dropper.
     */
    private var lastSelectedColor: Int = 0

    /**
     * Last color selected before user lifts his/her finger from screen.
     */
    private var onLastColorDetected: ((color: Int) -> Unit)? = null

    /**
     * Called everytime a new color get detected by dropper.
     */
    private var onColorDetected: ((color: Int) -> Unit)? = null

    /**
     * Interface for last color that get detected.
     */
    private var interfaceOnLastColorDetected: OnLastColorDetected? = null

    /**
     * Interface that get invoked when any color change happen.
     */
    private var interfaceOnColorDetected: OnColorDetected? = null

    init {

        // Add listener in case bitmap changes we have to wait for imageview's layout bounds
        // to change to be able to create new bitmap.
        imageview.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (isNewBitmap) {
                bitmapToViewInCircle = getImageViewBitmap()

                // If circles radius weren't set in xml file, then calculate it based on minimum dimension of
                // current view / 10.
                circlesRadius =
                    min((imageview.measuredWidth), (imageview.measuredHeight)) * 0.25f

                // If color ring stroke width wasn't set in xml file, then calculate it based on current radius
                // of circle.
                colorRingStrokeWidth = circlesRadius * 0.1f

                // This variable will later offset the circle.
                // We offset the circle to be visible to user which
                // area of picture they're using otherwise their finger
                // will block it.
                circleOffsetFromCenter = (circlesRadius * 1.5f)

                isNewBitmap = false
            }
        }

        context.theme.obtainStyledAttributes(attributeSet, R.styleable.FrameDropper, 0, 0).run {
            try {
                colorRingStrokeWidth =
                    getDimension(
                        R.styleable.FrameDropper_colorRingStrokeWidth,
                        colorRingStrokeWidth
                    )

                circlesRadius =
                    getDimension(R.styleable.FrameDropper_colorRingCircleRadius, circlesRadius)


                centerCrossStrokeWidth = getDimension(
                    R.styleable.FrameDropper_centerCrossStrokeWidth,
                    centerCrossStrokeWidth
                )

                centerCrossColor =
                    getColor(R.styleable.FrameDropper_centerCrossColor, centerCrossColor)

                centerCrossLineSize = getDimension(
                    R.styleable.FrameDropper_centerCrossLineSize,
                    centerCrossLineSize
                )

                setImageResource(getResourceId(R.styleable.FrameDropper_src, 0))

                imageviewGravity = getInteger(R.styleable.FrameDropper_imageGravity, Gravity.CENTER)

            } finally {
                recycle()
            }
        }
        // We will have drawing.
        setWillNotDraw(false)
        // Add imageview into view.
        addView(imageview)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // If circles radius weren't set in xml file, then calculate it based on minimum dimension of
        // current view / 10.
        if (circlesRadius == 0f)
            circlesRadius =
                min((imageview.measuredWidth), (imageview.measuredHeight)) * 0.25f

        // If color ring stroke width wasn't set in xml file, then calculate it based on current radius
        // of circle.
        if (colorRingStrokeWidth == 0f)
            colorRingStrokeWidth = circlesRadius * 0.1f

        // This variable will later offset the circle.
        // We offset the circle to be visible to user which
        // area of picture they're using otherwise their finger
        // will block it.
        circleOffsetFromCenter = (circlesRadius * 1.5f)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event!!.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                // Get position of current x and y.
                dropperXPosition = event.x
                dropperYPosition = event.y

                // Limit point to do not go further than view's dimensions.
                if (dropperYPosition > imageview.bottom) dropperYPosition =
                    imageview.bottom.toFloat()

                if (dropperXPosition > imageview.right) dropperXPosition =
                    imageview.right.toFloat()

                if (dropperXPosition < imageview.x) dropperXPosition =
                    imageview.x

                if (dropperYPosition < imageview.y) dropperYPosition =
                    imageview.y

                // Offset the Circle in case circle exceeds the height of layout y coordinate.
                offsetY = if (dropperYPosition - circleOffsetFromCenter * 1.5f <= 0f) {
                    (height - dropperYPosition)
                } else 0f

                // Translate the enlarged bitmap by it's x and y position inside parent because
                // parent might have some padding. Also we do this because the pointers position
                // is based on parent's dimensions and we shift it to avoid shifting in enlarged
                // bitmap.
                enlargedBitmapMatrix.setTranslate(imageview.x, imageview.y)

                // Scale the image to be more visible to user.
                enlargedBitmapMatrix.postScale(
                    2f,
                    2f,
                    dropperXPosition,
                    dropperYPosition + circleOffsetFromCenter - offsetY
                )

                // If user's finger is on the image, then show the circle.
                showCircle = true

                if (event.actionMasked == MotionEvent.ACTION_DOWN)
                    performClick()

                // Invalidate to draw content on the screen.
                invalidate()
                // Return true to show interest in consuming event.
                true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                // Call interfaces.
                onLastColorDetected?.invoke(lastSelectedColor)
                interfaceOnLastColorDetected?.onLastColorDetected(lastSelectedColor)

                // If user lifts his/her finger then don't show the circle anymore.
                showCircle = false
                // Reset offset.
                offsetY = 0f
                // Invalidate to draw content on the screen.
                invalidate()
                // We're no longer interested in consuming event.
                false
            }
            else -> {
                false
            }

        }
    }

    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)
        if (showCircle) {
            canvas!!.run {
                val bitmapToView = bitmapToViewInCircle
                val bitmapWidth = bitmapToView.width
                val bitmapHeight = bitmapToView.height

                var xPositionWithPadding = (dropperXPosition - imageview.x).toInt()
                var yPositionWithPadding = (dropperYPosition - imageview.y).toInt()

                // Validate the x position to not exceed bitmap dimension (otherwise it throws exception.)
                if (xPositionWithPadding >= bitmapWidth)
                    xPositionWithPadding = bitmapWidth - 1

                // Validate the y position to not exceed bitmap dimension (otherwise it throws exception.)
                if (yPositionWithPadding >= bitmapHeight)
                    yPositionWithPadding = bitmapHeight - 1

                // Finally get the color of current selected pixel (the pixel user pointing at) and set
                // The ring color to that.
                lastSelectedColor = bitmapToView.getPixel(
                    xPositionWithPadding,
                    yPositionWithPadding
                )
                colorRingPaint.color = lastSelectedColor

                // Call interfaces.
                interfaceOnColorDetected?.onColorDetected(lastSelectedColor)
                onColorDetected?.invoke(lastSelectedColor)

                // Variables to store positions of drawing to reuse them.
                val xPositionForDrawings = dropperXPosition
                val yPositionForDrawings = dropperYPosition - circleOffsetFromCenter + offsetY

                // Draw ring that indicates color of current pixel.
                drawCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius,
                    colorRingPaint
                )

                // Add circle to path object to later clip it and draw bitmap in it.
                enlargedBitmapPath.addCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius - colorRingPaint.strokeWidth * 0.47f,
                    Path.Direction.CW
                )

                // Save the canvas to current state.
                save()

                // Clip the circle.
                clipPath(enlargedBitmapPath)

                // Draw the background color in case there are transparent areas so it will be better visible to user.
                drawColor(Color.WHITE)

                // Finally draw the enlarged bitmap inside the circle.
                drawBitmap(bitmapToView, enlargedBitmapMatrix, enlargedBitmapPaint)

                // Restore the canvas to the saved point to no mess following drawing operations.
                restore()

                // Clear any paths.
                enlargedBitmapPath.rewind()

                // Calculates the position of center cross lines.
                var crossStartX = dropperXPosition - centerCrossLineSize
                if (crossStartX < 0f) crossStartX = 0f

                var crossEndX = dropperXPosition + centerCrossLineSize
                if (crossEndX > x + width) crossEndX = x + width

                // Draw horizontal cross in center of circle.
                drawLine(
                    crossStartX,
                    yPositionForDrawings,
                    crossEndX,
                    yPositionForDrawings,
                    centerCrossPaint
                )

                // Calculates the position of center cross lines.
                var crossStartY = dropperYPosition - centerCrossLineSize
                if (crossStartY < 0f) crossStartY = 0f

                var crossEndY = dropperYPosition + centerCrossLineSize
                if (crossEndY > y + height) crossEndY = y + height

                // Draw vertical cross in center of circle.
                drawLine(
                    xPositionForDrawings,
                    crossStartY - circleOffsetFromCenter + offsetY,
                    xPositionForDrawings,
                    crossEndY - circleOffsetFromCenter + offsetY,
                    centerCrossPaint
                )
            }
        }
    }

    /**
     * Sets imageview's bitmap.
     */
    fun setImageBitmap(bitmap: Bitmap) {
        imageview.setImageBitmap(bitmap)
        isNewBitmap = true
    }

    /**
     * Sets imageview's image resource.
     * @param resId resource id.
     */
    fun setImageResource(@DrawableRes resId: Int) {
        imageview.setImageResource(resId)
        isNewBitmap = true
    }

    /**
     * Returns bitmap from imageview via it's drawable. This method resizes the returned drawable into
     * view's bounds.
     * @return resized [Bitmap] that user is going to pick color from.
     */
    private fun getImageViewBitmap(): Bitmap {
        val mDrawable = imageview.drawable
        return if (mDrawable != null && mDrawable is BitmapDrawable)
            Bitmap.createScaledBitmap(mDrawable.bitmap, imageview.width, imageview.height, false)
        else Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    /**
     * Set listener that get invoked everytime that color by color dropper get changed.
     */
    fun setOnColorDetected(listener: (Int) -> Unit) {
        onColorDetected = listener
    }

    /**
     * Set listener that get invoked everytime that color by color dropper get changed.
     * @param listener Interface [OnColorDetected]
     */
    fun setOnColorDetected(listener: OnColorDetected) {
        interfaceOnColorDetected = listener
    }

    /**
     * Set listener that get invoked before user lifts his/her finger from screen.
     * This listener returns the last color that was selected before user lifts his/her finger
     * up from screen.
     */
    fun setOnLastColorDetected(listener: (Int) -> Unit) {
        onLastColorDetected = listener
    }

    /**
     * Set listener that get invoked before user lifts his/her finger from screen.
     * This listener returns the last color that was selected before user lifts his/her finger
     * up from screen.
     * @param listener Interface [OnLastColorDetected]
     */
    fun setOnLastColorDetected(listener: OnLastColorDetected) {
        interfaceOnLastColorDetected = listener
    }


    /**
     * Interface definition of callback that get invoked when any color changes by
     * dropper get detected.
     */
    interface OnColorDetected {
        fun onColorDetected(color: Int)
    }

    /**
     * Interface definition of callback that get invoked when user lifts his/her finger
     * up. This callback should be used when you want to get the last color that was
     * detected by dropper.
     */
    interface OnLastColorDetected {
        fun onLastColorDetected(color: Int)
    }
}