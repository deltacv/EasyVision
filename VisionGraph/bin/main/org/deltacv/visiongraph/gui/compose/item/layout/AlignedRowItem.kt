package org.deltacv.visiongraph.gui.compose.item.layout

import imgui.ImGui
import imgui.ImVec2
import org.deltacv.visiongraph.gui.compose.item.ContainerItem
import org.deltacv.visiongraph.gui.compose.item.Item

data class AlignedRowItem(
    override val parent: ContainerItem,
    override val children: List<Item>,
    val alignment: Double,
    val spacing: Double
) : ContainerItem {

    override fun measure(): ImVec2 {
        var w = 0.0
        var h = 0.0

        for ((i, child) in spatialChildren.withIndex()) {
            val size = child.measure()!!

            w += size.x
            if (i != children.lastIndex) {
                w += spacing
            }

            h = maxOf(h, size.y.toDouble())
        }

        return ImVec2(w.toFloat(), h.toFloat())
    }

    override fun render() {
        val size = measure()
        val start = ImGui.getCursorScreenPos()
        val offsetX = (parent.measure()!!.x - size.x) * alignment

        // Move to the start of the aligned row
        ImGui.setCursorPosX(ImGui.getCursorPosX() + offsetX.toFloat())

        for(child in actionChildren) {
            child.render()
        }

        for ((i, child) in spatialChildren.withIndex()) {
            child.render()
            if (i < children.lastIndex) {
                ImGui.sameLine(0f, spacing.toFloat()) // Let ImGui handle the gap
            }
        }

        // Ensure the next UI element starts below this row
        ImGui.setCursorScreenPos(start.x, start.y + size.y)
    }
}



