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

package org.deltacv.visiongraph.node.vision.imageproc

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.math.IntAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
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
    name = "nod_cannyedge",
    category = NodeCategory.IMAGE_PROC,
    description = "des_cannyedge"
)
@CodecType
class CannyEdgeNode : DrawNode<CannyEdgeNode.Session>(){

    val inputMat = MatAttribute(INPUT, "$[att_input]")

    val firstThreshold = IntAttribute(INPUT, "$[att_lowerthreshold]")
    val secondThreshold = IntAttribute(INPUT, "$[att_upperthreshold]")

    val outputMat = MatAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + inputMat.rebuildOnChange()

        + firstThreshold
        + secondThreshold

        outputMat.bindColorSpace(inputMat)
        outputMat.isBinary = true
        + outputMat.rebuildOnChange().enablePrevizButton()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val input = inputMat.genValue(current)
                // input.requireNonBinary(inputMat)

                input.color.letOrDefer {
                    if(it != ColorSpace.GRAY) {
                        inputMat.raise("err_grayscale_required")
                    }
                }

                val output = uniqueVariable("${input.value.v}Canny", Mat.new())

                val firstThresholdValue = firstThreshold.genValue(current).v
                val firstThresholdVariable = uniqueVariable("cannyFirstThreshold", int(firstThresholdValue))

                val secondThresholdValue = secondThreshold.genValue(current).v
                val secondThresholdVariable = uniqueVariable("cannySecondThreshold", int(secondThresholdValue))

                group {
                    private(output)

                    public(firstThresholdVariable, firstThreshold.tunerLabel())
                    public(secondThresholdVariable, secondThreshold.tunerLabel())
                }

                current.scope {
                    nameComment()

                    JvmOpenCv.Imgproc("Canny", input.value.v, output, firstThresholdVariable, secondThresholdVariable)
                    outputMat.streamIfEnabled(output, input.color)
                }

                session.outputMat = GenValue.Mat(output.resolved(), input.color)

                session
            }
        }

        generatorFor(CPythonLanguage) {
            current {
                val session = Session()

                val input = inputMat.genValue(current)
                input.requireNonBinary(inputMat)

                input.color.letOrDefer {
                    if(it != ColorSpace.GRAY) {
                        inputMat.raise("err_grayscale_required")
                    }
                }

                current.scope {
                    nameComment()

                    val output = uniqueVariable("${input.value}_canny",
                        cv2.callValue("Canny", CPythonLanguage.NoType, input.value.v, firstThreshold.genValue(current).v, secondThreshold.genValue(current).v)
                    )
                    local(output)

                    session.outputMat = GenValue.Mat(output.resolved(), input.color)
                }

                session
            }
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("inputMat", inputMat)
        encoder.obj("firstThreshold", firstThreshold)
        encoder.obj("secondThreshold", secondThreshold)
        encoder.obj("outputMat", outputMat)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputMat", inputMat)
        decoder.obj("firstThreshold", firstThreshold)
        decoder.obj("secondThreshold", secondThreshold)
        decoder.obj("outputMat", outputMat)
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if(attrib == outputMat) {
            return GenValue.Mat.defer { current.sessionOf(this)?.outputMat }
        }

        noValue(attrib)
    }

    class Session : CodeGenSession {
        lateinit var outputMat: GenValue.Mat
    }

}



