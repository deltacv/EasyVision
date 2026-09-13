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

import org.deltacv.visiongraph.codegen.build.Scope
import org.deltacv.visiongraph.codegen.dsl.ScopeCtx
import org.deltacv.visiongraph.util.loggerForThis

interface GenNode<S: CodeGenSession> : PolyglotGenerator<Unit, S> {

    val genOptions: CodeGenOptions

    val genNodeName: String?
        get() = null

    fun codeGenPropagate(current: CodeGen.Current)

    /**
     * Generates code if there's not a session in the current CodeGen
     * Automatically propagates to all the nodes attached to the output
     * attributes after genCode finishes. Called by default on onPropagateReceive()
     */
    @Suppress("UNCHECKED_CAST")
    fun genCodeIfNecessary(current: CodeGen.Current) {
        val logger = loggerForThis().value

        val codeGen = current.codeGen

        if(genOptions.genAtTheEnd && codeGen.stage != CodeGen.Stage.END_GEN) {
            if(!codeGen.endingNodes.contains(this)) {
                logger.debug("Marked {} as an ending node", this)
                codeGen.endingNodes.add(this)
            }

            return
        }

        val session = codeGen.sessions[this]

        if(session == null) {
            // prevents duplicate code in weird edge cases
            // (it's so hard to consider and test every possibility with nodes...)
            if(!codeGen.isBusy(this)) {
                codeGen.markBusy(this)

                val name = genNodeName

                logger.debug("Generating code for {}", name ?: this)

                codeGen.sessions[this] = genCode(Unit, current)

                codeGen.unmarkBusy(this)

                logger.debug("DONE generating code for {}", name ?: this)

                codeGenPropagate(current)
            }
        }
    }

    fun Scope.nameComment() {
        val name = genNodeName
        if(name != null) {
            comment("\"$name\"")
        }
    }

    fun ScopeCtx.nameComment() = scope.nameComment()

}