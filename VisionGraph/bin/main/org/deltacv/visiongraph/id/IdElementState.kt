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

// Internal helper class to encapsulate the common logic
internal class IdElementState<T: IdElement>(
    private val idContainer: IdContainer<T>,
    private val requestedId: Int?,
    private val self: T,
    private val onEnableCallback: () -> Unit
) {
    var hasEnabled = false
        private set

    private var internalId: Int? = null

    val id: Int
        get() {
            if (internalId == null) {
                enable()
            }
            return internalId!!
        }

    val isEnabled: Boolean
        get() = internalId?.let { idContainer.contains(it, self) } ?: false

    fun enable() {
        if(internalId == null || !idContainer.contains(id, self)) {
            internalId = provideId()
            onEnableCallback()
            hasEnabled = true
        }
    }

    private fun provideId() =
        if(requestedId == null) {
            idContainer.nextIdLazy(self).value
        } else idContainer.requestIdLazy(self, requestedId).value


    fun delete() {
        idContainer.removeId(id)
    }

    fun restore() {
        idContainer[id] = self
    }
}



