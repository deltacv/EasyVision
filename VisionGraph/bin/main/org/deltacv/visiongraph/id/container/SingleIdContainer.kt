package org.deltacv.visiongraph.id.container

import org.deltacv.visiongraph.id.IdElement

/**
 * Container restricted to a single element.
 */
class SingleIdContainer<T : IdElement> : DenseIdContainer<T>() {

    /**
     * Assigns an element to an ID.
     * @throws IllegalStateException if an element already exists
     */
    override fun requestId(element: T, id: Int): Int {
        if (isNotEmpty()) {
            throw IllegalStateException("This container can only have one element")
        }
        return super.requestId(element, id)
    }

    /**
     * Lazy ID request.
     */
    override fun requestIdLazy(element: T, id: Int): Lazy<Int> {
        if (isNotEmpty()) {
            throw IllegalStateException("This container can only have one element")
        }
        return super.requestIdLazy(element, id)
    }

    /**
     * Returns the contained element or null.
     */
    fun get(): T? = inmutable.firstOrNull()
}
