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

package org.deltacv.visiongraph.codegen.build.language.cpython

import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.ConValue
import org.deltacv.visiongraph.codegen.build.Value
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved

object CPythonOpenCv {
    object cv2 : CPythonType("cv2") {
        val RETR_LIST = ConValue(this, "cv2.RETR_LIST")

        val RETR_EXTERNAL = ConValue(this, "cv2.RETR_EXTERNAL")

        val CHAIN_APPROX_SIMPLE = ConValue(this, "cv2.CHAIN_APPROX_SIMPLE")

        val MORPH_RECT = ConValue(this, "cv2.MORPH_RECT")

        val HOUGH_GRADIENT = ConValue(this, "cv2.HOUGH_GRADIENT")

        val contourArea = ConValue(this, "cv2.contourArea")
    }

    val np = CPythonType("numpy", null, "np")

    val npArray = object : CPythonType("np.ndarray") {
        override var overridenImport = np
    }

    fun scalarTuple(scalar: GenValue.Scalar, languageHolder: CodeGen.LanguageHolder) = languageHolder.language {
        scalar.match(
            ifActual = { list ->
                val elements = list.elements.map { it.v }.toTypedArray()
                CPythonLanguage.tuple(*elements)
            },

            ifRuntime = { list ->
                Value.derive(CPythonLanguage.NoType, list.value.v)
            }
        )
    }

    fun toRuntimeLineParameters(params: GenValue.LineParameters, languageHolder: CodeGen.LanguageHolder) =
        when (params) {
            is GenValue.LineParameters.Components -> {
                val color = scalarTuple(params.color, languageHolder)
                val thickness = params.thickness.toRuntime(languageHolder)

                GenValue.LineParameters.Runtime(GenValue.Scalar.Inst(color.resolved()), thickness)
            }

            is GenValue.LineParameters.Runtime -> params
        }

    fun toRectTuple(rect: GenValue.Rect, languageHolder: CodeGen.LanguageHolder) = languageHolder.language {
        when (rect) {
            is GenValue.Rect.Components -> {
                val pos = rect.position.toRuntime(languageHolder)
                val size = rect.size.toRuntime(languageHolder)

                CPythonLanguage.tuple(
                    pos.x.v, pos.y.v, size.x.v, size.y.v
                )
            }

            is GenValue.Rect.Inst -> rect.value.v
        }
    }

    fun toRotatedRectTuple(rect: GenValue.RotatedRect, languageHolder: CodeGen.LanguageHolder) =
        languageHolder.language {
            when (rect) {
                is GenValue.RotatedRect.Components -> {
                    CPythonLanguage.tuple(
                        CPythonLanguage.tuple(rect.x.v, rect.y.v),
                        CPythonLanguage.tuple(rect.w.v, rect.h.v),
                        rect.angle.v
                    )
                }

                is GenValue.RotatedRect.Inst -> rect.value.v
            }
        }

}



