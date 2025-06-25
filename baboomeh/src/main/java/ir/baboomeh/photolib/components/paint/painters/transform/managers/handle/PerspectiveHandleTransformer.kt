package ir.baboomeh.photolib.components.paint.painters.transform.managers.handle

import android.content.Context
import android.graphics.Matrix
import ir.baboomeh.photolib.components.paint.painters.transform.Child
import ir.baboomeh.photolib.utils.extensions.dp
import ir.baboomeh.photolib.utils.extensions.isNearPoint
import ir.baboomeh.photolib.utils.gesture.TouchData

open class PerspectiveHandleTransformer(
    val context: Context,
) : HandleTransformer {

    /**
     * Enables free transform mode, allowing individual corner manipulation for advanced transformations.
     * When disabled, transformations maintain aspect ratio and shape consistency.
     * When enabled, each corner can be moved independently for perspective-like effects.
     */
    open var isPerspectiveHandling: Boolean = true

    override var selectedHandle: TransformHandle? = null

    protected var secondaryHandle: TransformHandle? = null

    protected var tertiaryHandle: TransformHandle? = null

    protected var isOnlyMoveX: Boolean = false

    protected var topLeftHandle: TransformHandle = TransformHandle(0f, 0f)
    protected var topCenterHandle: TransformHandle = TransformHandle(0f, 0f)
    protected var topRightHandle: TransformHandle = TransformHandle(0f, 0f)
    protected var rightCenterHandle: TransformHandle = TransformHandle(0f, 0f)
    protected var bottomRightHandle: TransformHandle = TransformHandle(0f, 0f)
    protected var bottomCenterHandle: TransformHandle = TransformHandle(0f, 0f)
    protected var bottomLeftHandle: TransformHandle = TransformHandle(0f, 0f)
    protected var leftCenterHandle: TransformHandle = TransformHandle(0f, 0f)

    /** The touch range in pixels for detecting handle interactions and determining touch sensitivity. */
    open var touchRange = context.dp(24)
    protected val mappingMatrix by lazy {
        Matrix()
    }

    protected val mappedPoints by lazy {
        FloatArray(8)
    }


    override fun createHandles(child: Child) {
        populateAllHandles(child)
    }

    override fun findHandle(child: Child, touchData: TouchData) {
        populateAllHandles(child)

        secondaryHandle = null
        tertiaryHandle = null

        selectedHandle = getAllHandles(child)
            .filter { touchData.isNearPoint(it.x, it.y, touchRange) }
            .minByOrNull {
                val dx = touchData.ex - it.x
                val dy = touchData.ey - it.y
                dx * dx + dy * dy
            }

        when (selectedHandle) {
            topCenterHandle -> {
                secondaryHandle = topLeftHandle
                tertiaryHandle = topRightHandle
                isOnlyMoveX = false
            }

            rightCenterHandle -> {
                secondaryHandle = topRightHandle
                tertiaryHandle = bottomRightHandle
                isOnlyMoveX = true
            }

            bottomCenterHandle -> {
                secondaryHandle = bottomRightHandle
                tertiaryHandle = bottomLeftHandle
                isOnlyMoveX = false
            }

            leftCenterHandle -> {
                secondaryHandle = bottomLeftHandle
                tertiaryHandle = topLeftHandle
                isOnlyMoveX = true
            }

            else -> {
                if (!isPerspectiveHandling) {
                    selectedHandle = null
                }
            }
        }
    }

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

    protected open fun populateAllHandles(child: Child) {
        child.mapSizeChangePointsByMatrices(mappedPoints)
        populateCenterHandles(mappedPoints)

        child.mapMeshPointsByMatrices(mappingMatrix, mappedPoints)
        populateCornerHandles(mappedPoints)
    }


    override fun transform(child: Child, touchData: TouchData) {
        populateAllHandles(child)

        mappedPoints[0] = touchData.dx
        mappedPoints[1] = touchData.dy

        child.transformationMatrix.invert(mappingMatrix)
        mappingMatrix.mapVectors(mappedPoints)

        val deltaX = if (isOnlyMoveX || isPerspectiveHandling) mappedPoints[0] else 0f
        val deltaY = if (!isOnlyMoveX || isPerspectiveHandling) mappedPoints[1] else 0f

        mappedPoints[0] = deltaX
        mappedPoints[1] = deltaY

        child.transformationMatrix.mapVectors(mappedPoints)

        if (isPerspectiveHandling) {
            selectedHandle?.apply {
                x = touchData.ex
                y = touchData.ey
            }
        }

        applyTouchDataToHandle(secondaryHandle)

        applyTouchDataToHandle(tertiaryHandle)

        mappedPoints[0] = topLeftHandle.x
        mappedPoints[1] = topLeftHandle.y
        mappedPoints[2] = topRightHandle.x
        mappedPoints[3] = topRightHandle.y
        mappedPoints[4] = bottomRightHandle.x
        mappedPoints[5] = bottomRightHandle.y
        mappedPoints[6] = bottomLeftHandle.x
        mappedPoints[7] = bottomLeftHandle.y

        child.invertMapMeshPointsByMatrices(mappingMatrix, mappedPoints)

        mappedPoints.copyInto(child.meshPoints)
        child.calculatePolyMatrix()
    }

    override fun getAllHandles(child: Child): Collection<TransformHandle> {
        populateAllHandles(child)

        return if (isPerspectiveHandling) {
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
            listOf(
                topCenterHandle,
                rightCenterHandle,
                bottomCenterHandle,
                leftCenterHandle
            )
        }
    }

    private fun applyTouchDataToHandle(handle: TransformHandle?) {
        handle?.apply {
            x += mappedPoints[0]
            y += mappedPoints[1]
        }
    }
}
