package ir.baboomeh.photolib.utils.history

interface HistoryHandler {

    class Options {
        var maximumHistorySize: Int = 15
    }

    var options: Options

    /**
     * Undoes the changes in stack.
     * @return current [HistoryState] that has been undone. Might be null.
     */
    fun undo(): HistoryState?

    /**
     * Redoes the changes in stack.
     * @return current [HistoryState] that has been redone. Might be null.
     */
    fun redo(): HistoryState?

    /**
     * Adds an [HistoryState] to stacks that later will be used to undo/redo the state.
     * @param state a [HistoryState] class.
     */
    fun addState(state: HistoryState)

    fun reset()

    fun popLastState(): Boolean

    fun popFirstState(): Boolean

    fun getUndoSize(): Int

    fun getRedoSize(): Int
}