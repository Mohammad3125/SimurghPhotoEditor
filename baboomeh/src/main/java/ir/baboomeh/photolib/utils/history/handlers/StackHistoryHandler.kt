package ir.baboomeh.photolib.utils.history.handlers

import ir.baboomeh.photolib.utils.history.HistoryHandler
import ir.baboomeh.photolib.utils.history.HistoryState
import java.util.Stack

open class StackHistoryHandler : HistoryHandler() {
    protected val undoStack = Stack<HistoryState>()
    protected val redoStack = Stack<HistoryState>()

    override var options = Options()

    override fun undo(): HistoryState? {
        undoStack.takeIf { it.size > 1 }?.apply {
            return pop().also { state ->
                state.undo()
                redoStack.push(state)
                callOnHistoryChanged()
            }
        }
        return null
    }

    override fun redo(): HistoryState? {
        redoStack.takeIf { it.isNotEmpty() }?.apply {
            return pop().also { state ->
                state.redo()
                undoStack.push(state)
                callOnHistoryChanged()
            }
        }
        return null
    }

    override fun addState(state: HistoryState) {
        if (options.maximumHistorySize == 0) {
            return
        }

        redoStack.clear()
        undoStack.push(state)

        if (isHistorySizeExceeded()) {
            popFirstState()
        }

        callOnHistoryChanged()
    }

    override fun reset() {
        undoStack.clear()
        redoStack.clear()
        callOnHistoryChanged()
    }

    override fun popLastState(): Boolean {
        undoStack.apply {
            if (isNotEmpty()) {
                pop()
                return true
            }
        }
        return false
    }

    override fun popFirstState(): Boolean {
        return undoStack.removeAt(0) != null
    }

    protected open fun isHistorySizeExceeded() = undoStack.size > options.maximumHistorySize

    override fun getUndoSize(): Int {
        return (undoStack.size - 1).coerceIn(0, undoStack.size)
    }

    override fun getRedoSize(): Int {
        return redoStack.size
    }
}