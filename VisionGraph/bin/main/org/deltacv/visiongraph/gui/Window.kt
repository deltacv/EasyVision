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
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import org.deltacv.visiongraph.id.DrawableIdElementBase
import org.deltacv.visiongraph.id.container.IdContext
import org.deltacv.visiongraph.util.event.PaperEventHandler
import org.deltacv.visiongraph.util.flags
import org.deltacv.mai18n.tr

abstract class Window(
    override val requestedId: Int? = null,
) : DrawableIdElementBase<Window>() {

    companion object;

    override val idContainer by lazy { IdContext.local.peekNonNull<Window>() }

    abstract var title: String
    abstract val windowFlags: Int

    open val modal: ModalMode = ModalMode.NotModal
    val isModal get() = modal is ModalMode.Modal

    private val imOpen = ImBoolean(true)
    val isOpen get() = imOpen.get()

    open val isCloseable = true

    open val focusOnHover = false

    private var imFocus = false
    private var requestedFocus = false

    var focus: Boolean
        set(value) {
            requestedFocus = value
        }
        get() = imFocus

    private var requestedPosition: ImVec2? = null
    private var imPosition = ImVec2()

    var position: ImVec2
        get() = imPosition
        set(value) {
            requestedPosition = value
        }

    private var requestedSize: ImVec2? = null
    private var imSize = ImVec2()

    var collapsed = false
        private set

    var hovered = false
        private set

    var size: ImVec2
        get() = imSize
        set(value) {
            requestedSize = value
        }

    val titleId get() = "${tr(title)}###$id"

    private var onDrawInitialized = false
    val onDraw by lazy {
        onDrawInitialized = true
        PaperEventHandler("Window-$title-OnDraw")
    }

    private var firstDraw = true

    override fun enable() {
        super.enable()
        firstDraw = true

        if(isModal) {
            // delete the other modal window if there's one
            for(window in idContainer) {
                if(window != this && window.isModal && window.isEnabled) {
                    window.delete() // delete current modal
                    break
                }
            }
        }
    }

    override fun draw() {
        val viewport = ImGui.getMainViewport()

        // --- modal: blackout overlay ---
        // same pattern as NodeList: push a semi-transparent black WindowBg,
        // let ImGui draw it natively, pop after end.
        // the modal window drawn after this stays on top via setNextWindowFocus.
        (modal as? ModalMode.Modal)?.let {
            ImGui.setNextWindowPos(viewport.posX, viewport.posY)
            ImGui.setNextWindowSize(viewport.sizeX, viewport.sizeY)

            ImGui.pushStyleColor(ImGuiCol.WindowBg, 0f, 0f, 0f, it.backgroundOpacity)

            ImGui.begin("##modal_overlay_$id", flags(
                ImGuiWindowFlags.NoDecoration,
                ImGuiWindowFlags.NoNav,
                ImGuiWindowFlags.NoMove,
                ImGuiWindowFlags.NoResize,
                ImGuiWindowFlags.NoSavedSettings,

                if(!firstDraw && !it.closeOnOutsideClick)
                    ImGuiWindowFlags.NoBringToFrontOnFocus
                else ImGuiWindowFlags.None
            ))

            if(ImGui.isWindowHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                if(it.closeOnOutsideClick) {
                    delete()
                } else {
                    onDraw.once { // defer to the next frame to avoid issues with ImGui state
                        focus = true // restore
                    }
                }
            }

            ImGui.end()

            ImGui.popStyleColor()
        }

        // --- position / size / focus hints ---

        preDrawContents()

        if(isModal) {
            val cx = viewport.posX + viewport.sizeX / 2f
            val cy = viewport.posY + viewport.sizeY / 2f
            ImGui.setNextWindowPos(cx, cy, 0, 0.5f, 0.5f) // pivot = center
        } else if(requestedPosition != null) {
            ImGui.setNextWindowPos(requestedPosition!!.x, requestedPosition!!.y)
            requestedPosition = null
        }

        if(requestedSize != null) {
            ImGui.setNextWindowSize(requestedSize!!.x, requestedSize!!.y)
            requestedSize = null
        }

        if(requestedFocus || (isModal && firstDraw) || (focusOnHover && hovered && !imFocus)) {
            ImGui.setNextWindowFocus()
            requestedFocus = false
        }

        val open = ImGui.begin(titleId, if(isCloseable) imOpen else null, windowFlags)

        collapsed = ImGui.isWindowCollapsed()
        hovered = ImGui.isWindowHovered()
        ImGui.getWindowPos(imPosition)
        ImGui.getWindowSize(imSize)
        imFocus = ImGui.isWindowFocused()

        if(open) {
            drawContents()
        }

        ImGui.end()

        postDrawContents()

        if(!imOpen.get()) {
            delete()
        }

        if(firstDraw) {
            firstDraw = false
        }

        if(onDrawInitialized) {
            onDraw.run()
        }
    }

    open fun preDrawContents() { }

    abstract fun drawContents()

    open fun postDrawContents() { }

    fun centerWindow(offset: ImVec2? = null) {
        val displaySize = ImGui.getMainViewport().size
        position = ImVec2(((displaySize.x - size.x) / 2) + (offset?.x ?: 0f), ((displaySize.y - size.y) / 2) + (offset?.y ?: 0f))
    }

    sealed class ModalMode {
        object NotModal : ModalMode()
        data class Modal(
            val backgroundOpacity: Float = 0.5f,
            val closeOnOutsideClick: Boolean = true
        ) : ModalMode()
    }
}

val Window.Companion.isModalWindowOpen get() = IdContext.local.peekNonNull<Window>().inmutable.any { it.isModal && it.isEnabled }
val Window.Companion.isAnyWindowHovered get() = IdContext.local.peekNonNull<Window>().inmutable.any { it.hovered }



