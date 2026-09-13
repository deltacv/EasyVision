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
import org.deltacv.visiongraph.attribute.misc.EnumAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Mat
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Size
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage.tuple
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

enum class BlurAlgorithm { Gaussian, Box, Median, Bilateral }

@PaperNode(
    name = "nod_blur",
    category = NodeCategory.IMAGE_PROC,
    description = "des_blur"
)
@CodecType
class BlurNode : DrawNode<BlurNode.Session>() {

    val input = MatAttribute(INPUT, "$[att_input]")

    val blurAlgo = EnumAttribute(INPUT, "$[att_bluralgo]", BlurAlgorithm.entries)
    val blurValue = IntAttribute(INPUT, "$[att_value]")

    val output = MatAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + input.rebuildOnChange()
        + blurAlgo
        + blurValue
        output.bindColorSpace(input)
        + output.enablePrevizButton().rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val inputMat = input.genValue(current)
                val algo = blurAlgo.genValue(current).value
                val blurVal = blurValue.genValue(current)

                val blurValVariable = uniqueVariable("blurValue", int(blurVal.v))
                val outputMat = uniqueVariable("blur${algo.name}Mat", Mat.new())

                group {
                    public(blurValVariable, blurValue.tunerLabel())
                    private(outputMat)
                }

                current.scope {
                    nameComment()

                    when(algo) {
                        BlurAlgorithm.Gaussian -> {
                            val kernelSize = 6.v * blurValVariable + 1.v
                            val sizeBlurVal = Size.new(kernelSize, kernelSize)

                            Imgproc("GaussianBlur", inputMat.value.v, outputMat, sizeBlurVal, blurValVariable)
                        }
                        BlurAlgorithm.Box -> {
                            val kernelSize = 2.v * blurValVariable + 1.v
                            val sizeBlurVal = Size.new(kernelSize, kernelSize)

                            Imgproc("blur", inputMat.value.v, outputMat, sizeBlurVal)
                        }
                        BlurAlgorithm.Median -> {
                            val kernelSize = 2.v * blurValVariable + 1.v
                            Imgproc("medianBlur", inputMat.value.v, outputMat, kernelSize)
                        }
                        BlurAlgorithm.Bilateral -> {
                            Imgproc("bilateralFilter", inputMat.value.v, outputMat, (-1).v, blurValVariable, blurValVariable)
                        }
                    }


                    output.streamIfEnabled(outputMat, inputMat.color)
                }

                session.outputMatValue = GenValue.Mat(outputMat.resolved(), inputMat.color)

                session
            }
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current {
                val inputMat = input.genValue(current)
                val algo = blurAlgo.genValue(current).value
                val blurVal = blurValue.genValue(current)

                current.scope {
                    nameComment()

                    val value = when (algo) {
                        BlurAlgorithm.Gaussian -> {
                            val kernelSize = uniqueVariable("kernel", 6.v * int(blurVal.v) + 1.v)
                            local(kernelSize)
                            val sizeBlurVal = tuple(kernelSize, kernelSize)

                            cv2.callValue("GaussianBlur", CPythonLanguage.NoType, inputMat.value.v, sizeBlurVal, int(blurVal.v))
                        }

                        BlurAlgorithm.Box -> {
                            val kernelSize = uniqueVariable("kernel", 2.v * int(blurVal.v) + 1.v)
                            local(kernelSize)

                            cv2.callValue("blur", CPythonLanguage.NoType, inputMat.value.v, tuple(kernelSize, kernelSize))
                        }

                        BlurAlgorithm.Median -> {
                            val kernelSize = 2.v * int(blurVal.v) + 1.v
                            cv2.callValue("medianBlur", CPythonLanguage.NoType, inputMat.value.v, kernelSize)
                        }

                        BlurAlgorithm.Bilateral -> {
                            cv2.callValue(
                                "bilateralFilter",
                                CPythonLanguage.NoType,
                                inputMat.value.v,
                                (-1).v,
                                blurVal.v,
                                blurVal.v
                            )
                        }
                    }

                    val variable = uniqueVariable("blur_${algo.name.lowercase()}", value)
                    local(variable)

                    session.outputMatValue = GenValue.Mat(variable.resolved(), inputMat.color)
                }

            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if(attrib == output) {
            return GenValue.Mat.defer { current.sessionOf(this)?.outputMatValue }
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)
        encoder.obj("blurAlgo", blurAlgo)
        encoder.obj("blurValue", blurValue)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("input", input)
        decoder.obj("blurAlgo", blurAlgo)
        decoder.obj("blurValue", blurValue)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var outputMatValue: GenValue.Mat
    }

}



