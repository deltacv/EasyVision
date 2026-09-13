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

package org.deltacv.visiongraph.gui.style

import imgui.extension.imnodes.ImNodes
import imgui.extension.imnodes.flag.ImNodesCol

interface ImNodesStyle : ImNodesStyleTemplate {
    override fun apply() {
        ImNodes.pushColorStyle(ImNodesCol.NodeBackground, nodeBackground)
        ImNodes.pushColorStyle(ImNodesCol.NodeBackgroundHovered, nodeBackgroundHovered)
        ImNodes.pushColorStyle(ImNodesCol.NodeBackgroundSelected, nodeBackgroundSelected)
        ImNodes.pushColorStyle(ImNodesCol.NodeOutline, nodeOutline)

        ImNodes.pushColorStyle(ImNodesCol.TitleBar, titleBar)
        ImNodes.pushColorStyle(ImNodesCol.TitleBarHovered, titleBarHovered)
        ImNodes.pushColorStyle(ImNodesCol.TitleBarSelected, titleBarSelected)

        ImNodes.pushColorStyle(ImNodesCol.Link, link)
        ImNodes.pushColorStyle(ImNodesCol.LinkHovered, linkHovered)
        ImNodes.pushColorStyle(ImNodesCol.LinkSelected, linkSelected)

        ImNodes.pushColorStyle(ImNodesCol.Pin, pin)
        ImNodes.pushColorStyle(ImNodesCol.PinHovered, pinHovered)

        ImNodes.pushColorStyle(ImNodesCol.BoxSelector, boxSelector)
        ImNodes.pushColorStyle(ImNodesCol.BoxSelectorOutline, boxSelectorOutline)
    }
}



