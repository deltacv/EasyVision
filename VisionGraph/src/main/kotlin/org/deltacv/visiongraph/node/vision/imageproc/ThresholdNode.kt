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

import imgui.ImGui
import imgui.type.ImInt
import org.deltacv.mai18n.tr
import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.misc.EnumAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.attribute.vision.structs.ScalarRangeAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Core
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Mat
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Scalar
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.gui.util.ImGuiEx
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.vision.ColorSpace
import org.deltacv.visiongraph.serialization.v1.data.SerializeData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.intOrNull

@PaperNode(
    name = "nod_colorthresh",
    category = NodeCategory.IMAGE_PROC,
    description = "des_colorthresh"
)
@CodecType
class ThresholdNode : DrawNode<ThresholdNode.Session>() {

    val input = MatAttribute(INPUT, "$[att_input]")
    val scalar = ScalarRangeAttribute(INPUT, ColorSpace.options[0], "$[att_threshold]")

    val output = MatAttribute(OUTPUT, "$[att_binaryoutput]")

    override fun onEnable() {
        + input.rebuildOnChange()
        + scalar
        output.colorSpace = ColorSpace.GRAY
        output.isBinary = true
        + output.enablePrevizButton().rebuildOnChange()
    }

    @SerializeData
    private var colorValue = ImInt()

    private var lastColor = ColorSpace.options.first()

    private val fontAwesome = Font.find("font-awesome")

    override fun drawNode() {
        input.drawHere()

        ImGui.newLine()

        fontAwesome.push()
        ImGui.text(EnumAttribute.icon)
        ImGui.popFont()

        ImGui.sameLine()
        ImGui.text(tr("att_colorspace"))

        ImGui.pushItemWidth(110.0f)
        val color = ImGuiEx.enumCombo(ColorSpace.options, colorValue)
        ImGui.popItemWidth()

        ImGui.newLine()

        if(color != lastColor) {
            scalar.color = color
            scalar.rebuildPreviz()
        }

        lastColor = color
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val range = scalar.genValue(current)

                var inputMat = input.genValue(current)
                inputMat.requireNonBinary(input)

                val matColor = inputMat.color
                val targetColor = lastColor

                val thresholdTargetMat = uniqueVariable("${targetColor.name.lowercase()}Threshold", Mat.new())

                val lowerScalar = uniqueVariable("lower${targetColor.name}",
                    Scalar.new(
                        range.a.min.v,
                        range.b.min.v,
                        range.c.min.v,
                        range.d.min.v,
                    )
                )

                val upperScalar = uniqueVariable("upper${targetColor.name}",
                    Scalar.new(
                        range.a.max.v,
                        range.b.max.v,
                        range.c.max.v,
                        range.d.max.v,
                    )
                )

                val (minLabel, maxLabel) = scalar.minMaxTunerLabels()

                group {
                    // lower color scalar
                    public(lowerScalar, minLabel)
                    // upper color scalar
                    public(upperScalar, maxLabel)
                    // output mat target
                    private(thresholdTargetMat)
                }

                current.scope {
                    nameComment()

                    deferredBlock(Resolvable.DependentPlaceholder(matColor) {
                        {
                            if(it != targetColor) {
                                Imgproc("cvtColor", inputMat.value.v, thresholdTargetMat, cvtColorValue(it, targetColor))
                            } else {
                                inputMat.value.v("copyTo", thresholdTargetMat)
                            }
                        }
                    })

                    Core("inRange", thresholdTargetMat, lowerScalar, upperScalar, thresholdTargetMat)
                    output.streamIfEnabled(thresholdTargetMat, ColorSpace.GRAY.resolved())
                }

                session.outputMat = GenValue.Mat(thresholdTargetMat.resolved(), ColorSpace.GRAY.resolved(), GenValue.Boolean.TRUE)

                session
            }
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current {
                val range = scalar.genValue(current)

                var inputMat = input.genValue(current)
                inputMat.requireNonBinary(input)

                val matColor = inputMat.color
                val targetColor = lastColor

                current.scope {
                    nameComment()

                    val target = uniqueVariable("threshold_target", inputMat.value.v)
                    local(target)

                    deferredBlock(Resolvable.DependentPlaceholder(matColor) {
                        {
                            target set cv2.callValue("cvtColor", CPythonLanguage.NoType, inputMat.value.v, cvtColorValue(it, targetColor))
                        }
                    })

                    val thresholdTargetMat = uniqueVariable("${targetColor.name.lowercase()}_thresh",
                        cv2.callValue("inRange", CPythonLanguage.NoType, target,
                            CPythonLanguage.tuple(range.a.min.v, range.b.min.v, range.c.min.v, range.d.min.v),
                            CPythonLanguage.tuple(range.a.max.v, range.b.max.v, range.c.max.v, range.d.max.v)
                        )
                    )

                    local(thresholdTargetMat)

                    session.outputMat = GenValue.Mat(thresholdTargetMat.resolved(), targetColor.resolved(), GenValue.Boolean.TRUE)
                }

                session
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if(attrib == output) {
            return GenValue.Mat.defer { current.sessionOf(this)?.outputMat }
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)
        encoder.int("color", colorValue.get())
        encoder.obj("scalar", scalar)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("input", input)
        colorValue.set(decoder.intOrNull("color") ?: 0)
        decoder.obj("scalar", scalar)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var outputMat: GenValue.Mat
    }

}



