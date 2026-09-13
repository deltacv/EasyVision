package org.deltacv.visiongraph.id.container

import org.deltacv.visiongraph.id.IdElement

/**
 * Generic abstraction layer for ID Containers mapping IdElements.
 */
interface IdContainer<T : IdElement> : Collection<T> {
    val inmutable: List<T>

    fun requestId(element: T, id: Int): Int
    fun requestId(element: T, id: String): Int
    fun nextId(element: T): Int
    fun nextId(element: () -> T): Int
    fun nextId(): Int

    fun requestIdLazy(element: T, id: String): Lazy<Int>
    fun requestIdLazy(element: T, id: Int): Lazy<Int>
    fun nextIdLazy(element: () -> T): Lazy<Int>
    fun nextIdLazy(element: T): Lazy<Int>
    fun nextIdLazy(): Lazy<Int>

    fun reserveId(id: Int): Int
    fun contains(id: Int): Boolean
    fun contains(id: String): Boolean
    fun contains(id: Int, elem: T): Boolean

    operator fun get(id: String): T?
    operator fun get(id: Int): T?
    fun removeId(id: Int)

    operator fun set(id: Int, element: T)
    fun clear()
}
