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

import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.codegen.Visibility
import org.deltacv.visiongraph.codegen.build.*
import org.deltacv.visiongraph.node.vision.ColorSpace

class ScopeCtx(val scope: Scope) : LanguageCtx(scope.language) {

    operator fun String.invoke(vararg parameters: Value) {
        scope.methodCall(this, *parameters)
    }

    operator fun Type.invoke(method: String, vararg parameters: Value) {
        scope.methodCall(this, method, *parameters)
    }

    operator fun Value.invoke(method: String, vararg parameters: Value) {
        scope.methodCall(this, method, *parameters)
    }

    private var isFirstGroup = true

    fun group(block: () -> Unit) {
        if(!isFirstGroup) {
            separate()
        }

        isFirstGroup = false

        block()
    }

    fun separate(separations: Int = 1) {
        repeat(separations) {
            scope.newLineIfNotBlank()
        }
    }

    fun streamMat(id: Int, mat: Value, matColor: ColorSpace = ColorSpace.RGB) {
        streamMat(id, mat, Resolvable.Now(matColor))
    }

    fun streamMat(id: Int, mat: Value, matColor: Resolvable<ColorSpace> = Resolvable.Now(ColorSpace.RGB)) {
        scope.streamMat(id, mat, matColor)
    }

    fun MatAttribute.streamIfEnabled(mat: Value, matColor: Resolvable<ColorSpace> = Resolvable.Now(ColorSpace.RGB)) {
        if(displayWindow != null) {
            streamMat(displayWindow!!.imageDisplay.id, mat, matColor)
        }
    }

    infix fun String.local(v: Value) =
        scope.localVariable(DeclarableVariable(this, v))

    fun findNullables(vararg value: Value) = scope.findNullables(*value)

    fun public(variable: DeclarableVariable, label: String? = null) =
        scope.instanceVariable(Visibility.PUBLIC, variable, label)
    fun private(variable: DeclarableVariable) =
        scope.instanceVariable(Visibility.PRIVATE, variable)
    fun protected(variable: DeclarableVariable) =
        scope.instanceVariable(Visibility.PROTECTED, variable)
    fun packagePrivate(variable: DeclarableVariable) =
        scope.instanceVariable(Visibility.PACKAGE_PRIVATE, variable)

    fun local(v: DeclarableVariable) = scope.localVariable(v)

    fun instanceVariable(
        vis: Visibility, variable: DeclarableVariable, label: String? = null,
        isStatic: Boolean = false, isFinal: Boolean = false
    ) = scope.instanceVariable(vis, variable, label, isStatic, isFinal)

    infix fun DeclarableVariable.set(v: Value) =
        scope.variableSet(this, v)

    fun Value.arraySet(index: Value, v: Value) =
        scope.arraySet(this, index, v)

    operator fun Value.set(index: Value, v: Value) = arraySet(index, v)

    infix fun DeclarableVariable.instanceSet(v: Value) =
        scope.instanceVariableSet(this, v)

    fun ifCondition(condition: Condition?, block: ScopeCtx.() -> Unit): IfChainCtx {
        if (condition == null) {
            block(this)
            return IfChainCtx(scope, null, false)
        }

        val ifScope = Scope(scope.tabsCount + 1, scope.language, scope.importScope)
        block(ScopeCtx(ifScope))

        val chain = scope.ifCondition(condition, ifScope)

        return IfChainCtx(scope, chain, true)
    }

    fun <T: Value> foreach(variable: T, list: Value, block: ScopeCtx.(T) -> Unit) {
        val loopScope = Scope(scope.tabsCount + 1, scope.language, scope.importScope)
        block(ScopeCtx(loopScope), variable)

        scope.foreachLoop(variable, list, loopScope)
    }

    fun <T: Value> forLoop(variable: T, start: Value, max: Value, step: Value?, block: ScopeCtx.(T) -> Unit) {
        val loopScope = Scope(scope.tabsCount + 1, scope.language, scope.importScope)
        block(ScopeCtx(loopScope), variable)

        scope.forLoop(variable, start, max, step, loopScope)
    }

    fun <T: Value> forLoop(variable: T, start: Value, max: Value, block: ScopeCtx.(T) -> Unit) =
        forLoop(variable, start, max, null, block)

    fun constructor(
        vis: Visibility, clazz: Type, vararg parameters: Parameter, block: ScopeCtx.() -> Unit
    ) {
        val constructorScope = Scope(scope.tabsCount + 1, scope.language, scope.importScope)
        block(ScopeCtx(constructorScope))

        scope.constructor(vis, clazz.className, constructorScope, *parameters)
    }

    fun deferredBlock(resolvable: Resolvable<ScopeCtx.() -> Unit>) {
        val block = resolvable.resolve()

        if(block != null) {
            block(ScopeCtx(scope))
        } else {
            val placeholder = Resolvable.DependentPlaceholder(resolvable) {
                val newScope = Scope(scope.tabsCount, scope.language, scope.importScope)
                it(ScopeCtx(newScope))

                newScope.get()
            }

            scope.write(placeholder.key)
        }
    }

    fun method(
        vis: Visibility, returnType: Type, name: String,
        vararg parameters: Parameter, isStatic: Boolean = false,
        isFinal: Boolean = false, isOverride: Boolean = false,
        isSynchronized: Boolean = false, block: ScopeCtx.() -> Unit
    ) {
        val methodScope = Scope(scope.tabsCount + 1, scope.language, scope.importScope)
        block(ScopeCtx(methodScope))

        scope.method(vis, returnType, name, methodScope, *parameters, isStatic = isStatic, isFinal = isFinal, isSynchronized = isSynchronized, isOverride = isOverride)
    }

    fun returnMethod(value: Value? = null) {
        scope.returnMethod(value)
    }

    fun clazz(
        vis: Visibility, name: String,
        extends: Type? = null, vararg implements: Type,
        isStatic: Boolean = false, isFinal: Boolean = false,
        block: ScopeCtx.() -> Unit
    ) {
        val clazzScope = Scope(scope.tabsCount + 1, scope.language, scope.importScope)
        block(ScopeCtx(clazzScope))

        scope.clazz(vis, name, clazzScope, extends, *implements, isStatic = isStatic, isFinal = isFinal)
    }

    fun comment(text: String) {
        scope.comment(text)
    }

    class IfChainCtx(
        private val scope: Scope,
        private val chain: Scope.IfChain?,
        private val enabled: Boolean
    ) {
        fun elseIf(condition: Condition, block: ScopeCtx.() -> Unit): IfChainCtx {
            if (!enabled) return this

            val elseIfScope = Scope(scope.tabsCount + 1, scope.language, scope.importScope)
            block(ScopeCtx(elseIfScope))

            chain!!.elseIf(condition, elseIfScope)
            return this
        }

        fun elseCondition(block: ScopeCtx.() -> Unit) {
            if (!enabled) return

            val elseScope = Scope(scope.tabsCount + 1, scope.language, scope.importScope)
            block(ScopeCtx(elseScope))

            chain!!.elseCondition(elseScope)
        }
    }

}



