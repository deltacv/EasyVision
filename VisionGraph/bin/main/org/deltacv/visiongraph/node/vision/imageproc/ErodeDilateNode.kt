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
import org.deltacv.visiongraph.util.Range2i

@PaperNode(
    name = "nod_erodedilate",
    category = NodeCategory.IMAGE_PROC,
    description = "des_erodedilate"
)
@CodecType
class ErodeDilateNode : DrawNode<ErodeDilateNode.Session>() {

    val inputMat = MatAttribute(INPUT, "$[att_binaryinput]")

    val erodeValue = IntAttribute(INPUT, "$[att_erode]")
    val dilateValue = IntAttribute(INPUT, "$[att_dilate]")

    val outputMat = MatAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + inputMat.rebuildOnChange()

        + erodeValue
        erodeValue.sliderMode(Range2i(0, 50))

        + dilateValue
        dilateValue.sliderMode(Range2i(0, 50))

        outputMat.bindColorSpace(inputMat)
        + outputMat.enablePrevizButton().rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val input = inputMat.genValue(current)
                input.requireBinary(inputMat)

                val erodeVal = erodeValue.genValue(current)
                val erodeValVariable = uniqueVariable("erodeValue", int(erodeVal.v))

                val dilateVal = erodeValue.genValue(current)
                val dilateValVariable = uniqueVariable("dilateValue", int(dilateVal.v))

                val element = uniqueVariable("element", JvmOpenCv.Mat.nullValue)

                val output = uniqueVariable("${input.value}ErodedDilated", JvmOpenCv.Mat.new())

                group {
                    public(erodeValVariable, erodeValue.tunerLabel())
                    public(dilateValVariable, dilateValue.tunerLabel())
                    private(element)
                    private(output)
                }

                current.scope {
                    nameComment()

                    input.value.v("copyTo", output)

                    ifCondition(erodeValVariable greaterThan int(0)) {
                        element instanceSet JvmOpenCv.Imgproc.callValue(
                            "getStructuringElement",
                            JvmOpenCv.Mat,
                            JvmOpenCv.Imgproc.MORPH_RECT,
                            JvmOpenCv.Size.new(erodeValVariable, erodeValVariable)
                        )

                        JvmOpenCv.Imgproc("erode", output, output, element)

                        separate()

                        element("release")
                    }

                    separate()

                    ifCondition(dilateValVariable greaterThan int(0)) {
                        element instanceSet JvmOpenCv.Imgproc.callValue(
                            "getStructuringElement",
                            JvmOpenCv.Mat,
                            JvmOpenCv.Imgproc.MORPH_RECT,
                            JvmOpenCv.Size.new(dilateValVariable, dilateValVariable)
                        )

                        JvmOpenCv.Imgproc("dilate", output, output, element)

                        separate()

                        element("release")
                    }

                    outputMat.streamIfEnabled(output, ColorSpace.GRAY.resolved())
                }

                session.outputMatValue = GenValue.Mat(output.resolved(), input.color, input.isBinary)

                session
            }
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current {
                val input = inputMat.genValue(current)
                input.requireBinary(inputMat)

                val erodeVal = erodeValue.genValue(current)
                val dilateVal = dilateValue.genValue(current)

                val output = uniqueVariable(
                    "${input.value}_eroded_dilated",
                    input.value.v.callValue("copy", CPythonLanguage.NoType)
                )

                val elementErode = uniqueVariable("element_erode", cv2.callValue(
                    "getStructuringElement",
                    CPythonLanguage.NoType,
                    cv2.MORPH_RECT,
                    CPythonLanguage.tuple(erodeVal.v, erodeVal.v)
                ))

                val elementDilate = uniqueVariable("element_dilate", cv2.callValue(
                    "getStructuringElement",
                    CPythonLanguage.NoType,
                    cv2.MORPH_RECT,
                    CPythonLanguage.tuple(dilateVal.v, dilateVal.v)
                ))

                group {
                    public(elementErode)
                    public(elementDilate)
                }

                current.scope {
                    nameComment()

                    local(output)

                    cv2("erode", output, output, elementErode)
                    cv2("dilate", output, output, elementDilate)
                }

                session.outputMatValue = GenValue.Mat(output.resolved(), input.color, input.isBinary)

                session
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if(attrib == outputMat) {
            return GenValue.Mat.defer { current.sessionOf(this)?.outputMatValue }
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("inputMat", inputMat)
        encoder.obj("erodeValue", erodeValue)
        encoder.obj("dilateValue", dilateValue)
        encoder.obj("outputMat", outputMat)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputMat", inputMat)
        decoder.obj("erodeValue", erodeValue)
        decoder.obj("dilateValue", dilateValue)
        decoder.obj("outputMat", outputMat)
    }

    class Session : CodeGenSession {
        lateinit var outputMatValue: GenValue.Mat
    }

}



