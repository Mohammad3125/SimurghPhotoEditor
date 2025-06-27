package ir.simurgh.photolib.components.paint.painters.transform.managers.handle

import ir.simurgh.photolib.components.paint.painters.transform.Child
import ir.simurgh.photolib.utils.gesture.TouchData

/**
 * Interface for handling transformations on child elements through interactive handles.
 * Provides methods for creating, finding, and applying transformations using touch gestures.
 */
interface HandleTransformer {
    /**
     * The currently selected transformation handle that will be used for applying transformations.
     * Null if no handle is currently selected.
     */
    var selectedHandle: TransformHandle?

    /**
     * Creates and initializes transformation handles for the specified child element.
     * This method should be called when setting up handles for a new child or when
     * the child's properties have changed significantly.
     *
     * @param child The child element for which to create transformation handles.
     */
    fun createHandles(child: Child)

    /**
     * Finds and selects the transformation handle closest to the touch position.
     * This method determines which handle should be selected based on the touch data
     * and updates the selectedHandle property accordingly.
     *
     * @param child The child element being transformed.
     * @param touchData The touch gesture data containing position and movement information.
     */
    fun findHandle(child: Child, touchData: TouchData)

    /**
     * Applies transformation to the child element based on the selected handle and touch data.
     * This method performs the actual transformation calculations and updates the child's
     * transformation matrix and properties.
     *
     * @param child The child element to transform.
     * @param touchData The touch gesture data containing position and movement information.
     */
    fun transform(child: Child, touchData: TouchData)

    /**
     * Returns all available transformation handles for the specified child element.
     * The collection includes all handles that can be used for transformation,
     * regardless of their current selection state.
     *
     * @param child The child element whose handles to retrieve.
     * @return A collection of all transformation handles for the child.
     */
    fun getAllHandles(child: Child): Collection<TransformHandle>
}
