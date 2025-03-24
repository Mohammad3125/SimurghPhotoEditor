package ir.manan.mananpic.utils.gesture.detectors.translation

interface OnTranslationDetector {
    fun onMoveBegin(detector: TranslationDetector): Boolean

    fun onMove(detector: TranslationDetector): Boolean

    fun onMoveEnded(detector: TranslationDetector)
}