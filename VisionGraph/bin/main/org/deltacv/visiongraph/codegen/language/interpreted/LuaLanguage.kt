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

package org.deltacv.visiongraph.codegen.language.interpreted

import org.deltacv.visiongraph.codegen.Visibility
import org.deltacv.visiongraph.codegen.build.*
import org.deltacv.visiongraph.codegen.csv
import org.deltacv.visiongraph.codegen.language.Language
import org.deltacv.visiongraph.codegen.language.BaseLanguage

object LuaLanguage : BaseLanguage(
    usesSemicolon = false,
    genInClass = false,
    optimizeImports = false
) {
    override val Parameter.string get() = name

    override val trueValue = ConValue(BooleanType, "true")
    override val falseValue = ConValue(BooleanType, "false")

    override fun newImportBuilder() = LuaImportBuilder(this)

    object java {
        val array = Type("array", "java")
    }

    override fun and(left: Condition, right: Condition) = condition("(${left.value}) and (${right.value})")
    override fun or(left: Condition, right: Condition) = condition("(${left.value}) or (${right.value})")

    override fun not(condition: Condition) = condition("not (${condition.value})")

    override fun instanceVariableDeclaration(
        vis: Visibility,
        variable: DeclarableVariable,
        label: String?,
        isStatic: Boolean,
        isFinal: Boolean
    ) = Pair(
        if(label != null) {
            "label(\"$label\", \"${variable.name}\")"
        } else null,
        "${variable.name} = ${variable.variableValue.value}${semicolonIfNecessary()}"
    )

    override fun localVariableDeclaration(
        variable: DeclarableVariable,
        isFinal: Boolean
    ) = instanceVariableDeclaration(Visibility.PUBLIC, variable).second

    override fun instanceVariableSetDeclaration(variable: DeclarableVariable, v: Value) = "${variable.name} = ${v.value!!}" + semicolonIfNecessary()

    override fun streamMatCallDeclaration(id: Value, mat: Value, cvtColor: Value) =
        methodCallDeclaration("stream", id, mat, cvtColor)

    override fun methodDeclaration(
        vis: Visibility,
        returnType: Type,
        name: String,
        vararg parameters: Parameter,
        isStatic: Boolean,
        isFinal: Boolean,
        isSynchronized: Boolean,
        isOverride: Boolean
    ): Pair<String?, String> {
        return Pair("",
            "function $name(${parameters.csv()})"
        )
    }

    override fun methodCallDeclaration(className: Type, methodName: String, vararg parameters: Value) =
        "${className.className}:$methodName(${parameters.csv()})"

    override fun methodCallDeclaration(callee: Value, methodName: String, vararg parameters: Value) =
        "${callee.value}:$methodName(${parameters.csv()})"

    override fun ifStatementDeclaration(condition: Condition) = "if ${condition.value}"

    override fun forLoopDeclaration(variable: Value, start: Value, max: Value, step: Value?) =
        "for ${variable.value}=${start.value},${max.value},${step?.let { ", $it" } ?: ""}) do"

    override fun foreachLoopDeclaration(variable: Value, iterable: Value) =
        "for i, ${variable.value} in ipairs(java.luaify(${iterable.value})) do"

    override fun classDeclaration(
        vis: Visibility,
        name: String,
        body: Scope,
        extends: Type?,
        vararg implements: Type,
        isStatic: Boolean,
        isFinal: Boolean
    ): String {
        throw UnsupportedOperationException("Class declarations are not supported in lua")
    }

    override fun enumClassDeclaration(name: String, vararg values: String): String {
        val builder = StringBuilder()

        for (value in values) {
            builder.append("$value = \"$value\"").appendLine()
        }

        return """local $name = {  
            |${builder.toString().trim()}
            |}""".trimMargin()
    }

    override fun callValue(classType: Type, methodName: String, returnType: Type, vararg parameters: Value) =
        ConValue(returnType, "${classType.className}:$methodName(${parameters.csv()})").apply {
            additionalImports(classType)
            additionalImports(*parameters)
        }

    override fun castValue(value: Value, castTo: Type, forceCast: Boolean) = ConValue(castTo, value.value)

    override fun newArrayOf(type: Type, size: Value): ConValue {
        return ConValue(arrayOf(type), "java.array(${type})").apply {
            additionalImports(type)
        }
    }

    override fun arraySize(array: Value) = ConValue(IntType, "#${array.value}")

    override fun block(start: String, body: Scope, indent: Int): String {
        val bodyStr = body.get().trimIndent().prependIndent("\t".repeat(indent + 1))
        val startIndent = "\t".repeat(indent)

        return "$startIndent$start\n$bodyStr\n${startIndent}end"
    }

    override fun importDeclaration(importPath: String, className: String) =
        throw UnsupportedOperationException("importDeclaration(importPath, className) is not supported in Lua")

    override fun new(type: Type, vararg parameters: Value) = ConValue(
        type, "${type.className}(${parameters.csv()})"
    )

    override fun nullVal(type: Type) = ConValue(type, "nil")

    class LuaImportBuilder(val lang: Language) : Language.ImportBuilder {
        private val imports = mutableMapOf<String, MutableList<String>>()

        override fun import(type: Type) {
            val actualType = type.overridenImport ?: type

            if(lang.isImportExcluded(actualType) || !actualType.shouldImport) return

            val classNames = imports[actualType.packagePath]

            if(classNames == null) {
                imports[actualType.packagePath] = mutableListOf(actualType.className)
            } else if(!classNames.contains(actualType.className)){
                classNames.add(actualType.className)
            }
        }

        override fun build(): String {
            val builder = StringBuilder()

            for ((importPath, classNames) in imports) {
                for (className in classNames) {
                    builder.appendLine("$className = java.import('$importPath.$className')")
                }
            }

            builder.append("""
                function forEachSmart(array, func)
                    for i, v in ipairs(array) do
                        func(v)
                    end
                end
            """.trimIndent())

            return builder.toString().trim()
        }

    }

}



