package ir.maneditor.mananpiclibrary.utils.gesture.gestures

interface OnRotateListener {
    fun onRotateBegin(initialDegree: Float): Boolean
    fun onRotate(degree: Float): Boolean
    fun onRotateEnded()
}