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
import org.deltacv.visiongraph.attribute.misc.EnumAttribute
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.structs.PointsAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.AccessorVariable
import org.deltacv.visiongraph.codegen.build.DeclarableVariable
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv
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

enum class BoundingMode {
    Normal, Rotated
}

@PaperNode(
    name = "nod_groupcontours_byratio",
    category = NodeCategory.CLASSIFICATION,
    description = "des_groupcontours_byratio"
)
@CodecType
class FilterContoursByRatioNode : DrawNode<FilterContoursByRatioNode.Session>() {

    val input = ListAttribute(INPUT, "$[att_contours]", PointsAttribute)

    val boundingMode = EnumAttribute(INPUT, "$[att_boundingmode]", BoundingMode.entries)

    val minRatio = IntAttribute(INPUT, "$[att_minratio]")
    val maxRatio = IntAttribute(INPUT, "$[att_maxratio]")

    val output = ListAttribute(OUTPUT, "$[att_filteredcontours]", PointsAttribute)

    override fun onEnable() {
        + input.rebuildOnChange()

        + boundingMode.rebuildOnChange()

        + minRatio
        + maxRatio

        minRatio.value.set(0)
        maxRatio.value.set(100)

        + output.rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            val session = Session()

            val contours = input.genValue(current)

            if(contours !is GenValue.List.Runtime<*>) {
                raise("Input contours must be a runtime list") // TODO: support other types
            }

            val minRatioVal = minRatio.genValue(current)
            val maxRatioVal = maxRatio.genValue(current)
            val mode = boundingMode.genValue(current).value

            current {
                val minRatioVar = uniqueVariable("minRatio", int(minRatioVal).v)
                val maxRatioVar = uniqueVariable("maxRatio", int(maxRatioVal).v)

                val contoursVarName = contours.value.map { it.value ?: "contours" }
                val contoursVar = uniqueVariable("${contoursVarName.v}ByRatio", JavaTypes.ArrayList(JvmOpenCv.MatOfPoint).new())

                val pointsVarName = contours.value.map { it.value ?: "points" }
                val points2f = uniqueVariable("${pointsVarName.v}2f", JvmOpenCv.MatOfPoint2f.new())

                group {
                    public(minRatioVar, minRatio.tunerLabel())
                    public(maxRatioVar, maxRatio.tunerLabel())

                    private(contoursVar)

                    if(mode == BoundingMode.Rotated) {
                        private(points2f)
                    }
                }

                current.scope {
                    nameComment()

                    contoursVar("clear")

                    foreach(AccessorVariable(JvmOpenCv.MatOfPoint, "contour"), contours.value.v) { contour ->
                        val ratioVar = if(mode == BoundingMode.Normal) {
                            val rect = uniqueVariable("rect", Imgproc.callValue("boundingRect", JvmOpenCv.Rect, contour))
                            local(rect)

                            uniqueVariable("ratio", rect.propertyValue("height", IntType).castTo(DoubleType) / rect.propertyValue("width", IntType).castTo(DoubleType))
                        } else {
                            points2f("release")
                            contour("convertTo", points2f, cvTypeValue("CV_32F"))

                            val rect = uniqueVariable("rect", Imgproc.callValue("minAreaRect", JvmOpenCv.RotatedRect, points2f))
                            local(rect)

                            separate()

                            val width = uniqueVariable("width", rect.propertyValue("size", JvmOpenCv.Size).propertyValue("width", IntType).castTo(DoubleType))
                            val height = uniqueVariable("height", rect.propertyValue("size", JvmOpenCv.Size).propertyValue("height", IntType).castTo(DoubleType))

                            local(width)
                            local(height)

                            ifCondition(height greaterThan width) {
                                val temp = uniqueVariable("temp", width)
                                local(temp)

                                width set height
                                height set temp
                            }

                            uniqueVariable("ratio", height / width)
                        }

                        separate()

                        local(ratioVar)

                        separate()

                        ifCondition((ratioVar greaterOrEqualThan (minRatioVar / 100.0.v)) and (ratioVar lessOrEqualThan (maxRatioVar / 100.0.v))) {
                            contoursVar("add", contour)
                        }
                    }
                }

                session.output = GenValue.List.Runtime(contoursVar.resolved(), GenValue.Points.Runtime::class.resolved())
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            val contours = input.genValue(current)

            if(contours !is GenValue.List.Runtime<*>) {
                raise("Input contours must be a runtime list") // TODO: support other types
            }

            val minRatioVal = minRatio.genValue(current)
            val maxRatioVal = maxRatio.genValue(current)

            current {
                val contoursVar = uniqueVariable("${contours.value.v}_by_ratio", CPythonLanguage.NoType.newArrayOfValues())

                current.scope {
                    local(contoursVar)

                    separate()

                    foreach(DeclarableVariable(CPythonLanguage.NoType, "contour"), contours.value.v) { contour ->
                        val rectangle = CPythonLanguage.declaredTupleVariable(
                            CPythonOpenCv.cv2.callValue("boundingRect", CPythonLanguage.NoType, contour), // "rect" is a tuple of 4 values:
                            "x", "y", "w", "h"
                        )
                        local(rectangle)

                        val ratioVar = uniqueVariable("ratio", (rectangle.get("w") / rectangle.get("h")))
                        local(ratioVar)

                        separate()

                        ifCondition((ratioVar greaterOrEqualThan (int(minRatioVal).v / 100.0.v)) and (ratioVar lessOrEqualThan (int(maxRatioVal).v / 100.0.v))) {
                            contoursVar("append", contour)
                        }
                    }
                }

                session.output = GenValue.List.Runtime(contoursVar.resolved(), GenValue.Points.Runtime::class.resolved())
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
        encoder.obj("boundingMode", boundingMode)
        encoder.obj("minRatio", minRatio)
        encoder.obj("maxRatio", maxRatio)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("input", input)
        decoder.obj("boundingMode", boundingMode)
        decoder.obj("minRatio", minRatio)
        decoder.obj("maxRatio", maxRatio)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var output: GenValue.List.Runtime<*>
    }

}



