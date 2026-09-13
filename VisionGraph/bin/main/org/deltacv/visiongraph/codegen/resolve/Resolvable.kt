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

package org.deltacv.visiongraph.codegen.resolve

import org.deltacv.visiongraph.id.IdElement
import org.deltacv.visiongraph.id.container.IdContext
import org.deltacv.visiongraph.util.event.PaperEventHandler
import kotlin.getValue

sealed class Resolvable<T> {

    companion object {
        const val PLACEHOLDER_PREFIX = "<mack!"
        const val PLACEHOLDER_SUFFIX = ">"

        // %s is the ID of the placeholder
        const val PLACEHOLDER_TEMPLATE = "$PLACEHOLDER_PREFIX%d$PLACEHOLDER_SUFFIX"
    }

    /* -- abstract Resolvable members -- */

    abstract fun letOrDefer(block: (T) -> Unit)
    abstract fun <R> tryReturn(success: (T) -> R, fail: (String) -> R): R
    abstract fun resolve(): T?

    /* -- utility functions -- */

    inline fun <reified R> map(
        crossinline transform: (T) -> R
    ): Resolvable<R> = Resolvable.fromValue { resolve()?.let(transform) }

    inline fun <R> flatMap(
        crossinline transform: (T?) -> Resolvable<R>
    ): Resolvable<R> = Resolvable.fromResolvable { resolve()?.let(transform) }

    /* -- IMPLEMENTATIONS -- */

    data class Now<T>(val result: T) : Resolvable<T>() {
        override fun letOrDefer(block: (T) -> Unit) = block(result)
        override fun <R> tryReturn(success: (T) -> R, fail: (String) -> R) = success(result)
        override fun resolve(): T = result

        override fun toString() = result.toString()
    }

    open class Placeholder<T>(
        val resolveLast: Boolean,
        private val resolver: () -> T?
    ) : Resolvable<T>(), IdElement {

        val key get() = String.format(PLACEHOLDER_TEMPLATE, id)

        private var usingOnResolve = false // to avoid creating the event handler if not necessary
        val onResolve by lazy {
            usingOnResolve = true
            PaperEventHandler("Placeholder-$id-OnResolve", catchExceptions = false)
        }

        private var resolving = false

        private var cachedValue: T? = null

        constructor(resolver: () -> T?) : this(resolveLast = false, resolver = resolver)

        override fun resolve(): T? {
            if (resolving) return cachedValue
            resolving = true

            val value = cachedValue ?: resolver()
            if (value != null && usingOnResolve) {
                onResolve.run()
            }

            cachedValue = value
            resolving = false
            return value
        }

        override fun letOrDefer(block: (T) -> Unit) {
            val value = resolve()
            if (value != null) {
                block(value)
            } else {
                onResolve.once {
                    val resolved = cachedValue ?: resolver()

                    if (resolved != null) {
                        cachedValue = resolved
                        block(resolved)
                    } else {
                        // huh? should not happen, but just in case, we can throw an exception here
                        throw IllegalStateException("Placeholder '$key' could not be resolved")
                    }
                }
            }
        }

        override fun <R> tryReturn(success: (T) -> R, fail: (String) -> R): R {
            val result = resolve()

            return if(result != null) {
                success(result)
            } else {
                fail(toString())
            }
        }

        override fun toString() = key

        override val id by IdContext.local.peekNonNull<Placeholder<*>>().nextIdLazy(this)
    }

    data class DependentPlaceholder<P, T>(val dependency: Resolvable<P>, val resolver: (P) -> T?) : Placeholder<T>(resolver = {
        val depValue = dependency.resolve()
        if (depValue != null) {
            resolver(depValue)
        } else {
            null
        }
    })

    data class DoubleDependentPlaceholder<P1, P2, T>(
        val dependency1: Resolvable<P1>,
        val dependency2: Resolvable<P2>,
        val resolver: (P1, P2) -> T?
    ) : Placeholder<T>(resolver = {
        val depValue1 = dependency1.resolve()
        val depValue2 = dependency2.resolve()
        if (depValue1 != null && depValue2 != null) {
            resolver(depValue1, depValue2)
        } else {
            null
        }
    })

    class ListPlaceholder<T>(
        dependencies: List<Resolvable<T>>,
    ) : Placeholder<List<T>>(resolver = {
        val vals = dependencies.map { it.resolve() }
        if(vals.all { it != null }) {
            vals.map { it!! }
        } else {
            null
        }
    }) {
        constructor(vararg dependencies: Resolvable<T>) : this(dependencies.toList())
    }
}



