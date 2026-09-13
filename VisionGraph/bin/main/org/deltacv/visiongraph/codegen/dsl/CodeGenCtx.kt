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

package org.deltacv.visiongraph.codegen.dsl

import org.deltacv.visiongraph.codegen.*
import org.deltacv.visiongraph.codegen.build.*
import org.deltacv.visiongraph.codegen.resolve.Resolvable

class CodeGenCtx(val codeGen: CodeGen) : LanguageCtx(codeGen.language) {

    fun enum(name: String, vararg values: String) {
        codeGen.classStartScope.enumClass(name, *values)
    }

    fun initScope(block: ScopeCtx.() -> Unit) {
        codeGen.initScope(block = block)
    }

    fun processFrameScope(block: ScopeCtx.() -> Unit) {
        codeGen.processFrameScope(block = block)
    }

    fun onViewportTappedScope(block: ScopeCtx.() -> Unit) {
        codeGen.viewportTappedScope(block = block)
    }

    private var isFirstGroup = true
    fun group(block: ScopeCtx.() -> Unit) {
        if(!isFirstGroup) {
            codeGen.classStartScope.separateStatement()
        }
        isFirstGroup = false

        codeGen.classStartScope(separate = false) { block(this) }
    }

    private var isFirstDeferredGroup = true
    fun <T> deferredGroup(dependency: Resolvable<T>, block: ScopeCtx.(T) -> Unit) {
        if(!isFirstDeferredGroup) {
            codeGen.classStartScope.separateStatement()
        }
        isFirstDeferredGroup = false

        codeGen.classStartScope.deferred(dependency, separate = false) {
            block(it)
        }
    }

    fun uniqueVariable(name: String, value: Value, isNullable: Boolean = false, allocateName: Boolean = false) =
        variable(tryName(name, allocate = allocateName), value, isNullable)

    fun tryName(name: String, allocate: Boolean = false) =
        codeGen.classStartScope.tryName(name, allocate = allocate)

    operator fun String.invoke(
        vis: Visibility, returnType: Type,
        vararg parameters: Parameter,
        isStatic: Boolean = false, isFinal: Boolean = false, isOverride: Boolean = true,
        scopeBlock: ScopeCtx.() -> Unit
    ) {
        val s = Scope(2, codeGen.language)
        scopeBlock(ScopeCtx(s))

        codeGen.classEndScope.method(
            vis, returnType, this, s, *parameters,
            isStatic = isStatic, isFinal = isFinal, isOverride = isOverride
        )
    }

}



