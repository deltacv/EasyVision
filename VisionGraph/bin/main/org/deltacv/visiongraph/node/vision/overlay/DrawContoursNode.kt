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
import org.deltacv.visiongraph.attribute.rebuildOnLink
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.attribute.vision.structs.LineParametersAttribute
import org.deltacv.visiongraph.attribute.vision.structs.PointsAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.DeclarableVariable
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Mat
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
    name = "nod_drawcontours",
    category = NodeCategory.OVERLAY,
    description = "des_drawcontours"
)
@CodecType
open class DrawContoursNode
@JvmOverloads constructor(val isDrawOnInput: Boolean = false)
    : DrawNode<DrawContoursNode.Session>()  {

    val inputMat = MatAttribute(INPUT, "$[att_input]")
    val contours = ListAttribute(INPUT, "$[att_contours]", PointsAttribute)

    val lineParams = LineParametersAttribute(INPUT, "$[att_params]")

    val outputMat = MatAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + inputMat.rebuildOnChange()

        + lineParams.rebuildOnLink()

        + contours.rebuildOnChange()

        if(!isDrawOnInput) {
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
                input.requireNonBinary(inputMat)

                val contoursList = contours.genValue(current)

                val output = uniqueVariable("${input.value.v}Contours", Mat.new())
                var drawMat = input.value.v

                group {
                    if (!isDrawOnInput) {
                        private(output)
                    }
                }

                current.scope {
                    nameComment()

                    if(!isDrawOnInput) {
                        drawMat = output
                        input.value.v("copyTo", drawMat)
                    }

                    if(contoursList is GenValue.List.Runtime<*>) {
                        Imgproc("drawContours", drawMat, contoursList.value.v, (-1).v,
                            JvmOpenCv.Scalar(lineParams.color, current),
                            lineParams.thicknessValue.v
                        )
                    } else {
                        separate()

                        val list = DeclarableVariable("contoursList", JavaTypes.ArrayList(JvmOpenCv.MatOfPoint).new())
                        local(list)

                        for (contour in (contoursList as GenValue.List.Actual<*>).elements) {
                            if (contour is GenValue.Points.Runtime) {
                                ifCondition(contour.value.v notEqualsTo language.nullValue) {
                                    list("add", contour.value.v)
                                }
                            } else {
                                raise("Points are not supported")
                            }
                        }

                        separate()

                        Imgproc("drawContours", drawMat, list, (-1).v,
                            JvmOpenCv.Scalar(lineParams.color, current),
                            lineParams.thicknessValue.v
                        )
                    }

                    if(!isDrawOnInput) {
                        outputMat.streamIfEnabled(drawMat, input.color)
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
                input.requireNonBinary(inputMat)

                val contoursList = contours.genValue(current)

                val lineParams = lineParams.genValue(current) as GenValue.LineParameters.Components

                current.scope {
                    nameComment()

                    val color = lineParams.color
                    val thickness = lineParams.thickness.v

                    val colorScalar = CPythonLanguage.tuple(color.a.v, color.b.v, color.c.v, color.d.v)

                    val target = if(isDrawOnInput) {
                        input.value.v
                    } else {
                        val output = uniqueVariable(
                            "${input.value}_contours", input.value.v.callValue("copy", CPythonLanguage.NoType)
                        )
                        local(output)

                        output
                    }

                    if(contoursList is GenValue.List.Runtime<*>) {
                        cv2("drawContours", target, contoursList.value.v, (-1).v, colorScalar, thickness)
                    } else {
                        separate()

                        val list = uniqueVariable("contoursList", CPythonLanguage.NoType.newArrayOfValues())
                        local(list)

                        for(contour in (contoursList as GenValue.List.Actual<*>).elements) {
                            if(contour is GenValue.Points.Runtime) {
                                ifCondition(contour.value.v notEqualsTo CPythonLanguage.nullValue) {
                                    list("append", contour.value.v)
                                }
                            } else {
                                raise("Invalid contour type")
                            }
                        }

                        separate()

                        cv2("drawContours", target, list, (-1).v, colorScalar, thickness)
                    }

                    session.outputMat = GenValue.Mat(target.resolved(), input.color, input.isBinary)
                }
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if(attrib == outputMat) {
            return GenValue.Mat.defer { current.sessionOf(this)?.outputMat }
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("inputMat", inputMat)
        encoder.obj("contours", contours)
        encoder.obj("lineParams", lineParams)
        if(!isDrawOnInput) {
            encoder.obj("outputMat", outputMat)
        }
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputMat", inputMat)
        decoder.obj("contours", contours)
        decoder.obj("lineParams", lineParams)
        if(!isDrawOnInput) {
            decoder.obj("outputMat", outputMat)
        }
    }

    class Session : CodeGenSession {
        lateinit var outputMat: GenValue.Mat
    }

}

@PaperNode(
    name = "nod_drawcontours_onimage",
    category = NodeCategory.OVERLAY,
    description = "des_drawcontours_onimage",
    showInList = false // executive decision
)
class DrawContoursOnImageNode : DrawContoursNode(true)



