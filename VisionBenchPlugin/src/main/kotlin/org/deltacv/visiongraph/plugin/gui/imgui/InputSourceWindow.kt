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

package org.deltacv.visiongraph.plugin.gui.imgui

import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import org.deltacv.visiongraph.engine.client.PaperVisionEngineClient
import org.deltacv.visiongraph.engine.client.response.StringResponse
import org.deltacv.visiongraph.gui.Window
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.plugin.engine.message.*
import org.deltacv.visiongraph.plugin.engine.message.response.InputSourcesListResponse
import org.deltacv.visiongraph.util.flags
import org.deltacv.mai18n.tr

class InputSourceWindow(
    val client: PaperVisionEngineClient
) : Window() {
    var inputSources = arrayOf<IpcInputSourceData>()

    private var previousInputSource: String? = null
    var currentInputSource: String? = null
        private set

    override var title = "$[win_inputsources]"
    override val windowFlags = flags(
        ImGuiWindowFlags.AlwaysAutoResize,
    )

    override val focusOnHover = true
    override val isCloseable = false

    private var initialPosition: ImVec2? = null

    private val fontAwesome = Font.find("font-awesome")
    private val fontAwesomeBig = Font.find("font-awesome-big")

    init {
        client.sendMessage(GetInputSourcesMessage().onResponseWith<InputSourcesListResponse> {
            inputSources = it.sources

            client.sendMessage(GetCurrentInputSourceMessage().onResponseWith<StringResponse> { currentSourceResponse ->
                currentInputSource = currentSourceResponse.value
            })
        })

        client.sendMessage(InputSourceListChangeListenerMessage().onResponseWith<InputSourcesListResponse> {
            inputSources = it.sources
        }) // subscribe to input source list changes

        onDraw.once {
            initialPosition = ImVec2(position.x, position.y)
        }
    }

    override fun drawContents() {
        if(ImGui.beginListBox("###$id")) {
            for (inputSource in inputSources) {
                fontAwesome.push()

                val type = when(inputSource.type) {
                    IpcInputSourceType.IMAGE -> FontAwesomeIcons.Image
                    IpcInputSourceType.CAMERA -> FontAwesomeIcons.Camera
                    IpcInputSourceType.VIDEO -> FontAwesomeIcons.Film
                    IpcInputSourceType.HTTP -> FontAwesomeIcons.Globe
                }

                ImGui.text(type)

                ImGui.popFont()

                ImGui.sameLine()

                if(ImGui.selectable(inputSource.name, currentInputSource == inputSource.name)) {
                    currentInputSource = inputSource.name
                }
            }
            ImGui.endListBox()
        }

        if(ImGui.button("Create new input source")) {
            CreateInputSourceWindow(client, fontAwesomeBig).enable()
        }

        if(previousInputSource != currentInputSource && currentInputSource != null) {
            client.sendMessage(SetInputSourceMessage(currentInputSource!!))
        }

        previousInputSource = currentInputSource
    }
}

class CreateInputSourceWindow(
    val client: PaperVisionEngineClient,
    val fontAwesome: Font
) : Window() {
    companion object {
        const val SEPARATION_MULTIPLIER = 1.5f
    }

    override var title = "$[win_createinput_sources]"

    override val windowFlags = flags(
        ImGuiWindowFlags.AlwaysAutoResize,
    )

    override val modal = ModalMode.Modal()

    override fun drawContents() {
        ImGui.pushStyleColor(ImGuiCol.Button, 0)

        fontAwesome.push()

        if(ImGui.button(FontAwesomeIcons.Camera)){
            client.sendMessage(OpenCreateInputSourceMessage(IpcInputSourceType.CAMERA))
            delete()
        }
        if(ImGui.isItemHovered()) {
            ImGui.popFont()
            ImGui.setTooltip(tr("mis_camerasource"))
            fontAwesome.push()
        }

        ImGui.sameLine()
        ImGui.indent(ImGui.getItemRectSizeX() * SEPARATION_MULTIPLIER)

        if(ImGui.button(FontAwesomeIcons.Image)){
            client.sendMessage(OpenCreateInputSourceMessage(IpcInputSourceType.IMAGE))
            delete()
        }
        if(ImGui.isItemHovered()) {
            ImGui.popFont()
            ImGui.setTooltip(tr("mis_imagesource"))
            fontAwesome.push()
        }

        ImGui.sameLine()
        ImGui.indent(ImGui.getItemRectSizeX() * SEPARATION_MULTIPLIER)

        if(ImGui.button(FontAwesomeIcons.Film)){
            client.sendMessage(OpenCreateInputSourceMessage(IpcInputSourceType.VIDEO))
            delete()
        }
        if(ImGui.isItemHovered()) {
            ImGui.popFont()
            ImGui.setTooltip(tr("mis_videosource"))
            fontAwesome.push()
        }

        ImGui.sameLine()
        ImGui.indent(ImGui.getItemRectSizeX() * SEPARATION_MULTIPLIER)

        if(ImGui.button(FontAwesomeIcons.Globe)){
            client.sendMessage(OpenCreateInputSourceMessage(IpcInputSourceType.HTTP))
            delete()
        }
        if(ImGui.isItemHovered()) {
            ImGui.popFont()
            ImGui.setTooltip(tr("mis_httpsource"))
            fontAwesome.push()
        }

        ImGui.popFont()
        ImGui.popStyleColor()
    }
}



