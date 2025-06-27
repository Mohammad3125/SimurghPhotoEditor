package ir.simurgh.photolib.properties

interface UnifiedBackgroundable : Backgroundable {
    fun setBackgroundUnifiedState(isUnified: Boolean)

    fun isBackgroundUnified(): Boolean
}