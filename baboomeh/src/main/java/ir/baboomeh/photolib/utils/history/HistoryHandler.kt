package ir.baboomeh.photolib.utils.history

abstract class HistoryHandler {

    class Options {
        var maximumHistorySize: Int = 40
    }

    fun interface OnHistoryStateChanged {
        fun onStateChanged()
    }

    protected var onHistoryStateChanged: OnHistoryStateChanged? = null

    internal fun setOnHistoryChanged(callback: OnHistoryStateChanged) {
        onHistoryStateChanged = callback
    }

    protected fun callOnHistoryChanged() {
        onHistoryStateChanged?.onStateChanged()
    }

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

    internal abstract fun reset()

    abstract fun popLastState(): Boolean

    abstract fun popFirstState(): Boolean

    abstract fun getUndoSize(): Int

    abstract fun getRedoSize(): Int
}