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

import org.deltacv.common.util.loggerForThis
import org.deltacv.visiongraph.plugin.project.VisionGraphProjectManager
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Window
import javax.swing.JButton
import javax.swing.JPanel

class NewProjectPanel(
    val projectManager: VisionGraphProjectManager,
    val ancestor: Window
) : JPanel() {

    val logger by loggerForThis()

    val newProjectBtt = JButton("New Project")

    val importProjectBtt = JButton("Import Project")

    init {
        layout = GridBagLayout()

        newProjectBtt.addActionListener {
            projectManager.newProjectAsk(ancestor)
        }

        add(newProjectBtt, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
        })

        importProjectBtt.addActionListener {
            projectManager.importProjectAsk(ancestor)
        }

        add(importProjectBtt, GridBagConstraints().apply {
            gridx = 1
            gridy = 0
        })
    }

}



