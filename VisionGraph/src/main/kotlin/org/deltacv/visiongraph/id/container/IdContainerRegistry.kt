package org.deltacv.visiongraph.id.container

import org.deltacv.visiongraph.id.IdElement
import kotlin.reflect.KClass

class IdContainerRegistry {

    private data class ContainerEntry<E: IdElement>(
        val clazz: KClass<E>,
        val container: IdContainer<E>
    ) {
        fun push(context: IdContext) = context.push(clazz, container)
    }

    private val lazyContainers = mutableListOf<Lazy<*>>()

    private val _containers = mutableListOf<ContainerEntry<*>>()
    val containers get() = _containers.map { it.container }

    fun <T: IdElement, C: IdContainer<T>> add(clazz: KClass<T>, container: C) = container.apply {
        _containers.add(ContainerEntry(clazz, this))
    }

    inline fun <reified T: IdElement, C: IdContainer<T>> add(container: C) = add(T::class, container)

    fun <T: IdElement, C: IdContainer<T>> invoke(clazz: KClass<T>, initializer: () -> C): Lazy<C> = lazy {
        val container = initializer()
        _containers.add(ContainerEntry(clazz, container))
        container
    }.apply { lazyContainers.add(this) }

    inline operator fun <reified T: IdElement, C: IdContainer<T>> invoke(noinline initializer: () -> C) =
        invoke(T::class, initializer)

    fun initializeLazyContainers() {
        for(lazy in lazyContainers) {
            lazy.value // force initialization
        }
        lazyContainers.clear()
    }

    fun <R> withContext(context: IdContext = IdContext.local, block: () -> R): R {
        initializeLazyContainers()

        try {
            for(entry in _containers) {
                entry.push(context)
            }

            val result = block()
            return result
        } finally {
            repeat(_containers.size) {
                context.pop()
            }
            context.assertEmpty()
        }
    }

}