package org.deltacv.visiongraph.id.container

import org.deltacv.visiongraph.id.IdElement

/**
 * ID Container explicitly optimized to track wildly disjointed, negative, or String HashMap
 * generated elements utilizing a native Map topology to prevent unbounded memory padding nulls.
 */
class SparseIdContainer<T : IdElement> : AbstractIdContainer<T>() {

    private val map = HashMap<Int, T>()
    private var nextExternalId = 0

    override fun requestId(element: T, id: Int): Int {
        if (contains(id, element)) return id
        
        val old = map.put(id, element)
        if (old != null) elements.remove(old)
        if (!elements.contains(element)) elements.add(element)
        
        markInmutableDirty()
        if (id >= nextExternalId) nextExternalId = id + 1
        return id
    }

    override fun nextId(element: T): Int {
        val id = nextExternalId++
        map[id] = element
        if (!elements.contains(element)) elements.add(element)
        
        markInmutableDirty()
        return id
    }

    override fun nextId(): Int {
        val id = nextExternalId++
        markInmutableDirty()
        return id
    }

    override fun reserveId(id: Int): Int {
        if (id >= nextExternalId) nextExternalId = id + 1
        markInmutableDirty()
        return id
    }

    override fun contains(id: Int): Boolean = map.containsKey(id)

    override fun contains(id: Int, elem: T): Boolean = map[id] == elem

    override fun get(id: Int): T? = map[id]

    override fun removeId(id: Int) {
        val elem = map.remove(id)
        if (elem != null) {
            elements.remove(elem)
            markInmutableDirty()
        }
    }

    override fun clear() {
        map.clear()
        elements.clear()
        markInmutableDirty()
        nextExternalId = 0
    }
}
