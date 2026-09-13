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

package org.deltacv.visiongraph.codegen

import imgui.ImGui
import imgui.ImVec2
import org.deltacv.mai18n.tr
import org.deltacv.visiongraph.VisionGraph
import org.deltacv.visiongraph.codegen.language.Language
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.exception.AttributeGenException
import org.deltacv.visiongraph.exception.GenException
import org.deltacv.visiongraph.exception.NodeGenException
import org.deltacv.visiongraph.gui.ToastWindow
import org.deltacv.visiongraph.gui.DialogMessageWindow
import org.deltacv.visiongraph.gui.Popup
import org.deltacv.visiongraph.gui.TooltipPopup
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.id.container.DenseIdContainer
import org.deltacv.visiongraph.id.container.IdContext
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.Node
import org.deltacv.visiongraph.util.hashCodeString
import org.deltacv.visiongraph.util.loggerForThis

class CodeGenManager(val visionGraph: VisionGraph) {

    val logger by loggerForThis()

    fun build(
        name: String,
        language: Language = JavaLanguage,
        isForPreviz: Boolean = false
    ): String? {
        val placeholders = DenseIdContainer<Resolvable.Placeholder<*>>()

        IdContext.local.push(placeholders) // all placeholders created during code gen will be caught here

        for(popup in IdContext.local.peekNonNull<Popup>().inmutable) {
            if(popup.label == "Gen-Error") {
                popup.delete()
            }
        }
        visionGraph.clearToasts()

        val codeGen = CodeGen(name, language, isForPreviz)

        logger.info("-- Starting CodeGen #${codeGen.hashCodeString} --")

        val result = try {
            codeGen.stage = CodeGen.Stage.INITIAL_GEN

            visionGraph.nodeEditor.outputNode.input.requireAttachedAttribute() // output always needs to be connected

            // start off code generation chain
            visionGraph.nodeEditor.outputNode.genCodeIfNecessary(codeGen.current)

            codeGen.stage = CodeGen.Stage.END_GEN

            codeGen.build()
        } catch (attrEx: AttributeGenException) {
            codeGen.stage = CodeGen.Stage.ENDED_ERROR

            TooltipPopup(
                attrEx.message,
                8.0,
                label = "Gen-Error"
            ) { ImVec2(attrEx.attribute.position.x + 5, attrEx.attribute.position.y + 20) }.enable()

            val node = attrEx.attribute.parentNode
            showError(codeGen, node)

            logger.warn("-- CodeGen ${codeGen.hashCodeString} FAILED due to attribute exception --", attrEx)
            return null
        } catch(nodeEx: NodeGenException) {
            codeGen.stage = CodeGen.Stage.ENDED_ERROR

            TooltipPopup(
                nodeEx.message,
                8.0,
                label = "Gen-Error"
            ) { ImVec2(nodeEx.node.screenPosition.x, nodeEx.node.screenPosition.y - 20) }.enable()

            showError(codeGen, nodeEx.node)

            logger.warn("-- CodeGen ${codeGen.hashCodeString} FAILED due to node exception --", nodeEx)
            return null
        } catch(ex: Exception) {
            codeGen.stage = CodeGen.Stage.ENDED_ERROR

            DialogMessageWindow(
                message = "mis_codegen_error",
                title = "win_codegen_error",
                textArea = ex.stackTraceToString(),
                font = Font.find("calcutta-big")
            ).enable()

            logger.error("-- CodeGen #${codeGen.hashCodeString} FAILED due to ${if(ex is GenException) "gen" else "unknown"} exception --", ex)
            return null
        } finally {
            IdContext.local.pop<Resolvable.Placeholder<*>>() // we're done with placeholders
        }

        codeGen.stage = CodeGen.Stage.ENDED_SUCCESS

        logger.debug("Flags defined during CodeGen #{}:{}", codeGen.hashCodeString, if(codeGen.flags().isEmpty()) " none" else "")
        for(flag in codeGen.flags()) {
            logger.debug("- $flag")
        }

        logger.info("-- CodeGen #${codeGen.hashCodeString} OK --")

        return result.trim()
    }

    private fun showError(codeGen: CodeGen, node: Node<*>) {
        if(!codeGen.isForPreviz) { // dont scroll if we're on an active previz session, can become annoying
            visionGraph.nodeEditor.editorPanning.x = (-node.gridPosition.x) - (node.size.x / 2) + ImGui.getMainViewport().size.x / 2
            visionGraph.nodeEditor.editorPanning.y = (-node.gridPosition.y) - (node.size.y / 2) + ImGui.getMainViewport().size.y / 2
        }

        val toast = if(node is DrawNode<*>) {
            tr("mis_codegen_erroron_toast", tr(node.annotationData.name))
        } else {
            tr("mis_codegen_errortoast")
        }

        ToastWindow(toast, 5.0, font = Font.find("calcutta-big")).enable()
    }

}



