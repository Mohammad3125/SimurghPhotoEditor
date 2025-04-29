package ir.baboomeh.photolib.components.paint.painters.coloring.flood

import android.graphics.Bitmap

interface FloodFill {
    fun fill(bitmap: Bitmap, ex: Int, ey: Int, replaceColor: Int, threshold: Float)
}