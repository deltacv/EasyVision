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
import org.deltacv.eocvsim.plugin.api.VisualizerSidebarApi
import org.deltacv.visiongraph.plugin.VisionGraphPlugin
import org.deltacv.visiongraph.plugin.project.VisionGraphProjectTree
import java.awt.Color
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.TitledBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class PaperVisionTabPanel(
    private val plugin: VisionGraphPlugin
) : VisualizerSidebarApi.Tab(plugin) {

    val root = DefaultMutableTreeNode("/")

    private var previousSelectedProjectNode: VisionGraphProjectTree.TreeNode.Project? = null
    val projectList = JTree(root)
    val projectButtonsPanel = PaperVisionTabButtonsPanel(projectList, plugin.visionGraphProjectManager)

    val projectListAndButtonsPanel = JPanel()

    val sourceSelectorPanelApi =
        plugin.eocvSimApi.visualizerApi.visualizerComponentsFactoryApi.createSourceSelectorPanel()

    override fun create(target: JPanel) = apiImpl {
        target.layout = GridBagLayout()

        projectListAndButtonsPanel.layout = GridBagLayout()
        projectListAndButtonsPanel.border = TitledBorder("Projects").apply {
            titleFont = titleFont.deriveFont(Font.BOLD)
            border = EmptyBorder(0, 0, 0, 0)
        }

        projectList.apply {
            isRootVisible = false
            showsRootHandles = true

            font = font.deriveFont(12f)

            cellRenderer = ProjectTreeCellRenderer()

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val node = projectList.lastSelectedPathComponent ?: return
                    if (node !is DefaultMutableTreeNode) return

                    val nodeObject = node.userObject

                    if (e.clickCount >= 2) {
                        if (nodeObject is VisionGraphProjectTree.TreeNode.Project) {
                            previousSelectedProjectNode = nodeObject
                            plugin.visionGraphProjectManager.requestOpenProject(nodeObject)
                        }
                    } else if (e.clickCount == 1 && previousSelectedProjectNode != nodeObject) {
                        if (nodeObject is VisionGraphProjectTree.TreeNode.Project) {
                            previousSelectedProjectNode = nodeObject
                            plugin.visionGraphProjectManager.previewProject(nodeObject)
                            setSourceSelectorEnabled(true)
                        } else {
                            plugin.visionGraphProjectManager.previewProject(null) // return to papervision default pipeline
                            setSourceSelectorEnabled(false)
                        }
                    }
                }
            })
        }

        val projectListScroll = JScrollPane()

        projectListScroll.setViewportView(projectList)

        plugin.visionGraphProjectManager.onRefresh {
            refreshProjectTree()
        }

        projectListAndButtonsPanel.add(projectListScroll, GridBagConstraints().apply {
            gridy = 0

            weightx = 0.5
            weighty = 1.0
            fill = GridBagConstraints.BOTH

            ipadx = 120
            ipady = 20
        })

        projectListAndButtonsPanel.add(projectButtonsPanel, GridBagConstraints().apply {
            gridy = 1
            ipady = 20
        })

        target.add(projectListAndButtonsPanel, GridBagConstraints().apply {
            gridy = 0

            weightx = 0.5
            weighty = 0.8
            fill = GridBagConstraints.BOTH

            insets = Insets(10, 20, 5, 20)
        })

        sourceSelectorPanelApi.jPanel.border = TitledBorder("Sources").apply {
            titleFont = titleFont.deriveFont(Font.BOLD)
            border = EmptyBorder(0, 0, 0, 0)
        }

        target.add(sourceSelectorPanelApi.jPanel, GridBagConstraints().apply {
            gridy = 1

            weightx = 0.5
            weighty = 0.5
            fill = GridBagConstraints.BOTH

            insets = Insets(10, 20, 5, 20)
        })

        refreshProjectTree()
    }

    fun refreshProjectTree() {
        val rootTree = plugin.visionGraphProjectManager.projectTree.rootTree.nodes

        SwingUtilities.invokeLater {
            root.removeAllChildren()

            if (rootTree.isNotEmpty()) {
                fun buildTree(folder: VisionGraphProjectTree.TreeNode.Folder): DefaultMutableTreeNode {
                    val folderNode = DefaultMutableTreeNode(folder)

                    for (node in folder.nodes) {
                        when (node) {
                            is VisionGraphProjectTree.TreeNode.Project -> {
                                folderNode.add(DefaultMutableTreeNode(node))
                            }

                            is VisionGraphProjectTree.TreeNode.Folder -> {
                                folderNode.add(buildTree(node))
                            }
                        }
                    }

                    return folderNode
                }

                for (node in rootTree) { // skip root "/" from showing up
                    if (node is VisionGraphProjectTree.TreeNode.Folder) {
                        root.add(buildTree(node))
                    } else if (node is VisionGraphProjectTree.TreeNode.Project) {
                        root.add(DefaultMutableTreeNode(node))
                    }
                }
            }

            (projectList.model as DefaultTreeModel).reload()
            projectList.revalidate()
        }
    }

    private fun setSourceSelectorEnabled(enabled: Boolean) {
        sourceSelectorPanelApi.apply {
            isInteractionEnabled = enabled
            allowSwitching = enabled

            // Recursively enable/disable and adjust foreground color for graying effect
            fun setComponentTreeEnabled(component: java.awt.Component, enabled: Boolean) {
                component.isEnabled = enabled

                if (component is JComponent) {
                    if (!enabled) {
                        // Store original foreground if not already stored
                        if (component.getClientProperty("originalForeground") == null) {
                            component.putClientProperty("originalForeground", component.foreground)
                        }
                        // Apply grayed-out color
                        val original = component.getClientProperty("originalForeground") as? Color
                        component.foreground = original?.let {
                            Color(
                                (it.red + 128) / 2,
                                (it.green + 128) / 2,
                                (it.blue + 128) / 2,
                                it.alpha
                            )
                        }
                    } else {
                        // Restore original foreground
                        val original = component.getClientProperty("originalForeground") as? Color
                        if (original != null) {
                            component.foreground = original
                        }
                    }
                }

                if (component is java.awt.Container) {
                    for (child in component.components) {
                        setComponentTreeEnabled(child, enabled)
                    }
                }
            }

            setComponentTreeEnabled(jPanel, enabled)

            jPanel.repaint()
        }
    }

    override val title = "VisionGraph"

    override fun onActivated(): Unit = apiImpl {
        if (!plugin.eocvSimApi.configApi.hasFlag("hasShownPaperVisionHint")) {
            val hint = HintManager.Hint(
                "Create a new VisionGraph project here",
                projectButtonsPanel.newProjectBtt,
                SwingConstants.TOP, null
            )

            HintManager.showHint(hint)

            plugin.eocvSimApi.configApi.putFlag("hasShownPaperVisionHint")
        }

        plugin.eocvSimApi.visualizerApi.viewportApi.setFpsMeterEnabled(false)

        setSourceSelectorEnabled(false)

        sourceSelectorPanelApi.refresh()
    }

    override fun onDeactivated() = apiImpl {
        HintManager.hideAllHints()

        plugin.eocvSimApi.visualizerApi.viewportApi.setFpsMeterEnabled(true)

        setSourceSelectorEnabled(false)
    }
}



