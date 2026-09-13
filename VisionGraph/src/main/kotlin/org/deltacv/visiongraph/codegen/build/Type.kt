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

import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.csv
import org.deltacv.visiongraph.util.hashCodeString

open class Type(
    val className: String,
    open val packagePath: String = "",
    val generics: Array<Type> = arrayOf(),

    open val overridenImport: Type? = null,
    val isArray: Boolean = false,
    private val initializer: (InitializerCtx.() -> Unit)? = null
) {

    companion object {
        val NONE = Type("", "")
    }

    val hasInitializer = initializer != null

    val hasGenerics get() = generics.isNotEmpty()

    open val shouldImport get() = className != packagePath

    val shortNameWithGenerics get() =
        if(hasGenerics)
            "$className<${generics.csv()}>"
        else className

    fun initialize(current: CodeGen.Current) {
        val flag = "typeInitialized#$hashCodeString"

        if(initializer != null && !current.codeGen.hasFlag(flag)) {
            initializer.invoke(InitializerCtx(this, current))
            current.codeGen.addFlag(flag)
        }
    }

    override fun equals(other: Any?) = other is Type
                && className == other.className
                && packagePath == other.packagePath
                && generics.contentEquals(other.generics)
                && overridenImport == other.overridenImport
                && isArray == other.isArray

    override fun toString() = "Type(className=$className, packagePath=$packagePath, actualImport=$overridenImport, isArray=$isArray)"

    @ConsistentCopyVisibility
    data class InitializerCtx internal constructor(val type: Type, val current: CodeGen.Current)
}

private val typeCache = mutableMapOf<Class<*>, Type>()

val Any.genType: Type get() = this::class.java.genType

val Class<*>.genType: Type get() {
    if(!typeCache.containsKey(this)) {
        typeCache[this] = Type(simpleName, getPackage()?.name ?: "")
    }

    return typeCache[this]!!
}



