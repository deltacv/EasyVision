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
import org.deltacv.visiongraph.attribute.vision.structs.PointsAttribute
import org.deltacv.visiongraph.attribute.vision.structs.RotatedRectAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.Value
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
import org.deltacv.visiongraph.codegen.dsl.ScopeCtx
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
import org.deltacv.visiongraph.serialization.v2.objOrSkip

@PaperNode(
    name = "nod_boundingrotated_rect",
    category = NodeCategory.FEATURE_DET,
    description = "des_boundingrotated_rect"
)
@CodecType
class BoundingRotatedRectsNode : DrawNode<BoundingRotatedRectsNode.Session>() {

    val contours = ListAttribute(INPUT, "$[att_contours]", PointsAttribute)
    val outputRects = ListAttribute(OUTPUT, "$[att_rects]", RotatedRectAttribute)

    override fun onEnable() {
        + contours.rebuildOnChange()
        + outputRects.rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            val session = Session()

            current {
                val input = contours.genValue(current)

                val name = if(input is GenValue.List.Runtime<*>) {
                    input.value.v
                } else null

                val points2f = uniqueVariable("${name ?: "points"}2f", JvmOpenCv.MatOfPoint2f.new())
                val rectsList = uniqueVariable("${name?.run { this.value + "R"} ?: "r"}otRects", JavaTypes.ArrayList(JvmOpenCv.RotatedRect).new())

                group {
                    private(points2f)
                    private(rectsList)
                }

                current.scope {
                    nameComment()

                    rectsList("clear")

                    fun ScopeCtx.withPoints(points: Value) {
                        points2f("release")
                        points("convertTo", points2f, cvTypeValue("CV_32F"))

                        separate()

                        rectsList("add", Imgproc.callValue("minAreaRect", JvmOpenCv.RotatedRect, points2f))
                    }

                    if(input is GenValue.List.Runtime<*>) {
                        foreach(variable(JvmOpenCv.MatOfPoint, "points"), input.value.v) {
                            withPoints(it)
                        }
                    } else {
                        for(element in (input as GenValue.List.Actual<*>).elements) {
                            if(element is GenValue.Points.Runtime) {
                                withPoints(element.value.v)
                            } else {
                                raise("Invalid input type for contours")
                            }
                        }
                    }
                }

                session.rects = GenValue.List.Runtime(rectsList.resolved(), GenValue.RotatedRect.Inst::class.resolved())
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current {
                val input = contours.genValue(current)

                val name = if(input is GenValue.List.Runtime<*>) {
                    input.value.v.toString() + "_r"
                } else null

                val rectsList = uniqueVariable("${name ?: "r"}ot_rects", CPythonLanguage.NoType.newArrayOfValues())

                current.scope {
                    nameComment()

                    local(rectsList)

                    fun ScopeCtx.withPoints(points: Value) {
                        rectsList("append", cv2.callValue("minAreaRect", CPythonLanguage.NoType, points))
                    }

                    if(input is GenValue.List.Runtime<*>) {
                        foreach(variable(CPythonLanguage.NoType, "points"), input.value.v) {
                            withPoints(it)
                        }
                    } else {
                        for(element in (input as GenValue.List.Actual<*>).elements) {
                            if(element is GenValue.Points.Runtime) {
                                withPoints(element.value.v)
                            } else {
                                raise("Invalid input type for contours")
                            }
                        }
                    }
                }

                session.rects = GenValue.List.Runtime(rectsList.resolved(), GenValue.RotatedRect.Inst::class.resolved())
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        return when(attrib) {
            outputRects -> current.nonNullSessionOf(this).rects
            else -> noValue(attrib)
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("contours", contours)
        encoder.obj("outputRects", outputRects)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.objOrSkip("contours", contours)
        decoder.objOrSkip("outputRects", outputRects)
    }

    class Session : CodeGenSession {
        lateinit var rects: GenValue.List.Runtime<GenValue.RotatedRect.Inst>
    }
}



