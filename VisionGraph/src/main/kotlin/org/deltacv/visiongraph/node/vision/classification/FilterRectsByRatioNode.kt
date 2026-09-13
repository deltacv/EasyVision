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

package org.deltacv.visiongraph.node.vision.classification

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.math.IntAttribute
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.structs.RectAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.AccessorVariable
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
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
    name = "nod_grouprects_byratio",
    category = NodeCategory.CLASSIFICATION,
    description = "des_grouprects_byratio"
)
@CodecType
class FilterRectsByRatioNode : DrawNode<FilterRectsByRatioNode.Session>() {

    val input = ListAttribute(INPUT, "$[att_rects]", RectAttribute)

    val minRatio = IntAttribute(INPUT, "$[att_minratio]")
    val maxRatio = IntAttribute(INPUT, "$[att_maxratio]")

    val output = ListAttribute(OUTPUT, "$[att_filteredrects]", RectAttribute)

    override fun onEnable() {
        + input.rebuildOnChange()

        + minRatio
        + maxRatio

        minRatio.value.set(0)
        maxRatio.value.set(100)

        + output.rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            val session = Session()

            val rects = input.genValue(current)

            if(rects !is GenValue.List.Runtime<*>) {
                raise("Input contours must be a runtime list") // TODO: support other types
            }

            val minRatioVal = minRatio.genValue(current)
            val maxRatioVal = maxRatio.genValue(current)

            current {
                val minRatioVar = uniqueVariable("minRatio", minRatioVal.v)
                val maxRatioVar = uniqueVariable("maxRatio", maxRatioVal.v)

                val rectsVar = uniqueVariable("${rects.value.v}ByRatio", JavaTypes.ArrayList(JvmOpenCv.Rect).new())

                group {
                    public(minRatioVar, minRatio.tunerLabel())
                    public(maxRatioVar, maxRatio.tunerLabel())

                    private(rectsVar)
                }

                current.scope {
                    nameComment()

                    rectsVar("clear")

                    foreach(AccessorVariable(JvmOpenCv.Rect, "rect"), rects.value.v) { rect ->
                        val ratioVar = uniqueVariable("ratio", rect.propertyValue("height", IntType).castTo(DoubleType) / rect.propertyValue("width", IntType).castTo(DoubleType))
                        local(ratioVar)

                        ifCondition((ratioVar greaterOrEqualThan (minRatioVar / 100.0.v)) and (ratioVar lessOrEqualThan (maxRatioVar / 100.0.v))) {
                            rectsVar("add", rect)
                        }
                    }
                }

                session.output = GenValue.List.Runtime(rectsVar.resolved(), GenValue.Rect.Inst::class.resolved())
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            val rects = input.genValue(current)

            if(rects !is GenValue.List.Runtime<*>) {
                raise("Input must be a runtime list") // TODO: support other types
            }

            val minRatioVal = minRatio.genValue(current)
            val maxRatioVal = maxRatio.genValue(current)

            current {
                val rectsVar = uniqueVariable("${rects.value.v}_by_ratio", CPythonLanguage.NoType.newArrayOfValues())

                current.scope {
                    nameComment()

                    local(rectsVar)

                    separate()

                    foreach(AccessorVariable(CPythonLanguage.NoType, "rect"), rects.value.v) { rect ->
                        val ratioVar = uniqueVariable("ratio", (rect[2.v, IntType] / rect[3.v, IntType]))
                        local(ratioVar)

                        ifCondition((ratioVar greaterOrEqualThan (minRatioVal.v / 100.0.v)) and (ratioVar lessOrEqualThan (maxRatioVal.v / 100.0.v))) {
                            rectsVar("append", rect)
                        }
                    }
                }

                session.output = GenValue.List.Runtime(rectsVar.resolved(), GenValue.Points.Runtime::class.resolved())
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        return when(attrib) {
            output -> GenValue.List.Runtime.defer { current.sessionOf(this)?.output }
            else -> noValue(attrib)
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)
        encoder.obj("minRatio", minRatio)
        encoder.obj("maxRatio", maxRatio)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("input", input)
        decoder.obj("minRatio", minRatio)
        decoder.obj("maxRatio", maxRatio)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var output: GenValue.List.Runtime<*>
    }

}



