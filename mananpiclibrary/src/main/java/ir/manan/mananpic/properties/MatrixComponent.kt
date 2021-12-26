package ir.manan.mananpic.properties

import android.graphics.RectF

interface MatrixComponent {

    fun reportBound(): RectF

    fun reportRotation(): Float

    fun reportPivotX(): Float

    fun reportPivotY(): Float
}