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

package org.deltacv.visiongraph.plugin.gui.eocvsim.dialog

import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.FileAlreadyExistsException
import javax.swing.*
import javax.swing.border.EmptyBorder

class CreateNewProjectPanel(
    projects: List<String>,
    groups: List<String>,
    projectName: String? = null,
    newProjectCallback: (group: String?, name: String) -> Unit
) : JPanel() {

    val projectNameField = JTextField()
    val groupComboBox = JComboBox<String>()

    init {
        preferredSize = Dimension(400, 100)
        layout = GridBagLayout()

        border = EmptyBorder(5, 15, 15, 15)

        add(JLabel("Project Name:").apply {
            horizontalAlignment = JLabel.RIGHT
            border = EmptyBorder(0,0,0,10)
        }, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            insets = Insets(10, 0, 0, 0)
        })

        projectNameField.text = projectName ?: ""

        add(projectNameField, GridBagConstraints().apply {
            gridx = 1
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(10, 0, 0, 0)
        })

        add(JLabel("Group:").apply {
            horizontalAlignment = JLabel.RIGHT
            border = EmptyBorder(0,0,0,10)
        }, GridBagConstraints().apply {
            gridx = 0
            gridy = 1

            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(10, 0, 0, 0)
        })

        groupComboBox.addItem("None")

        for(group in groups) {
            if(group.isBlank() || group == "/") continue
            groupComboBox.addItem(group)
        }

        groupComboBox.selectedIndex = 0

        add(groupComboBox, GridBagConstraints().apply {
            gridx = 1
            gridy = 1
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(10, 0, 0, 0)
        })

        add(JButton("New Group").apply {
            addActionListener {
                val newGroup = JOptionPane.showInputDialog(this@CreateNewProjectPanel, "Enter new group name:") ?: return@addActionListener

                if(newGroup.trim().isBlank()) {
                    JOptionPane.showMessageDialog(this@CreateNewProjectPanel, "Group name cannot be empty !")
                    return@addActionListener
                }

                if(newGroup.trim().startsWith(".")) {
                    JOptionPane.showMessageDialog(this@CreateNewProjectPanel, "Group name cannot start with a dot !")
                    return@addActionListener
                }

                (groupComboBox.model as DefaultComboBoxModel<String>).addElement(newGroup)
                groupComboBox.selectedItem = newGroup.trim()
            }
        }, GridBagConstraints().apply {
            gridx = 2
            gridy = 1
            insets = Insets(10, 5, 0, 0)
        })

        val buttonsPanel = JPanel().apply {
            border = EmptyBorder(5, 0, 0, 0)

            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(Box.createHorizontalGlue())

            add(JButton(if(projectName != null) "Import" else "Create").apply {
                addActionListener {
                    if(!projects.contains(projectNameField.text)) {
                        if(projectNameField.text.trim().isNotBlank()) {
                            val projectName = groupComboBox.selectedItem!!.toString()

                            try {
                                newProjectCallback(
                                    if (projectName == "None") null else projectName,
                                    projectNameField.text
                                )
                            } catch(_: FileAlreadyExistsException) {
                                JOptionPane.showMessageDialog(this, "Project already exists")
                                return@addActionListener
                            }

                            SwingUtilities.getWindowAncestor(this).isVisible = false
                        } else {
                            JOptionPane.showMessageDialog(this, "Please enter a valid name")
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, "Project already exists")
                    }
                }
            })

            add(Box.createRigidArea(Dimension(10, 0)))

            add(JButton("Cancel").apply {
                addActionListener { SwingUtilities.getWindowAncestor(this).isVisible = false }
            })

            add(Box.createHorizontalGlue())
        }

        add(buttonsPanel, GridBagConstraints().apply {
            gridx = 0
            gridy = 2
            gridwidth = 3

            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.CENTER
        })
    }

}



