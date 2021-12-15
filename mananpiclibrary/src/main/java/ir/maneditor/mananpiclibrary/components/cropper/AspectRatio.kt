package ir.maneditor.mananpiclibrary.components.cropper

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
     * @param limitRect Rectangle that represents the limit bounds (to limit dirty rect resizing)
     * @return Validated rectangle with correct aspect-ratio(if has any.)
     */
    abstract fun validate(
        rect: RectF,
        dirtyRect: RectF,
        limitRect: RectF
    ): RectF

}