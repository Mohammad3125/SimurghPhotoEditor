package ir.simurgh.photolib.components.paint.painters.painter

enum class PainterMessage {
    /** Request the parent view to invalidate and redraw */
    INVALIDATE,

    /** Request the parent view to save the current state to history */
    SAVE_HISTORY,

    /** Request the parent view to cache the current layers */
    CACHE_LAYERS
}