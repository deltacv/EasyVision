package org.deltacv.visiongraph.id.container

import org.deltacv.visiongraph.id.IdElement

/**
 * Common baseline state container handling snapshot immutability
 * and redundant boilerplate methods.
 */
abstract class AbstractIdContainer<T : IdElement> : IdContainer<T> {

    protected var elements = LinkedHashSet<T>()

    private var _inmutable: List<T> = emptyList()
    protected var inmutableDirty = false

    override val inmutable: List<T>
        get() {
            if (inmutableDirty) {
                _inmutable = elements.toList()
                inmutableDirty = false
            }
            return _inmutable
        }

    protected fun markInmutableDirty() {
        inmutableDirty = true
    }

    override fun requestId(element: T, id: String): Int = requestId(element, id.hashCode())
    
    override fun nextId(element: () -> T): Int = nextId(element())
    
    override fun requestIdLazy(element: T, id: String) = lazy { requestId(element, id) }
    override fun requestIdLazy(element: T, id: Int) = lazy { requestId(element, id) }
    override fun nextIdLazy(element: () -> T) = lazy { nextId(element()) }
    override fun nextIdLazy(element: T) = lazy { nextId(element) }
    override fun nextIdLazy() = lazy { nextId() }
    
    override fun contains(id: String) = contains(id.hashCode())
    override operator fun get(id: String): T? = get(id.hashCode())
    override operator fun set(id: Int, element: T) { requestId(element, id) }

    override val size: Int
        get() = elements.size

    override fun contains(element: T) = elements.contains(element)
    override fun containsAll(elements: Collection<T>) = this.elements.containsAll(elements)
    override fun isEmpty() = elements.isEmpty()
    override fun iterator() = inmutable.iterator()
}
