/*
 * VisionGraph
 * Copyright (C) 2024 Sebastian Erives, deltacv

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.deltacv.visiongraph.codegen.dsl.jvm

import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.build.Value
import org.deltacv.visiongraph.codegen.build.DeclarableVariable
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.enableJavaTargets
import org.deltacv.visiongraph.codegen.dsl.LanguageCtx
import org.deltacv.visiongraph.codegen.dsl.ScopeCtx

class JvmTargetsCtx(context: LanguageCtx) {
    val frontRectTargets = context.run {
        DeclarableVariable("frontRectTargets", JavaTypes.HashMap(JavaTypes.String, JvmOpenCv.Rect).new())
    }
    val backRectTargets = context.run {
        DeclarableVariable("backRectTargets", JavaTypes.HashMap(JavaTypes.String, JvmOpenCv.Rect).new())
    }

    val frontRotRectTargets = context.run {
        DeclarableVariable("frontRotRectTarget", JavaTypes.HashMap(JavaTypes.String, JvmOpenCv.RotatedRect).new())
    }
    val backRotRectTargets = context.run {
        DeclarableVariable("backRotRectTarget", JavaTypes.HashMap(JavaTypes.String, JvmOpenCv.RotatedRect).new())
    }

    fun ScopeCtx.addRectTarget(label: Value, rect: Value) {
        "addRectTarget"(label, rect)
    }

    fun ScopeCtx.addRotRectTarget(label: Value, rect: Value) {
        "addRotRectTarget"(label, rect)
    }

    fun ScopeCtx.swapTargets() {
        "swapTargets"()
    }
}

fun <T> CodeGen.Current.jvmTargets(enableTargetsIfNeeded: Boolean = true, block: JvmTargetsCtx.() -> T): T {
    if(enableTargetsIfNeeded) {
        enableJavaTargets()
    }

    return block(JvmTargetsCtx(codeGen.context))
}



