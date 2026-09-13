package org.deltacv.visiongraph.id.container

import org.deltacv.visiongraph.id.IdElement

/**
 * Optimized ID container designed solely for tightly packed, sequential numeric IDs.
 * Utilizes primitive indices mapped to an array for ultra-fast loop rendering in ImGui contexts.
 */
open class DenseIdContainer<T : IdElement> : AbstractIdContainer<T>() {

    protected val slots = mutableListOf<T?>()
    private var nextExternalId = 0

    private fun checkBounds(id: Int) {
        if (id < 0 || id > 1000 + slots.size) { 
            throw IllegalArgumentException("DenseIdContainer structurally prohibits extremely sparse or negative numeric iterations (Requested ID: $id). Use SparseIdContainer.")
        }
    }

    override fun requestId(element: T, id: Int): Int {
        if (contains(id, element)) return id
        checkBounds(id)

        while (slots.size <= id) slots.add(null)

        val old = slots[id]
        if (old != null) elements.remove(old)

        slots[id] = element
        if (!elements.contains(element)) elements.add(element)

        markInmutableDirty()

        if (id >= nextExternalId) nextExternalId = id + 1
        return id
    }

    override fun nextId(element: T): Int {
        val id = nextExternalId++

        while (slots.size <= id) slots.add(null)
        slots[id] = element

        if (!elements.contains(element)) elements.add(element)
        markInmutableDirty()
        return id
    }

    override fun nextId(): Int {
        val id = nextExternalId++

        while (slots.size <= id) slots.add(null)
        // slot at id remains null, but the size is ensured

        markInmutableDirty()
        return id
    }

    override fun reserveId(id: Int): Int {
        checkBounds(id)
        while (slots.size <= id) slots.add(null)
        if (id >= nextExternalId) nextExternalId = id + 1
        markInmutableDirty()
        return id
    }

    override fun contains(id: Int): Boolean {
        return id in 0 until slots.size && slots[id] != null
    }

    override fun contains(id: Int, elem: T): Boolean {
        return id in 0 until slots.size && slots[id] == elem
    }

    override fun get(id: Int): T? {
        return slots.getOrNull(id)
    }

    override fun removeId(id: Int) {
        if (id !in 0 until slots.size) return
        val elem = slots[id]
        if (elem != null) {
            elements.remove(elem)
            markInmutableDirty()
            slots[id] = null
        }
    }

    override fun clear() {
        slots.clear()
        elements.clear()
        markInmutableDirty()
        nextExternalId = 0
    }
}
