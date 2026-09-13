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

import org.deltacv.visiongraph.codegen.resolve.Resolvable.Now
import org.deltacv.visiongraph.codegen.resolve.Resolvable.Placeholder

fun <T> T.resolved() = Now(this)

inline fun <T> Resolvable.Companion.fromValue(
    crossinline resolver: () -> T?
): Resolvable<T> {
    val value = resolver()
    return if (value != null) {
        Now(value)
    } else {
        Placeholder { resolver() }
    }
}

inline fun <T> Resolvable.Companion.fromResolvable(
    crossinline resolver: () -> Resolvable<T>?
): Resolvable<T> {
    val resolvable = resolver()
    return resolvable ?: Placeholder { resolver()?.resolve() }
}

@JvmName("fromValueInline")
inline fun <T> Resolvable.Companion.from(
    crossinline resolver: () -> T?
) = fromValue(resolver)

@JvmName("fromResolvableInline")
inline fun <T> Resolvable.Companion.from(
    crossinline resolver: () -> Resolvable<T>?
) = fromResolvable(resolver)



