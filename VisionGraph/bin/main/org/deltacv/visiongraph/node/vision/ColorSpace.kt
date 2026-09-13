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

package org.deltacv.visiongraph.node.vision

enum class ColorSpace(val channels: Int, val channelNames: Array<String>, val isOption: Boolean = true) {
    RGBA(4,  arrayOf("R", "G", "B", "A")),
    RGB(3,   arrayOf("R", "G", "B")),
    BGR(3,   arrayOf("B", "G", "R")),
    HSV(3,   arrayOf("H", "S", "V")),
    YCrCb(3, arrayOf("Y ", "Cr", "Cb")),
    LAB(3,   arrayOf("L", "a", "b")),
    GRAY(1,  arrayOf("Gray")),
    GENERIC(4, arrayOf("A", "B", "C", "D"), false);

    companion object {
        val options by lazy { entries.filter { it.isOption } }
    }
}