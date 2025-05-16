package ir.baboomeh.photolib.utils.history

interface HistoryState {
    fun undo()

    fun redo()
}