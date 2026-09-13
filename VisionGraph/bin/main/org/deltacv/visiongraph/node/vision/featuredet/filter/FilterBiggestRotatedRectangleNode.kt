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

package org.deltacv.visiongraph.node.vision.featuredet.filter

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.rebuildOnLink
import org.deltacv.visiongraph.attribute.vision.structs.RotatedRectAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.DeclarableVariable
import org.deltacv.visiongraph.codegen.build.Value
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.dsl.ScopeCtx
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.objOrSkip

@PaperNode(
    name = "nod_filterbiggest_rotrect",
    category = NodeCategory.CLASSIFICATION,
    description = "des_filterbiggest_rotrect"
)
@CodecType
class FilterBiggestRotatedRectangleNode : DrawNode<FilterBiggestRotatedRectangleNode.Session>() {

    val input = ListAttribute(INPUT, "$[att_rotrects]", RotatedRectAttribute)
    val fallback = RotatedRectAttribute(INPUT, "$[att_fallback]")
    val output = RotatedRectAttribute(OUTPUT, "$[att_biggestrot_rect]")

    override fun onEnable() {
        + input.rebuildOnLink()
        + fallback.rebuildOnLink()
        + output
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val rectsList = input.genValue(current)

                val fallbackRect = if(fallback.hasLink)
                    fallback.genValue(current)
                else null

                val biggestRect = uniqueVariable("biggestRotRect", JvmOpenCv.RotatedRect.nullValue, isNullable = true)

                group {
                    private(biggestRect)
                }

                current.scope {
                    nameComment()

                    biggestRect instanceSet biggestRect.nullValue

                    fun ScopeCtx.withRuntimeRect(rect: Value) {
                        ifCondition(rect notEqualsTo language.nullValue) {
                            ifCondition(
                                biggestRect equalsTo biggestRect.nullValue or
                                        (rect.propertyValue("size", JvmOpenCv.Size).callValue("area", DoubleType)
                                                greaterThan biggestRect.propertyValue("size", JvmOpenCv.Size).callValue("area", DoubleType)
                                                )
                            ) {
                                biggestRect instanceSet rect
                            }
                        }
                    }

                    if (rectsList is GenValue.List.Runtime<*>) {
                        foreach(variable(JvmOpenCv.RotatedRect, "rect"), rectsList.value.v) { rect ->
                            withRuntimeRect(rect)
                        }
                    } else {
                        for (element in (rectsList as GenValue.List.Actual<*>).elements) {
                            if (element is GenValue.RotatedRect.Components) {
                                separate()
                                val rect = DeclarableVariable(
                                    "rect",
                                    JvmOpenCv.RotatedRect.new(
                                        JvmOpenCv.Point.new(element.x.v, element.y.v),
                                        JvmOpenCv.Size.new(element.w.v, element.h.v),
                                        element.angle.v
                                    )
                                )

                                local(rect)

                                ifCondition(
                                    biggestRect equalsTo biggestRect.nullValue or (rect.callValue(
                                        "area",
                                        DoubleType
                                    ) greaterThan biggestRect.callValue("area", DoubleType))
                                ) {
                                    biggestRect instanceSet rect
                                }
                            } else if (element is GenValue.Rect.Inst) {
                                separate()
                                withRuntimeRect(element.value.v)
                            }
                        }
                    }

                    ifCondition(biggestRect equalsTo biggestRect.nullValue) {
                        if (fallbackRect != null) {
                            biggestRect instanceSet JvmOpenCv.toRotatedRectInst(fallbackRect, current).value.v
                        } else {
                            biggestRect instanceSet JvmOpenCv.RotatedRect.new()
                        }
                    }
                }

                session.biggestRect = GenValue.RotatedRect.Inst(biggestRect.resolved())

                session
            }
        }

        generatorFor(CPythonLanguage) {
            current {
                val session = Session()

                val rectsList = input.genValue(current)

                val fallbackRect = if(fallback.hasLink)
                    fallback.genValue(current)
                else null

                val biggestRect = uniqueVariable("biggest_rect", CPythonLanguage.nullValue, isNullable = true)

                current.scope {
                    nameComment()

                    local(biggestRect)

                    fun ScopeCtx.withRuntimeRect(rect: Value) {
                        ifCondition(rect notEqualsTo language.nullValue) {
                            // 2 - width, 3 - height, of tuple (x, y, w, h)
                            val rectArea = rect[1.v, CPythonLanguage.NoType][0.v, CPythonLanguage.NoType] * rect[1.v, CPythonLanguage.NoType][1.v, CPythonLanguage.NoType]
                            val biggestRectArea =
                                biggestRect[1.v, CPythonLanguage.NoType][0.v, CPythonLanguage.NoType] * biggestRect[1.v, CPythonLanguage.NoType][1.v, CPythonLanguage.NoType]

                            ifCondition(
                                biggestRect equalsTo CPythonLanguage.nullValue or (rectArea greaterThan biggestRectArea)
                            ) {
                                biggestRect instanceSet rect
                            }
                        }
                    }

                    if (rectsList is GenValue.List.Runtime<*>) {
                        foreach(variable(CPythonLanguage.NoType, "rect"), rectsList.value.v) { rect ->
                            withRuntimeRect(rect)
                        }
                    } else {
                        for (element in (rectsList as GenValue.List.Actual<*>).elements) {
                            if (element is GenValue.RotatedRect.Components) {
                                separate()

                                val rect = uniqueVariable(
                                    "rect",
                                    CPythonLanguage.tuple(
                                        CPythonLanguage.tuple(element.x.v, element.y.v),
                                        CPythonLanguage.tuple(element.w.v, element.h.v),
                                        element.angle.v
                                    )
                                )
                                local(rect)

                                val rectArea = rect[2.v, CPythonLanguage.NoType] * rect[3.v, CPythonLanguage.NoType]
                                val biggestRectArea =
                                    biggestRect[2.v, CPythonLanguage.NoType] * biggestRect[3.v, CPythonLanguage.NoType]

                                ifCondition(
                                    (biggestRect equalsTo CPythonLanguage.nullValue) or (rectArea greaterThan biggestRectArea)
                                ) {
                                    biggestRect instanceSet rect
                                }
                            } else if (element is GenValue.RotatedRect.Inst) {
                                separate()
                                withRuntimeRect(element.value.v)
                            }
                        }
                    }

                    ifCondition(biggestRect equalsTo CPythonLanguage.nullValue) {
                        if (fallbackRect != null) {
                            biggestRect instanceSet CPythonOpenCv.toRotatedRectTuple(fallbackRect, current)
                        } else {
                            biggestRect instanceSet CPythonLanguage.tuple(
                                CPythonLanguage.tuple(0.v, 0.v),
                                CPythonLanguage.tuple(0.v, 0.v),
                                0.v
                            )
                        }
                    }
                }

                session.biggestRect = GenValue.RotatedRect.Inst(biggestRect.resolved())

                session
            }
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if (attrib == output) {
            return GenValue.RotatedRect.Inst.defer { current.sessionOf(this)?.biggestRect }
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)
        encoder.obj("fallback", fallback)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.objOrSkip("input", input)
        decoder.objOrSkip("fallback", fallback)
        decoder.objOrSkip("output", output)
    }

    class Session : CodeGenSession {
        lateinit var biggestRect: GenValue.RotatedRect.Inst
    }

}



