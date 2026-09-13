/*
 * VisionGraph
 * Copyright (C) 2026 Sebastian Erives, deltacv
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.deltacv.visiongraph.id.container

import org.deltacv.visiongraph.id.IdElement
import kotlin.reflect.KClass

class IdContext {

    companion object {
        // Thread-local instance of IdContainerStack
        private val threadLocalStack = ThreadLocal.withInitial { IdContext() }

        // Accessor for the current thread's stack
        val local: IdContext
            get() = threadLocalStack.get()
    }

    private val stacks = mutableMapOf<KClass<out IdElement>, ArrayDeque<IdContainer<*>>>()
    private val pushOrder = mutableListOf<KClass<out IdElement>>()

    fun <T: IdElement> push(clazz: KClass<T>, container: IdContainer<out T>) {
        val stack = stacks[clazz] ?: ArrayDeque()

        stack.addLast(container)
        pushOrder.add(clazz)

        stacks[clazz] = stack
    }

    inline fun <reified T: IdElement> push(container: IdContainer<out T>) = push(T::class, container)

    @Suppress("UNCHECKED_CAST")
    fun <T: IdElement> peek(clazz: KClass<T>): IdContainer<T>? {
        val stack = stacks[clazz] ?: return null
        if(stack.isEmpty()) return null

        return stack.last() as IdContainer<T> // uhhhh.... this is fine lol
    }

    inline fun <reified T: IdElement> peek() = peek(T::class)

    inline fun <reified T: IdElement> peekNonNull() = peek(T::class) ?: throw NullPointerException("No IdElementContainer was found for ${T::class.java.typeName} in the stack")

    inline fun <reified T: IdElement> peekSingle(): T? {
        val container = peek<T>() ?: return null

        if(container is SingleIdContainer) {
            return container.get()
        } else {
            throw ClassCastException("The container for ${T::class.simpleName} is not a SingleIdElementContainer")
        }
    }

    inline fun <reified T: IdElement> peekSingleNonNull() = peekSingle<T>() ?: throw NullPointerException("No IdElement was found for ${T::class.java.typeName} in the stack")

    fun <T: IdElement> pop(clazz: KClass<T>): Boolean {
        val stack = stacks[clazz] ?: return false
        if(stack.isEmpty()) return false

        stack.removeLast()
        val historyIndex = pushOrder.lastIndexOf(clazz)
        if(historyIndex >= 0) {
            pushOrder.removeAt(historyIndex)
        }

        if(stack.isEmpty()) {
            stacks.remove(clazz)
        }

        return true
    }

    @JvmName("popClass")
    inline fun <reified T: IdElement> pop() = pop(T::class)

    fun pop(): Boolean {
        val clazz = pushOrder.removeLastOrNull() ?: return false
        val stack = stacks[clazz] ?: return false
        if(stack.isEmpty()) return false

        stack.removeLast()

        if(stack.isEmpty()) {
            stacks.remove(clazz)
        }

        return true
    }

    fun all(): List<IdContainer<*>> {
        val all = mutableListOf<IdContainer<*>>()

        stacks.forEach { (_, stack) ->
            all.addAll(stack)
        }

        return all
    }

    fun assertEmpty() {
        val nonEmptyStacks = stacks.filter { it.value.isNotEmpty() }.map { it.key.qualifiedName }
        if(nonEmptyStacks.isNotEmpty()) {
            throw IllegalStateException("Push/Pop imbalance detected in IdContext. Non-empty stacks are of: ${nonEmptyStacks.joinToString(", ")}")
        }
    }

}



