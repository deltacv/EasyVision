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
import org.deltacv.mai18n.tr
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.util.ElapsedTime
import org.deltacv.visiongraph.util.flags

class ToastWindow(
    val message: String,
    val durationSeconds: Double = 5.0,
    val font: Font? = null
) : Window() {
    override var title = "toast"

    override val windowFlags = flags(
        ImGuiWindowFlags.NoTitleBar,
        ImGuiWindowFlags.NoResize,
        ImGuiWindowFlags.NoMove,
        ImGuiWindowFlags.NoScrollbar,
    )

    val timer = ElapsedTime()

    override fun onEnable() {
        super.onEnable()
        timer.reset()

        for(window in idContainer.inmutable) {
            if(window is ToastWindow && window != this) {
                window.delete()
            }
        }

        firstDraw = true
    }

    private var firstDraw = true

    override fun drawContents() {
        if(font != null) {
            font.push()
        }

        val currentSize = if(firstDraw) {
            ImVec2(
                ImGui.calcTextSize(tr(message)).x + 10,
                ImGui.calcTextSize(tr(message)).y + 10
            )
        } else size

        position = ImVec2(
            ImGui.getMainViewport().centerX - currentSize.x / 2,
            ImGui.getMainViewport().sizeY - currentSize.y - 20
        )

        ImGui.text(tr(message))

        if(font != null) {
            ImGui.popFont()
        }

        if(timer.seconds > durationSeconds) {
            delete()
        }
    }
}



