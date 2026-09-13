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

package org.deltacv.visiongraph.gui

import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.gui.util.ImGuiEx
import org.deltacv.visiongraph.id.Misc
import org.deltacv.visiongraph.util.flags
import org.deltacv.mai18n.tr
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.getValue

class DialogMessageWindow(
    val message: String,
    title: String = "mis_message",
    val textArea: String? = null,
    val font: Font? = null,
    override val modal: ModalMode = ModalMode.Modal()
): Window() {

    override var title = tr(title)

    override val windowFlags = flags(
        ImGuiWindowFlags.NoResize,
        ImGuiWindowFlags.NoCollapse,
        ImGuiWindowFlags.NoMove,
        ImGuiWindowFlags.NoScrollbar,
        ImGuiWindowFlags.NoScrollWithMouse
    )

    val textAreaId by Misc.newId()

    override fun drawContents() {
        font?.let {
            it.push()
        }

        val messageSize = ImGui.calcTextSize(tr(message))

        var maxWidth = messageSize.x
        var height = messageSize.y

        ImGuiEx.centeredText(message)

        ImGui.setCursorPos(ImVec2(ImGui.getCursorPosX(), ImGui.getCursorPosY() - 20))

        if(textArea != null) {
            val textSize = ImGui.calcTextSize(tr(textArea))
            ImGui.inputTextMultiline("##$textAreaId", ImString(textArea), ImGui.calcTextSize(tr(textArea)).x, textSize.y + 20)

            height += textSize.y.coerceAtMost(ImGui.getMainViewport().sizeY * 0.6f)
            maxWidth = maxOf(maxWidth, textSize.x)
        }

        ImGui.dummy(0f, 15f)
        ImGui.newLine()

        val gotItSize = ImGui.calcTextSize(tr("mis_gotit"))
        height += gotItSize.y * 2

        var buttonsWidth = gotItSize.x
        if(textArea != null) {
            buttonsWidth += ImGui.calcTextSize(tr("mis_copytext")).y
        }

        ImGuiEx.alignForWidth(buttonsWidth, 0.5f)

        if(ImGui.button(tr("mis_gotit"))) {
            delete()
        }

        if(textArea != null) {
            ImGui.sameLine()

            if(ImGui.button(tr("mis_copytext"))) {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(
                    StringSelection(textArea), null
                )
            }
        }

        font?.let {
            ImGui.popFont()
        }

        size = ImVec2(maxWidth + 10, height + 80)
    }
}



