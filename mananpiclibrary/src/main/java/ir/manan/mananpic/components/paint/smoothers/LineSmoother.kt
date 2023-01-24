package ir.manan.mananpic.components.paint.smoothers

abstract class LineSmoother {
    var onDrawPoint: OnDrawPoint? = null

    abstract fun setFirstPoint(ex: Float, ey: Float, smoothness: Float, stampWidth: Float)
    abstract fun addPoints(ex: Float, ey: Float, smoothness: Float, stampWidth: Float)
    abstract fun setLastPoint(ex: Float, ey: Float, smoothness: Float, stampWidth: Float)

    interface OnDrawPoint {
        fun onDrawPoint(ex: Float, ey: Float)
    }

}