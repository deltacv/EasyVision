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

import com.github.serivesmejia.eocvsim.util.SysUtil
import com.github.serivesmejia.eocvsim.util.extension.plus
import org.deltacv.visiongraph.plugin.project.VisionGraphProjectManager
import org.deltacv.visiongraph.plugin.project.VisionGraphProjectTree
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.io.File
import java.util.concurrent.CancellationException
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.filechooser.FileNameExtensionFilter

class EditSelectionPanel(
    val targetProjects: List<VisionGraphProjectTree.TreeNode.Project>,
    val projectManager: VisionGraphProjectManager,
    val ancestor: Window
) : JPanel() {

    val deleteProjectBtt = JButton("Delete Project${if(targetProjects.size > 1) "s" else ""}")

    val exportProjectBtt = JButton("Export Project${if(targetProjects.size > 1) "s" else ""}")

    val cloneProjectBtt = JButton("Clone Selected Project")

    init {0
        layout = GridBagLayout()

        deleteProjectBtt.addActionListener {
            JOptionPane.showConfirmDialog(
                ancestor,
                "Are you sure you want to delete the selected project(s)?",
                "Delete Project",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            ).takeIf { it == JOptionPane.YES_OPTION }?.let {
                projectManager.bulkDeleteProjects(*targetProjects.toTypedArray())
            }
        }

        add(deleteProjectBtt, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
        })

        exportProjectBtt.addActionListener {
            var nextDir: File? = null

            fun openFileChooserFor(project: VisionGraphProjectTree.TreeNode.Project) {
                JFileChooser().apply {
                    if(nextDir == null) {
                        nextDir = fileSystemView.defaultDirectory
                    }

                    fileFilter = FileNameExtensionFilter("VisionGraph Project (.paperproj)", "paperproj")
                    selectedFile = nextDir!! + File.separator + project.name

                    if(showSaveDialog(ancestor) == JFileChooser.APPROVE_OPTION) {
                        val file = if(selectedFile.extension != "paperproj") {
                            selectedFile + ".paperproj"
                        } else selectedFile

                        if(file.exists()) {
                            val result = JOptionPane.showConfirmDialog(
                                ancestor,
                                "File already exists in the selected directory. Do you wish to replace it?"
                            )

                            if(result == JOptionPane.CANCEL_OPTION) {
                                throw CancellationException() // handle this later
                            } else if(result != JOptionPane.YES_OPTION) {
                                openFileChooserFor(project)
                                return@apply
                            }
                        }

                        SysUtil.saveFileStr(file, projectManager.readProjectFile(project))

                        nextDir = file.parentFile
                    } else {
                        throw CancellationException() // handle later as well
                    }
                }
            }

            for(project in targetProjects) {
                try {
                    openFileChooserFor(project)
                } catch (_: CancellationException) { // riiiiight here
                    break
                }
            }
        }

        add(exportProjectBtt, GridBagConstraints().apply {
            gridx = 1
            gridy = 0
        })

        if(targetProjects.size == 1) {
            cloneProjectBtt.addActionListener { projectManager.cloneProjectAsk(targetProjects[0], ancestor) }

            add(cloneProjectBtt, GridBagConstraints().apply {
                gridx = 0
                gridy = 1
                // fill
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                gridwidth = 2

                anchor = GridBagConstraints.CENTER

                insets = Insets(3, 0, 0, 0)
            })
        }
    }

}



