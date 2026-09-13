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

package org.deltacv.visiongraph.gui.style.imnodes

import org.deltacv.visiongraph.gui.style.ImNodesStyle
import org.deltacv.visiongraph.gui.style.rgbaColor

object ImNodesDarkStyle : ImNodesStyle {
    override val nodeBackground = rgbaColor(50, 50, 50, 255)
    override val nodeBackgroundHovered = rgbaColor(75, 75, 75, 255)
    override val nodeBackgroundSelected = rgbaColor(75, 75, 75, 255)
    override val nodeOutline = rgbaColor(100, 100, 100, 255)

    override val titleBar = rgbaColor(41, 74, 122, 255)
    override val titleBarHovered = rgbaColor(66, 150, 250, 255)
    override val titleBarSelected = rgbaColor(66, 150, 250, 255)

    override val link = rgbaColor(61, 133, 224, 200)
    override val linkHovered = rgbaColor(66, 150, 250, 255)
    override val linkSelected = rgbaColor(66, 150, 250, 255)

    override val pin = rgbaColor(53, 150, 250, 180)
    override val pinHovered = rgbaColor(53, 150, 250, 255)

    override val boxSelector = rgbaColor(61, 133, 224, 30)
    override val boxSelectorOutline = rgbaColor(61, 133, 224, 150)
}



