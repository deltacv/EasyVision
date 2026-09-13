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
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.Generator

class GeneratorCtx<I, S: CodeGenSession>(
    val genInput: I,
    val current: CodeGen.Current
) {
    fun throwLanguageNotSupported(): Nothing =
        throw NoSuchElementException(
            "No generator found for language ${current.language::class.simpleName}"
        )
}

fun <I, S: CodeGenSession>
        generator(init: GeneratorCtx<I, S>.() -> S) =
    Generator<I, S> { input, current ->
        init(GeneratorCtx(input, current))
    }



