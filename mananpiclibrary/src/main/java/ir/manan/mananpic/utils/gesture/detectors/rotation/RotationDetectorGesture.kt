package ir.manan.mananpic.utils.gesture.detectors.rotation

import ir.manan.mananpic.utils.gesture.gestures.Gesture

/**
 * Interface definition for a class that detects rotation.
 */
interface RotationDetectorGesture : Gesture {
    /**
     * Sets rotation step of rotation detector.
     * If it's set, then rotation increments or decrements step by step, for example
     * if user gestures the rotation clock-wise and step was 2.5f then it would increment by 2.5f and so on.
     */
    fun setRotationStep(rotationStep: Float)


    /**
    * Resets rotation of detector to a degree.
    */
    fun resetRotation(resetTo : Float)
}