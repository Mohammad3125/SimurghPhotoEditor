package ir.baboomeh.photolib.utils.history

import java.util.Stack

class StackHistoryHandler : HistoryHandler {


    private val undoStack = Stack<HistoryState>()
    private val redoStack = Stack<HistoryState>()

    override var options = HistoryHandler.Options()

    override fun undo(): HistoryState? {
        undoStack.takeIf { it.size > 1 }?.apply {
            return pop().also { state ->
                state.undo()
                redoStack.push(state)
            }
        }
        return null
    }

    override fun redo(): HistoryState? {
        redoStack.takeIf { it.isNotEmpty() }?.apply {
            return pop().also { state ->
                state.redo()
                undoStack.push(state)
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
    }

    override fun reset() {
        undoStack.clear()
        redoStack.clear()
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

    private fun isHistorySizeExceeded() = undoStack.size > options.maximumHistorySize

    override fun getUndoSize(): Int {
        return undoStack.size
    }

    override fun getRedoSize(): Int {
        return redoStack.size
    }
}