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

package org.deltacv.visiongraph.codegen.build.language

import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.resolve.Resolvable

object GenPreviz {

    // ------ INT CONVERSION ------

    fun toPrevizInt(
        num: GenValue.Int,
        labelSource: TypedAttribute<*>,
        current: CodeGen.Current,
        variableName: String = "int"
    ) = toPrevizInt(num, labelSource.tunerLabel(), variableName, current)

    fun toPrevizInt(
        num: GenValue.Int,
        label: String,
        variableName: String,
        current: CodeGen.Current,
    ) = current {
        if (codeGen.isForPreviz) {
            val variable = uniqueVariable(variableName, num.v, allocateName = true)

            deferredGroup(num.isActual.value) { isActual ->
                if (isActual) public(variable, label)
            }

            GenValue.Int.Runtime(
                Resolvable.DependentPlaceholder(num.isActual.value) {
                    if (it) variable else num.v
                },
                isActual = GenValue.Boolean.FALSE
            )
        } else num
    }

    // ------ FLOAT CONVERSION ------

    fun toPrevizFloat(
        num: GenValue.Float,
        labelSource: TypedAttribute<*>,
        current: CodeGen.Current,
        variableName: String = "float"
    ) = toPrevizFloat(num, labelSource.tunerLabel(), variableName, current)

    fun toPrevizFloat(
        num: GenValue.Float,
        label: String,
        variableName: String,
        current: CodeGen.Current,
    ) = current {
        if (codeGen.isForPreviz) {
            val variable = uniqueVariable(variableName, num.v, allocateName = true)

            deferredGroup(num.isActual.value) { isActual ->
                if (isActual) public(variable, label)
            }

            GenValue.Float.Runtime(
                Resolvable.DependentPlaceholder(num.isActual.value) {
                    if (it) variable else num.v
                },
                isActual = GenValue.Boolean.FALSE
            )
        } else num
    }

    // ------ DOUBLE CONVERSION ------

    fun toPrevizDouble(
        num: GenValue.Double,
        labelSource: TypedAttribute<*>,
        current: CodeGen.Current,
        variableName: String = "double"
    ) = toPrevizDouble(num, labelSource.tunerLabel(), variableName, current)

    fun toPrevizDouble(
        num: GenValue.Double,
        label: String,
        variableName: String,
        current: CodeGen.Current,
    ) = current {
        if (codeGen.isForPreviz) {
            val variable = uniqueVariable(variableName, num.v, allocateName = true)

            deferredGroup(num.isActual.value) { isActual ->
                if (isActual) public(variable, label)
            }

            GenValue.Double.Runtime(
                Resolvable.DependentPlaceholder(num.isActual.value) {
                    if (it) variable else num.v
                },
                isActual = GenValue.Boolean.FALSE
            )
        } else num
    }

    // ------ VEC2 CONVERSION ------

    fun toPrevizVec2(
        vec: GenValue.Vec2,
        labelSource: TypedAttribute<*>,
        current: CodeGen.Current,
        prefix: String = "vec"
    ) = toPrevizVec2(vec, labelSource.tunerLabel(0), labelSource.tunerLabel(1), current, prefix)

    fun toPrevizVec2(
        vec: GenValue.Vec2,
        firstLabel: String,
        secondLabel: String,
        current: CodeGen.Current,
        prefix: String = "vec",
    ) = current {
        if (codeGen.isForPreviz) {
            val x = toPrevizInt(vec.x, firstLabel, "${prefix}X", current)
            val y = toPrevizInt(vec.y, secondLabel, "${prefix}Y", current)

            GenValue.Vec2.Runtime(x.toRuntime(current), y.toRuntime(current))
        } else {
            vec
        }
    }
}