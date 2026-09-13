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

package org.deltacv.visiongraph.gui.util

import imgui.ImGui
import imgui.flag.ImGuiMouseButton
import imgui.type.ImBoolean
import imgui.type.ImInt
import org.deltacv.mai18n.tr


object ImGuiEx {

    fun rangeSliders(min: Int, max: Int,
                     minValue: ImInt, maxValue: ImInt,
                     minId: Int, maxId: Int,
                     width: Float = 110f) {
        ImGui.pushItemWidth(width)
        ImGui.sliderInt("###$minId", minValue.data, min, max)

        ImGui.sameLine()

        ImGui.sliderInt("###$maxId", maxValue.data, min, max)
        ImGui.popItemWidth()

        val mn = minValue.get()
        val mx = maxValue.get()

        if(mn > mx) {
            minValue.set(mx)
        }
        if(mx < mn) {
            maxValue.set(mn)
        }
    }
    fun rangeTextInputs(
        min: Int, max: Int,
        minValue: ImInt, maxValue: ImInt,
        minId: Int, maxId: Int,
        width: Float = 110f
    ) {
        ImGui.pushItemWidth(width)
        ImGui.inputInt("###$minId", minValue)
        ImGui.sameLine()
        ImGui.inputInt("###$maxId", maxValue)
        ImGui.popItemWidth()

        // Clamp values within range
        val mn = minValue.get().coerceIn(min, max)
        val mx = maxValue.get().coerceIn(min, max)

        if (mn > mx) {
            minValue.set(mx)
        } else {
            minValue.set(mn)
        }

        if (mx < mn) {
            maxValue.set(mn)
        } else {
            maxValue.set(mx)
        }
    }

    private val valuesStringCache = mutableMapOf<Class<*>, Array<String>>()

    fun <T: Enum<T>> enumCombo(values: List<T>, currentItem: ImInt): T {
        val clazz = values[0]::class.java

        val valuesStrings = if (valuesStringCache.containsKey(clazz)) {
            valuesStringCache[clazz]!!
        } else {
            val v = values.map {
                it.name
            }.toTypedArray()
            valuesStringCache[clazz] = v

            v
        }

        ImGui.combo("", currentItem, valuesStrings)

        return values[currentItem.get()]
    }

    fun toggleButton(label: String, currentState: Boolean): Boolean {
        ImGui.button(label)

        return if(ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            !currentState
        } else currentState
    }

    fun toggleSwitch(strId: String?, v: ImBoolean) {
        val p = ImGui.getCursorScreenPos()
        val drawList = ImGui.getWindowDrawList()

        val height = ImGui.getFrameHeight()
        val width = height * 1.55f
        val radius = height * 0.50f

        if (ImGui.invisibleButton(strId, width, height)) {
            v.set(!v.get())
        }

        val colBg: Int
        if (ImGui.isItemHovered()) {
            colBg = if (v.get())
                ImGui.getColorU32((145 + 20).toFloat(), 211f, (68 + 20).toFloat(), 255f)
            else
                ImGui.getColorU32((218 - 20).toFloat(), (218 - 20).toFloat(), (218 - 20).toFloat(), 255f)
        } else {
            colBg = if (v.get())
                ImGui.getColorU32(145f, 211f, 68f, 255f)
            else
                ImGui.getColorU32(218f, 218f, 218f, 255f)
        }

        drawList.addRectFilled(
            p.x, p.y,
            p.x + width, p.y + height,
            colBg,
            height * 0.5f
        )

        drawList.addCircleFilled(
            if (v.get()) (p.x + width - radius) else (p.x + radius),
            p.y + radius,
            radius - 1.5f,
            ImGui.getColorU32(255f, 255f, 255f, 255f),
            32
        )
    }

    fun centeredText(text: String) {
        val textSize = ImGui.calcTextSize(tr(text))
        val windowSize = ImGui.getWindowSize()
        val pos = windowSize.x / 2 - textSize.x / 2

        ImGui.sameLine(pos)
        ImGui.text(tr(text))
        ImGui.newLine()
    }

    fun alignForWidth(width: Float, alignment: Float): Float {
        val avail = ImGui.getContentRegionAvail().x
        val pos = (avail - width) * alignment

        ImGui.setCursorPosX(ImGui.getCursorPosX() + pos)
        return pos
    }

    inline fun alignedRow(alignment: Float, crossinline content: (measuring: Boolean) -> Unit) {
        val start = ImGui.getCursorPos()

        // --- pass 1: move off-screen ---
        ImGui.setCursorPosX(-10000f) // push far left
        ImGui.beginGroup()
        content(true)
        ImGui.endGroup()

        val size = ImGui.getItemRectSize()

        // restore cursor
        ImGui.setCursorPos(start)

        // --- align ---
        val avail = ImGui.getContentRegionAvail().x
        val offset = (avail - size.x) * alignment
        ImGui.setCursorPosX(start.x + offset)

        // --- pass 2: real draw ---
        content(false)
    }
}



