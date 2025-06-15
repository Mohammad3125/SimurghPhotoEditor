package ir.baboomeh.photolib.utils.history

/**
 * Abstract base class for managing history operations like undo and redo.
 * This class provides the foundation for implementing different history management strategies
 * and handles the communication with listeners about history state changes.
 */
abstract class HistoryHandler {

    /**
     * Configuration options for history management.
     * Contains settings like maximum history size and other behavioral parameters.
     */
    class Options {
        /** Maximum number of history states to maintain. Default is 40. */
        var maximumHistorySize: Int = 40
    }

    /**
     * Functional interface for receiving notifications about history state changes.
     * Implementations should update UI elements like undo/redo buttons based on these notifications.
     */
    fun interface OnHistoryStateChanged {
        /**
         * Called when the history state changes (after undo, redo, add, or reset operations).
         */
        fun onStateChanged()
    }

    /** Listener for history state changes. */
    protected var onHistoryStateChanged: OnHistoryStateChanged? = null

    /**
     * Sets the callback listener for history state changes.
     * This is used internally by the history management system.
     * @param callback The listener to be notified of state changes.
     */
    internal fun setOnHistoryChanged(callback: OnHistoryStateChanged) {
        onHistoryStateChanged = callback
    }

    /**
     * Notifies the registered listener about history state changes.
     * Should be called after any operation that modifies the history state.
     */
    protected fun callOnHistoryChanged() {
        onHistoryStateChanged?.onStateChanged()
    }

    /** Configuration options for this history handler. */
    abstract var options: Options

    /**
     * Undoes the changes in stack.
     * @return current [HistoryState] that has been undone. Might be null.
     */
    internal abstract fun undo(): HistoryState?

    /**
     * Redoes the changes in stack.
     * @return current [HistoryState] that has been redone. Might be null.
     */
    internal abstract fun redo(): HistoryState?

    /**
     * Adds an [HistoryState] to stacks that later will be used to undo/redo the state.
     * @param state a [HistoryState] class.
     */
    internal abstract fun addState(state: HistoryState)

    /**
     * Resets the history by clearing all stored states.
     * This operation cannot be undone.
     */
    internal abstract fun reset()

    /**
     * Removes the most recent history state from the stack.
     * @return True if a state was successfully removed, false otherwise.
     */
    abstract fun popLastState(): Boolean

    /**
     * Removes the oldest history state from the stack.
     * This is typically used when history size limits are exceeded.
     * @return True if a state was successfully removed, false otherwise.
     */
    abstract fun popFirstState(): Boolean

    /**
     * Gets the number of operations that can be undone.
     * @return The count of available undo operations.
     */
    abstract fun getUndoSize(): Int

    /**
     * Gets the number of operations that can be redone.
     * @return The count of available redo operations.
     */
    abstract fun getRedoSize(): Int
}
