package ir.maneditor.mananpiclibrary.utils.gesture.gestures

interface OnRotateListener {
    fun onRotateBegin(initialDegree: Float)
    fun onRotate(degree: Float)
    fun onRotateEnded()
}