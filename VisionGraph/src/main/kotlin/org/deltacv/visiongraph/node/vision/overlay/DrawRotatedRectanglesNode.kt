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

package org.deltacv.visiongraph.node.vision.overlay

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.attribute.vision.structs.LineParametersAttribute
import org.deltacv.visiongraph.attribute.vision.structs.RotatedRectAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.Value
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.np
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Mat
import org.deltacv.visiongraph.codegen.dsl.ScopeCtx
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@PaperNode(
    name = "nod_drawrotated_rects",
    category = NodeCategory.OVERLAY,
    description = "des_drawrotated_rects"
)
@CodecType
open class DrawRotatedRectanglesNode
@JvmOverloads constructor(val isDrawOnInput: Boolean = false) : DrawNode<DrawRotatedRectanglesNode.Session>() {

    val inputMat = MatAttribute(INPUT, "$[att_input]")
    val rectangles = ListAttribute(INPUT, "$[att_rects]", RotatedRectAttribute)

    val lineParams = LineParametersAttribute(INPUT, "$[att_params]")

    val outputMat = MatAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + inputMat.rebuildOnChange()

        + lineParams

        + rectangles.rebuildOnChange()

        if (!isDrawOnInput) {
            outputMat.bindColorSpace(inputMat)
        + outputMat.enablePrevizButton().rebuildOnChange()
        } else {
            inputMat.attributeName = "$[att_drawon_image]"
        }
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val lineParams = JvmOpenCv.toRuntimeLineParameters(lineParams.genValue(current), current)

                val input = inputMat.genValue(current)
                val rectanglesList = rectangles.genValue(current)

                val output = uniqueVariable("${input.value.v}RotRects", Mat.new())

                var drawMat = input.value.v

                group {
                    if (!isDrawOnInput) {
                        private(output)
                    }
                }

                current.scope {
                    nameComment()

                    if (!isDrawOnInput) {
                        drawMat = output
                        input.value.v("copyTo", drawMat)
                    }

                    fun ScopeCtx.drawRuntimeRect(rectValue: Value) {
                        ifCondition(rectValue notEqualsTo language.nullValue) {
                            val rectPoints = uniqueVariable("rectPoints", JvmOpenCv.Point.newArray(4.v))
                            local(rectPoints)
                            rectValue("points", rectPoints)

                            val matOfPoint = uniqueVariable("matOfPoint", JvmOpenCv.MatOfPoint.new(rectPoints))
                            local(matOfPoint)

                            separate()

                            // Draw the rectangle using the points
                            ifCondition((drawMat notEqualsTo language.nullValue) and not(drawMat.callValue("empty", BooleanType).condition())) {
                                Imgproc(
                                    "polylines", drawMat,
                                    JavaTypes.Collections.callValue(
                                        "singletonList",
                                        JavaTypes.List(JvmOpenCv.MatOfPoint),
                                        matOfPoint
                                    ), // list of points forming the rotated rectangle
                                    trueValue, // closed polygon
                                    lineParams.color.value.v,
                                    lineParams.thicknessValue.v
                                )
                            }
                        }
                    }

                    if (rectanglesList !is GenValue.List.Runtime<*>) {
                        for (rectangle in (rectanglesList as GenValue.List.Actual<*>).elements) {
                            if (rectangle is GenValue.RotatedRect.Components) {
                                TODO("implement rotated rect drawing for components")
                            } else if (rectangle is GenValue.RotatedRect.Inst) {
                                drawRuntimeRect(rectangle.value.v)
                            }
                        }
                    } else {
                        foreach(variable(JvmOpenCv.RotatedRect, "rect"), rectanglesList.value.v) {
                            drawRuntimeRect(it)
                        }
                    }

                    if (!isDrawOnInput) {
                        outputMat.streamIfEnabled(output, input.color)
                    }
                }

                session.outputMat = GenValue.Mat(drawMat.resolved(), input.color, input.isBinary)

                session
            }
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current {
                val input = inputMat.genValue(current)
                val rectanglesList = rectangles.genValue(current)

                val lineParams = lineParams.genValue(current) as GenValue.LineParameters.Components

                current.scope {
                    nameComment()

                    val target = if (isDrawOnInput) {
                        input.value.v
                    } else {
                        val output = uniqueVariable(
                            "${input.value.v}_rot_rects",
                            input.value.v.callValue("copy", CPythonLanguage.NoType)
                        )
                        local(output)
                        output
                    }

                    val color = lineParams.color
                    val thickness = lineParams.thickness.v

                    val colorScalar = CPythonLanguage.tuple(color.a.v, color.b.v, color.c.v, color.d.v)

                    // TODO: Implement rotated rect drawing in python

                    fun ScopeCtx.runtimeRect(rectValue: Value) {
                        ifCondition(rectValue notEqualsTo language.nullValue) {
                            val box = uniqueVariable("box", cv2.callValue("boxPoints", CPythonLanguage.NoType, rectValue))
                            local(box)
                            box set np.callValue("int32", CPythonLanguage.NoType, box)
                            cv2("drawContours", target, CPythonLanguage.NoType.newArrayOfValues(box), (0).v, colorScalar, thickness)
                        }
                    }

                    if (rectanglesList !is GenValue.List.Runtime<*>) {
                        for (rectangle in (rectanglesList as GenValue.List.Actual<*>).elements) {
                            if (rectangle is GenValue.RotatedRect.Components) {
                                raise("RotatedRects are not supported")
                            } else if (rectangle is GenValue.Rect.Inst) {
                                runtimeRect(rectangle.value.v)
                            }
                        }
                    } else {
                        foreach(variable(CPythonLanguage.NoType, "rect"), rectanglesList.value.v) { rect ->
                            runtimeRect(rect)
                        }
                    }

                    session.outputMat = GenValue.Mat(target.resolved(), input.color, input.isBinary)
                }
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if (attrib == outputMat) {
            return GenValue.Mat.defer { current.sessionOf(this)?.outputMat }
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("inputMat", inputMat)
        encoder.obj("rectangles", rectangles)
        encoder.obj("lineParams", lineParams)
        encoder.obj("outputMat", outputMat)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputMat", inputMat)
        decoder.obj("rectangles", rectangles)
        decoder.obj("lineParams", lineParams)
        decoder.obj("outputMat", outputMat)
    }

    class Session : CodeGenSession {
        lateinit var outputMat: GenValue.Mat
    }

}



