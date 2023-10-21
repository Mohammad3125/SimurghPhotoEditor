package ir.manan.mananpic.components.paint.painters.coloring.flood

import android.graphics.Bitmap

interface FloodFill {
    fun fill(bitmap: Bitmap, ex: Int, ey: Int, replaceColor: Int, threshold: Float)
}