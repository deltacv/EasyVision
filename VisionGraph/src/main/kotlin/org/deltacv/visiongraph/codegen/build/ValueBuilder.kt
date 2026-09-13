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

import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.language.Language
import org.deltacv.visiongraph.node.vision.ColorSpace

interface ValueBuilder {

    val language: Language

    val trueValue: Value
    val falseValue: Value

    fun nullVal(type: Type): Value

    fun lessThan(left: Value, right: Value) = condition("${left.value} < ${right.value}")
    fun lessOrEqualThan(left: Value, right: Value) = condition("${left.value} <= ${right.value}")

    fun greaterThan(left: Value, right: Value) = condition("${left.value} > ${right.value}")
    fun greaterOrEqualThan(left: Value, right: Value) = condition("${left.value} >= ${right.value}")

    fun equals(left: Value, right: Value) = condition("${left.value} == ${right.value}")
    fun notEquals(left: Value, right: Value) = condition("${left.value} != ${right.value}")

    fun objectEquals(left: Value, right: Value) = condition("${left.value}.equals(${right.value})")

    fun and(left: Condition, right: Condition) = condition("(${left.value}) && (${right.value})")
    fun or(left: Condition, right: Condition) = condition("(${left.value}) || (${right.value})")

    fun not(condition: Condition) = condition("!(${condition.value})").apply {
        additionalImports(condition)
    }

    fun isInstanceOf(value: Value, type: Type): Condition
    fun isNotInstanceOf(value: Value, type: Type) = not(isInstanceOf(value, type))

    fun condition(value: String) = Condition(language.BooleanType, value)
    fun condition(value: Value) = Condition(language.BooleanType, value.value!!)

    fun sum(a: Value, b: Value) =
        operation(
            determineRelevantNumberType(a.type, b.type),
            "${a.toStringAndWrapIfOp()} + ${b.toStringAndWrapIfOp()}"
        )

    fun subtraction(a: Value, b: Value) =
        operation(
            determineRelevantNumberType(a.type, b.type),
            "${a.toStringAndWrapIfOp()} - ${b.toStringAndWrapIfOp()}"
        )

    fun multiplication(a: Value, b: Value) =
        operation(
            determineRelevantNumberType(a.type, b.type),
            "${a.toStringAndWrapIfOp()} * ${b.toStringAndWrapIfOp()}"
        )

    fun division(a: Value, b: Value) =
        operation(
            determineRelevantNumberType(a.type, b.type),
            "${a.toStringAndWrapIfOp()} / ${b.toStringAndWrapIfOp()}"
        )

    fun operation(relevantNumberType: Type, operation: String) = Operation(relevantNumberType, operation)

    fun determineRelevantNumberType(a: Type, b: Type) =
        if(a == language.DoubleType || b == language.DoubleType) {
            language.DoubleType
        } else if(a == language.FloatType || b == language.FloatType) {
            language.FloatType
        } else if(a == language.LongType || b == language.LongType) {
            language.LongType
        } else language.IntType

    fun Value.toStringAndWrapIfOp() = if(this is Operation) {
        "(${value})"
    } else value

    fun string(value: String): Value
    fun string(value: Value): Value

    fun new(type: Type, vararg parameters: Value): Value

    fun arrayOf(type: Type): Type

    fun newArrayOf(type: Type, size: Value): Value

    fun newArrayOfValues(type: Type, vararg values: Value): Value

    fun arraySize(array: Value): Value

    fun value(type: Type, value: String) = ConValue(type, value)

    fun castValue(value: Value, castTo: Type, forceCast: Boolean = false): Value

    fun callValue(methodName: String, returnType: Type, vararg parameters: Value): Value
    fun callValue(classType: Type, methodName: String, returnType: Type, vararg parameters: Value): Value
    fun callValue(callee: Value, methodName: String, returnType: Type, vararg parameters: Value): Value

    fun propertyValue(from: Value, property: String, type: Type): Value
    fun propertyVariable(from: Value, property: String, type: Type): DeclarableVariable

    fun arrayValue(from: Value, index: Value, type: Type): Value

    fun arrayValueMultiple(from: Value, indices: List<Value>, type: Type): Value

    fun enumValue(type: Type, constantName: String) = ConValue(type, "$type.$constantName")

    fun cvtColorValue(a: ColorSpace, b: ColorSpace): Value {
        var newA = a.name
        var newB = b.name

        if(a == ColorSpace.RGBA && b != ColorSpace.RGB) {
            newA = "RGB"
        } else if(a != ColorSpace.RGB && b == ColorSpace.RGBA) {
            newB = "RGB"
        }

        if(a == ColorSpace.LAB) {
            newA = "Lab"
        }
        if(b == ColorSpace.LAB) {
            newB = "Lab"
        }

        return ConValue(language.IntType, "Imgproc.COLOR_${newA}2${newB}").apply {
            additionalImports(JvmOpenCv.Imgproc)
        }
    }

    fun cvTypeValue(cvType: String): Value {
        return ConValue(language.IntType, "CvType.$cvType").apply {
            additionalImports(JvmOpenCv.CvType)
        }
    }

}



