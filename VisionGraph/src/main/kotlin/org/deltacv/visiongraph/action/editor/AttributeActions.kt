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

package org.deltacv.visiongraph.action.editor

import org.deltacv.visiongraph.action.Action
import org.deltacv.visiongraph.node.Link

class CreateLinkAction(
    val link: Link
) : Action() {
    override fun undo() {
        link.delete()
    }

    override fun execute() {
        link.associatedAction = this
        if(link.isEnabled) return

        if(link.hasEnabled) {
            link.restore()
        } else {
            link.enable()
        }
    }

    override fun toString() = "CreateLinkAction(link=$link)"
}

class DeleteLinksAction(
    val links: List<Link>
) : Action() {
    override fun undo() {
        for(link in links) {
            if(link.isEnabled) continue
            link.restore()
        }
    }

    override fun execute() {
        links.forEach {
            it.delete()
        }
    }

    override fun toString() = "DeleteLinksAction(#links=${links.size})"
}



