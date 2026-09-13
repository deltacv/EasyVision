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

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.codegen.build.Scope
import org.deltacv.visiongraph.codegen.dsl.CodeGenCtx
import org.deltacv.visiongraph.codegen.language.Language
import org.deltacv.visiongraph.codegen.resolve.PlaceholderResolver
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.util.loggerFor

enum class Visibility {
    PUBLIC, PRIVATE, PROTECTED, PACKAGE_PRIVATE
}

class CodeGen(
    var className: String,
    val language: Language,
    val isForPreviz: Boolean = false
) {

    companion object {
        val logger by loggerFor<CodeGen>()
    }

    val importScope = Scope(0, language)
    val importScopePlaceholder = Resolvable.Placeholder(resolveLast = true) {
        importScope
    }

    val classStartScope = Scope(1, language, importScope, isForPreviz)
    val classEndScope   = Scope(1, language, importScope, isForPreviz)

    val initScope = Scope(2, language, importScope, isForPreviz)
    val processFrameScope = Scope(2, language, importScope, isForPreviz)
    val viewportTappedScope = Scope(2, language, importScope, isForPreviz)

    val current = Current(this, processFrameScope, isForPreviz)

    internal val sessions = mutableMapOf<Generator<*, *>, CodeGenSession>()

    private val busyNodes = mutableListOf<Generator<*, *>>()
    private val busyAttributes = mutableListOf<Attribute>()

    fun isBusy(node: Generator<*, *>) = busyNodes.contains(node)
    fun markBusy(node: Generator<*, *>) = busyNodes.add(node)
    fun unmarkBusy(node: Generator<*, *>) = busyNodes.remove(node)

    fun isBusy(attribute: Attribute) = busyAttributes.contains(attribute)
    fun markBusy(attribute: Attribute) = busyAttributes.add(attribute)
    fun unmarkBusy(attribute: Attribute) = busyAttributes.remove(attribute)

    internal val endingNodes = mutableListOf<GenNode<*>>()

    private val flags = mutableListOf<String>()

    private val placeholderResolver = PlaceholderResolver(importScope)

    enum class Stage {
        CREATION, INITIAL_GEN, END_GEN, ENDED_SUCCESS, ENDED_ERROR
    }

    var stage = Stage.CREATION

    fun build(): String {
        if(endingNodes.isNotEmpty()) logger.info("-- Ending Nodes --")
        for(node in endingNodes) {
            node.genCodeIfNecessary(current)
        }

        importScope.initializeTypes(current)

        val raw = language.build(this)
        logger.debug("Pre-placeholders generated source code:\n{}", raw)

        val resolved = placeholderResolver.resolve(raw)
        logger.debug("Post-placeholders generated source code:\n{}", resolved)

        return resolved
    }

    fun addFlag(flag: String) = if(!flags.contains(flag)) flags.add(flag) else false
    fun hasFlag(flag: String) = flags.contains(flag)
    fun flags() = flags.toTypedArray()

    val context = CodeGenCtx(this)

    inline operator fun <T> invoke(crossinline block: CodeGenCtx.() -> T) = block(context)

    interface LanguageHolder {
        val language: Language
    }
    interface ScopeHolder {
        val scope: Scope
    }

    data class Current(val codeGen: CodeGen, override val scope: Scope, val isForPreviz: Boolean) : LanguageHolder, ScopeHolder {
        override val language get() = codeGen.language

        @Suppress("UNCHECKED_CAST")
        fun <S: CodeGenSession> sessionOf(node: Generator<*, S>) = codeGen.sessions[node] as S?

        fun <S: CodeGenSession> nonNullSessionOf(node: Generator<Unit, S>) = sessionOf(node) ?: run {
            if(node is GenNode<*>) {
                node.genCodeIfNecessary(this)
            }

            this@Current.sessionOf(node)
                ?: throw IllegalStateException("Node ${node::class.simpleName} did not generate a session when requested")
        }

        inline operator fun <R> invoke(crossinline scopeBlock: CodeGenCtx.() -> R) = codeGen.invoke(scopeBlock)
    }

}

data class CodeGenOptions(var genAtTheEnd: Boolean = false)

interface CodeGenSession
object NoSession : CodeGenSession