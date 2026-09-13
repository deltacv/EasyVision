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

import imgui.ImGui
import imgui.ImVec2
import imgui.extension.imnodes.ImNodes
import imgui.extension.imnodes.flag.ImNodesCol
import imgui.flag.ImGuiMouseButton
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.mai18n.tr

abstract class DrawNode<S: CodeGenSession>(
    isDeletable: Boolean = true,
    joinActionStack: Boolean = true,
    rebuildOnLink: Boolean = true
) : Node<S>(isDeletable, joinActionStack, rebuildOnLink) {

    var nextNodePosition: ImVec2? = null

    var pinToMouse = false
    var pinToMouseOffset = ImVec2()

    override val genNodeName: String?
        get() = "${tr(annotationData.name)} (#$id)"

    private var lastPinToMouse = false
    private var pinToMouseNewOffset = ImVec2()

    private var isFirstDraw = true

    val annotationData by lazy {
        val annotation = this.javaClass.getAnnotation(PaperNode::class.java)
            ?: throw IllegalArgumentException("Node ${javaClass.typeName} needs to have a @PaperNode annotation")

        AnnotationData(annotation.name, annotation.description, annotation.category, annotation.showInList)
    }

    var titleColor = annotationData.category.color
    var titleHoverColor = annotationData.category.colorSelected

    open fun init() {}

    override fun draw() {
        val title = annotationData.name

        nextNodePosition?.let {
            ImNodes.setNodeEditorSpacePos(id, it.x, it.y)
            nextNodePosition = null
        }

        ImNodes.pushColorStyle(ImNodesCol.TitleBar, titleColor)
        ImNodes.pushColorStyle(ImNodesCol.TitleBarHovered, titleHoverColor)
        ImNodes.pushColorStyle(ImNodesCol.TitleBarSelected, titleHoverColor)

         ImNodes.beginNode(id)
             ImNodes.beginNodeTitleBar()
                 ImGui.textUnformatted(tr(title))
             ImNodes.endNodeTitleBar()

             drawNode()
             drawAttributes()
         ImNodes.endNode()

        ImNodes.getNodeDimensions(size, id)
        ImNodes.getNodeEditorSpacePos(position, id)
        ImNodes.getNodeScreenSpacePos(screenPosition, id)
        ImNodes.getNodeGridSpacePos(gridPosition, id)

        if(isFirstDraw) {
            init()
            isFirstDraw = false
        }

        ImNodes.popColorStyle()
        ImNodes.popColorStyle()
        ImNodes.popColorStyle()

        if(pinToMouse) {
            val mousePos = ImGui.getMousePos()

            if(pinToMouse != lastPinToMouse) {
                val nodeDims = ImVec2()

                // I have no idea why this is needed
                ImNodes.getNodeDimensions(nodeDims, id)

                pinToMouseNewOffset = ImVec2(
                    (nodeDims.x / 2) + pinToMouseOffset.x,
                    (nodeDims.y / 2) + pinToMouseOffset.y
                )
            }

            val newPosX = mousePos.x - pinToMouseNewOffset.x
            val newPosY = mousePos.y - pinToMouseNewOffset.y

            ImNodes.setNodeEditorSpacePos(id, newPosX, newPosY)

            if(ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
                pinToMouse = false
            }
        }

        lastPinToMouse = pinToMouse
    }

    open fun drawNode() { }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)

        val pos = ImNodes.getNodeEditorSpacePos(id)
        encoder.float("x", pos.x)
        encoder.float("y", pos.y)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        val x = decoder.float("x")
        val y = decoder.float("y")

        nextNodePosition = ImVec2(x, y)
    }

    data class AnnotationData(val name: String,
                              val description: String,
                              val category: NodeCategory,
                              val showInList: Boolean)

}



