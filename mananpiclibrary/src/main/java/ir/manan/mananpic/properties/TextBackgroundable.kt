package ir.manan.mananpic.properties

interface TextBackgroundable : Backgroundable {
    fun setBackgroundUnifiedState(isUnified: Boolean)

    fun isBackgroundUnified(): Boolean
}