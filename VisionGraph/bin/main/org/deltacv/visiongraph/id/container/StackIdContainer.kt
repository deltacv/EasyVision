package org.deltacv.visiongraph.id.container

import org.deltacv.visiongraph.id.IdElement
import kotlin.math.max

/**
 * Array-mapped Dense Container augmented with stack-cursor tracking logic.
 * Useful for ImGui immediate-mode linear iteration and Undo/Redo systems.
 */
class StackIdContainer<T : IdElement> : DenseIdContainer<T>() {

    var stackPointer: Int = 1

    var stackPointerFollowing = true
        private set

    private fun movePointerToLast() {
        if (stackPointerFollowing) stackPointer = slots.size
    }

    override fun requestId(element: T, id: Int): Int {
        val result = super.requestId(element, id)
        movePointerToLast()
        return result
    }

    override fun nextId(element: T): Int {
        val result = super.nextId(element)
        movePointerToLast()
        return result
    }

    override fun nextId(): Int {
        val result = super.nextId()
        movePointerToLast()
        return result
    }

    override fun reserveId(id: Int): Int {
        val result = super.reserveId(id)
        movePointerToLast()
        return result
    }

    fun peek(): T? = slots.getOrNull(stackPointer - 1)

    fun pushforwardIfNonNull() {
        if (slots.getOrNull(stackPointer) != null) {
            stackPointer += 1
            if (stackPointer == slots.size) stackPointerFollowing = true
        }
    }

    fun peekAndPushback(): T? {
        val element = peek()
        if (element != null) {
            stackPointer = max(stackPointer - 1, 1)
            stackPointerFollowing = false
        }
        return element
    }

    fun pop() { removeId(max(stackPointer - 1, 0)) }

    fun fork() {
        if (stackPointer == slots.size) return
        val newE = ArrayList<T?>()
        for (i in 0 until stackPointer) newE.add(slots[i])

        slots.clear()
        slots.addAll(newE)
        elements.clear()
        elements.addAll(slots.filterNotNull())
        markInmutableDirty()
        stackPointerFollowing = true
    }

    override fun clear() {
        super.clear()
        stackPointer = 1
        stackPointerFollowing = true
    }
}
