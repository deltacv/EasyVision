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
import imgui.internal.ImRect
import org.deltacv.visiongraph.id.Misc

// this is an extremely hacky "dummy" table which uses the columns api.
// mostly used for the nodes list, because nodes don't respect any sort of
// imgui constraint, whether they are being used within the columns api or anything.
// they are just placed on the default position regardless of anything.
// (that is in fact a good thing for a node editor, but in this case we are trying to
// make a nice looking table by using another node editor to be able to display the nodes
// as they are...)
//
// to use the columns api with the nodes within a node editor, we create the columns as normal,
// except that instead of putting the nodes in the columns directly, we place "dummy" invisible
// elements which have the same size as the node that would correspond in each cell.
//
// later on, we get the position of the dummy element of each cell and set it to the corresponding
// node, which is independent of our table, i could *technically* make the calculations myself to place
// the nodes in their corresponding positions without this bad hack, but hey if imgui can do it for us
// why not use it instead?
//
// ...i hate this so
class Table(val maxColumns: Int = 4, val drawCallback: ((Int, ImVec2) -> Unit)? = null) {


    private val cells = mutableListOf<TableCell>()

    // Results of layout
    private val cellRects = mutableMapOf<Int, ImRect>()
    val currentRects: Map<Int, ImRect> get() = cellRects

    private val columnsId by Misc.newId()

    fun add(id: Int, size: ImVec2) {
        cells += TableCell(id, size)
    }

    fun contains(id: Int): Boolean {
        return cells.any { it.id == id }
    }

    fun setSize(id: Int, size: ImVec2) {
        cells.find { it.id == id }?.size = size
    }

    /**
     * Convert the flat list into a 2D structure of rows.
     */
    private fun chunkedRows(): List<List<TableCell>> {
        if (cells.isEmpty()) return emptyList()
        val columns = minOf(maxColumns, cells.size)
        return cells.chunked(columns)
    }

    fun draw() {
        if (cells.isEmpty()) return

        val rows = chunkedRows()
        val columns = minOf(maxColumns, cells.size)

        // Clear previous rect cache
        cellRects.clear()

        if (ImGui.beginTable("###$columnsId", columns)) {
            for (row in rows) {
                ImGui.tableNextRow()
                var colIndex = 0

                for (cell in row) {
                    ImGui.tableSetColumnIndex(colIndex)

                    // Dummy element for layout
                    ImGui.invisibleButton("###${cell.id}", cell.size.x, cell.size.y)

                    val rect = ImRect(ImGui.getItemRectMin(), ImGui.getItemRectMax())
                    cell.rect = rect
                    cellRects[cell.id] = rect

                    drawCallback?.invoke(cell.id, rect.min)

                    colIndex++
                }
            }

            ImGui.endTable()
        }
    }

    fun getPos(id: Int): ImVec2? = cellRects[id]?.min

    // Struct holding per-cell info
    private data class TableCell(
        val id: Int,
        var size: ImVec2,
        var rect: ImRect? = null,
    )
}



