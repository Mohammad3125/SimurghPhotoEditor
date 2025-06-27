package ir.simurgh.photolib.components.paint.painters.cropper

import ir.simurgh.photolib.components.paint.painters.cropper.HandleBar.BOTTOM
import ir.simurgh.photolib.components.paint.painters.cropper.HandleBar.BOTTOM_LEFT
import ir.simurgh.photolib.components.paint.painters.cropper.HandleBar.BOTTOM_RIGHT
import ir.simurgh.photolib.components.paint.painters.cropper.HandleBar.LEFT
import ir.simurgh.photolib.components.paint.painters.cropper.HandleBar.RIGHT
import ir.simurgh.photolib.components.paint.painters.cropper.HandleBar.TOP
import ir.simurgh.photolib.components.paint.painters.cropper.HandleBar.TOP_LEFT
import ir.simurgh.photolib.components.paint.painters.cropper.HandleBar.TOP_RIGHT


/**
 * Enumeration of crop frame handle positions used for interactive resizing.
 *
 * This enum defines all possible handle locations on a crop rectangle, allowing
 * users to resize the crop area by dragging different parts of the frame:
 *
 * **Corner Handles:** Allow resizing in both width and height simultaneously
 * - [TOP_LEFT]: Upper-left corner handle
 * - [TOP_RIGHT]: Upper-right corner handle
 * - [BOTTOM_LEFT]: Lower-left corner handle
 * - [BOTTOM_RIGHT]: Lower-right corner handle
 *
 * **Edge Handles:** Allow resizing in a single dimension
 * - [TOP]: Top edge handle (height adjustment)
 * - [BOTTOM]: Bottom edge handle (height adjustment)
 * - [LEFT]: Left edge handle (width adjustment)
 * - [RIGHT]: Right edge handle (width adjustment)
 *
 * The handle positions are used by the cropping system to determine:
 * - Which type of resize operation to perform
 * - How to maintain aspect ratios during resize
 * - Which visual feedback to provide to users
 * - Touch area detection for gesture handling
 */
enum class HandleBar {
    /** Top-left corner handle for diagonal resizing */
    TOP_LEFT,

    /** Top edge handle for vertical resizing */
    TOP,

    /** Top-right corner handle for diagonal resizing */
    TOP_RIGHT,

    /** Bottom-left corner handle for diagonal resizing */
    BOTTOM_LEFT,

    /** Bottom edge handle for vertical resizing */
    BOTTOM,

    /** Bottom-right corner handle for diagonal resizing */
    BOTTOM_RIGHT,

    /** Left edge handle for horizontal resizing */
    LEFT,

    /** Right edge handle for horizontal resizing */
    RIGHT
}
