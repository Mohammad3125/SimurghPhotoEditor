package ir.baboomeh.photolib.utils.history.handlers

import ir.baboomeh.photolib.utils.history.HistoryState

class StackFullRestoreHistoryHandler : StackHistoryHandler() {
    override fun undo(): HistoryState? {
        undoStack.takeIf { it.isNotEmpty() }?.apply {
            return pop().also { state ->
                state.undo()
                redoStack.push(state)
                callOnHistoryChanged()
            }
        }
        return null
    }

    override fun getUndoSize(): Int {
        return undoStack.size
    }
}