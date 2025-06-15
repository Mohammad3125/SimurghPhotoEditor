package ir.baboomeh.photolib.utils.history

/**
 * Interface defining a reversible operation that can be undone and redone.
 * Implementations should store all necessary data to reverse their operations
 * and restore the previous state when undo() is called.
 */
interface HistoryState {
    /**
     * Reverses the operation represented by this history state.
     * This method should restore the system to the state it was in
     * before this operation was performed.
     */
    fun undo()

    /**
     * Re-applies the operation represented by this history state.
     * This method should restore the system to the state it was in
     * after this operation was originally performed.
     */
    fun redo()
}
