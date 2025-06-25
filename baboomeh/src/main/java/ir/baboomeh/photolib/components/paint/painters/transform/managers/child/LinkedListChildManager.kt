package ir.baboomeh.photolib.components.paint.painters.transform.managers.child

import ir.baboomeh.photolib.components.paint.painters.transform.Child
import java.util.LinkedList

open class LinkedListChildManager: ChildManager {

    protected val childHolder = LinkedList<Child>()

    override fun addChild(child: Child) {
        childHolder.add(child)
    }

    override fun addAllChildren(children: Collection<Child>) {
        childHolder.addAll(children)
    }

    override fun swap(fromIndex: Int, toIndex: Int) {
        val temp = childHolder[fromIndex]
        childHolder[fromIndex] = childHolder[toIndex]
        childHolder[toIndex] = temp
    }

    override fun reorder(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) {
            return
        }

        val temp = childHolder[fromIndex]
        childHolder.removeAt(fromIndex)

        if (fromIndex < toIndex) {
            childHolder.addLast(temp)
        } else {
            childHolder.addFirst(temp)
        }
    }

    override fun removeChild(child: Child) {
        childHolder.remove(child)
    }

    override fun removeChildAt(index: Int) {
        childHolder.removeAt(index)
    }

    override fun removeAllChildren() {
        childHolder.clear()
    }

    override fun getAllChildren(): List<Child> {
        return childHolder.toList()
    }

    override fun iterator(): Iterator<Child> {
        return childHolder.iterator()
    }
}