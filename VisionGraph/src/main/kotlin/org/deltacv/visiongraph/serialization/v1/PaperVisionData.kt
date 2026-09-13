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

package org.deltacv.visiongraph.serialization.v1

import imgui.ImVec2

abstract class AttributeSerializationData {
    open var id: Int = 0
}

class BasicAttribData(id: Int) : AttributeSerializationData() {
    init {
        this.id = id
    }

    constructor(): this(0)
}

abstract class NodeSerializationData {
    open var id: Int = 0
    open var nodePos: ImVec2 = ImVec2(0f, 0f)
}

class BasicNodeData(
    id: Int,
    nodePos: ImVec2
) : NodeSerializationData() {

    init {
        this.id = id
        this.nodePos = nodePos
    }

    constructor() : this(0, ImVec2(0f, 0f))
}

data class LinkSerializationData(
    var from: Int,
    var to: Int
) {
    constructor() : this(0, 0)
}



