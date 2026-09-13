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

package org.deltacv.visiongraph.codegen.language

import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.Visibility
import org.deltacv.visiongraph.codegen.build.*
import org.deltacv.visiongraph.codegen.build.language.StandardTypes
import org.deltacv.visiongraph.codegen.csv
import org.deltacv.visiongraph.codegen.dsl.LanguageCtx
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.exception.GenException

interface Language : ValueBuilder, CodeGen.LanguageHolder {

    val excludedImports: List<Type>

    val sourceFileExtension: String

    override val language get() = this

    val BooleanType get() = StandardTypes.cboolean

    val IntType get() = StandardTypes.cint
    val LongType get() = StandardTypes.clong
    val FloatType get() = StandardTypes.cfloat
    val DoubleType get() = StandardTypes.cdouble

    val VoidType get() = StandardTypes.cvoid

    val nullType get() = VoidType
    val nullValue get() = ConValue(nullType, "null")

    operator fun <R> invoke(block: LanguageCtx.() -> R) = LanguageCtx(this).block()

    fun newImportBuilder(): ImportBuilder

    fun Array<out Parameter>.csv(): String {
        val stringArray = this.map { it.string }.toTypedArray()
        return stringArray.csv()
    }

    val Parameter.string: String

    fun isImportExcluded(import: Type) = excludedImports.contains(import)

    fun boolean(value: Boolean) = if (value) trueValue else falseValue

    fun int(value: GenValue.Int): Resolvable<Value> = when (value) {
        is GenValue.Int.Actual -> value.value.map { int(it) }
        is GenValue.Int.Runtime -> value.value.map { int(it) }
    }

    fun int(value: Value) = when (value.type) {
        FloatType -> {
            val float = value.value?.toFloatOrNull()
            if (float == null) {
                castValue(value, language.IntType)
            } else {
                int(float.toInt())
            }
        }

        DoubleType -> {
            val double = value.value?.toDoubleOrNull()
            if (double == null) {
                castValue(value, language.IntType)
            } else {
                int(double.toInt())
            }
        }

        IntType -> value

        else -> throw GenException("Cannot convert value of type ${value.type} to Int")
    }

    fun int(value: Int) = ConValue(language.IntType, value.toString())

    fun long(value: Value) = castValue(value, language.LongType)
    fun long(value: Long) = ConValue(LongType, value.toString())

    fun float(value: GenValue.Float): Resolvable<Value> = when (value) {
        is GenValue.Float.Actual -> value.value.map { float(it) }
        is GenValue.Float.Runtime -> value.value.map { float(it) }
    }

    fun float(value: Value) = when (value.type) {
        IntType -> {
            val int = value.value?.toIntOrNull()
            if (int == null) {
                castValue(value, language.FloatType)
            } else {
                float(int.toFloat())
            }
        }

        DoubleType -> {
            val double = value.value?.toDoubleOrNull()
            if (double == null) {
                castValue(value, language.FloatType)
            } else {
                float(double.toFloat())
            }
        }

        FloatType -> value

        else -> throw GenException("Cannot convert value of type ${value.type} to Float")
    }

    fun float(value: Float): Value = ConValue(FloatType, value.toString())

    fun double(value: GenValue.Double): Resolvable<Value> = when (value) {
        is GenValue.Double.Actual -> value.value.map { double(it) }
        is GenValue.Double.Runtime -> value.value.map { double(it) }
    }

    fun double(value: Value) = when (value.type) {
        IntType -> {
            val int = value.value?.toIntOrNull()
            if (int == null) {
                castValue(value, language.DoubleType)
            } else {
                double(int.toDouble())
            }
        }

        FloatType -> {
            val float = value.value?.toFloatOrNull()
            if (float == null) {
                castValue(value, language.DoubleType)
            } else {
                double(float.toDouble())
            }
        }

        DoubleType -> value

        else -> throw GenException("Cannot convert value of type ${value.type} to Double")
    }

    fun double(value: Double) = ConValue(DoubleType, value.toString())

    fun instanceVariableDeclaration(
        vis: Visibility, variable: DeclarableVariable, label: String? = null,
        isStatic: Boolean = false, isFinal: Boolean = false
    ): Pair<String?, String>

    fun localVariableDeclaration(variable: DeclarableVariable, isFinal: Boolean = false): String

    fun variableSetDeclaration(variable: DeclarableVariable, v: Value): String
    fun arrayValueSetDeclaration(value: Value, index: Value, v: Value): String
    fun instanceVariableSetDeclaration(variable: DeclarableVariable, v: Value): String
    fun methodCallDeclaration(className: Type, methodName: String, vararg parameters: Value): String
    fun methodCallDeclaration(callee: Value, methodName: String, vararg parameters: Value): String

    fun methodCallDeclaration(methodName: String, vararg parameters: Value): String

    fun streamMatCallDeclaration(id: Value, mat: Value, cvtColor: Value): String

    fun constructorDeclaration(vis: Visibility, className: String, vararg parameters: Parameter): String

    fun methodDeclaration(
        vis: Visibility,
        returnType: Type,
        name: String,
        vararg parameters: Parameter,
        isStatic: Boolean = false,
        isFinal: Boolean = false,
        isSynchronized: Boolean = false,
        isOverride: Boolean = false
    ): Pair<String?, String>

    fun returnDeclaration(value: Value? = null): String

    fun ifStatementDeclaration(condition: Condition): String
    fun elseIfStatementDeclaration(condition: Condition): String
    fun elseStatementDeclaration(): String

    fun forLoopDeclaration(variable: Value, start: Value, max: Value, step: Value?): String
    fun foreachLoopDeclaration(variable: Value, iterable: Value): String
    fun whileLoopDeclaration(condition: Condition): String

    fun classDeclaration(
        vis: Visibility, name: String, body: Scope,
        extends: Type? = null, vararg implements: Type,
        isStatic: Boolean = false, isFinal: Boolean = false
    ): String

    fun enumClassDeclaration(name: String, vararg values: String): String

    fun comment(text: String): String

    fun block(start: String, body: Scope, indent: Int): String

    fun build(codeGen: CodeGen): String

    interface ImportBuilder {
        fun import(type: Type)

        fun build(): String
    }

}



