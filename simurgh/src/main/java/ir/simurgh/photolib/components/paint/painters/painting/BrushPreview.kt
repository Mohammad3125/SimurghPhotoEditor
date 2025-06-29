package ir.simurgh.photolib.components.paint.painters.painting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.view.doOnLayout
import ir.simurgh.photolib.R
import ir.simurgh.photolib.components.paint.painters.painting.brushes.Brush
import ir.simurgh.photolib.components.paint.painters.painting.engines.CachedCanvasEngine
import ir.simurgh.photolib.components.paint.smoothers.BezierLineSmoother
import ir.simurgh.photolib.components.paint.smoothers.LineSmoother
import ir.simurgh.photolib.utils.extensions.dp
import ir.simurgh.photolib.utils.gesture.TouchData
import kotlin.random.Random

/**
 * BrushPreview is a custom View component that renders a preview of how a brush will look when drawing.
 * It displays a sample stroke using the configured brush properties and settings.
 */
open class BrushPreview(context: Context, attributeSet: AttributeSet?) :
    View(context, attributeSet) {

    /** Secondary constructor that calls the primary constructor with null attributes. */
    constructor(context: Context) : this(context, null)

    init {
        // Enable hardware acceleration for better performance during drawing operations.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** Controls whether the checkerboard background pattern is displayed behind the brush preview. */
    open var isCheckerBoardEnabled = true
        set(value) {
            field = value
            // Trigger a redraw when the checkerboard visibility changes.
            invalidate()
        }

    /** The brush instance used to render the preview stroke. */
    open var brush: Brush? = null
        set(value) {
            field = value

            // Reinitialize the preview when a new brush is set.
            doOnLayout {
                initialize(
                    width,
                    height,
                    paddingLeft.toFloat(),
                    paddingRight.toFloat(),
                    paddingTop.toFloat(),
                    paddingBottom.toFloat()
                )
            }

            // Request a new render with the updated brush.
            requestRender()
        }

    /** Paint object configured with a checkerboard pattern shader for the background. */
    protected val checkerPatternPaint by lazy {
        Paint().apply {
            shader = BitmapShader(
                BitmapFactory.decodeResource(resources, R.drawable.checker),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )
        }
    }

    /**
     * Determines the size requirements for this view.
     * Sets default dimensions of 120dp width and 40dp height when not explicitly specified.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val measureWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measureHeight = MeasureSpec.getSize(heightMeasureSpec)

        // Determine final width based on measurement mode.
        val finalWidth = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> {
                // Use the exact size specified by parent.
                measureWidth
            }

            MeasureSpec.AT_MOST, MeasureSpec.UNSPECIFIED -> {
                // Use preferred size of 120dp, constrained by available space.
                val w = dp(120).toInt()
                if (w > measureWidth) measureWidth else w
            }

            else -> {
                // Fallback to minimum suggested width.
                suggestedMinimumWidth
            }
        }

        // Determine final height based on measurement mode.
        val finalHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> {
                // Use the exact size specified by parent.
                measureHeight
            }

            MeasureSpec.AT_MOST, MeasureSpec.UNSPECIFIED -> {
                // Use preferred size of 40dp, constrained by available space.
                val h = dp(40).toInt()
                if (h > measureHeight) measureHeight else h
            }

            else -> {
                // Fallback to minimum suggested height.
                suggestedMinimumHeight
            }
        }

        // Set the measured dimensions for this view.
        setMeasuredDimension(finalWidth, finalHeight)
    }

    /**
     * Called when the size or position of this view changes.
     * Reinitializes the brush preview when layout changes occur.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (changed) {
            // Recalculate preview when view dimensions change.
            initialize(
                width,
                height,
                paddingLeft.toFloat(),
                paddingRight.toFloat(),
                paddingTop.toFloat(),
                paddingBottom.toFloat()
            )
        }
    }

    /**
     * Initializes the brush preview by calculating stroke points and preparing cached properties.
     * Called when the view layout changes or a new brush is set.
     */
    protected open fun initialize(
        width: Int,
        height: Int,
        paddingLeft: Float,
        paddingRight: Float,
        paddingTop: Float,
        paddingBottom: Float
    ) {

        brush?.let { finalBrush ->

            // Calculate the path points for the preview stroke.
            calculatePoints(
                width.toFloat(),
                height.toFloat(),
                paddingLeft,
                paddingRight,
                paddingTop,
                paddingBottom
            )

            // Initialize cached properties for consistent rendering.
            initializeCachedProperties()

            // Process the calculated points with the brush.
            callPoints(finalBrush)
        }
    }

    /**
     * Renders the brush preview on the canvas.
     * Draws the checkerboard background (if enabled) and the brush stroke.
     */
    override fun onDraw(canvas: Canvas) {
        if (isCheckerBoardEnabled) {
            // Draw the checkerboard pattern background.
            canvas.drawPaint(checkerPatternPaint)
        }
        brush?.let {
            // Draw the brush stroke preview.
            drawPoints(canvas, it)
        }
    }

    /**
     * Requests a re-render of the brush preview.
     * Recalculates points and triggers a view invalidation.
     */
    open fun requestRender() {
        brush?.let { b ->
            doOnLayout {
                // Recalculate points with the current brush.
                callPoints(b)
                // Trigger a redraw of the view.
                invalidate()
            }
        }
    }

    companion object {

        /** Path object used to define the stroke curve for preview generation. */
        protected val path = Path()

        /** Canvas instance used for bitmap operations during snapshot creation. */
        protected val canvasBitmap by lazy {
            Canvas()
        }

        /** PathMeasure instance used to sample points along the preview path. */
        protected val pathMeasure by lazy {
            PathMeasure()
        }

        /** Cached canvas engine for efficient brush stroke rendering. */
        protected val engine by lazy { CachedCanvasEngine() }

        /** Line smoother that handles stroke smoothing and point generation. */
        protected var lineSmoother: LineSmoother = BezierLineSmoother().apply {
            onDrawPoint = object : LineSmoother.OnDrawPoint {
                override fun onDrawPoint(
                    ex: Float,
                    ey: Float,
                    angleDirection: Float,
                    totalDrawCount: Int,
                    isLastPoint: Boolean
                ) {
                    // Cache the generated points and direction angles for rendering.
                    cachePointHolder.add(ex)
                    cachePointHolder.add(ey)
                    cacheDirectionAngleHolder.add(angleDirection)
                }
            }
        }

        /** Cache for random rotation values used in brush rendering. */
        protected val rotationCache = mutableListOf<Float>()

        /** Cache for random scale values used in brush rendering. */
        protected val scaleCache = mutableListOf<Float>()

        /** Cache for random X scatter values used in brush rendering. */
        protected val scatterXCache = mutableListOf<Float>()

        /** Cache for random Y scatter values used in brush rendering. */
        protected val scatterYCache = mutableListOf<Float>()

        /** Counter for cycling through cached properties during rendering. */
        protected var cacheCounter = 0

        /** Size of the cache arrays for brush properties. */
        protected var cacheSizeInByte = 2000

        /** List holding X and Y coordinates of processed stroke points. */
        protected var cachePointHolder = mutableListOf<Float>()

        /** List holding direction angles for each processed point. */
        protected var cacheDirectionAngleHolder = mutableListOf<Float>()

        /** Array storing the raw path points for the preview stroke. */
        protected var points = FloatArray(320)

        /** Temporary array for holding single path point coordinates. */
        protected val pathPointHolder = FloatArray(2)

        /** Touch data object used for simulating brush strokes. */
        protected val touchData = TouchData()

        /**
         * Creates a bitmap snapshot of a brush stroke preview.
         * Generates a bitmap showing how the brush will appear when drawing.
         *
         * @param targetWidth Width of the output bitmap.
         * @param targetHeight Height of the output bitmap.
         * @param paddingHorizontal Horizontal padding around the stroke.
         * @param paddingVertical Vertical padding around the stroke.
         * @param brush The brush to render the preview for.
         * @param resolution Number of points to sample along the path (must be even).
         * @param lineSmoother The line smoother to use for stroke generation.
         * @param customPath Optional custom path to use instead of the default curve.
         * @return A bitmap containing the brush stroke preview.
         */
        fun createBrushSnapshot(
            targetWidth: Int,
            targetHeight: Int,
            paddingHorizontal: Float = 0f,
            paddingVertical: Float = 0f,
            brush: Brush,
            resolution: Int = 320,
            lineSmoother: LineSmoother = BezierLineSmoother(),
            customPath: Path? = null
        ): Bitmap {

            // Validate resolution parameter - must be even for proper point pairing.
            if (resolution % 2 != 0) {
                throw IllegalArgumentException("resolution should be divisible by 2")
            }

            // Validate minimum resolution requirement.
            if (resolution < 2) {
                throw IllegalArgumentException("resolution should be more than 2")
            }

            // Resize points array if resolution has changed.
            if (points.size != resolution) {
                points = FloatArray(resolution)
            }

            // Update line smoother if a different one is provided.
            if (this.lineSmoother != lineSmoother) {
                this.lineSmoother = lineSmoother

                this.lineSmoother.apply {
                    onDrawPoint = object : LineSmoother.OnDrawPoint {
                        override fun onDrawPoint(
                            ex: Float,
                            ey: Float,
                            angleDirection: Float,
                            totalDrawCount: Int,
                            isLastPoint: Boolean
                        ) {
                            // Cache points and angles generated by the smoother.
                            cachePointHolder.add(ex)
                            cachePointHolder.add(ey)
                            cacheDirectionAngleHolder.add(angleDirection)
                        }
                    }
                }
            }

            // Calculate the path points for the given dimensions.
            calculatePoints(
                targetWidth.toFloat(),
                targetHeight.toFloat(),
                paddingHorizontal,
                paddingHorizontal,
                paddingVertical,
                paddingVertical,
                customPath
            )

            // Initialize cached properties for consistent rendering.
            initializeCachedProperties()

            // Process the points with the brush to generate stroke data.
            callPoints(brush)

            // Create the output bitmap with specified dimensions.
            val snapshot = createBitmap(targetWidth, targetHeight)

            // Set up canvas to draw on the bitmap.
            canvasBitmap.setBitmap(snapshot)

            // Render the brush stroke onto the bitmap.
            drawPoints(canvasBitmap, brush)

            return snapshot
        }

        /**
         * Calculates points along a curved path for the brush preview.
         * Creates a bezier curve or uses a custom path if provided.
         *
         * @param targetWidth Total width of the drawing area.
         * @param targetHeight Total height of the drawing area.
         * @param paddingLeft Left padding from the edge.
         * @param paddingRight Right padding from the edge.
         * @param paddingTop Top padding from the edge.
         * @param paddingBottom Bottom padding from the edge.
         * @param customPath Optional custom path to use instead of default curve.
         */
        protected fun calculatePoints(
            targetWidth: Float,
            targetHeight: Float,
            paddingLeft: Float,
            paddingRight: Float,
            paddingTop: Float,
            paddingBottom: Float,
            customPath: Path? = null,
        ) {

            // Calculate drawable area considering padding.
            val widthF = targetWidth - paddingRight
            val heightF = targetHeight - paddingBottom

            if (customPath != null) {
                // Use the provided custom path.
                path.set(customPath)
            } else {
                // Create a default curved path for the preview.
                path.rewind()
                path.moveTo(paddingLeft, heightF * 0.5f + paddingTop)
                path.cubicTo(widthF * 0.25f, heightF, widthF * 0.75f, 0f, widthF, heightF * 0.5f)
            }

            // Measure the path to sample points along its length.
            pathMeasure.setPath(path, false)
            val length = pathMeasure.length

            // Calculate sampling parameters.
            val pointsHalf = (points.size / 2)
            val speed = length / pointsHalf
            var distance = 0f

            // Sample points along the path at regular intervals.
            repeat(pointsHalf) {
                pathMeasure.getPosTan(distance, pathPointHolder, null)

                val ind = it * 2

                distance += speed

                // Store X and Y coordinates in the points array.
                points[ind] = pathPointHolder[0]
                points[ind + 1] = pathPointHolder[1]
            }

            // Ensure the last point is exactly at the path end.
            pathMeasure.getPosTan(length, pathPointHolder, null)

            points[points.lastIndex - 1] = pathPointHolder[0]
            points[points.lastIndex] = pathPointHolder[1]
        }

        /**
         * Processes the calculated points through the brush and line smoother.
         * Simulates a complete brush stroke from start to end.
         *
         * @param brush The brush to use for processing the stroke points.
         */
        protected fun callPoints(brush: Brush) {

            // Reset cache and clear previous data.
            cacheCounter = 0
            cachePointHolder.clear()
            cacheDirectionAngleHolder.clear()

            // Get the starting point.
            val ex = points[0]
            val ey = points[1]

            // Temporarily disable brush smoothness for preview generation.
            val brushSmoothness = brush.smoothness
            brush.smoothness = 0f

            // Set up initial touch data.
            touchData.ex = ex
            touchData.ey = ey

            // Begin the stroke with the first point.
            lineSmoother.setFirstPoint(
                touchData,
                brush
            )

            engine.onMoveBegin(
                touchData, brush
            )

            // Process intermediate points along the path.
            for (i in 2..points.size - 2 step 2) {
                touchData.ex = points[i]
                touchData.ey = points[i + 1]

                // Add each point to the smoother.
                lineSmoother.addPoints(
                    touchData,
                    brush
                )

                // Process the point through the engine.
                engine.onMove(
                    touchData,
                    brush
                )
            }

            // Process the final point to complete the stroke.
            touchData.ex = points[points.lastIndex - 1]
            touchData.ey = points[points.lastIndex]

            lineSmoother.setLastPoint(
                touchData,
                brush
            )

            engine.onMoveEnded(
                touchData, brush
            )

            // Restore the original brush smoothness.
            brush.smoothness = brushSmoothness
        }

        /**
         * Renders the processed points onto the provided canvas.
         * Uses cached properties for consistent brush appearance.
         *
         * @param canvas The canvas to draw the brush stroke on.
         * @param brush The brush configuration to use for rendering.
         */
        protected fun drawPoints(canvas: Canvas, brush: Brush) {
            cacheCounter = 0

            // Draw each cached point using the engine.
            for (i in cachePointHolder.indices step 2) {

                // Apply cached random properties for brush variation.
                engine.cachedScatterX = scatterXCache[cacheCounter]
                engine.cachedScatterY = scatterYCache[cacheCounter]
                engine.cachedScale = scaleCache[cacheCounter]
                engine.cachedRotation = rotationCache[cacheCounter]

                // Draw the point with the configured properties.
                engine.draw(
                    cachePointHolder[i],
                    cachePointHolder[i + 1],
                    cacheDirectionAngleHolder[i / 2],
                    canvas,
                    brush,
                    1
                )

                // Cycle through the cache to reuse properties.
                if (++cacheCounter > cacheSizeInByte - 1) {
                    cacheCounter = 0
                }
            }
        }

        /**
         * Initializes cached random properties for brush rendering.
         * Creates consistent random values for rotation, scale, and scatter effects.
         */
        protected fun initializeCachedProperties() {
            if (rotationCache.isEmpty()) {

                // Clear any existing cached values.
                rotationCache.clear()
                scaleCache.clear()
                scatterXCache.clear()
                scatterYCache.clear()

                // Generate random values for brush variation effects.
                repeat(cacheSizeInByte) {
                    // Random rotation values from 0 to 1.
                    rotationCache.add(Random.nextInt(0, 100) / 100f)
                    // Random scale values from 0 to 1.
                    scaleCache.add(Random.nextInt(0, 100) / 100f)
                    // Random X scatter values from -1 to 1.
                    scatterXCache.add(Random.nextInt(-100, 100) / 100f)
                    // Random Y scatter values from -1 to 1.
                    scatterYCache.add(Random.nextInt(-100, 100) / 100f)
                }
            }
        }
    }
}
