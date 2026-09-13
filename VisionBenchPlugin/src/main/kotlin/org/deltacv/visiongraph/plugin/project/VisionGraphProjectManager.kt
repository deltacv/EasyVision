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

import com.github.serivesmejia.eocvsim.util.SysUtil
import com.github.serivesmejia.eocvsim.util.extension.removeFromEnd
import org.deltacv.common.util.loggerForThis
import org.deltacv.eocvsim.plugin.api.EOCVSimApi
import org.deltacv.eocvsim.sandbox.nio.SandboxFileSystem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.deltacv.visiongraph.engine.client.response.JsonElementResponse
import org.deltacv.visiongraph.engine.client.response.OkResponse
import org.deltacv.visiongraph.platform.defaultPaperVisionFolderPath
import org.deltacv.visiongraph.plugin.VisionGraphPlugin
import org.deltacv.visiongraph.plugin.VisionGraphProcessRunner
import org.deltacv.visiongraph.plugin.engine.EOCVSimIpcEngine
import org.deltacv.visiongraph.plugin.engine.message.DiscardCurrentRecoveryMessage
import org.deltacv.visiongraph.plugin.engine.message.EditorChangeMessage
import org.deltacv.visiongraph.plugin.engine.message.GetCurrentProjectMessage
import org.deltacv.visiongraph.plugin.engine.message.SaveCurrentProjectMessage
import org.deltacv.visiongraph.plugin.gui.eocvsim.dialog.VisionGraphDialogFactory
import org.deltacv.visiongraph.plugin.previz.SinglePipelineCompiler
import org.deltacv.visiongraph.plugin.project.recovery.RecoveredProject
import org.deltacv.visiongraph.plugin.project.recovery.RecoveryDaemonProcessManager
import org.deltacv.visiongraph.plugin.project.recovery.RecoveryData
import org.deltacv.visiongraph.util.event.PaperEventHandler
import org.deltacv.visiongraph.util.hashCodeString
import org.openftc.easyopencv.OpenCvPipeline
import java.awt.Window
import java.io.File
import java.io.FileNotFoundException
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText

