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
package org.deltacv.visiongraph.node.vision.imageproc.bitwise

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
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
    name = "nod_bitwiseand",
    category = NodeCategory.IMAGE_PROC,
    description = "des_bitwiseand"
)
@CodecType
class BitwiseANDNode : DrawNode<BitwiseANDNode.Session>() {

    val first = MatAttribute(INPUT, "$[att_first]")
    val second = MatAttribute(INPUT, "$[att_second]")

    val output = MatAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + first.rebuildOnChange()
        + second.rebuildOnChange()

        output.bindColorSpace(first)
        + output.enablePrevizButton().rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            val session = Session()

            val firstValue = first.genValue(current)
            val secondValue = second.genValue(current)

            Resolvable.DoubleDependentPlaceholder(firstValue.isBinary.value, secondValue.isBinary.value) { first, second ->
                first == second
            }.letOrDefer {
                raiseAssert(it, "err_bitwiseor_binary")
            }

            current {
                val outputMat = uniqueVariable("bitwiseANDMat", Mat.new())

                group {
                    public(outputMat)
                }

                current.scope {
                    nameComment()

                    outputMat("release")
                    JvmOpenCv.Core("bitwise_and", firstValue.value.v, secondValue.value.v, outputMat)
                    output.streamIfEnabled(outputMat, secondValue.color)
                }

                val isBinaryPlaceholder = Resolvable.DoubleDependentPlaceholder(firstValue.isBinary.value, secondValue.isBinary.value) { first, second ->
                    first && second
                }
                val isBinary = GenValue.Boolean(isBinaryPlaceholder)

                session.outputMatValue = GenValue.Mat(outputMat.resolved(), secondValue.color, isBinary)
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            val firstValue = first.genValue(current)
            val secondValue = second.genValue(current)

            Resolvable.DoubleDependentPlaceholder(firstValue.isBinary.value, secondValue.isBinary.value) { first, second ->
                first == second
            }.letOrDefer {
                raiseAssert(it, "err_bitwiseor_binary")
            }

            current {
                val value = CPythonOpenCv.cv2.callValue("bitwise_and", CPythonLanguage.NoType, firstValue.value.v, secondValue.value.v)
                val variable = uniqueVariable("bitwiseANDMat", value)

                current.scope {
                    nameComment()
                    local(variable)
                }

                val isBinaryPlaceholder = Resolvable.DoubleDependentPlaceholder(firstValue.isBinary.value, secondValue.isBinary.value) { first, second ->
                    first && second
                }
                val isBinary = GenValue.Boolean(isBinaryPlaceholder)

                session.outputMatValue = GenValue.Mat(variable.resolved(), secondValue.color, isBinary)
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
        encoder.obj("first", first)
        encoder.obj("second", second)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("first", first)
        decoder.obj("second", second)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var outputMatValue: GenValue.Mat
    }

}



