package org.deltacv.visiongraph.gui.compose.item

import imgui.ImGui
import imgui.ImVec2
import org.deltacv.visiongraph.gui.compose.property.Property
import org.deltacv.visiongraph.gui.compose.property.type.Text
import org.deltacv.mai18n.tr

data class ButtonItem(
    override val parent: ContainerItem,
    val text: Property<Text>,
    val onClick: () -> Unit
) : Item {
    override fun measure(): ImVec2 {
        val t = text.get()

        t.font?.push()
        val textSize = ImGui.calcTextSize(tr(t.string))
        t.font?.let { ImGui.popFont() }

        val pad = ImGui.getStyle().framePadding

        return ImVec2(
            textSize.x + pad.x * 2f,
            textSize.y + pad.y * 2f
        )
    }

    override fun render() {
        val t = text.get()

        t.font?.push()

        if(ImGui.button(tr(t.string))) {
            onClick()
        }

        t.font?.let { ImGui.popFont() }
    }
}

data class TextItem(
    override val parent: ContainerItem,
    val text: Property<Text>
) : Item {
    override fun measure(): ImVec2 {
        val t = text.get()

        t.font?.push()
        val size = ImVec2(
            ImGui.calcTextSize(tr(t.string)).x,
            ImGui.calcTextSize(tr(t.string)).y
        )
        t.font?.let { ImGui.popFont() }

        return size
    }

    override fun render() {
        val t = text.get()

        t.font?.push()
        ImGui.text(tr(t.string))
        t.font?.let { ImGui.popFont() }
    }
}

class NewLineItem(override val parent: ContainerItem) : Item {
    override fun measure() = ImVec2(0f, ImGui.getStyle().itemSpacing.y)

    override fun render() {
        ImGui.newLine()
    }
}



