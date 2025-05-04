package ir.baboomeh.photolib.utils.gesture.detectors.translation

interface OnTranslationDetector {
    fun onMoveBegin(detector: TranslationDetector): Boolean

    fun onMove(detector: TranslationDetector): Boolean

    fun onMoveEnded(detector: TranslationDetector)
}