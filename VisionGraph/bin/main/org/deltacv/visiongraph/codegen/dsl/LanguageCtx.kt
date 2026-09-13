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

import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.codegen.build.*
import org.deltacv.visiongraph.codegen.language.Language
import org.deltacv.visiongraph.node.vision.ColorSpace

@Suppress("UNUSED")
open class LanguageCtx(override val language: Language): CodeGen.LanguageHolder {

    val Int.v get() = ConValue(language.IntType, toString())
    val Long.v get() = ConValue(language.LongType, toString())
    val Float.v get() = ConValue(language.FloatType, toString())
    val Double.v get() = ConValue(language.DoubleType, toString())
    val String.v get() = ConValue(Type.NONE, this)

    val GenValue.Number.v get() = value(this@LanguageCtx)

    val Resolvable<Double>.v @JvmName("vRDouble") get() = tryReturn({ it.v }, { it.v })
    val Resolvable<Int>.v @JvmName("vRInt") get() = tryReturn({ it.v }, { it.v })
    val Resolvable<Value>.v @JvmName("vRValue") get() = tryReturn({ it }, { it.v })
    val Resolvable<String>.v @JvmName("vRString") get() = tryReturn({ it.v }, { it.v })
    val Resolvable<ColorSpace>.v @JvmName("vRColorSpace") get() = tryReturn({ it.name.v }, { it.v })

    val trueValue get() = language.trueValue
    val falseValue get() = language.falseValue

    val BooleanType get() = language.BooleanType
    val IntType get() = language.IntType
    val LongType get() = language.LongType
    val FloatType get() = language.FloatType
    val DoubleType get() = language.DoubleType

    val VoidType get() = language.VoidType

    val nullType = language.nullType
    val nullValue = language.nullValue

    val Type.nullValue get() = language.nullVal(this)
    val Value.nullValue get() = type.nullValue

    infix fun Value.lessThan(right: Value) = language.lessThan(this, right)
    infix fun Value.lessOrEqualThan(right: Value) = language.lessOrEqualThan(this, right)

    infix fun Value.greaterThan(right: Value) = language.greaterThan(this, right)
    infix fun Value.greaterOrEqualThan(right: Value) = language.greaterOrEqualThan(this, right)

    infix fun Value.equalsTo(right: Value) = language.equals(this, right)
    infix fun Value.notEqualsTo(right: Value) = language.notEquals(this, right)

    fun not(cond: Condition) = language.not(cond)

    infix fun Condition.and(right: Condition) = language.and(this, right)
    infix fun Condition.or(right: Condition) = language.or(this, right)

    infix fun Value.isInstanceOf(type: Type) = language.isInstanceOf(this, type)
    infix fun Value.isNotInstanceOf(type: Type) = language.isNotInstanceOf(this, type)

    fun <T: Value> List<T>.joinWithAnd(joiner: (T) -> Condition): Condition? {
        if(isEmpty()) return null

        var condition: Condition? = null

        for(value in this) {
            val newCondition = joiner(value)

            condition = if(condition == null) {
                newCondition
            } else {
                condition and newCondition
            }
        }

        return condition
    }

    fun Value.castTo(type: Type, force: Boolean = false) = language.castValue(this, type, force)

    fun Value.condition(): Condition {
        require(type == language.BooleanType) {
            "Cannot convert value of type $type to condition (needs the Language's BooleanType)"
        }

        return language.condition(this)
    }

    // NUMBER OPERATORS

    operator fun Value.plus(right: Value) = language.sum(this, right)
    operator fun Value.minus(right: Value) = language.subtraction(this, right)
    operator fun Value.times(right: Value) = language.multiplication(this, right)
    operator fun Value.div(right: Value) = language.division(this, right)

    infix fun Int.plus(right: Int) = language.sum(this.v, right.v)
    infix fun Long.plus(right: Long) = language.sum(this.v, right.v)
    infix fun Float.plus(right: Float) = language.sum(this.v, right.v)
    infix fun Double.plus(right: Double) = language.sum(this.v, right.v)

    infix fun Int.minus(right: Int) = language.subtraction(this.v, right.v)
    infix fun Long.minus(right: Long) = language.subtraction(this.v, right.v)
    infix fun Float.minus(right: Float) = language.subtraction(this.v, right.v)
    infix fun Double.minus(right: Double) = language.subtraction(this.v, right.v)

    infix fun Int.by(right: Int) = language.multiplication(this.v, right.v)
    infix fun Long.by(right: Long) = language.multiplication(this.v, right.v)
    infix fun Float.by(right: Float) = language.multiplication(this.v, right.v)
    infix fun Double.by(right: Double) = language.multiplication(this.v, right.v)

    infix fun Int.div(right: Int) = language.division(this.v, right.v)
    infix fun Long.div(right: Long) = language.division(this.v, right.v)
    infix fun Float.div(right: Float) = language.division(this.v, right.v)
    infix fun Double.div(right: Double) = language.division(this.v, right.v)

    fun boolean(value: Boolean) = language.boolean(value)

    fun int(value: GenValue.Int) = language.int(value)
    fun int(value: Value) = language.int(value)
    fun int(value: Int) = language.int(value)

    fun long(value: Value) = language.long(value)
    fun long(value: Long) = language.long(value)

    fun float(value: GenValue.Float) = language.float(value)
    fun float(value: Value) = language.float(value)
    fun float(value: Float) = language.float(value)

    fun double(value: GenValue.Double) = language.double(value)
    fun double(value: Value) = language.double(value)
    fun double(value: Double) = language.double(value)

    fun new(type: Type, vararg parameters: Value) = language.new(type, *parameters)

    fun string(value: String) = language.string(value)
    fun string(value: Value) = language.string(value)

    @JvmName("newExt")
    fun Type.new(vararg parameters: Value) = new(this, *parameters)

    fun Type.arrayType() = language.arrayOf(this)

    fun Value.arraySize() = language.arraySize(this)

    fun Type.newArray(size: Value) = language.newArrayOf(this, size)

    fun Type.newArrayOfValues(vararg values: Value) = language.newArrayOfValues(this, *values)

    fun value(type: Type, value: String) = language.value(type, value)

    fun String.callValue(returnType: Type, vararg parameters: Value) =
        language.callValue(this, returnType, *parameters)

    fun Type.callValue(methodName: String, returnType: Type, vararg parameters: Value) =
        language.callValue(this, methodName, returnType, *parameters)

    fun Value.callValue(methodName: String, returnType: Type, vararg parameters: Value) =
        language.callValue(this, methodName, returnType, *parameters)

    fun Value.objectEquals(right: Value) = language.objectEquals(this, right)

    fun Value.propertyValue(property: String, type: Type) = language.propertyValue(this, property, type)
    fun Value.propertyVariable(property: String, type: Type) = language.propertyVariable(this, property, type)

    operator fun Value.get(index: Value, type: Type) = language.arrayValue(this, index, type)

    operator fun Value.get(type: Type, vararg indexes: Value) = language.arrayValueMultiple(this, indexes.toList(), type)

    fun enumValue(type: Type, constantName: String) = language.enumValue(type, constantName)

    fun cvtColorValue(a: ColorSpace, b: ColorSpace) = language.cvtColorValue(a, b)

    fun cvTypeValue(cvType: String) = language.cvTypeValue(cvType)

    fun variable(name: String, value: Value, isNullable: Boolean = false) = DeclarableVariable(name, value, isNullable)
    fun variable(type: Type, name: String) = AccessorVariable(type, name)
}



