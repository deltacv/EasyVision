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
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.id.DrawableIdElementBase
import org.deltacv.visiongraph.id.container.IdContext
import org.deltacv.visiongraph.util.ElapsedTime
import org.deltacv.visiongraph.util.flags
import org.deltacv.mai18n.tr

class TooltipPopup(
    val text: String,
    val timeoutSeconds: Double,
    val font: Font? = null,
    label: String = "mack",
    val positionProvider: () -> ImVec2,
) : Popup(label) {

    companion object {
        fun showWarning(text: String) = TooltipPopup(text, 6.0, label = "Warning").enable()
    }

    override val position: ImVec2
        get() = positionProvider()

    override val title = "Tooltip"
    override val flags = flags(
        ImGuiWindowFlags.NoTitleBar,
        ImGuiWindowFlags.NoResize,
        ImGuiWindowFlags.NoMove,
        ImGuiWindowFlags.NoScrollbar
    )

    private val elapsedTime = ElapsedTime()

    constructor(
        text: String,
        timeoutSeconds: Double,
        font: Font? = null,
        label: String = "mack",
        position: ImVec2 = ImGui.getMousePos(),
    ) : this(text, timeoutSeconds, null, label, { position })

    override fun onEnable() {
        elapsedTime.reset()
    }

    override fun drawContents() {
        if(elapsedTime.seconds >= timeoutSeconds) {
            delete()
        }

        font?.push()
        ImGui.text(tr(text))
        font?.let { ImGui.popFont() }
    }

}

abstract class Popup(
    val label: String? = null
) : DrawableIdElementBase<Popup>() {
    override val idContainer = IdContext.local.peekNonNull<Popup>()

    abstract val title: String
    abstract val flags: Int

    private var open = false

    var isVisible = false
        private set

    open val position: ImVec2 = ImGui.getMousePos()
    val idName by lazy { "${title}###$id" }

    abstract fun drawContents()

    override fun draw() {
        if(open) {
            ImGui.openPopup(idName)
            open = false
        }

        if(isVisible) {
            ImGui.setNextWindowPos(position)
        }

        if (ImGui.beginPopup(idName, flags)) {
            drawContents()
            ImGui.endPopup()
        }

        isVisible = ImGui.isPopupOpen(idName)
    }

    override fun enable() {
        if(!isEnabled) {
            open = true
        }

        if(label != null) {
            for(popup in IdContext.local.peekNonNull<Popup>().inmutable) {
                if(popup != this && popup.label == label) { // close other popups with the same label
                    popup.delete()
                }
            }
        }
        super.enable()
    }
}



