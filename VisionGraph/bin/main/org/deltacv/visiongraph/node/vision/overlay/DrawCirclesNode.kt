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
import org.deltacv.visiongraph.attribute.vision.structs.CircleAttribute
import org.deltacv.visiongraph.attribute.vision.structs.LineParametersAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.AccessorVariable
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
    name = "nod_drawcircles",
    category = NodeCategory.OVERLAY,
    description = "des_drawcircles"
)
@CodecType
open class DrawCirclesNode : DrawNode<DrawCirclesNode.Session>() {

    val inputMat = MatAttribute(INPUT, "$[att_input]")
    val circles = ListAttribute(INPUT, "$[att_circles]", CircleAttribute)

    val line = LineParametersAttribute(INPUT, "$[att_params]")

    val outputMat = MatAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + inputMat.rebuildOnChange()

        + line

        + circles.rebuildOnChange()

        outputMat.bindColorSpace(inputMat)
        + outputMat.enablePrevizButton().rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val line = JvmOpenCv.toRuntimeLineParameters(line.genValue(current), current)

                val input = inputMat.genValue(current)
                val circlesValue = circles.genValue(current) as? GenValue.List.Runtime<*>
                    ?: raise("") // TODO: handle non-runtime lists

                val output = uniqueVariable("${input.value.v}Circles", Mat.new())

                group {
                    private(output)
                }

                current.scope {
                    nameComment()

                    input.value.v("copyTo", output)

                    foreach(AccessorVariable(JvmOpenCv.Circle, "circle"), circlesValue.value.v) {
                        JvmOpenCv.Imgproc(
                            "circle",
                            output,
                            it.propertyValue("center", JvmOpenCv.Point),
                            int(it.propertyValue("radius", FloatType)),
                            JvmOpenCv.Scalar(line.color, current),
                            line.thicknessValue.v
                        )
                    }

                    outputMat.streamIfEnabled(output, input.color)
                }

                session.outputMat = GenValue.Mat(output.resolved(), input.color, input.isBinary)

                session
            }
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current {
                val input = inputMat.genValue(current)
                val circlesValue = circles.genValue(current) as? GenValue.List.Runtime<*>
                    ?: raise("") // TODO: handle non-runtime lists

                val line = line.genValue(current) as GenValue.LineParameters.Components

                current.scope {
                    nameComment()

                    val output = uniqueVariable("${input.value.v}_circles",
                        input.value.v.callValue("copy", CPythonLanguage.NoType)
                    )
                    local(output)

                    ifCondition(circlesValue.value.v isNotInstanceOf nullType) {
                        foreach(CPythonLanguage.accessorTupleVariable("x", "y", "r"), circlesValue.value.v) {
                            CPythonOpenCv.cv2("circle",
                                output,
                                CPythonLanguage.tuple(int(it.get("x")), int(it.get("y"))),
                                int(it.get("r")),
                                CPythonLanguage.tuple(
                                    line.color.a.v,
                                    line.color.b.v,
                                    line.color.c.v
                                ),
                                line.thickness.v
                            )
                        }
                    }

                    session.outputMat = GenValue.Mat(output.resolved(), input.color, input.isBinary)
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
        encoder.obj("circles", circles)
        encoder.obj("line", line)
        encoder.obj("outputMat", outputMat)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputMat", inputMat)
        decoder.obj("circles", circles)
        decoder.obj("line", line)
        decoder.obj("outputMat", outputMat)
    }

    class Session : CodeGenSession {
        lateinit var outputMat: GenValue.Mat
    }

}



