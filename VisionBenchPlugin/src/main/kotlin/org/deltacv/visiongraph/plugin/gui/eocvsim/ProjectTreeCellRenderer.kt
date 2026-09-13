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

package org.deltacv.visiongraph.plugin.gui.eocvsim

import org.deltacv.visiongraph.plugin.project.VisionGraphProjectTree
import java.awt.Component
import javax.swing.JTree
import javax.swing.UIManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

class ProjectTreeCellRenderer: DefaultTreeCellRenderer() {

    override fun getTreeCellRendererComponent(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
        val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)

        if(value is DefaultMutableTreeNode) {
            val node = value.userObject

            if(node is VisionGraphProjectTree.TreeNode.Project) {
                icon = UIManager.getIcon("FileView.fileIcon")
            } else if(node is VisionGraphProjectTree.TreeNode.Folder) {
                icon = UIManager.getIcon("FileView.directoryIcon")
            }
        }

        return component
    }

}



