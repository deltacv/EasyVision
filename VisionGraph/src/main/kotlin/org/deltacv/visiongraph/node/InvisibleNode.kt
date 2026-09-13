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

package org.deltacv.visiongraph.node

import imgui.extension.imnodes.ImNodes
import imgui.extension.imnodes.flag.ImNodesCol
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.NoSession
import org.deltacv.visiongraph.gui.style.rgbaColor
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@CodecType
open class InvisibleNode(private val shouldDraw: Boolean = true) : Node<NoSession>(isDeletable = false) {

    private val invisibleColor = rgbaColor(0, 0, 0, 0)

    override fun draw() {
        if(shouldDraw) {
            ImNodes.pushColorStyle(ImNodesCol.NodeOutline, invisibleColor)
            ImNodes.pushColorStyle(ImNodesCol.TitleBar, invisibleColor)
            ImNodes.pushColorStyle(ImNodesCol.TitleBarHovered, invisibleColor)
            ImNodes.pushColorStyle(ImNodesCol.TitleBarSelected, invisibleColor)
            ImNodes.pushColorStyle(ImNodesCol.NodeBackground, invisibleColor)
            ImNodes.pushColorStyle(ImNodesCol.NodeBackgroundHovered, invisibleColor)
            ImNodes.pushColorStyle(ImNodesCol.NodeBackgroundSelected, invisibleColor)

            ImNodes.beginNode(id)
            ImNodes.endNode()

            repeat(7) {
                ImNodes.popColorStyle()
            }
        }
    }

    override fun genCode(input: Unit, current: CodeGen.Current) = NoSession

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.ignore()
    }

}


