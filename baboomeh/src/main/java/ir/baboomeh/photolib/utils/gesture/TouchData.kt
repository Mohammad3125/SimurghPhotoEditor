package ir.baboomeh.photolib.utils.gesture

data class TouchData(
    var ex: Float = 0f,
    var ey: Float = 0f,
    var dx: Float = 0f,
    var dy: Float = 0f,
    var dxSum: Float = 0f,
    var dySum: Float = 0f,
    var time: Long = 0,
    var pressure: Float = 1f
) {

    constructor(touchData: TouchData) : this() {
        set(touchData)
    }

    override fun toString(): String {
        return buildString {
            append("  ex ")
            append(ex)
            append("  ey ")
            append(ey)
            append("  dx ")
            append(dx)
            append("  dy ")
            append(dy)
            append("  time ")
            append(time)
            append("  pressure ")
            append(pressure)
        }
    }

    fun set(touchData: TouchData) {
        this.ex = touchData.ex
        this.ey = touchData.ey
        this.dx = touchData.dx
        this.dy = touchData.dy
        this.dxSum = touchData.dxSum
        this.dySum = touchData.dySum
        this.time = touchData.time
        this.pressure = touchData.pressure
    }
}