package ir.maneditor.mananpiclibrary.views.cropper

import android.graphics.RectF

/**
 * An abstract class for applying aspect ratio to [MananCropper].
 */
abstract class AspectRatio {

    /**
     * Resizes the cropper's rectangle with given handler and differences in X and Y coordinates.
     * @param rect The current cropper rectangle.
     * @param handleBar Determines which handle bar has been selected.
     * @param dx The difference in X coordinate.
     * @param dy The difference in Y coordinate.
     * @return Resized rectangle.
     */
    abstract fun resize(rect: RectF, handleBar: HandleBar?, dx: Float, dy: Float): RectF

    /**
     * Validates size of cropper rectangle to be in specified aspect ratio.
     * This class checks if rectangles width and height is between minimum width,height and maximum width,height.
     * @param rect Current cropper rectangle.
     * @param dirtyRect Rectangle that has been modified(resized).
     * @param minWidth Minimum width allowed for view.
     * @param maxWidth Maximum width allowed for view.
     * @param minHeight Minimum height allowed for rectangle.
     * @param maxHeight Maximum height allowed for rectangle.
     * @return Validated rectangle with correct aspect-ratio(if has any.)
     */
    abstract fun validate(
        rect: RectF,
        dirtyRect: RectF,
        minWidth: Float,
        maxWidth: Float,
        minHeight: Float,
        maxHeight: Float
    ): RectF

}