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

package org.deltacv.visiongraph.id

import org.deltacv.visiongraph.id.container.IdContainer

interface StatedIdElement : IdElement {
    fun enable()
    fun delete()
    fun restore()
    fun onEnable() { }
}

@Suppress("UNCHECKED_CAST")
abstract class StatedIdElementBase<T : StatedIdElementBase<T>> : StatedIdElement {

    abstract val idContainer: IdContainer<T>

    open val requestedId: Int? = null

    private val state by lazy {
        IdElementState(
            idContainer,
            requestedId,
            this as T,
            ::onEnable
        )
    }

    var hasEnabled: Boolean
        get() = state.hasEnabled
        private set(_) {}

    val isEnabled: Boolean
        get() = state.isEnabled

    override val id: Int
        get() = state.id

    override fun enable() = state.enable()

    override fun delete() = state.delete()

    override fun restore() = state.restore()
}



