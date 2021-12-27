package ir.manan.mananpic.properties

interface EditableComponent {
    fun applyRotation(degree: Float)

    fun applyScale(scaleFactor: Float)

    fun applyMovement(dx: Float, dy: Float)
}