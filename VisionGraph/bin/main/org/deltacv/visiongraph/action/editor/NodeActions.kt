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
import org.deltacv.visiongraph.node.Node

class CreateNodesAction(
    val nodes: List<Node<*>>
) : Action() {

    constructor(node: Node<*>) : this(listOf(node))

    override fun undo() {
        for(node in nodes) {
            if(node.isEnabled) {
                node.delete()
            }
        }
    }

    override fun execute() {
        for(node in nodes) {
            if(node.isEnabled) continue

            if(node.hasEnabled) {
                node.restore()
            } else {
                node.enable()
            }
        }
    }

    override fun toString() = "CreateNodesAction(#nodes=${nodes.size})"
}

class DeleteNodesAction(
    val nodes: List<Node<*>>
) : Action() {
    override fun undo() {
        for(node in nodes) {
            if(node.isEnabled) continue

            if(node.hasEnabled) {
                node.restore()
            } else {
                node.enable()
            }
        }
    }

    override fun execute() {
        nodes.forEach {
            it.delete()
        }
    }

    override fun toString() = "DeleteNodesAction(#nodes=${nodes.size})"
}



