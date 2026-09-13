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

package org.deltacv.visiongraph.codegen.build.language.jvm

import org.deltacv.visiongraph.codegen.build.Type

object JavaTypes {

    val String = Type("String", "java.lang")

    val Math = object: Type("Math", "java.lang") {
        override val shouldImport = false
    }

    fun Set(elementType: Type) = Type(
        "Set", "java.util",
        arrayOf(elementType)
    )

    fun ArrayList(elementType: Type) = Type(
        "ArrayList", "java.util",
        generics = arrayOf(elementType)
    )

    fun HashMap(key: Type, value: Type) = Type(
        "HashMap", "java.util",
        generics = arrayOf(key, value)
    )

    class Map(key: Type, value: Type) : Type(
        "Map", "java.util",
        generics = arrayOf(key, value)
    ) {
        class Entry(key: Type, value: Type) : Type(
            "Map.Entry", "java.util",
            arrayOf(key, value)
        )
    }

    fun List(elementType: Type) = Type(
        "List", "java.util",
        arrayOf(elementType)
    )

    val Collections = Type("Collections", "java.util")

    val LabelAnnotation = Type("Label", "org.deltacv.eocvsim.virtualreflect.jvm")

}



