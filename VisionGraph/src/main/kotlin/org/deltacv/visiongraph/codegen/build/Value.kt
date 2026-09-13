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

val String.v get() = ConValue(genType, this)
val Number.v get() = toString().v

abstract class Value {

    companion object {
        val NONE = ConValue(Type.NONE, null)

        fun derive(type: Type, other: Value) = ConValue(type, other.value).apply {
            additionalImports(other)
        }
    }

    abstract val type: Type
    abstract val value: String?

    private val internalImports = mutableListOf<Type>()
    val imports get() = internalImports as List<Type>

    protected fun processImports() {
        // avoid adding imports for primitive types
        if(type.shouldImport) {
            additionalImports(type)
        }

        for(genericType in type.generics) {
            if(genericType.shouldImport) {
                additionalImports(genericType)
            }
        }
    }

    fun additionalImports(vararg imports: Type) {
        internalImports.addAll(imports)
    }

    fun additionalImports(vararg imports: Value) {
        for(import in imports) {
            internalImports.addAll(import.imports)
        }
    }
}

open class ConValue(override val type: Type, override val value: String?): Value() {
    init {
        processImports()
    }

    override fun toString() = value ?: "null"
}

class EmptyConValue(type: Type) : ConValue(type, null)

class Condition(booleanType: Type, condition: String) : ConValue(booleanType, condition)
class Operation(numberType: Type, operation: String) : ConValue(numberType, operation)

open class AccessorVariable(type: Type, name: String) : DeclarableVariable(name, ConValue(type, name))

open class DeclarableVariable(val name: String, val variableValue: Value, val isNullable: Boolean = false) : ConValue(variableValue.type, name) {

    constructor(type: Type, name: String) : this(name, EmptyConValue(type))

    init {
        additionalImports(*variableValue.imports.toTypedArray())
    }

    override fun toString() = name
}



