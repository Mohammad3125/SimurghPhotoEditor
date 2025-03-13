package ir.manan.mananpic.properties

import androidx.annotation.IntRange

interface Opacityable {
    fun getOpacity(): Int

    fun setOpacity(@IntRange(0, 255) opacity: Int)
}