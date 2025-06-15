package ir.baboomeh.photolib.utils.history.handlers

import ir.baboomeh.photolib.utils.history.HistoryState

/**
 * A specialized history handler that allows undoing all operations including the initial state.
 * Unlike the base StackHistoryHandler, this implementation can undo the very first operation
 * and provides access to the complete history stack size.
 */
class StackFullRestoreHistoryHandler : StackHistoryHandler() {

    /**
     * Undoes the last operation by popping from the undo stack.
     * This implementation allows undoing all operations, including the initial state,
     * unlike the base class which preserves at least one state.
     * @return The history state that was undone, or null if no operations to undo.
     */
    override fun undo(): HistoryState? {
        // Check if there are any operations to undo.
        undoStack.takeIf { it.isNotEmpty() }?.apply {
            return pop().also { state ->
                // Execute the undo operation on the state.
                state.undo()
                // Move the undone state to redo stack for potential redo.
                redoStack.push(state)
                // Notify listeners about the history change.
                callOnHistoryChanged()
            }
        }
        return null
    }

    /**
     * Gets the total number of operations that can be undone.
     * This returns the actual stack size without any restrictions.
     * @return The total number of undo operations available.
     */
    override fun getUndoSize(): Int {
        return undoStack.size
    }
}
