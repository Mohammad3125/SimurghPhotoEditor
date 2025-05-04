package ir.baboomeh.photolib.utils

import android.graphics.Rect
import android.graphics.RectF

fun RectF.perimeter(): Float = (width() + height()) * 2f

fun Rect.perimeter(): Int = (width() + height()) * 2