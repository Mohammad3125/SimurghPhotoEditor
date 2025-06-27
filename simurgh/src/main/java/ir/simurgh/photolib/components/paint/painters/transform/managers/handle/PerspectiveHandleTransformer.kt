package ir.simurgh.photolib.components.paint.painters.transform.managers.handle

import android.content.Context
import android.graphics.Matrix
import ir.simurgh.photolib.components.paint.painters.painter.MessageChannel
import ir.simurgh.photolib.components.paint.painters.painter.PainterMessage
import ir.simurgh.photolib.components.paint.painters.transform.Child
import ir.simurgh.photolib.utils.extensions.dp
import ir.simurgh.photolib.utils.extensions.isNearPoint
import ir.simurgh.photolib.utils.gesture.TouchData

/**
 * Implementation of HandleTransformer that provides perspective and standard transformation capabilities.
 * This class manages transformation handles for child elements, supporting both normal transformation
 * and free perspective transformations.
 *
 * @param context Android context for accessing resources and display metrics.
 * @param messageChannel Communication channel for sending painter messages and invalidation requests.
 */
open class PerspectiveHandleTransformer(
    val context: Context,
    val messageChannel: MessageChannel
) : HandleTransformer {

    /**
     * Enables free transform mode, allowing individual corner manipulation for advanced transformations.
     * When enabled, each corner can be moved independently for perspective-like effects.
     */
    open var isPerspectiveHandling: Boolean = false
        set(value) {
            field = value
            // Trigger canvas invalidation to reflect the mode change which has more handles to draw (corner handles.)
            messageChannel.onSendMessage(PainterMessage.INVALIDATE)
        }

    /**
     * The currently selected transformation handle being manipulated by the user.
     * Null when no handle is selected or active.
     */
    override var selectedHandle: TransformHandle? = null

    /**
     * Secondary handle that moves in conjunction with the primary selected handle.
     * Used for maintaining geometric relationships during transformations.
     */
    protected var secondaryHandle: TransformHandle? = null

    /**
     * Tertiary handle that moves in conjunction with the primary selected handle.
     * Used for maintaining geometric relationships during transformations.
     */
    protected var tertiaryHandle: TransformHandle? = null

    /**
     * Flag indicating whether the current transformation should only affect the X-axis.
     * Used for constraining movement to horizontal transformations.
     */
    protected var isOnlyMoveX: Boolean = false

    // Corner transformation handles positioned at the four corners of the element.
    /** Top-left corner transformation handle. */
    protected var topLeftHandle: TransformHandle = TransformHandle(0f, 0f)

    /** Top-center edge transformation handle. */
    protected var topCenterHandle: TransformHandle = TransformHandle(0f, 0f)

    /** Top-right corner transformation handle. */
    protected var topRightHandle: TransformHandle = TransformHandle(0f, 0f)

    /** Right-center edge transformation handle. */
    protected var rightCenterHandle: TransformHandle = TransformHandle(0f, 0f)

    /** Bottom-right corner transformation handle. */
    protected var bottomRightHandle: TransformHandle = TransformHandle(0f, 0f)

    /** Bottom-center edge transformation handle. */
    protected var bottomCenterHandle: TransformHandle = TransformHandle(0f, 0f)

    /** Bottom-left corner transformation handle. */
    protected var bottomLeftHandle: TransformHandle = TransformHandle(0f, 0f)

    /** Left-center edge transformation handle. */
    protected var leftCenterHandle: TransformHandle = TransformHandle(0f, 0f)

    /**
     * The touch range in pixels for detecting handle interactions and determining touch sensitivity.
     */
    open var touchRange = context.dp(24)

    /**
     * Matrix used for coordinate transformations and mappings between different coordinate spaces.
     * Lazily initialized to avoid unnecessary object creation.
     */
    protected val mappingMatrix by lazy {
        Matrix()
    }

    /**
     * Reusable float array for storing mapped coordinate points during transformations.
     * Lazily initialized to avoid unnecessary object creation.
     */
    protected val mappedPoints by lazy {
        FloatArray(8)
    }

    /**
     * Creates and initializes all transformation handles for the specified child element.
     *
     * @param child The child element for which to create transformation handles.
     */
    override fun createHandles(child: Child) {
        populateAllHandles(child)
    }

    /**
     * Finds and selects the transformation handle closest to the touch position.
     * Sets up secondary and tertiary handles based on the selected primary handle
     * to maintain proper geometric relationships during transformation.
     *
     * @param child The child element being transformed.
     * @param touchData The touch gesture data containing position information.
     */
    override fun findHandle(child: Child, touchData: TouchData) {
        populateAllHandles(child)

        // Reset secondary handles.
        secondaryHandle = null
        tertiaryHandle = null

        // Find the closest handle to the touch position.
        selectedHandle = getAllHandles(child)
            .filter { touchData.isNearPoint(it.x, it.y, touchRange) }
            .minByOrNull {
                val dx = touchData.ex - it.x
                val dy = touchData.ey - it.y
                dx * dx + dy * dy // Calculate squared distance for performance.
            }

        // Configure secondary and tertiary handles based on the selected handle.
        when (selectedHandle) {
            topCenterHandle -> {
                secondaryHandle = topLeftHandle
                tertiaryHandle = topRightHandle
                isOnlyMoveX = false // Allow Y-axis movement.
            }

            rightCenterHandle -> {
                secondaryHandle = topRightHandle
                tertiaryHandle = bottomRightHandle
                isOnlyMoveX = true // Restrict to X-axis movement.
            }

            bottomCenterHandle -> {
                secondaryHandle = bottomRightHandle
                tertiaryHandle = bottomLeftHandle
                isOnlyMoveX = false // Allow Y-axis movement.
            }

            leftCenterHandle -> {
                secondaryHandle = bottomLeftHandle
                tertiaryHandle = topLeftHandle
                isOnlyMoveX = true // Restrict to X-axis movement.
            }

            else -> {
                // For corner handles, only allow selection in perspective mode.
                if (!isPerspectiveHandling) {
                    selectedHandle = null
                }
            }
        }
    }

    /**
     * Populates the center edge handles with their current positions based on the child's boundaries.
     *
     * @param array Float array containing the mapped coordinate points.
     */
    protected open fun populateCenterHandles(array: FloatArray) {
        topCenterHandle.x = array[0]
        topCenterHandle.y = array[1]
        rightCenterHandle.x = array[2]
        rightCenterHandle.y = array[3]
        bottomCenterHandle.x = array[4]
        bottomCenterHandle.y = array[5]
        leftCenterHandle.x = array[6]
        leftCenterHandle.y = array[7]
    }

    /**
     * Populates the corner handles with their current positions based on the child's mesh points.
     *
     * @param array Float array containing the mapped coordinate points.
     */
    protected open fun populateCornerHandles(array: FloatArray) {
        topLeftHandle.x = array[0]
        topLeftHandle.y = array[1]
        topRightHandle.x = array[2]
        topRightHandle.y = array[3]
        bottomRightHandle.x = array[4]
        bottomRightHandle.y = array[5]
        bottomLeftHandle.x = array[6]
        bottomLeftHandle.y = array[7]
    }

    /**
     * Updates all transformation handles with their current positions by mapping
     * the child's coordinate points through its transformation matrices.
     *
     * @param child The child element whose handles need to be updated.
     */
    protected open fun populateAllHandles(child: Child) {
        // Update center handles based on size change points.
        child.mapSizeChangePointsByMatrices(mappedPoints)
        populateCenterHandles(mappedPoints)

        // Update corner handles based on mesh points.
        child.mapMeshPointsByMatrices(mappingMatrix, mappedPoints)
        populateCornerHandles(mappedPoints)
    }

    /**
     * Applies transformation to the child element based on the selected handle and touch movement.
     * Updates the child's mesh points and recalculates its transformation matrix.
     *
     * @param child The child element to transform.
     * @param touchData The touch gesture data containing movement delta information.
     */
    override fun transform(child: Child, touchData: TouchData) {
        populateAllHandles(child)

        // Map touch delta to local coordinate space.
        mappedPoints[0] = touchData.dx
        mappedPoints[1] = touchData.dy

        child.transformationMatrix.invert(mappingMatrix)
        mappingMatrix.mapVectors(mappedPoints)

        // Apply movement constraints based on handle type and mode.
        val deltaX = if (isOnlyMoveX || isPerspectiveHandling) mappedPoints[0] else 0f
        val deltaY = if (!isOnlyMoveX || isPerspectiveHandling) mappedPoints[1] else 0f

        mappedPoints[0] = deltaX
        mappedPoints[1] = deltaY

        child.transformationMatrix.mapVectors(mappedPoints)

        // In perspective mode, directly set handle position to touch position.
        if (isPerspectiveHandling) {
            selectedHandle?.apply {
                x = touchData.ex
                y = touchData.ey
            }
        }

        // Apply movement to secondary and tertiary handles.
        applyTouchDataToHandle(secondaryHandle)
        applyTouchDataToHandle(tertiaryHandle)

        // Update mesh points with new handle positions.
        mappedPoints[0] = topLeftHandle.x
        mappedPoints[1] = topLeftHandle.y
        mappedPoints[2] = topRightHandle.x
        mappedPoints[3] = topRightHandle.y
        mappedPoints[4] = bottomRightHandle.x
        mappedPoints[5] = bottomRightHandle.y
        mappedPoints[6] = bottomLeftHandle.x
        mappedPoints[7] = bottomLeftHandle.y

        // Convert handle positions back to local coordinate space.
        child.invertMapMeshPointsByMatrices(mappingMatrix, mappedPoints)

        // Update child's mesh and recalculate transformation matrix.
        mappedPoints.copyInto(child.meshPoints)
        child.calculatePolyMatrix()
    }

    /**
     * Returns all available transformation handles for the specified child element.
     * The returned handles depend on the current transformation mode.
     *
     * @param child The child element whose handles to retrieve.
     * @return Collection of transformation handles - all 8 handles in perspective mode,
     *         or only the 4 center handles in standard mode.
     */
    override fun getAllHandles(child: Child): Collection<TransformHandle> {
        populateAllHandles(child)

        return if (isPerspectiveHandling) {
            // Return all handles for full perspective transformation.
            listOf(
                topLeftHandle,
                topCenterHandle,
                topRightHandle,
                rightCenterHandle,
                bottomRightHandle,
                bottomCenterHandle,
                bottomLeftHandle,
                leftCenterHandle
            )
        } else {
            // Return only center handles for constrained transformation.
            listOf(
                topCenterHandle,
                rightCenterHandle,
                bottomCenterHandle,
                leftCenterHandle
            )
        }
    }

    /**
     * Applies the calculated movement delta to the specified transformation handle.
     *
     * @param handle The transformation handle to update, or null if no handle should be updated.
     */
    private fun applyTouchDataToHandle(handle: TransformHandle?) {
        handle?.apply {
            x += mappedPoints[0]
            y += mappedPoints[1]
        }
    }
}
