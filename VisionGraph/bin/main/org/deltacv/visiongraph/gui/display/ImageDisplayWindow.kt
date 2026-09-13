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

package org.deltacv.visiongraph.gui.display

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import org.deltacv.mai18n.tr
import org.deltacv.visiongraph.engine.previz.ClientPrevizStream
import org.deltacv.visiongraph.gui.Window
import org.deltacv.visiongraph.util.flags

class ImageDisplayWindow(
    val imageDisplay: ImageDisplay,
    override val isCloseable: Boolean = true
) : Window() {
    override var title = "Preview"

    override val focusOnHover = true

    override val windowFlags = flags(
        ImGuiWindowFlags.AlwaysAutoResize,
        ImGuiWindowFlags.NoMove
    )

    override fun drawContents() {
        imageDisplay.draw()

        val pipelineStream = imageDisplay.clientPrevizStream

        val buttonText = if(pipelineStream.sizing == ClientPrevizStream.Sizing.MINIMIZED) {
            "Maximize"
        } else {
            "Minimize"
        }

        if (ImGui.button(buttonText)) {
            if(pipelineStream.sizing == ClientPrevizStream.Sizing.MINIMIZED) {
                pipelineStream.maximize()
            } else {
                pipelineStream.minimize()
            }
        }

        ImGui.sameLine()

        val pipelineFps = pipelineStream.statistics.fps

        val statusText = if(pipelineStream.isAtOfflineTexture(imageDisplay.id))
            "mis_loading"
        else if(pipelineFps <= 0)
            "mis_runningok"
        else "mis_runningok_atfps"

        ImGui.text(tr(statusText, String.format("%.1f", pipelineFps)))
    }
}



