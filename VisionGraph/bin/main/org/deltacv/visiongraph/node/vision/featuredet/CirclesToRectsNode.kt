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

package org.deltacv.visiongraph.node.vision.featuredet

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.structs.CircleAttribute
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
    name = "$[nod_circlesto_rects]",
    category = NodeCategory.TRANSFORM,
    description = "$[des_circlesto_rects]"
)
@CodecType
class CirclesToRectsNode : DrawNode<CirclesToRectsNode.Session>() {

    val circles = ListAttribute(INPUT, "$[att_circles]", CircleAttribute)

    val output = ListAttribute(OUTPUT, "$[att_rects]", RectAttribute)

    override fun onEnable() {
        + circles.rebuildOnChange()
        + output.rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            val session = Session()

            current {
                val circles = circles.genValue(current)
                if (circles !is GenValue.List.Runtime<*>) {
                    raise("Only runtime lists are supported for now")
                }

                val rects = uniqueVariable("circlesRects", JavaTypes.ArrayList(JvmOpenCv.Rect).new())

                group {
                    private(rects)
                }
5
                current.scope {
                    nameComment()

                    foreach(AccessorVariable(JvmOpenCv.Circle, "circle"), circles.value.v) {
                        val center = it.propertyValue("center", JvmOpenCv.Point)
                        val x = center.propertyValue("x", DoubleType)
                        val y = center.propertyValue("y", DoubleType)

                        val radius = it.propertyValue("radius", DoubleType)

                        rects("add",
                            JvmOpenCv.Rect.new(
                                int(x - it.propertyValue("radius", DoubleType)),
                                int(y - it.propertyValue("radius", DoubleType)),
                                int(radius * 2.v),
                                int(radius * 2.v)
                            )
                        )
                    }
                }

                session.outputRects =
                    GenValue.List.Runtime(rects.resolved(), GenValue.Rect.Inst::class.resolved())
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current {
                val circles = circles.genValue(current)
                if (circles !is GenValue.List.Runtime<*>) {
                    raise("Only runtime lists are supported for now")
                }

                current.scope {
                    nameComment()

                    val rects = uniqueVariable("circles_rects", CPythonLanguage.NoType.newArrayOfValues())
                    local(rects)

                    separate()

                    ifCondition(circles.value.v isNotInstanceOf  nullType) {
                        foreach(CPythonLanguage.accessorTupleVariable("x", "y", "r"), circles.value.v) {
                            rects("append",
                                CPythonLanguage.tuple(
                                    int(it.get("x") - it.get("r")),
                                    int(it.get("y") - it.get("r")),
                                    int(it.get("r") * 2.v),
                                    int(it.get("r") * 2.v)
                                )
                            )
                        }
                    }

                    session.outputRects =
                        GenValue.List.Runtime(rects.resolved(), GenValue.Rect.Inst::class.resolved())
                }
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if (attrib == output) {
            return GenValue.List.Runtime.defer { current.sessionOf(this)?.outputRects }
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("circles", circles)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("circles", circles)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var outputRects: GenValue.List.Runtime<GenValue.Rect.Inst>
    }
}



