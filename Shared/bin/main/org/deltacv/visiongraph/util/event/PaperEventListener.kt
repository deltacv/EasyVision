/*
 * PaperVision
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
package org.deltacv.visiongraph.util.event

@JvmInline
value class PaperEventListenerId(val value: Int)

typealias PaperOnceEventListener = () -> Unit
typealias PaperEventListener = PaperEventListenerContext.() -> Unit

/**
 * Class to provide context to an event listener, mainly
 * to allow removing itself from the event handler
 * @param handler the event handler
 * @param id the listener ID
 */
class PaperEventListenerContext(
    private val handler: PaperEventHandler,
    private val id: PaperEventListenerId,
) {

    /**
     * Removes the listener from the event handler
     */
    fun removeListener() {
        handler.removeListener(id)
    }
}



