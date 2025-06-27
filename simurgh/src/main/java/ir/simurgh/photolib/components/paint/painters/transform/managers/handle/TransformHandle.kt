package ir.simurgh.photolib.components.paint.painters.transform.managers.handle

/**
 * Represents a transformation handle with screen coordinates.
 * This data class stores the position of an interactive handle that users can drag
 * to transform child elements in the painting interface.
 *
 * @param x The horizontal screen coordinate of the transformation handle.
 * @param y The vertical screen coordinate of the transformation handle.
 */
data class TransformHandle(var x: Float, var y: Float)
