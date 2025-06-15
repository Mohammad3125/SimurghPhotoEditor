package ir.baboomeh.photolib.properties

/**
 * Interface for tools that support masking operations.
 *
 * Masking tools can operate in two modes:
 * - **Additive mode**: Add to the mask (reveal areas)
 * - **Eraser mode**: Remove from the mask (hide areas)
 *
 * This interface is implemented by various masking tools such as:
 * - Brush-based masking tools
 * - Lasso selection tools used for masking
 * - Shape-based masking tools
 * - Pen tool for precise mask editing
 *
 * The eraser mode typically uses blend modes like DST_OUT to remove
 * content from the mask instead of adding to it.
 */
interface MaskTool {
    /**
     * Sets the eraser mode for this masking tool.
     *
     * When eraser mode is enabled, the tool should remove content from
     * the mask instead of adding to it. This is typically implemented
     * using blend modes like PorterDuff.Mode.DST_OUT.
     *
     * @param isEnabled true to enable eraser mode (subtractive),
     *                 false to enable normal mode (additive)
     */
    fun setEraserMode(isEnabled: Boolean)
}
