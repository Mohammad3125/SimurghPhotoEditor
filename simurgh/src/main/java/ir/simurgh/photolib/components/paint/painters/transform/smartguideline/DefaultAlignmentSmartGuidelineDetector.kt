package ir.simurgh.photolib.components.paint.painters.transform.smartguideline

import android.graphics.Matrix
import android.graphics.RectF
import ir.simurgh.photolib.components.paint.painters.transform.Child
import ir.simurgh.photolib.utils.extensions.setMaximumRect
import ir.simurgh.photolib.utils.matrix.SimurghMatrix
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

open class DefaultAlignmentSmartGuidelineDetector() : SmartAlignmentGuidelineDetector {

    /**
     * Holds lines for smart guidelines.
     */
    private val smartGuidelineHolder = arrayListOf<Guideline>()

    /** Temporary rectangle for various calculations to avoid object allocation. */
    private val tempRect by lazy {
        RectF()
    }

    /** Array holding mapped mesh points for drawing after transformation calculations. */
    private val mappedMeshPoints by lazy {
        FloatArray(8)
    }

    protected val mappingMatrix by lazy {
        SimurghMatrix()
    }

    /**
     * Sets flags of smart guideline to customize needed smart guidelines,
     * for example if user sets [Guidelines.CENTER_X] and [Guidelines.BOTTOM_BOTTOM], only these
     * guidelines would be detected.
     * If [Guidelines.ALL] is set then all flags would bet set to 1 indicating they are all enabled.
     * ### NOTE: Flags should OR each other to create desired output:
     *      setFlags(LEFT_LEFT.or(RIGHT_LEFT).or(CENTER_X)))
     *      setFlags(LEFT_LEFT | RIGHT_LEFT | CENTER_X)
     * @see Guidelines
     */
    /**
     * Sets flags for smart guideline detection and rendering.
     * This method controls which types of alignment guidelines are active.
     *
     * Guidelines can be combined using bitwise OR operations:
     * ```
     * setSmartGuidelineFlags(Guidelines.LEFT_LEFT or Guidelines.CENTER_X)
     * ```
     *
     * @param flags Bitwise combination of guideline flags from Guidelines class.
     * @see [ir.baboomeh.photolib.components.paint.painters.transform.smartguideline.Guidelines]
     */
    override var alignmentFlags: Int = Guidelines.NONE
        set(value) {
            // If flag has the ALL in it then store the maximum int value in flag holder to indicate
            // that all of flags has been set, otherwise set it to provided flags.
            field = if (value.and(Guidelines.ALL) != 0) Int.MAX_VALUE else value
        }

    /** The acceptable distance in pixels for triggering smart guideline detection. */
    override var alignmentAcceptableDistance: Float = 2f

