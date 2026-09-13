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

package org.deltacv.visiongraph.codegen.build

import org.deltacv.visiongraph.codegen.dsl.ScopeCtx
import org.deltacv.visiongraph.codegen.*
import org.deltacv.visiongraph.codegen.language.Language
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.node.vision.ColorSpace

data class Scope(
    val tabsCount: Int = 1,
    val language: Language,
    val importScope: Scope? = null,
    val isForPreviz: Boolean = false
) : CodeGen.ScopeHolder {

    override val scope = this

    private var builder = StringBuilder()

    private val usedNames = mutableListOf<String>()

    private val tabs by lazy {
        val builder = StringBuilder()

        repeat(tabsCount) {
            builder.append("\t")
        }

        builder.toString()
    }

    private val importBuilder by lazy { language.newImportBuilder() }

    private val nullables: MutableList<DeclarableVariable> =
        importScope?.nullables ?: mutableListOf()

    private val typesToInitialize: MutableList<Type> =
        importScope?.typesToInitialize ?: mutableListOf()

    init {
        if(importScope == this) {
            throw IllegalArgumentException("Import scope cannot be itself")
        }
    }

    fun importType(vararg types: Type) {
        if(importScope != null) {
            importScope.importType(*types)
        } else {
            for (type in types) {
                if(type.hasInitializer && !typesToInitialize.contains(type)) {
                    typesToInitialize.add(type)
                }

                if (type.shouldImport) {
                    importBuilder.import(type)
                }
                for(generic in type.generics) {
                    importType(generic)
                }
            }
        }
    }

    private fun importValue(vararg values: Value) {
        for(value in values) {
            if(value == Value.NONE) continue
            importType(*value.imports.toTypedArray())
        }
    }

    private fun handleNullability(variable: DeclarableVariable) {
        if(variable.isNullable) {
            nullables.add(variable)
        }
    }

    fun findNullables(vararg values: Value): List<DeclarableVariable> {
        val result = mutableListOf<DeclarableVariable>()

        for(variable in nullables) {
            for(value in values) {
                if(variable.name in (value.value ?: "") && !result.contains(variable)) {
                    result.add(variable)
                }
            }
        }

        return result
    }

    fun initializeTypes(current: CodeGen.Current) {
        for(type in typesToInitialize) {
            type.initialize(current)
        }
    }

    fun instanceVariable(vis: Visibility, variable: DeclarableVariable, label: String? = null,
                         isStatic: Boolean = false, isFinal: Boolean = false) {
        newStatement()
        usedNames.add(variable.name)

        handleNullability(variable)

        val pair = language.instanceVariableDeclaration(
            vis, variable,
            if(isForPreviz) label else null, // labels are ignored in non-previsualization mode
            isStatic, isFinal
        )

        // import after asking language for the declaration because the language
        // may add additional imports to the variable (e.g. annotation types).
        importValue(variable)

        pair.first?.let {
            builder.appendLine("$tabs$it")
        }
        builder.append("$tabs${pair.second}")
    }
    
    fun localVariable(variable: DeclarableVariable) {
        newStatement()
        usedNames.add(variable.name)
        importValue(variable)

        handleNullability(variable)

        builder.append("$tabs${language.localVariableDeclaration(variable)}")
    }

    fun tryName(name: String, allocate: Boolean = false): String {
        if (name !in usedNames) {
            if(allocate) {
                usedNames += name
            }

            return name
        }

        var count = 1
        var newName: String

        do {
            newName = "$name$count"
            count++
        } while (newName in usedNames)

        if(allocate) {
            usedNames += newName
        }

        return newName
    }

    fun variableSet(variable: DeclarableVariable, v: Value) {
        newStatement()
        importValue(v)

        builder.append("$tabs${language.variableSetDeclaration(variable, v)}")
    }

    fun arraySet(value: Value, index: Value, v: Value) {
        newStatement()
        importValue(v)

        builder.append("$tabs${language.arrayValueSetDeclaration(value, index, v)}")
    }

    fun instanceVariableSet(variable: DeclarableVariable, v: Value) {
        newStatement()
        importValue(v)

        builder.append("$tabs${language.instanceVariableSetDeclaration(variable, v)}")
    }

    fun methodCall(className: Type, methodName: String, vararg parameters: Value) {
        newStatement()
        importType(className)
        importValue(*parameters)
        
        builder.append("$tabs${language.methodCallDeclaration(className, methodName, *parameters)}")
    }

    fun methodCall(callee: Value, methodName: String, vararg parameters: Value) {
        newStatement()
        importValue(callee, *parameters)

        builder.append("$tabs${language.methodCallDeclaration(callee, methodName, *parameters)}")
    }

    fun streamMat(id: Int, mat: Value, matColor: Resolvable<ColorSpace> = Resolvable.Now(ColorSpace.RGB)) {
        if(isForPreviz) {
            newStatement()

            val cvtCode = Resolvable.DependentPlaceholder(matColor) {
                if(it != ColorSpace.RGB) {
                    language.cvtColorValue(it, ColorSpace.RGB)
                } else Value.NONE
            }

            val declaration = Resolvable.DependentPlaceholder(cvtCode) {
                importValue(it)
                language.streamMatCallDeclaration(language.int(id), mat, it)
            }.key

            builder.append("$tabs$declaration")
        }
    }

    fun methodCall(methodName: String, vararg parameters: Value) {
        newStatement()
        importValue(*parameters)

        builder.append("$tabs${language.methodCallDeclaration(methodName, *parameters)}")
    }


    fun constructor(
        vis: Visibility, className: String,
        body: Scope, vararg parameters: Parameter
    ) {
        newLineIfNotBlank()

        for(parameter in parameters) {
            importType(parameter.type)
        }

        builder.append(language.block(language.constructorDeclaration(vis, className, *parameters), body, tabsCount))
    }

    fun method(
        vis: Visibility, returnType: Type, name: String, body: Scope,
        vararg parameters: Parameter,
        isStatic: Boolean = false, isFinal: Boolean = false,
        isSynchronized: Boolean = false, isOverride: Boolean = false,
        indentOverride: Int? = null
    ) {
        newLineIfNotBlank()

        for(parameter in parameters) {
            importType(parameter.type)
        }

        val methodDeclaration = language.methodDeclaration(
            vis, returnType, name, *parameters,
            isStatic = isStatic, isFinal = isFinal, isSynchronized = isSynchronized, isOverride = isOverride
        )

        if(methodDeclaration.first?.trim()?.isNotEmpty() == true) {
            for(line in methodDeclaration.first!!.split("\n")) {
                builder.append("$tabs$line").appendLine()
            }
        }

        builder.append(language.block(methodDeclaration.second, body, indentOverride ?: tabsCount))
    }

    fun returnMethod(value: Value? = null) {
        newStatement()
        if(value != null) importValue(value)

        builder.append("$tabs${language.returnDeclaration(value)}")
    }

    fun clazz(
        vis: Visibility, name: String, body: Scope,
        extends: Type? = null, vararg implements: Type,
        isStatic: Boolean = false, isFinal: Boolean = false
    ) {
        if(extends != null) importType(extends)
        importType(*implements)

        newLineIfNotBlank()

        builder.append(language.block(
            language.classDeclaration(vis, name, body, extends, *implements, isStatic = isStatic, isFinal = isFinal),
            body, tabsCount
        ))
    }

    fun enumClass(name: String, vararg values: String) {
        newStatement()

        builder.append("$tabs${language.enumClassDeclaration(name, *values)}")
    }

    private fun block(block: String, scope: Scope) {
        newStatement()
        builder.append(language.block(block, scope, tabsCount))
    }

    fun ifCondition(condition: Condition, scope: Scope): IfChain {
        newStatement()

        val declaration = language.ifStatementDeclaration(condition)
        builder.append(language.block(declaration, scope, tabsCount))

        return IfChain(this, tabsCount)
    }

    fun whileLoop(condition: Condition, scope: Scope) = block(language.whileLoopDeclaration(condition), scope)

    fun foreachLoop(variable: Value, iterable: Value, scope: Scope) {
        importValue(variable, iterable)

        block(
            language.foreachLoopDeclaration(variable, iterable),
            scope
        )
    }

    fun forLoop(variable: Value, start: Value, max: Value, step: Value?, scope: Scope) {
        importValue(variable)

        block(language.forLoopDeclaration(variable, start, max, step), scope)
    }

    fun scope(scope: Scope, indentOverride: Int? = null) {
        newLineIfNotBlank()

        builder.append(
            if(indentOverride != null && indentOverride >= 0)
                scope.toString().trimIndent().prependIndent("\t".repeat(indentOverride))
            else scope.toString()
        )
    }

    fun comment(text: String) {
        newStatement()
        // handle if text is multiline, add $tabs to each line
        val lines = if(text.contains("\n")) {
            language.comment(text).split("\n").joinToString("\n$tabs") { it.trim() }
        } else {
            language.comment(text)
        }

        builder.append("$tabs$lines")
    }

    fun newStatement() {
        if(builder.isNotEmpty()) {
            builder.appendLine()
        }
    }

    fun separateStatement() {
        while(builder.isNotEmpty() && builder[builder.length - 1].isWhitespace()) {
            builder.deleteCharAt(builder.length - 1)
        }

        if(builder.isNotEmpty()) {
            builder.appendLine()
        }
    }

    fun newLineIfNotBlank() {
        val str = get()

        if(!str.endsWith("\n\n") && str.endsWith("\n")) {
            builder.appendLine()
        } else if(!str.endsWith("\n\n")) {
            builder.append("\n")
        }
    }

    fun write(str: String) {
        newLineIfNotBlank()
        builder.append(str)
    }

    fun clear() = builder.clear()

    fun get() = importBuilder.build() + builder.toString()

    override fun toString() = get()

    inline operator fun <R> invoke(separate: Boolean = true, crossinline block: ScopeCtx.() -> R): R {
        val result = block(ScopeCtx(this))

        if(separate) {
            newStatement()
        }

        return result
    }

    fun <T> deferred(dependency: Resolvable<T>, separate: Boolean = true, block: ScopeCtx.(T) -> Unit) {
        val placeholder = Resolvable.DependentPlaceholder(dependency) { value ->
            val scope = copy()
            scope(separate = separate) { block(value) }
            scope.toString()
        }

        write(placeholder.resolve() ?: placeholder.key)
    }

    class IfChain(
        private val parent: Scope,
        private val baseIndent: Int
    ) {
        private fun appendBranch(branch: String, scope: Scope) {
            while(parent.builder.isNotEmpty() && parent.builder[parent.builder.length - 1].isWhitespace()) {
                parent.builder.deleteCharAt(parent.builder.length - 1)
            }

            parent.builder.append(' ')
            parent.builder.append(
                parent.language.block(
                    branch,
                    scope,
                    baseIndent
                ).trimStart()
            )
        }

        fun elseIf(condition: Condition, scope: Scope): IfChain {
            appendBranch(parent.language.elseIfStatementDeclaration(condition), scope)
            return this
        }

        fun elseCondition(scope: Scope) {
            appendBranch(parent.language.elseStatementDeclaration(), scope)
        }
    }

}

data class Parameter(override val type: Type, val name: String, val isFinal: Boolean = false) : Value() {
    override val value = name
}

