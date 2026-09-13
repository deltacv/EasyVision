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
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.structs.PointsAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
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
    name = "nod_filterbiggest_contour",
    category = NodeCategory.CLASSIFICATION,
    description = "des_filterbiggest_contour"
)
@CodecType
class FilterBiggestContourNode : DrawNode<FilterBiggestContourNode.Session>() {

    val input = ListAttribute(INPUT, "$[att_contours]", PointsAttribute)
    val output = PointsAttribute(OUTPUT, "$[att_biggestcontour]")

    override fun onEnable() {
        + input.rebuildOnChange()
        + output.rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val contoursList = input.genValue(current)

                val biggestContour = uniqueVariable("biggestContour", JvmOpenCv.MatOfPoint.nullValue)

                group {
                    private(biggestContour)
                }

                current.scope {
                    nameComment()

                    biggestContour instanceSet biggestContour.nullValue

                    if(contoursList is GenValue.List.Runtime<*>) {
                        foreach(variable(JvmOpenCv.MatOfPoint, "contour"), contoursList.value.v) { contour ->
                            val contourArea = Imgproc.callValue("contourArea", JvmOpenCv.MatOfPoint, contour)
                            val biggestContourArea = Imgproc.callValue("contourArea", JvmOpenCv.MatOfPoint, biggestContour)

                            ifCondition(
                                biggestContour equalsTo biggestContour.nullValue or (contourArea greaterThan biggestContourArea)
                            ) {
                                biggestContour instanceSet contour
                            }
                        }
                    } else {
                        for(element in (contoursList as GenValue.List.Actual<*>).elements) {
                            separate()

                            val contour = if(element is GenValue.Points.Runtime) {
                                element.value.v
                            } else {
                                raise("Invalid element in contours list")
                            }

                            ifCondition(contour notEqualsTo language.nullValue) {
                                val contourArea = Imgproc.callValue("contourArea", JvmOpenCv.MatOfPoint, contour)
                                val biggestContourArea = Imgproc.callValue("contourArea", JvmOpenCv.MatOfPoint, biggestContour)

                                ifCondition(
                                    biggestContour equalsTo biggestContour.nullValue or (contourArea greaterThan biggestContourArea)
                                ) {
                                    biggestContour instanceSet contour
                                }
                            }
                        }
                    }
                }

                session.biggestContour = GenValue.Points.Runtime(biggestContour.resolved())

                session
            }
        }

        generatorFor(CPythonLanguage) {
            current {
                val session = Session()

                val inputValue = input.genValue(current)

                current.scope {
                    nameComment()

                    val contoursList = if(inputValue is GenValue.List.Runtime<*>) {
                        inputValue.value.v
                    } else {
                        val list = uniqueVariable("contours_list", CPythonLanguage.NoType.newArrayOfValues())
                        local(list)

                        for(element in (inputValue as GenValue.List.Actual<*>).elements) {
                            if(element is GenValue.Points.Runtime) {
                                ifCondition(element.value.v notEqualsTo language.nullValue) {
                                    list("append", element.value.v)
                                }
                            } else {
                                raise("Invalid element in contours list")
                            }
                        }

                        separate()

                        list
                    }

                    val biggestContour = uniqueVariable(
                        "biggest_contour",
                        CPythonOpenCv.np.callValue("array",
                            CPythonLanguage.NoType, CPythonLanguage.newArrayOf(CPythonLanguage.NoType, 0.v)
                        )
                    )

                    local(biggestContour)

                    ifCondition(contoursList isNotInstanceOf nullType) {
                        biggestContour set "max".callValue(
                            CPythonLanguage.NoType,
                            contoursList,
                            CPythonLanguage.namedArgument("key", cv2.contourArea)
                        )
                    }

                    session.biggestContour = GenValue.Points.Runtime(biggestContour.resolved())
                }

                session
            }
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if(attrib == output) {
            return GenValue.Points.Runtime.defer { current.sessionOf(this)?.biggestContour }
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("input", input)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var biggestContour: GenValue.Points.Runtime
    }

}



