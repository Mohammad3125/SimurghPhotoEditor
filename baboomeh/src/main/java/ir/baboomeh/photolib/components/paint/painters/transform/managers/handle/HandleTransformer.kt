package ir.baboomeh.photolib.components.paint.painters.transform.managers.handle

import ir.baboomeh.photolib.components.paint.painters.transform.Child
import ir.baboomeh.photolib.utils.gesture.TouchData

interface HandleTransformer {
    var selectedHandle: TransformHandle?

    fun createHandles(child: Child)

    fun findHandle(child:Child, touchData: TouchData)

    fun transform(child:Child, touchData: TouchData)

    fun getAllHandles(child: Child) : Collection<TransformHandle>
}