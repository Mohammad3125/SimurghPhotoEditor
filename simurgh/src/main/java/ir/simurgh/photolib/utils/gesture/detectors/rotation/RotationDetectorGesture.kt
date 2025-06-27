package ir.simurgh.photolib.utils.gesture.detectors.rotation

import ir.simurgh.photolib.utils.gesture.gestures.Gesture

/**
 * Interface definition for a class that detects rotation.
 */
interface RotationDetectorGesture : Gesture {
    /**
     * Sets rotation step of rotation detector.
     * If it's set, then rotation increments or decrements step by step, for example
     * if user gestures the rotation clock-wise and step was 2.5f then it would increment by 2.5f and so on.
     * @param rotationStep The step size in degrees for quantized rotation. Set to 0 for continuous rotation.
     */
    fun setRotationStep(rotationStep: Float)

    /**
     * Resets rotation of detector to a degree.
     * This allows setting the baseline rotation angle for subsequent rotation calculations.
     * @param resetTo The angle in degrees to reset the rotation to.
     */
    fun resetRotation(resetTo: Float)
}
