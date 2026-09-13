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

object JythonLanguage : BaseLanguage(
    usesSemicolon = false,
    genInClass = false,
    optimizeImports = false
) {

    override val Parameter.string get() = name

    override val trueValue = ConValue(BooleanType, "True")
    override val falseValue = ConValue(BooleanType, "False")

    override fun newImportBuilder() = PythonImportBuilder(this)

    object jarray {
        val array = Type("array", "jarray")
        val zeros = Type("zeros", "jarray")
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
            "def $name(${parameters.csv()})"
        )
    }

    override fun ifStatementDeclaration(condition: Condition) = "if ${condition.value}"

    override fun forLoopDeclaration(variable: Value, start: Value, max: Value, step: Value?) =
        "for ${variable.value} in range(${start.value}, ${max.value}${step?.let { ", $it" } ?: ""})"

    override fun foreachLoopDeclaration(variable: Value, iterable: Value) =
        "for ${variable.value} in ${iterable.value}"

    override fun classDeclaration(
        vis: Visibility,
        name: String,
        body: Scope,
        extends: Type?,
        vararg implements: Type,
        isStatic: Boolean,
        isFinal: Boolean
    ): String {
        throw UnsupportedOperationException("Class declarations are not supported in Python")
    }

    override fun enumClassDeclaration(name: String, vararg values: String): String {
        val builder = StringBuilder()

        for(value in values) {
            builder.append("$value: \"$value\"").appendLine()
        }

        return """var $name = {  
            |${builder.toString().trim()}
            |}""".trimMargin()
    }

    override fun castValue(value: Value, castTo: Type, forceCast: Boolean) = ConValue(castTo, value.value)

    override fun newArrayOf(type: Type, size: Value): ConValue {
        val t = when(type) {
            BooleanType -> "z"
            IntType -> "i"
            LongType -> "l"
            FloatType -> "f"
            DoubleType -> "d"

            else -> type.className
        }

        return ConValue(arrayOf(type), "zeros(${size.value}, $t)").apply {
            additionalImports(jarray.zeros)
        }
    }

    override fun arraySize(array: Value) = ConValue(IntType, "len(${array.value})")

    override fun block(start: String, body: Scope, indent: Int): String {
        val bodyStr = body.get().trimIndent().prependIndent("\t".repeat(indent + 1))
        val startIndent = "\t".repeat(indent)

        return "$startIndent${start.trim()}:\n$bodyStr"
    }

    override fun importDeclaration(importPath: String, className: String) =
        throw UnsupportedOperationException("importDeclaration(importPath, className) is not supported in Python")

    override fun new(type: Type, vararg parameters: Value) = ConValue(
        type, "${type.className}(${parameters.csv()})"
    )

    override fun nullVal(type: Type) = ConValue(type, "None")

    class PythonImportBuilder(val lang: Language) : Language.ImportBuilder {
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

            for((importPath, classNames) in imports) {
                builder.appendLine().append("from $importPath import ")

                if(classNames.size > 1) builder.append("(")

                for((i, className) in classNames.withIndex()) {
                    builder.append(className)

                    if(i != classNames.size -1) builder.append(", ")
                }

                if(classNames.size > 1) builder.append(")")
            }

            return builder.toString().trim()
        }

    }

}



