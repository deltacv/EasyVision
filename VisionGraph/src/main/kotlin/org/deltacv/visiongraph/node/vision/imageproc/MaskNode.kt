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
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
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
    name = "nod_binarymask",
    category = NodeCategory.IMAGE_PROC,
    description = "des_binarymask"
)
@CodecType
class MaskNode : DrawNode<MaskNode.Session>(){

    val inputMat = MatAttribute(INPUT, "$[att_input]")
    val maskMat  = MatAttribute(INPUT, "$[att_binarymask]")

    val outputMat = MatAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + inputMat.rebuildOnChange()
        + maskMat.rebuildOnChange()

        outputMat.bindColorSpace(inputMat)
        + outputMat.enablePrevizButton().rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val input = inputMat.genValue(current)
                input.requireNonBinary(inputMat)

                val mask = maskMat.genValue(current)
                mask.requireBinary(maskMat)

                val output = uniqueVariable("${input.value}Mask", Mat.new())

                group {
                    private(output)
                }

                current.scope {
                    nameComment()

                    output("release")
                    input.value.v("copyTo", output, mask.value.v)

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

                val mask = maskMat.genValue(current)
                mask.requireBinary(maskMat)

                current.scope {
                    nameComment()

                    val output = uniqueVariable("${input.value}_mask",
                        cv2.callValue("bitwise_and", CPythonLanguage.NoType, input.value.v, input.value.v, CPythonLanguage.namedArgument("mask", mask.value.v))
                    )
                    local(output)

                    session.outputMat = GenValue.Mat(output.resolved(), input.color)
                }

                session
            }
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
        encoder.obj("maskMat", maskMat)
        encoder.obj("outputMat", outputMat)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputMat", inputMat)
        decoder.obj("maskMat", maskMat)
        decoder.obj("outputMat", outputMat)
    }

    class Session : CodeGenSession {
        lateinit var outputMat: GenValue.Mat
    }

}



