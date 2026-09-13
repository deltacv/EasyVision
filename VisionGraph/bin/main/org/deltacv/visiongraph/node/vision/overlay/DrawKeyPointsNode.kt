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
import org.deltacv.visiongraph.attribute.vision.structs.KeyPointAttribute
import org.deltacv.visiongraph.attribute.vision.structs.ScalarAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Mat
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.node.vision.ColorSpace
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@PaperNode(
    name = "nod_drawkeypoints",
    category = NodeCategory.OVERLAY,
    description = "des_drawkeypoints"
)
@CodecType
open class DrawKeyPointsNode
@JvmOverloads constructor(val isDrawOnInput: Boolean = false) : DrawNode<DrawKeyPointsNode.Session>() {

    val inputMat = MatAttribute(INPUT, "$[att_input]")
    val keypoints = ListAttribute(INPUT, "$[att_keypoints]", KeyPointAttribute)

    val lineColor = ScalarAttribute(INPUT, ColorSpace.RGB, "$[att_linecolor]")

    val outputMat = MatAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        +inputMat.rebuildOnChange()

        +lineColor

        +keypoints.rebuildOnChange()

        if (!isDrawOnInput) {
            outputMat.bindColorSpace(inputMat)
            +outputMat.enablePrevizButton().rebuildOnChange()
        } else {
            inputMat.attributeName = "$[att_drawon_image]"
        }
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val color = lineColor.genValue(current)

                val input = inputMat.genValue(current)

                val keypointsValue = keypoints.genValue(current)
                if (keypointsValue !is GenValue.List.Runtime<*>) {
                    raise("Only runtime lists are supported for now")
                }

                val output = uniqueVariable("${input.value.v}KeyPoints", Mat.new())

                var drawMat = if (!isDrawOnInput) {
                    output
                } else {
                    input.value.v
                }

                val colorScalar = uniqueVariable(
                    "keypointColor",
                    JvmOpenCv.Scalar(color, current)
                )

                group {
                    if (!isDrawOnInput) {
                        private(output)
                    }
                    public(colorScalar, lineColor.tunerLabel())
                }

                current.scope {
                    nameComment()

                    JvmOpenCv.Features2d("drawKeypoints", input.value.v, keypointsValue.value.v, drawMat, colorScalar)

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
                val color = lineColor.genValue(current) as GenValue.Scalar.Components

                val input = inputMat.genValue(current)
                val keypointsValue = keypoints.genValue(current)
                if (keypointsValue !is GenValue.List.Runtime<*>) {
                    raise("Only runtime lists are supported for now")
                }

                current.scope {
                    nameComment()

                    val output = uniqueVariable(
                        "${input.value.v}_keypoints",
                        cv2.callValue(
                            "drawKeypoints",
                            CPythonLanguage.NoType,
                            input.value.v,
                            keypointsValue.value.v,
                            CPythonOpenCv.np.callValue(
                                "array",
                                CPythonLanguage.NoType, CPythonLanguage.newArrayOf(CPythonLanguage.NoType, 0.v)
                            ),
                            CPythonLanguage.tuple(color.a.v, color.b.v, color.c.v)
                        )
                    )

                    local(output)
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
        encoder.obj("keypoints", keypoints)
        encoder.obj("lineColor", lineColor)
        if (!isDrawOnInput) {
            encoder.obj("outputMat", outputMat)
        }
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputMat", inputMat)
        decoder.obj("keypoints", keypoints)
        decoder.obj("lineColor", lineColor)
        if (!isDrawOnInput) {
            decoder.obj("outputMat", outputMat)
        }
    }

    class Session : CodeGenSession {
        lateinit var outputMat: GenValue.Mat
    }

}

@PaperNode(
    name = "nod_drawrects_onimage",
    category = NodeCategory.OVERLAY,
    description = "des_drawrects_onimage",
    showInList = false // executive decision
)
class DrawKeyPointsOnImageNode : DrawKeyPointsNode(true)



