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

import com.formdev.flatlaf.demo.HintManager
import com.github.serivesmejia.eocvsim.gui.component.PopupX
import org.deltacv.visiongraph.plugin.project.VisionGraphProjectManager
import org.deltacv.visiongraph.plugin.project.VisionGraphProjectTree
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode

class PaperVisionTabButtonsPanel(
    projectsJTree: JTree,
    projectManager: VisionGraphProjectManager
) : JPanel(GridBagLayout()) {

    val newProjectBtt  = JButton("New")
    val editSelectionBtt = JButton("Edit")

    val openSelectionBtt = JButton("Open Selected Project")

    init {
        add(newProjectBtt, GridBagConstraints().apply {
            insets = Insets(0, 0, 0, 5)
        })

        var lastNewProjectPopup: PopupX? = null

        newProjectBtt.addActionListener {
            val location = newProjectBtt.locationOnScreen

            HintManager.hideAllHints()

            val window = SwingUtilities.getWindowAncestor(this)
            val popup = PopupX(window, NewProjectPanel(projectManager, SwingUtilities.getWindowAncestor(this)), location.x, location.y)

            popup.onShow {
                val popupX = location.x + (newProjectBtt.size.width / 2) - (popup.window.size.width / 2)
                val popupY = popup.window.location.y // Keep the original Y position, or update as needed

                popup.setLocation(popupX, popupY)
            }

            lastNewProjectPopup?.hide()
            popup.show()
            lastNewProjectPopup = popup
        }

        var lastEditSelectionPopup: PopupX? = null

        editSelectionBtt.addActionListener {
            if(projectsJTree.selectionPaths == null) return@addActionListener

            val projects = mutableListOf<VisionGraphProjectTree.TreeNode.Project>()

            for(selection in projectsJTree.selectionPaths!!) {
                val selectedProject = selection.lastPathComponent
                if(selectedProject !is DefaultMutableTreeNode || selectedProject.userObject !is VisionGraphProjectTree.TreeNode.Project)
                    continue

                if(projects.contains(selectedProject.userObject)) continue // avoid duplication
                projects.add(selectedProject.userObject as VisionGraphProjectTree.TreeNode.Project)
            }

            val location = editSelectionBtt.locationOnScreen

            val window = SwingUtilities.getWindowAncestor(this)
            val popup = PopupX(window, EditSelectionPanel(projects, projectManager, SwingUtilities.getWindowAncestor(this)), location.x, location.y)

            popup.onShow {
                val popupX = location.x + (editSelectionBtt.size.width / 2) - (popup.window.size.width / 2)
                val popupY = popup.window.location.y // Keep the original Y position, or update as needed

                popup.setLocation(popupX, popupY)
            }

            lastEditSelectionPopup?.hide()
            popup.show()
            lastEditSelectionPopup = popup
        }

        add(editSelectionBtt, GridBagConstraints().apply { gridx = 1 })

        openSelectionBtt.addActionListener {
            val selectedProject = projectsJTree.lastSelectedPathComponent
            if(selectedProject !is DefaultMutableTreeNode || selectedProject.userObject !is VisionGraphProjectTree.TreeNode.Project)
                return@addActionListener

            projectManager.requestOpenProject(selectedProject.userObject as VisionGraphProjectTree.TreeNode.Project)
        }

        projectsJTree.addTreeSelectionListener {
            val selectedProject = projectsJTree.lastSelectedPathComponent

            val state = selectedProject is DefaultMutableTreeNode &&
                    selectedProject.userObject is VisionGraphProjectTree.TreeNode.Project

            editSelectionBtt.isEnabled = state
            openSelectionBtt.isEnabled = state
        }

        add(openSelectionBtt, GridBagConstraints().apply {
            gridwidth = 2
            gridy = 1

            insets = Insets(5, 0, 0, 0)
            weightx = 1.0

            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.CENTER
        })

        editSelectionBtt.isEnabled = false
        openSelectionBtt.isEnabled = false
    }

}



