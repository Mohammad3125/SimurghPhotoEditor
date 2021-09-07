package ir.maneditor.mananpiclibrary.properties

import androidx.annotation.ColorInt
import androidx.annotation.ColorRes

interface Colorable {

    fun applyColorResource(@ColorRes color: Int)

    fun applyColor(@ColorInt color: Int)
}