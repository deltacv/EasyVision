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

import org.deltacv.visiongraph.plugin.project.recovery.RecoveredProject
import java.awt.BorderLayout
import java.awt.Dimension
import java.time.Instant
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class ProjectRecoveryPanel(
    recoveredProjects: List<RecoveredProject>, // List of (Project Name, Date)
    callback: (List<RecoveredProject>) -> Unit
) : JPanel() {

    init {
        layout = BorderLayout(10, 10)
        preferredSize = Dimension(400, 250)

        // Descriptive label at the top
        val label = JLabel(
            "<html><div style='text-align: center;'>" +
                    "<h3>VisionGraph detected unsaved projects from a previous session.<br>" +
                    "You can choose which projects to recover below.</h3></div></html>"
        )
        label.horizontalAlignment = SwingConstants.CENTER
        add(label, BorderLayout.NORTH)

        // Table data with the "Recover" column initially true
        val data = recoveredProjects.map {
            arrayOf(it.originalProjectPath, Date.from(Instant.ofEpochMilli(it.date)), true)
        }.toTypedArray()

        // Table model
        val model = object : DefaultTableModel(data, arrayOf("Project Name", "Date", "Recover")) {
            override fun getColumnClass(column: Int): Class<*> = when (column) {
                2 -> Boolean::class.java   // Column 2 will show checkboxes
                1 -> Date::class.java
                else -> String::class.java
            }

            override fun isCellEditable(row: Int, column: Int): Boolean = column == 2
        }

        // JTable
        val table = JTable(model).apply {
            rowHeight = 25

            // Robust fix: make sure Recover column uses checkbox editor and centered renderer
            getColumnModel().getColumn(2).cellEditor = DefaultCellEditor(JCheckBox())
            getColumnModel().getColumn(2).cellRenderer = DefaultTableCellRenderer().apply {
                horizontalAlignment = SwingConstants.CENTER
            }
        }

        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createEmptyBorder(-5, 10, 0, 10)
        add(scrollPane, BorderLayout.CENTER)

        // Buttons panel
        val buttonsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder(0, 0, 10, 0)

            add(Box.createHorizontalGlue())

            add(JButton("Recover Selected").apply {
                addActionListener {
                    val selectedProjects = mutableListOf<RecoveredProject>()
                    for (i in 0 until model.rowCount) {
                        if (model.getValueAt(i, 2) as Boolean) {
                            selectedProjects.add(recoveredProjects[i])
                        }
                    }
                    callback(selectedProjects.toList())
                    SwingUtilities.getWindowAncestor(this@ProjectRecoveryPanel).isVisible = false
                }
            })

            add(Box.createRigidArea(Dimension(10, 0)))

            add(JButton("Discard All").apply {
                addActionListener {
                    JOptionPane.showConfirmDialog(
                        this@ProjectRecoveryPanel,
                        "Are you sure you want to discard all recovered projects?",
                        "Discard All Projects",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    ).takeIf { it == JOptionPane.YES_OPTION }?.run {
                        callback(emptyList())
                        SwingUtilities.getWindowAncestor(this@ProjectRecoveryPanel).isVisible = false
                    }
                }
            })

            add(Box.createHorizontalGlue())
        }

        add(buttonsPanel, BorderLayout.SOUTH)
    }
}




