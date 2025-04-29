package ir.baboomeh.photolib.properties

interface TextBackgroundable : Backgroundable {
    fun setBackgroundUnifiedState(isUnified: Boolean)

    fun isBackgroundUnified(): Boolean
}