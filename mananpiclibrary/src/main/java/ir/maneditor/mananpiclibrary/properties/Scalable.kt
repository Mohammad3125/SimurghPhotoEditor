package ir.maneditor.mananpiclibrary.properties


/**
 * Interface definition for applying a scale on the implementor.
 */
interface Scalable {

    /**
     * Applies the scale with given scale factor.
     * @param scaleFactor Determines how much should a view scale.
     */
    fun applyScale(scaleFactor: Float)
}
