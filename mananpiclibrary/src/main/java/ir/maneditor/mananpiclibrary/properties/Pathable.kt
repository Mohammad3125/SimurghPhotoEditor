package ir.maneditor.mananpiclibrary.properties

/**
 * An abstract class for defining views that support path effect like [android.widget.TextView] and etc...
 */
interface Pathable {

    /**
     * A function for applying the path effect on certain views that are capable of.
     * @param on This parameter means the times that path is visible and drawing.
     * @param off This parameter means the times that path is invisible and not drawing (spacing in a sense).
     * @param radius This parameter represents that radius of path effect around corner o views like TextView (default is 0)
     * @param strokeWidth This parameter represents the width of path effect (default is 1).
     */
    fun applyPath(on: Float, off: Float, radius: Float = 0f, strokeWidth: Float = 1f)

    /**
     * An Abstract function for applying the path effect on certain views that are capable of.
     * @param onAndOff This parameter defines the times that a path effect is equally visible and not visible.
     * @param radius This parameter represents that radius of path effect around corner of views like TextView (default is 0).
     * @param strokeWidth This parameter represents the width of path effect (default is 1).
     * @see applyPath
     */
    fun applyPath(onAndOff: Float, radius: Float = 0f, strokeWidth: Float = 1f)


    /**
     * This function defines the way that a view should remove it's path effect.
     */
    fun removePath()


}