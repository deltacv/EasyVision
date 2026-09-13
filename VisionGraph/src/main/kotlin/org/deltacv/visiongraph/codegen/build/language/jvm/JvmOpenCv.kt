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

import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.Visibility
import org.deltacv.visiongraph.codegen.build.ConValue
import org.deltacv.visiongraph.codegen.build.DeclarableVariable
import org.deltacv.visiongraph.codegen.build.Parameter
import org.deltacv.visiongraph.codegen.build.Type
import org.deltacv.visiongraph.codegen.build.language.StandardTypes
import org.deltacv.visiongraph.codegen.dsl.ScopeCtx
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.codegen.resolve.resolved

object JvmOpenCv {

    val OpenCvPipeline = Type("OpenCvPipeline", "org.openftc.easyopencv")
    val StreamableOpenCvPipeline = Type("StreamableOpenCvPipeline", "org.deltacv.eocvsim.pipeline")

    object Imgproc : Type("Imgproc", "org.opencv.imgproc") {
        val RETR_LIST = ConValue(StandardTypes.cint, "Imgproc.RETR_LIST").apply {
            additionalImports(this)
        }

        val RETR_EXTERNAL = ConValue(StandardTypes.cint, "Imgproc.RETR_EXTERNAL").apply {
            additionalImports(this)
        }

        val CHAIN_APPROX_SIMPLE = ConValue(StandardTypes.cint, "Imgproc.CHAIN_APPROX_SIMPLE").apply {
            additionalImports(this)
        }

        val MORPH_RECT = ConValue(StandardTypes.cint, "Imgproc.MORPH_RECT").apply {
            additionalImports(this)
        }

        val HOUGH_GRADIENT = ConValue(StandardTypes.cint, "Imgproc.HOUGH_GRADIENT").apply {
            additionalImports(this)
        }
    }

    val Features2d = Type("Features2d", "org.opencv.features2d")

    object CvType : Type("CvType", "org.opencv.core")

    val Core = Type("Core", "org.opencv.core")

    val Mat = Type("Mat", "org.opencv.core")
    val MatOfInt = Type("MatOfInt", "org.opencv.core")
    val MatOfPoint = Type("MatOfPoint", "org.opencv.core")
    val MatOfPoint2f = Type("MatOfPoint2f", "org.opencv.core")
    val MatOfKeyPoint = Type("MatOfKeyPoint", "org.opencv.core")

    val Size = Type("Size", "org.opencv.core")
    val Scalar = Type("Scalar", "org.opencv.core")

    fun Scalar(genValue: GenValue.Scalar, langHolder: CodeGen.LanguageHolder) = langHolder.language {
        when (genValue) {
            is GenValue.Scalar.Inst -> ConValue(Scalar, genValue.value.v.value)
            is GenValue.Scalar.Components -> Scalar.new(genValue.a.v, genValue.b.v, genValue.c.v, genValue.d.v)
        }
    }

    fun syncScalarVariable(
        scalarVariable: DeclarableVariable,
        scalar: GenValue.Scalar.Components,
        current: CodeGen.Current
    ) = current {
        current.scope.deferred(Resolvable.ListPlaceholder(
            scalar.a.isActual.value,
            scalar.b.isActual.value,
            scalar.c.isActual.value,
            scalar.d.isActual.value
        )) { isActualValues ->
            repeat(4) {
                if(!isActualValues[it]) {
                    val target = scalarVariable.propertyVariable("val", DoubleType.arrayType())
                    target[it.v] = scalar.toActualOrNull()!!.elements[it].v
                }
            }
        }
    }

    val Rect = Type("Rect", "org.opencv.core")

    fun toRectInst(rect: GenValue.Rect, langHolder: CodeGen.LanguageHolder) = when (rect) {
        is GenValue.Rect.Components -> langHolder.language {
            val pos = rect.position.toRuntime(langHolder)
            val size = rect.size.toRuntime(langHolder)

            GenValue.Rect.Inst(Rect.new(pos.x.v, pos.y.v, size.x.v, size.y.v).resolved())
        }

        is GenValue.Rect.Inst -> rect
    }

    val RotatedRect = Type("RotatedRect", "org.opencv.core")

    fun toRotatedRectInst(rect: GenValue.RotatedRect, langHolder: CodeGen.LanguageHolder) = when (rect) {
        is GenValue.RotatedRect.Components -> langHolder.language {
            GenValue.RotatedRect.Inst(
                RotatedRect.new(
                    Point.new(rect.x.v, rect.y.v),
                    Size.new(rect.w.v, rect.h.v),
                    rect.angle.v
                ).resolved()
            )
        }

        is GenValue.RotatedRect.Inst -> rect
    }

    val Point = Type("Point", "org.opencv.core")
    val KeyPoint = Type("KeyPoint", "org.opencv.core")

    object SimpleBlobDetector : Type("SimpleBlobDetector", "org.opencv.features2d") {
        val Params = Type("SimpleBlobDetector_Params", "org.opencv.features2d")
    }

    fun toRuntimeLineParameters(
        line: GenValue.LineParameters,
        current: CodeGen.Current
    ): GenValue.LineParameters.Runtime {
        return current {
            when (line) {
                is GenValue.LineParameters.Components -> {
                    val color = uniqueVariable("lineColor", Scalar(line.color, current))
                    val thickness = uniqueVariable("lineThickness", line.thickness.v)

                    group {
                        public(color)
                        public(thickness)
                    }

                    syncScalarVariable(color, line.color, current)

                    GenValue.LineParameters.Runtime(
                        GenValue.Scalar.Inst(Resolvable.Now(color)),
                        GenValue.Int.Runtime(Resolvable.Now(thickness))
                    )
                }

                is GenValue.LineParameters.Runtime -> line
            }
        }
    }

    val Circle = Type("Circle", "Circle") {
        current {
            codeGen.classEndScope {
                clazz(Visibility.PACKAGE_PRIVATE, "Circle", isStatic = true) {
                    val centerVariable = DeclarableVariable(Point, "center")
                    val radiusVariable = DeclarableVariable(DoubleType, "radius")

                    group {
                        instanceVariable(
                            Visibility.PACKAGE_PRIVATE,
                            centerVariable,
                            isFinal = true
                        )

                        instanceVariable(
                            Visibility.PACKAGE_PRIVATE,
                            radiusVariable,
                            isFinal = true
                        )
                    }

                    separate()

                    val centerParameter = Parameter(Point, "center")
                    val radiusParameter = Parameter(FloatType, "radius")

                    constructor(Visibility.PACKAGE_PRIVATE, type, centerParameter, radiusParameter) {
                        centerVariable instanceSet centerParameter
                        radiusVariable instanceSet radiusParameter
                    }
                }
            }
        }
    }
}



