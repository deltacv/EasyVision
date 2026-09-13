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

package org.deltacv.visiongraph.plugin.project

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class VisionGraphProjectTree(val rootPath: Path) {

    val rootTree = scanDeep(rootPath)

    // returns a list of all projects in the tree by their path string
    val projects by lazy { getAllProjects(rootTree) }

    val folders by lazy { getAllFolders(rootTree) }

    private fun getAllProjects(node: TreeNode.Folder): List<String> {
        val projectList = mutableListOf<String>()

        for (child in node.nodes) {
            when (child) {
                is TreeNode.Project -> projectList.add(child.name.trim())
                is TreeNode.Folder -> projectList.addAll(getAllProjects(child))
            }
        }

        return projectList
    }

    private fun getAllFolders(node: TreeNode.Folder): List<String> {
        val folderList = mutableListOf<String>()

        // Check if the current folder contains any projects
        if (node.nodes.any { it is TreeNode.Project }) {
            if(node.name.isNotBlank())
                folderList.add(node.name)
        }

        // Recursively check subfolders
        for (child in node.nodes) {
            if (child is TreeNode.Folder) {
                folderList.addAll(getAllFolders(child))
            }
        }

        return folderList
    }

    fun print() {
        printTree(rootTree, 0)
    }

    fun get(vararg path: String) = get(rootTree, path.toList())

    private fun get(currentNode: TreeNode.Folder, path: List<String>): TreeNode? {
        if (path.isEmpty()) return currentNode

        val nextNode = currentNode.nodes.find { it.name == path[0] }
        return when {
            nextNode is TreeNode.Folder && path.size > 1 -> get(nextNode, path.drop(1))
            nextNode != null && path.size == 1 -> nextNode
            else -> null
        }
    }

    private fun printTree(tree: TreeNode.Folder, depth: Int) {
        for (node in tree.nodes) {
            when (node) {
                is TreeNode.Project -> {
                    println("  ".repeat(depth) + node.name)
                }
                is TreeNode.Folder -> {
                    println("  ".repeat(depth) + node.name)
                    printTree(node, depth + 1)
                }
            }
        }
    }

    private fun scanDeep(path: Path): TreeNode.Folder {
        val tree = mutableListOf<TreeNode>()

        rootPath.fileSystem.provider().newDirectoryStream(path) { true }.use { stream ->
            for (entry in stream) {
                if(entry.name.startsWith(".")) continue // skip hidden files and folders

                if (entry.isDirectory()) {
                    tree.add(scanDeep(entry))
                } else {
                    tree.add(TreeNode.Project(entry.name))
                }
            }
        }

        return TreeNode.Folder(path.name, tree)
    }

    sealed class TreeNode(val name: String) {
        class Project(name: String) : TreeNode(name)
        class Folder(name: String, val nodes: List<TreeNode>) : TreeNode(name)

        override fun toString() = name
    }
}



