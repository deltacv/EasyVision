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

import org.deltacv.visiongraph.codegen.build.ConValue
import org.deltacv.visiongraph.codegen.build.Type
import org.deltacv.visiongraph.codegen.build.Value

fun Array<out String>.csv(): String {
    val builder = StringBuilder()

    for((i, parameter) in this.withIndex()) {
        builder.append(parameter)

        if(i < this.size - 1) {
            builder.append(", ")
        }
    }

    return builder.toString()
}

fun Array<out Value>.csv(): String {
    val stringArray = this.map { it.value!! }.toTypedArray()
    return stringArray.csv()
}

fun List<Value>.csv() = this.toTypedArray().csv()

fun Array<out Type>.csv(): String {
    val stringArray = this.map { it.shortNameWithGenerics }.toTypedArray()
    return stringArray.csv()
}

fun csv(vararg value: Value): Value {
    if(value.isEmpty()) return ConValue(Type.NONE, "")

    val type = value[0].type

    // ensure all values are of the same type
    for(v in value) {
        if(v.type != type) {
            throw IllegalArgumentException("All values must be of the same type to create a CSV Value.")
        }
    }

    return ConValue(type, value.csv())
}



