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
import org.deltacv.visiongraph.attribute.vision.structs.RectAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
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
    name = "nod_boundingrect",
    category = NodeCategory.FEATURE_DET,
    description = "des_boundingrect"
)
@CodecType
class BoundingRectsNode : DrawNode<BoundingRectsNode.Session>() {

    val inputContours = ListAttribute(INPUT, "$[att_contours]", PointsAttribute)
    val outputRects = ListAttribute(OUTPUT, "$[att_boundingrects]", RectAttribute)

    override fun onEnable() {
        + inputContours.rebuildOnChange()
        + outputRects.rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val input = inputContours.genValue(current)

                val listName = if (input is GenValue.List.Runtime<*>) {
                    "${input.value.v}Rects"
                } else "rects"

                val rectsList = uniqueVariable(listName, JavaTypes.ArrayList(JvmOpenCv.Rect).new())

                group {
                    private(rectsList)
                }

                current.scope {
                    nameComment()

                    rectsList("clear")

                    if (input is GenValue.List.Runtime<*>) {
                        foreach(variable(JvmOpenCv.MatOfPoint, "points"), input.value.v) {
                            rectsList("add", Imgproc.callValue("boundingRect", JvmOpenCv.Rect, it))
                        }
                    } else {
                        for (points in (input as GenValue.List.Actual<*>).elements) {
                            if (points is GenValue.Points.Runtime) {
                                ifCondition(points.value.v notEqualsTo language.nullValue) {
                                    rectsList(
                                        "add",
                                        Imgproc.callValue("boundingRect", JvmOpenCv.Rect, points.value.v)
                                    )
                                }
                            } else {
                                raise("Invalid input type for contours")
                            }
                        }
                    }
                }

                session.outputRects = GenValue.List.Runtime(rectsList.resolved(), GenValue.Rect.Inst::class.resolved())

                session
            }
        }

        generatorFor(CPythonLanguage) {
            current {
                val session = Session()

                val input = inputContours.genValue(current)

                val listName = if (input is GenValue.List.Runtime<*>) {
                    "${input.value.v}_rects"
                } else "rects"

                val rectsList = uniqueVariable(listName, CPythonLanguage.NoType.newArray(0.v))

                current.scope {
                    nameComment()

                    local(rectsList)

                    if (input is GenValue.List.Runtime<*>) {
                        foreach(variable(CPythonLanguage.NoType, "points"), input.value.v) { points ->
                            rectsList("append", cv2.callValue("boundingRect", CPythonLanguage.NoType, points))
                        }
                    } else {
                        for (points in (input as GenValue.List.Actual<*>).elements) {
                            if (points is GenValue.Points.Runtime) {
                                ifCondition(points.value.v notEqualsTo language.nullValue) {
                                    rectsList(
                                        "append",
                                        cv2.callValue("boundingRect", CPythonLanguage.NoType, points.value.v)
                                    )
                                }
                            } else {
                                raise("Invalid input type for contours")
                            }
                        }
                    }
                }

                session.outputRects = GenValue.List.Runtime(rectsList.resolved(), GenValue.Rect.Inst::class.resolved())

                session
            }
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if (attrib == outputRects) {
            return GenValue.List.Runtime.defer { current.sessionOf(this)?.outputRects }
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("inputContours", inputContours)
        encoder.obj("outputRects", outputRects)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputContours", inputContours)
        decoder.obj("outputRects", outputRects)
    }

    class Session : CodeGenSession {
        lateinit var outputRects: GenValue.List.Runtime<GenValue.Rect.Inst>
    }

}



