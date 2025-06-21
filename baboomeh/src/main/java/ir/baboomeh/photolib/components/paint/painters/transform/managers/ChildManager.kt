package ir.baboomeh.photolib.components.paint.painters.transform.managers

import ir.baboomeh.photolib.components.paint.painters.transform.Child

interface ChildManager: Iterable<Child> {
    fun addChild(child: Child)

    fun addAllChildren(children: Collection<Child>)

    /**
     * Swaps two children in the rendering order.
     * This method exchanges the positions of two objects in the z-order.
     *
     * @param fromIndex The index of the first object.
     * @param toIndex The index of the second object.
     */
    fun swap(fromIndex: Int, toIndex: Int)

    /**
     * Reorders child placement without changing other children's placement.
     * ```kotlin
     *  // For example imagine this list: 1,2,3,4,5;
     *  manager.reorder(fromIndex = 4, toIndex: 0)
     *  // List after reorder: 5,1,2,3,4
     * ```
     *
     */
    fun reorder(fromIndex: Int, toIndex: Int)

    fun removeChild(child: Child)

    fun removeChildAt(index: Int)

    fun removeAllChildren()

    fun getAllChildren() : List<Child>
}