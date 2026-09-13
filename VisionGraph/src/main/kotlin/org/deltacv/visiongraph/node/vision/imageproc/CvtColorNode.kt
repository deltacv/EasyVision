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
import org.deltacv.visiongraph.attribute.misc.EnumAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
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
    name = "nod_cvtcolor",
    category = NodeCategory.IMAGE_PROC,
    description = "des_cvtcolor"
)
@CodecType
class CvtColorNode : DrawNode<CvtColorNode.Session>() {

    val input  = MatAttribute(INPUT, "$[att_input]")
    val output = MatAttribute(OUTPUT, "$[att_output]").enablePrevizButton()

    val convertTo = EnumAttribute(INPUT, "$[att_convertto]", ColorSpace.options)

    override fun onEnable() {
        + input.rebuildOnChange()
        + convertTo.rebuildOnChange()

        output.bindColorSpace(convertTo)
        + output.rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val inputMat = input.genValue(current)
                inputMat.requireNonBinary(input)

                val targetColor = convertTo.genValue(current).value
                val matColor = inputMat.color

                val matColorResolved = matColor.resolve()

                if(matColorResolved == null || matColorResolved != targetColor) {
                    val mat = uniqueVariable("${inputMat.value.v}${targetColor.name}", Mat.new())

                    group {
                        // create mat instance variable
                        private(mat)
                    }

                    current.scope { // add a cvtColor step in processFrame
                        nameComment()

                        deferredBlock(Resolvable.DependentPlaceholder(matColor) {
                            {
                                if(it != targetColor) {
                                    Imgproc("cvtColor", inputMat.value.v, mat, cvtColorValue(it, targetColor))
                                } else {
                                    // copyTo
                                    Imgproc("copyTo", inputMat.value.v, mat)
                                }
                            }
                        })

                        output.streamIfEnabled(mat, targetColor.resolved())
                    }

                    session.outputMatValue = GenValue.Mat(mat.resolved(), targetColor.resolved()) // store data in the current session
                } else {
                    // we don't need to do any processing if the mat is
                    // already of the color the user specified to convert to
                    session.outputMatValue = inputMat
                }

                session
            }
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current {
                val inputMat = input.genValue(current)
                inputMat.requireNonBinary(input)

                val targetColor = convertTo.genValue(current).value
                val matColor = inputMat.color
                val matColorResolved = matColor.resolve()

                current.scope {
                    nameComment()

                    if (matColorResolved == null || matColorResolved != targetColor) {
                        val value = Resolvable.DependentPlaceholder(matColor) {
                            if(it != targetColor) {
                                cv2.callValue("cvtColor", CPythonLanguage.NoType, inputMat.value.v, cvtColorValue(it, targetColor))
                            } else {
                                inputMat.value.v.callValue("copy", CPythonLanguage.NoType)
                            }
                        }.v

                        val mat = uniqueVariable("${inputMat.value.v}_${targetColor.name.lowercase()}", value)

                        local(mat)

                        session.outputMatValue = GenValue.Mat(mat.resolved(), targetColor.resolved()) // store data in the current session
                    } else {
                        // we don't need to do any processing if the mat is
                        // already of the color the user specified to convert to
                        session.outputMatValue = inputMat
                    }
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
        encoder.obj("convertTo", convertTo)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("input", input)
        decoder.obj("convertTo", convertTo)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var outputMatValue: GenValue.Mat
    }

}



