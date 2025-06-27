package ir.simurgh.photolib.utils.history.handlers

import ir.simurgh.photolib.utils.history.HistoryHandler
import ir.simurgh.photolib.utils.history.HistoryState
import java.util.Stack

/**
 * A stack-based implementation of history handler that manages undo and redo operations.
 * This class uses two stacks to maintain the history of operations: one for undo operations
 * and another for redo operations. It preserves at least one state in the undo stack
 * to maintain a baseline state.
 */
open class StackHistoryHandler : HistoryHandler() {
    /** Stack containing states that can be undone. */
    protected val undoStack = Stack<HistoryState>()

    /** Stack containing states that can be redone. */
    protected val redoStack = Stack<HistoryState>()

    /** Configuration options for this history handler. */
    override var options = Options()

    /**
     * Undoes the last operation by popping from the undo stack.
     * This implementation preserves at least one state in the undo stack,
     * preventing complete restoration to an empty state.
     * @return The history state that was undone, or null if cannot undo.
     */
    override fun undo(): HistoryState? {
        // Ensure at least one state remains in the undo stack.
        undoStack.takeIf { it.size > 1 }?.apply {
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
     * Redoes the last undone operation by popping from the redo stack.
     * @return The history state that was redone, or null if no operations to redo.
     */
    override fun redo(): HistoryState? {
        // Check if there are any operations to redo.
        redoStack.takeIf { it.isNotEmpty() }?.apply {
            return pop().also { state ->
                // Execute the redo operation on the state.
                state.redo()
                // Move the redone state back to undo stack.
                undoStack.push(state)
                // Notify listeners about the history change.
                callOnHistoryChanged()
            }
        }
        return null
    }

    /**
     * Adds a new history state to the undo stack.
     * This operation clears the redo stack since new operations invalidate
     * previously undone operations. Also enforces maximum history size limits.
     * @param state The history state to add.
     */
    override fun addState(state: HistoryState) {
        // Skip adding states if maximum history size is set to 0.
        if (options.maximumHistorySize == 0) {
            return
        }

        // Clear redo stack as new operations invalidate redo history.
        redoStack.clear()
        // Add the new state to undo stack.
        undoStack.push(state)

        // Remove oldest states if history size limit is exceeded.
        if (isHistorySizeExceeded()) {
            popFirstState()
        }

        // Notify listeners about the history change.
        callOnHistoryChanged()
    }

    /**
     * Clears both undo and redo stacks, resetting the history to empty state.
     */
    override fun reset() {
        undoStack.clear()
        redoStack.clear()
        // Notify listeners about the history change.
        callOnHistoryChanged()
    }

    /**
     * Removes the most recent state from the undo stack.
     * @return True if a state was removed, false if the stack was empty.
     */
    override fun popLastState(): Boolean {
        undoStack.apply {
            if (isNotEmpty()) {
                pop()
                return true
            }
        }
        return false
    }

    /**
     * Removes the oldest state from the undo stack.
     * This is typically used when the history size limit is exceeded.
     * @return True if a state was removed, false if removal failed.
     */
    override fun popFirstState(): Boolean {
        return undoStack.removeAt(0) != null
    }

    /**
     * Checks if the current history size exceeds the configured maximum.
     * @return True if the history size limit is exceeded.
     */
    protected open fun isHistorySizeExceeded() = undoStack.size > options.maximumHistorySize

    /**
     * Gets the number of operations that can be undone.
     * This implementation subtracts 1 to preserve at least one state in the stack.
     * @return The number of undo operations available.
     */
    override fun getUndoSize(): Int {
        return (undoStack.size - 1).coerceIn(0, undoStack.size)
    }

    /**
     * Gets the number of operations that can be redone.
     * @return The number of redo operations available.
     */
    override fun getRedoSize(): Int {
        return redoStack.size
    }
}