    override fun detectAlignmentGuidelines(
        selectedTransformable: Child,
        otherTransformable: List<Child>,
        pageBounds: RectF
    ): AlignmentGuidelineResult? {

        // Clear the last result.
        smartGuidelineHolder.clear()

        // Get flags to determine if we should use corresponding guideline or not.
        val isLeftLeftEnabled = alignmentFlags.and(Guidelines.LEFT_LEFT) != 0
        val isLeftRightEnabled = alignmentFlags.and(Guidelines.LEFT_RIGHT) != 0
        val isRightLeftEnabled = alignmentFlags.and(Guidelines.RIGHT_LEFT) != 0
        val isRightRightEnabled =
            alignmentFlags.and(Guidelines.RIGHT_RIGHT) != 0
        val isTopTopEnabled = alignmentFlags.and(Guidelines.TOP_TOP) != 0
        val isTopBottomEnabled = alignmentFlags.and(Guidelines.TOP_BOTTOM) != 0
        val isBottomTopEnabled = alignmentFlags.and(Guidelines.BOTTOM_TOP) != 0
        val isBottomBottomEnabled =
            alignmentFlags.and(Guidelines.BOTTOM_BOTTOM) != 0
        val isCenterXEnabled = alignmentFlags.and(Guidelines.CENTER_X) != 0
        val isCenterYEnabled = alignmentFlags.and(Guidelines.CENTER_Y) != 0

        selectedTransformable.mapMeshPointsByMatrices(mappingMatrix, mappedMeshPoints)
        tempRect.setMaximumRect(mappedMeshPoints)

        // Stores total value that selected component should shift in each axis
        var totalToShiftX = 0f
        var totalToShiftY = 0f

        // Remove selected component from list of children (because we don't need to find smart guideline for
        // selected component which is a undefined behaviour) and then map each bounds of children to get exact
        // location of points and then add page's bounds to get smart guidelines for page too.
        otherTransformable.map { otherChild ->
            RectF().apply {
                otherChild.mapMeshPointsByMatrices(mappingMatrix, mappedMeshPoints)
                setMaximumRect(mappedMeshPoints)
            }
        }.plus(pageBounds).forEach { childBounds ->

            // Calculate distance between two centers in x axis.
            val centerXDiff = childBounds.centerX() - tempRect.centerX()
            val centerXDiffAbs = abs(centerXDiff)

            // Calculate distance between two centers in y axis.
            val centerYDiff = childBounds.centerY() - tempRect.centerY()
            val centerYDiffAbs = abs(centerYDiff)

            // If absolute value of difference two center x was in range of acceptable distance,
            // then store total difference to later shift the component.
            if (centerXDiffAbs <= alignmentAcceptableDistance && isCenterXEnabled) {
                totalToShiftX = centerXDiff
            }
            if (centerYDiffAbs <= alignmentAcceptableDistance && isCenterYEnabled) {
                totalToShiftY = centerYDiff
            }

            // Calculate distance between two lefts.
            val leftToLeft = childBounds.left - tempRect.left
            val leftToLeftAbs = abs(leftToLeft)

            // Calculate distance between two other component left and selected component right.
            val leftToRight = childBounds.left - tempRect.right
            val leftToRightAbs = abs(leftToRight)

            // Calculate distance between two rights.
            val rightToRight = childBounds.right - tempRect.right
            val rightToRightAbs = abs(rightToRight)

            // Calculate distance between other component right and selected component left.
            val rightToLeft = childBounds.right - tempRect.left
            val rightToLeftAbs = abs(rightToLeft)

            // If left to left of two components was less than left two right and
            // if the lesser value was in acceptable range then set total shift amount
            // in x axis to that value.
            // If we are currently centering in x direction then any of these
            // side should not be calculated or be smart guided.
            if (totalToShiftX != centerXDiff) {
                if (leftToLeftAbs < leftToRightAbs) {
                    if (leftToLeftAbs <= alignmentAcceptableDistance && isLeftLeftEnabled) {
                        totalToShiftX = leftToLeft
                    }
                } else if (leftToRightAbs < leftToLeftAbs) {
                    if (leftToRightAbs <= alignmentAcceptableDistance && isLeftRightEnabled) {
                        totalToShiftX = leftToRight
                    }
                }
                // If right to right of two components was less than right to left of them,
                // Then check if we haven't set the total shift amount so far, if either we didn't
                // set any value to shift so far or current difference is less than current
                // total shift amount, then set total shift amount to the right to right difference.
                if (rightToRightAbs < rightToLeftAbs) {
                    if (rightToRightAbs <= alignmentAcceptableDistance && isRightRightEnabled) {
                        if (totalToShiftX == 0f) {
                            totalToShiftX = rightToRight
                        } else if (rightToRightAbs < abs(totalToShiftX)) {
                            totalToShiftX = rightToRight
                        }
                    }
                } else if (rightToLeftAbs < rightToRightAbs) {
                    if (rightToLeftAbs <= alignmentAcceptableDistance && isRightLeftEnabled) {
                        if (totalToShiftX == 0f) {
                            totalToShiftX = rightToLeft
                        } else if (rightToLeftAbs < abs(totalToShiftX)) {
                            totalToShiftX = rightToLeft
                        }
                    }
                }
            }

            val topToTop = childBounds.top - tempRect.top
            val topToTopAbs = abs(topToTop)
            val topToBottom = childBounds.top - tempRect.bottom
            val topToBottomAbs = abs(topToBottom)

            val bottomToBottom = childBounds.bottom - tempRect.bottom
            val bottomToBottomAbs = abs(bottomToBottom)
            val bottomToTop = childBounds.bottom - tempRect.top
            val bottomToTopAbs = abs(bottomToTop)

            if (totalToShiftY != centerYDiff) {
                if (topToTopAbs < topToBottomAbs) {
                    if (topToTopAbs <= alignmentAcceptableDistance && isTopTopEnabled) {
                        totalToShiftY = topToTop
                    }
                } else if (topToBottomAbs < topToTopAbs && isTopBottomEnabled) {
                    if (topToBottomAbs <= alignmentAcceptableDistance) {
                        totalToShiftY = topToBottom
                    }
                }

                if (bottomToBottomAbs < bottomToTopAbs) {
                    if (bottomToBottomAbs <= alignmentAcceptableDistance && isBottomBottomEnabled) {
                        if (totalToShiftY == 0f) {
                            totalToShiftY = bottomToBottom
                        } else if (bottomToBottomAbs < abs(totalToShiftY)) {
                            totalToShiftY = bottomToBottom
                        }
                    }
                } else if (bottomToTopAbs < bottomToBottomAbs) {
                    if (bottomToTopAbs <= alignmentAcceptableDistance && isBottomTopEnabled) {
                        if (totalToShiftY == 0f) {
                            totalToShiftY = bottomToTop
                        } else if (bottomToTopAbs < abs(totalToShiftY)) {
                            totalToShiftY = bottomToTop
                        }
                    }
                }
            }

            tempRect.offset(totalToShiftX, totalToShiftY)

            // Calculate the minimum and maximum amount of two axes
            // because we want to draw a line from leftmost to rightmost
            // and topmost to bottommost component.
            val minTop = min(tempRect.top, childBounds.top)
            val maxBottom = max(tempRect.bottom, childBounds.bottom)

            val minLeft = min(tempRect.left, childBounds.left)
            val maxRight = max(tempRect.right, childBounds.right)

            smartGuidelineHolder.run {

                val isNotPage = childBounds !== pageBounds

                // Draw a line on left side of selected component if two lefts are the same
                // or right of other component is same to left of selected component
                if (totalToShiftX == leftToLeft || totalToShiftX == rightToLeft) {
                    add(
                        Guideline(
                            floatArrayOf(tempRect.left, minTop, tempRect.left, maxBottom),
                            isNotPage
                        )
                    )
                }
                // Draw a line on right side of selected component if left side of other
                // component is right side of selected component or two rights are the same.
                if (totalToShiftX == leftToRight || totalToShiftX == rightToRight) {
                    add(
                        Guideline(
                            floatArrayOf(tempRect.right, minTop, tempRect.right, maxBottom),
                            isNotPage
                        )
                    )
                }

                // Draw a line on other component top if it's top is same as
                // selected component top or bottom of selected component is same as
                // top of other component.
                if (totalToShiftY == topToTop || totalToShiftY == topToBottom) {
                    add(
                        Guideline(
                            floatArrayOf(
                                minLeft,
                                childBounds.top,
                                maxRight,
                                childBounds.top
                            ), isNotPage
                        )
                    )
                }
                // Draw a line on other component bottom if bottom of it is same as
                // selected component's top or two bottoms are the same.
                if (totalToShiftY == bottomToTop || totalToShiftY == bottomToBottom) {
                    add(
                        Guideline(
                            floatArrayOf(
                                minLeft,
                                childBounds.bottom,
                                maxRight,
                                childBounds.bottom
                            ), isNotPage
                        )
                    )
                }

                // Finally draw a line from center of each component to another.
                if (totalToShiftX == centerXDiff || totalToShiftY == centerYDiff) {
                    if (isNotPage) {
                        add(
                            Guideline(
                                floatArrayOf(
                                    tempRect.centerX(),
                                    tempRect.centerY(),
                                    childBounds.centerX(),
                                    childBounds.centerY()
                                ), true
                            )
                        )
                    } else {
                        if (totalToShiftX == centerXDiff) {
                            add(
                                Guideline(
                                    floatArrayOf(
                                        tempRect.centerX(),
                                        pageBounds.top,
                                        tempRect.centerX(),
                                        pageBounds.bottom
                                    ), false
                                )
                            )
                        }

                        if (totalToShiftY == centerYDiff) {
                            add(
                                Guideline(
                                    floatArrayOf(
                                        pageBounds.left,
                                        tempRect.centerY(),
                                        pageBounds.right,
                                        tempRect.centerY()
                                    ), false
                                )
                            )
                        }
                    }
                }
            }
        }

        return AlignmentGuidelineResult(smartGuidelineHolder.toList(), Matrix().apply {
            setTranslate(totalToShiftX, totalToShiftY)
        })

    }
}