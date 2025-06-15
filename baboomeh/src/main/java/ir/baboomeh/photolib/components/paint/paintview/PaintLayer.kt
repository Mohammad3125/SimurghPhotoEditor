package ir.baboomeh.photolib.components.paint.paintview

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * Represents a single layer in the layered paint system.
 *
 * Each paint layer contains:
 * - A bitmap that holds the actual pixel data
 * - Layer properties like opacity and blending mode
 * - Lock state to prevent accidental editing
 *
 * Paint layers work similarly to layers in professional image editing software,
 * allowing for non-destructive editing workflows where each layer can be
 * modified independently.
 *
 * Key features:
 * - **Opacity control**: Each layer can have individual opacity (0.0 to 1.0)
 * - **Blending modes**: Layers can blend with underlying layers using various modes
 * - **Lock protection**: Layers can be locked to prevent accidental modification
 * - **Cloning support**: Layers can be duplicated with or without bitmap copying
 *
 * Usage example:
 * ```kotlin
 * // Create a new layer
 * val layer = PaintLayer(bitmap = myBitmap, opacity = 0.8f)
 *
 * // Set blending mode
 * layer.blendingMode = PorterDuff.Mode.MULTIPLY
 *
 * // Lock the layer
 * layer.isLocked = true
 *
 * // Clone the layer
 * val clonedLayer = layer.clone(cloneBitmap = true)
 * ```
 *
 * @param bitmap The bitmap containing the layer's pixel data
 * @param isLocked Whether the layer is locked from editing
 * @param opacity The layer's opacity value (0.0 = transparent, 1.0 = opaque)
 */
data class PaintLayer(
    var bitmap: Bitmap,
    var isLocked: Boolean = false,
    var opacity: Float = 1f,
) {

    /**
     * The blending mode used when compositing this layer with underlying layers.
     *
     * Setting this property automatically updates [blendingModeObject] to the
     * corresponding PorterDuffXfermode instance, or null for SRC mode.
     *
     * Common blending modes:
     * - SRC: Normal blending (default)
     * - MULTIPLY: Multiply colors (darkening effect)
     * - SCREEN: Screen colors (lightening effect)
     * - OVERLAY: Overlay effect
     * - SRC_ATOP: Only draw where destination pixels exist
     *
     * @see PorterDuff.Mode for all available blending modes
     */
    var blendingMode = PorterDuff.Mode.SRC
        set(value) {
            field = value
            // Create PorterDuffXfermode object for non-SRC modes
            blendingModeObject = if (blendingMode != PorterDuff.Mode.SRC) {
                PorterDuffXfermode(blendingMode)
            } else {
                null
            }
        }

    /**
     * The PorterDuffXfermode object used for rendering this layer.
     *
     * This is automatically managed by [blendingMode] setter.
     * - null for normal (SRC) blending
     * - PorterDuffXfermode instance for other blending modes
     *
     * This object is used directly by Canvas.drawBitmap() calls.
     */
    var blendingModeObject: PorterDuffXfermode? = null

    /**
     * Creates a copy of this paint layer.
     *
     * @param cloneBitmap If true, creates a new bitmap copy. If false, references the same bitmap.
     *                   Use true for independent layers, false for shared bitmap scenarios.
     * @return A new PaintLayer with the same properties
     *
     * Note: When cloneBitmap is false, both layers will share the same bitmap instance,
     * so modifications to one will affect the other.
     */
    fun clone(cloneBitmap: Boolean): PaintLayer {
        return PaintLayer(
            if (cloneBitmap) bitmap.copy(
                bitmap.config ?: Bitmap.Config.ARGB_8888,
                true
            ) else bitmap,
            isLocked,
            opacity
        ).also { copied ->
            // Copy the blending mode (this also sets blendingModeObject)
            copied.blendingMode = blendingMode
        }
    }

    /**
     * Copies all properties from another paint layer to this one.
     *
     * This replaces all properties of this layer with those from the other layer:
     * - Bitmap reference
     * - Lock state
     * - Opacity
     * - Blending mode
     *
     * @param otherLayer The layer to copy properties from
     *
     * Note: This copies the bitmap reference, not the bitmap data itself.
     * Both layers will share the same bitmap after this operation.
     */
    fun set(otherLayer: PaintLayer) {
        bitmap = otherLayer.bitmap
        isLocked = otherLayer.isLocked
        opacity = otherLayer.opacity
        blendingMode = otherLayer.blendingMode
    }

    /**
     * Compares this paint layer with another for equality.
     *
     * Two paint layers are considered equal if they have:
     * - The same bitmap instance (reference equality)
     * - The same lock state
     * - The same opacity value
     * - The same blending mode
     *
     * @param other The object to compare with
     * @return true if the layers are equal, false otherwise
     */
    override fun equals(other: Any?): Boolean {
        other as PaintLayer
        return (bitmap === other.bitmap) &&
                (isLocked == other.isLocked) &&
                (opacity == other.opacity) &&
                (blendingMode == other.blendingMode)
    }

    /**
     * Generates a hash code for this paint layer.
     *
     * The hash code is based on:
     * - Bitmap instance hash
     * - Lock state
     * - Opacity value
     * - Blending mode
     *
     * @return Hash code for this layer
     */
    override fun hashCode(): Int {
        var result = bitmap.hashCode()
        result = 31 * result + isLocked.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + blendingMode.hashCode()
        return result
    }
}
