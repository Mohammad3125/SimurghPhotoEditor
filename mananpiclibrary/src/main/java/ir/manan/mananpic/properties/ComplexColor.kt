package ir.manan.mananpic.properties

interface ComplexColor {
    /**
     * Shifts the complex color with given amount.
     * @param dx Amount to shift in x axis.
     * @param dy Amount to shift in y axis.
     */
    fun shiftColor(dx: Float, dy: Float)

    /**
     * Scales the complex color around pivot points with given scale factor.
     * @param scaleFactor Total amount to scale. Scale of 1f means no scaling is applied.
     * @param px Point in x axis which complex color scales around.
     * @param py Point in y axis which complex color scales around.
     */
    fun scaleColor(scaleFactor: Float)

    /**
     * Rotates the complex color around given pivot points.
     * @param rotation exact rotation point.
     * @param px Point in x axis which complex color rotates around.
     * @param py Point in y axis which complex color rotates around.
     */
    fun rotateColor(rotation: Float)
}