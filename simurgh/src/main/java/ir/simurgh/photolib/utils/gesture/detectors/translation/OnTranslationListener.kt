package ir.simurgh.photolib.utils.gesture.detectors.translation

/**
 * Interface for receiving callbacks about translation (pan/move) gesture events.
 * This interface provides methods to handle the start, progress, and end of translation gestures.
 */
interface OnTranslationListener {
    /**
     * Called when a translation gesture begins.
     * This is typically triggered when the user first touches the screen.
     * @param detector The translation detector that detected the gesture begin.
     * @return True to indicate interest in receiving further events, false otherwise.
     */
    fun onMoveBegin(detector: TranslationDetector): Boolean

    /**
     * Called continuously during a translation gesture.
     * This method provides the current translation data through the detector parameter.
     * @param detector The translation detector containing current gesture data.
     * @return True to continue receiving move events, false to stop.
     */
    fun onMove(detector: TranslationDetector): Boolean

    /**
     * Called when the translation gesture ends.
     * This is typically triggered when the user lifts their finger(s) from the screen.
     * @param detector The translation detector that detected the gesture end.
     */
    fun onMoveEnded(detector: TranslationDetector)
}
