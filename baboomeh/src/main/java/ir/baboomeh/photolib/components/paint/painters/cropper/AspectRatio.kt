package ir.baboomeh.photolib.components.paint.painters.cropper

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
     * @param limitRect Rectangle that represents the limit bounds. (to limit dirty rect resizing)
     * @return Validated rectangle with correct aspect-ratio(if has any.)
     */
    abstract fun validate(
        rect: RectF,
        dirtyRect: RectF,
        limitRect: RectF
    ): RectF


    /**
     * Applies aspect ratio to width or height.
     * If final width or height exceeds the maximum amount, it normalizes them to fit inside bounds.
     * @param maxWidth Maximum width allowed for aspect-ratio.
     * @param maxHeight Maximum height allowed for aspect-ratio.
     * @return A [Pair]. First element is aspect-ratio applied width and second is aspect-ratio applied height.
     */
    abstract fun normalizeAspectRatio(
        maxWidth: Float,
        maxHeight: Float
    ): Pair<Float, Float>

}