package ir.manan.mananpic.properties

import androidx.annotation.ColorInt

interface Colorable {

    fun changeColor(@ColorInt color: Int)

    @ColorInt
    fun getColor() : Int
}