class VisionGraphProjectManager(
    val classpath: String,
    val fileSystem: SandboxFileSystem,
    val engine: EOCVSimIpcEngine,
    val plugin: VisionGraphPlugin,
    val eocvSim: EOCVSimApi,
) {

    companion object {
        val recoveryFolder get() =
            File(defaultPaperVisionFolderPath + File.separator + "papervision_recovery").apply { mkdir() }
    }

    val root: Path = fileSystem.getPath("")

    val latestSourceFolder: Path = root.resolve(".latest_source")

    val onMainUpdate = eocvSim.mainLoopHook

    var projectTree = VisionGraphProjectTree(root)
        private set

    var projects = listOf<VisionGraphProjectTree.TreeNode.Project>()
        private set

    var currentProject: VisionGraphProjectTree.TreeNode.Project? = null
        private set

    var currentPaperVisionProject: VisionGraphProject? = null
        private set

    val previewPipelines = mutableListOf<WeakReference<Class<out OpenCvPipeline>>>()

    fun paperVisionProjectFrom(
        project: VisionGraphProjectTree.TreeNode.Project,
        tree: JsonElement
    ) = VisionGraphProject(
        Instant.now().toEpochMilli(),
        findProjectFolderPath(project)!!.pathString,
        project.name,
        tree
    )

    val recoveryDaemonProcessManager = RecoveryDaemonProcessManager(classpath).apply {
        start()
    }

    val logger by loggerForThis()

    val recoveredProjects
        get() = mutableListOf<RecoveredProject>().apply {
            if (recoveryFolder.exists()) {
                for (file in recoveryFolder.listFiles() ?: arrayOf()) {
                    if (file.extension == "recoverypaperproj") {
                        val recoveredProject = try {
                            Json.decodeFromString<RecoveredProject>(file.readText())
                        } catch (e: Exception) {
                            logger.warn("Failed to read recovery file, deleting", e)
                            file.delete()
                            continue
                        }

                        val projectPath = fileSystem.getPath(recoveredProject.originalProjectPath)

                        if (projectPath.exists()) {
                            val project = try {
                                Json.decodeFromString<VisionGraphProject>(String(
                                    fileSystem.readAllBytes(projectPath),
                                    StandardCharsets.UTF_8
                                ))
                            } catch (e: Exception) {
                                logger.warn("Failed to read project file for phased recovery, deleting", e)
                                file.delete()
                                continue
                            }

                            logger.info("Found recovered project ${recoveredProject.originalProjectPath} from ${recoveredProject.date} compared to ${project.timestamp}")

                            if (recoveredProject.date > project.timestamp) {
                                add(recoveredProject)
                                logger.info("Asking for recovered project ${recoveredProject.originalProjectPath} from ${recoveredProject.date}")
                            }
                        } else {
                            file.delete()
                            logger.info("Discarded phased recovery file for ${recoveredProject.originalProjectPath}")
                        }
                    }
                }
            }
        }.toList()

    val onRefresh = PaperEventHandler("VisionGraphProjectManager-onRefresh")

    fun init() {
        engine.setMessageHandlerOf<DiscardCurrentRecoveryMessage> {
            discardCurrentRecovery()
            respond(OkResponse())
        }

        engine.setMessageHandlerOf<SaveCurrentProjectMessage> {
            saveCurrentProject(message.json)
            respond(OkResponse())
        }

        engine.setMessageHandlerOf<GetCurrentProjectMessage> {
            respond(JsonElementResponse(currentPaperVisionProject!!.data))
        }

        engine.setMessageHandlerOf<EditorChangeMessage> {
            if (currentProject != null) {
                sendRecoveryProject(currentProject!!, message.json)
            }
        }
    }

    fun refresh() {
        projectTree = VisionGraphProjectTree(root)
        projects = recursiveSearchProjects(projectTree.rootTree)

        onRefresh.run()
    }

    private fun recursiveSearchProjects(root: VisionGraphProjectTree.TreeNode.Folder): List<VisionGraphProjectTree.TreeNode.Project> {
        val list = mutableListOf<VisionGraphProjectTree.TreeNode.Project>()

        for (node in root.nodes) {
            when (node) {
                is VisionGraphProjectTree.TreeNode.Folder -> {
                    list.addAll(recursiveSearchProjects(node))
                }

                is VisionGraphProjectTree.TreeNode.Project -> {
                    list.add(node)
                }
            }
        }

        return list
    }

    fun importProjectAsk(ancestor: Window) {
        SwingUtilities.invokeLater {
            JFileChooser().apply {
                fileFilter = FileNameExtensionFilter("VisionGraph Project (.paperproj)", "paperproj")

                if (showSaveDialog(ancestor) == JFileChooser.APPROVE_OPTION) {
                    VisionGraphDialogFactory.displayNewProjectDialog(
                        ancestor,
                        projectTree.projects,
                        projectTree.folders,
                        selectedFile.name.removeFromEnd(".paperproj")
                    ) { projectGroup, projectName ->
                        try {
                            importProject(projectGroup ?: "", projectName, selectedFile)
                        } catch (e: java.nio.file.FileAlreadyExistsException) {
                            throw e // new project dialog will handle this
                        } catch (e: Exception) {
                            JOptionPane.showMessageDialog(
                                ancestor,
                                "Project file failed to load: ${e.javaClass.simpleName} ${e.message}"
                            )
                            logger.warn("Project file ${selectedFile.absolutePath} failed to load", e)
                            return@displayNewProjectDialog
                        }

                        JOptionPane.showConfirmDialog(
                            ancestor,
                            "Do you wish to open the project that was just imported?",
                            "Project Imported",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                        ).takeIf { it == JOptionPane.YES_OPTION }?.run {
                            requestOpenProject(findProject(projectGroup ?: "", "$projectName.paperproj")!!)
                        }
                    }
                }
            }
        }
    }

    fun cloneProjectAsk(project: VisionGraphProjectTree.TreeNode.Project, ancestor: Window) {
        SwingUtilities.invokeLater {
            VisionGraphDialogFactory.displayNewProjectDialog(
                ancestor,
                projectTree.projects,
                projectTree.folders,
                "${project.name.removeFromEnd(".paperproj")} (Copy)"
            ) { projectGroup, projectName ->
                try {
                    cloneProject(projectGroup ?: "", projectName, project)
                } catch (e: FileAlreadyExistsException) {
                    throw e // new project dialog will handle this
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        ancestor,
                        "Project file failed to load: ${e.javaClass.simpleName} ${e.message}"
                    )
                    logger.warn("Project file ${project.name} failed to load", e)
                    return@displayNewProjectDialog
                }

                JOptionPane.showConfirmDialog(
                    ancestor,
                    "Do you wish to open the project that was just cloned?",
                    "Project Imported",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                ).takeIf { it == JOptionPane.YES_OPTION }?.run {
                    requestOpenProject(findProject(projectGroup ?: "", "$projectName.paperproj")!!)
                }
            }
        }
    }

    fun importProject(path: String, name: String, file: File) {
        newProject(path, name, jsonElement = Json.decodeFromString<VisionGraphProject>(SysUtil.loadFileStr(file)).data)
    }

    fun cloneProject(path: String, newName: String, project: VisionGraphProjectTree.TreeNode.Project) {
        newProject(path, newName, jsonElement = Json.decodeFromString<VisionGraphProject>(readProjectFile(project)).data)
    }

    fun newProjectAsk(ancestor: Window) {
        SwingUtilities.invokeLater {
            VisionGraphDialogFactory.displayNewProjectDialog(
                ancestor,
                projectTree.projects,
                projectTree.folders,
            ) { projectGroup, projectName ->
                newProject(projectGroup ?: "", projectName)

                JOptionPane.showConfirmDialog(
                    ancestor,
                    "Do you wish to open the project that was just created?",
                    "Project Created",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                ).takeIf { it == JOptionPane.YES_OPTION }?.run {
                    requestOpenProject(findProject(projectGroup ?: "", "$projectName.paperproj")!!)
                }
            }
        }
    }

    fun newProject(path: String, name: String, jsonElement: JsonElement? = null, appendExtension: Boolean = true) {
        val projectPath = fileSystem.getPath("/").resolve(path)
        fileSystem.createDirectories(projectPath)

        val projectFile = projectPath.resolve(if (appendExtension) "$name.paperproj" else name)
        fileSystem.createFile(projectFile)
        fileSystem.write(
            projectFile, Json.encodeToString(VisionGraphProject(
                Instant.now().toEpochMilli(),
                path, name,
                jsonElement ?: JsonObject(emptyMap())
            )).toByteArray(StandardCharsets.UTF_8)
        )

        refresh()
    }

    fun deleteProject(project: VisionGraphProjectTree.TreeNode.Project) {
        bulkDeleteProjects(project)
    }

    fun bulkDeleteProjects(vararg projects: VisionGraphProjectTree.TreeNode.Project) {
        for (project in projects) {
            val path = findProjectPath(project) ?: throw FileNotFoundException("Project $project not found in tree")
            fileSystem.delete(path)
        }

        refresh()
    }

    fun readProjectFile(project: VisionGraphProjectTree.TreeNode.Project) =
        String(
            fileSystem.readAllBytes(
                findProjectPath(project) ?: throw FileNotFoundException("Project $project not found in tree")
            ),
            StandardCharsets.UTF_8
        )

    fun requestOpenProject(project: VisionGraphProjectTree.TreeNode.Project) {
        onMainUpdate.once {
            openProject(project)
        }
    }

    fun previewProject(project: VisionGraphProjectTree.TreeNode.Project?) {
        onMainUpdate.once {
            if(project == null) {
                plugin.isRunningPreviewPipeline = false
                plugin.switchToDefaultPipelineIfNecessary()
                return@once
            }

            val path = (findProjectFolderPath(project)?.pathString ?: "").replace("/", "_")
            val name = project.name.removeFromEnd(".paperproj")

            try {
                val source = latestSourceFolder.resolve("${path}_$name.java").readText()

                val clazz = SinglePipelineCompiler.compilePipeline(source)

                previewPipelines.add(WeakReference(clazz))

                plugin.isRunningPreviewPipeline = true

                eocvSim.pipelineManagerApi.changePipelineAnonymous(clazz, force = true)
            } catch (e: Exception) {
                logger.warn("Failed to show pipeline preview for project ${project.name}", e)

                plugin.isRunningPreviewPipeline = false
                plugin.switchToDefaultPipelineIfNecessary()
                return@once
            }
        }
    }

    fun openProject(project: VisionGraphProjectTree.TreeNode.Project) {
        logger.info("Opening ${project.name}")

        currentPaperVisionProject = Json.decodeFromString<VisionGraphProject>(readProjectFile(project))

        SwingUtilities.invokeLater {
            eocvSim.visualizerApi.frame!!.isVisible = false
        }

        VisionGraphProcessRunner.onPaperVisionExit.once {
            SwingUtilities.invokeLater {
                eocvSim.visualizerApi.frame!!.isVisible = true
            }
        }

        VisionGraphProcessRunner.execPaperVision(classpath)

        currentProject = project
    }

    fun saveCurrentProject(json: JsonElement) {
        val project = currentProject ?: return
        val path = findProjectPath(project) ?: return

        logger.info("Saving ${path.pathString}")

        fileSystem.write(
            path,
            Json.encodeToString(paperVisionProjectFrom(currentProject!!, json)).toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun findProject(path: String, name: String): VisionGraphProjectTree.TreeNode.Project? {
        val paths = path.split("/").filter { it.isNotBlank() }

        // Start at the root of the project tree
        var currentNode: VisionGraphProjectTree.TreeNode = projectTree.rootTree

        if (path.isNotBlank() || path != "/") {
            // Traverse the path segments
            for (segment in paths) {
                if (currentNode is VisionGraphProjectTree.TreeNode.Folder) {
                    // Find the subfolder with the matching segment name
                    val nextNode = currentNode.nodes.find { it.name == segment }
                    if (nextNode != null) {
                        currentNode = nextNode
                    } else {
                        // If the segment is not found, return null
                        return null
                    }
                } else {
                    // If we encounter a non-folder node before the path is fully traversed, return null
                    return null
                }
            }
        }

        // At the end of the path, check for a project with the given name
        if (currentNode is VisionGraphProjectTree.TreeNode.Folder) {
            return currentNode.nodes.find {
                it is VisionGraphProjectTree.TreeNode.Project && it.name == name
            } as? VisionGraphProjectTree.TreeNode.Project
        }

        // If the final node is not a folder or doesn't contain the project, return null
        return null
    }

    fun findProjectFolderPath(project: VisionGraphProjectTree.TreeNode.Project) =
        findProjectPath(project)?.toAbsolutePath()?.parent

    fun findProjectPath(project: VisionGraphProjectTree.TreeNode.Project) =
        findProjectPath(project, projectTree.rootTree, root)

    private fun findProjectPath(
        targetProject: VisionGraphProjectTree.TreeNode.Project,
        currentNode: VisionGraphProjectTree.TreeNode,
        currentPath: Path
    ): Path? {
        return when (currentNode) {
            is VisionGraphProjectTree.TreeNode.Folder -> {
                for (node in currentNode.nodes) {
                    val path = findProjectPath(targetProject, node, currentPath.resolve(currentNode.name))
                    if (path != null) {
                        return path
                    }
                }
                null
            }

            is VisionGraphProjectTree.TreeNode.Project -> {
                if (currentNode == targetProject) {
                    return currentPath.resolve(currentNode.name)
                }
                null
            }
        }
    }

    fun sendRecoveryProject(
        projectNode: VisionGraphProjectTree.TreeNode.Project,
        tree: JsonElement
    ) {
        val projectPath = findProjectPath(projectNode)?.pathString ?: return
        val hex = projectPath.hashCodeString

        recoveryDaemonProcessManager.sendRecoveryData(
            RecoveryData(
                recoveryFolder.path,
                "${hex}.recoverypaperproj",
                RecoveredProject(
                    projectPath,
                    System.currentTimeMillis(),
                    hex,
                    paperVisionProjectFrom(projectNode, tree)
                )
            )
        )
    }

    fun discardCurrentRecovery() {
        if (currentProject == null) return
        val path = findProjectPath(currentProject!!)?.pathString ?: return

        logger.info("Discarding recovery for $path")

        val recoveryFile = recoveryFolder.resolve("${path.hashCodeString}.recoverypaperproj")
        recoveryFile.delete()
    }

    fun recoverProject(recoveredProject: RecoveredProject) {
        val projectPath = fileSystem.getPath(recoveredProject.originalProjectPath)

        if (projectPath.exists()) {
            fileSystem.delete(projectPath)
        }

        newProject(
            recoveredProject.project.path,
            recoveredProject.project.name,
            jsonElement = recoveredProject.project.data,
            appendExtension = false
        )

        logger.info("Recovered project ${recoveredProject.originalProjectPath} from ${recoveredProject.date}")

        refresh()
    }

    fun saveLatestSource(source: String, project: VisionGraphProjectTree.TreeNode.Project? = currentProject) {
        fileSystem.createDirectories(latestSourceFolder)

        val path = (if (project == null) "" else findProjectFolderPath(project)?.pathString ?: "").replace("/", "_")
        val name = project?.name?.removeFromEnd(".paperproj") ?: "unsaved_project_${System.currentTimeMillis()}"

        val sourceFile = latestSourceFolder.resolve("${path}_$name.java")
        if (!sourceFile.exists()) {
            fileSystem.createFile(sourceFile)
        }

        fileSystem.write(sourceFile, source.toByteArray(StandardCharsets.UTF_8), StandardOpenOption.WRITE)
    }

    fun closeCurrentProject() {
        currentProject = null
    }

    fun deleteAllRecoveredProjects() {
        for (file in recoveryFolder.listFiles() ?: arrayOf()) {
            if (file.extension == "recoverypaperproj") {
                file.delete()
            }
        }
    }

}



