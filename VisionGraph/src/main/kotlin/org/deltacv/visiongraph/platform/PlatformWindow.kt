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

package org.deltacv.visiongraph.platform

import imgui.ImVec2

data class PlatformFileFilter(
    val name: String,
    val extensions: List<String>
)

enum class PlatformFileChooserResult {
    CANCELLED,
    OK
}

interface PlatformWindow {
    var title: String
    var icon: String

    val size: ImVec2

    var visible: Boolean

    var maximized: Boolean

    var focus: Boolean

    fun requestFocus()

    fun close()

    fun setCloseListener(listener: (() -> Boolean)?)

    fun saveFileDialog(content: ByteArray, defaultName: String = "", vararg platformFileFilter: PlatformFileFilter): PlatformFileChooserResult
}



