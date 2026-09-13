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
import org.deltacv.visiongraph.attribute.math.DoubleAttribute
import org.deltacv.visiongraph.attribute.math.RangeAttribute
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.attribute.vision.structs.CircleAttribute
import org.deltacv.visiongraph.codegen.*
import org.deltacv.visiongraph.codegen.build.AccessorVariable
import org.deltacv.visiongraph.codegen.build.DeclarableVariable
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.BaseLanguage
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.util.Range2d

@PaperNode(
    name = "nod_houghcircles",
    category = NodeCategory.FEATURE_DET,
    description = "des_houghcircles"
)
@CodecType
class HoughCirclesNode : DrawNode<HoughCirclesNode.Session>() {

    val input = MatAttribute(INPUT, "$[att_input]")

    val minDistance = DoubleAttribute(INPUT, "$[att_mindistance]", 1.0)

    val radiusRange = RangeAttribute(INPUT, "$[att_radiusrange]", 0, 300)

    val downscale = DoubleAttribute(INPUT, "$[att_downscale]")

    val param1 = DoubleAttribute(INPUT, "$[att_edgethreshold]", 255.0)
    val param2 = DoubleAttribute(INPUT, "$[att_accumulatorthreshold]", 100.0)

    val output = ListAttribute(OUTPUT, "$[att_circles]", CircleAttribute)

    override fun onEnable() {
        + input.rebuildOnChange()

        + minDistance
        minDistance.fieldMode(Range2d(0.1, Double.MAX_VALUE))

        + radiusRange
        radiusRange.useSliders = false

        + downscale
        downscale.fieldMode(Range2d(0.1, Double.MAX_VALUE))

        + param1
        param1.fieldMode(Range2d(1.0, Double.MAX_VALUE))

        + param2
        param2.fieldMode(Range2d(1.0, Double.MAX_VALUE))

        + output.rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor<BaseLanguage> {
            // Circle type needs to be lazily evaluated to gen its inner class
            val session = Session()

            val inputValue = input.genValue(current).value

            val minDistanceValue = minDistance.genValue(current)

            val minRadiusValue = radiusRange.genValue(current).min
            val maxRadiusValue = radiusRange.genValue(current).max

            val param1Value = param1.genValue(current)
            val param2Value = param2.genValue(current)

            val downscaleValue = downscale.genValue(current)

            current {
                val circlesMatVar = uniqueVariable("houghCirclesMat", JvmOpenCv.Mat.new())
                val circlesListVar = uniqueVariable("houghCirclesList",
                    JavaTypes.ArrayList(JvmOpenCv.Circle).new()
                )

                val minDistanceVar = uniqueVariable("houghCirclesMinDistance", double(minDistanceValue).v)

                val minRadiusVar = uniqueVariable("houghCirclesMinRadius", int(minRadiusValue.toInt(current)).v)
                val maxRadiusVar = uniqueVariable("houghCirclesMaxRadius", int(maxRadiusValue.toInt(current)).v)

                val param1Var = uniqueVariable("houghCirclesParam1", double(param1Value).v)
                val param2Var = uniqueVariable("houghCirclesParam2", double(param2Value).v)

                val downscaleVar = uniqueVariable("houghCirclesDownscale", double(downscaleValue).v)

                group {
                    private(circlesMatVar)
                    private(circlesListVar)

                    public(minDistanceVar, minDistance.tunerLabel())
                    public(minRadiusVar, radiusRange.tunerLabel(0))
                    public(maxRadiusVar, radiusRange.tunerLabel(1))
                    public(param1Var, param1.tunerLabel())
                    public(param2Var, param2.tunerLabel())
                    public(downscaleVar, downscale.tunerLabel())
                }

                current.scope {
                    nameComment()

                    circlesMatVar("release")
                    circlesListVar("clear")

                    separate()

                    JvmOpenCv.Imgproc("HoughCircles",
                        inputValue.v, circlesMatVar,
                        JvmOpenCv.Imgproc.HOUGH_GRADIENT,
                        downscaleVar,
                        minDistanceVar,
                        param1Var, param2Var,
                        minRadiusVar, maxRadiusVar
                    )

                    separate()

                    // Convert Mat to List<Circle>
                    forLoop(AccessorVariable(IntType, "x"), 0.v, circlesMatVar.callValue("cols", IntType)) {
                        val circle = DeclarableVariable("circle", circlesMatVar.callValue("get", DoubleType.arrayType(), 0.v, it))
                        local(circle)

                        separate()

                        val p = DeclarableVariable("p",
                            JvmOpenCv.Point.new(
                                int(circle[0.v, DoubleType]),
                                int(circle[1.v, DoubleType])
                            )
                        )
                        local(p)

                        val c = DeclarableVariable("c", int(circle[2.v, DoubleType]))
                        local(c)

                        separate()

                        circlesListVar("add", JvmOpenCv.Circle.new(p, c))
                    }
                }

                session.circles = GenValue.List.Runtime(circlesListVar.resolved(), GenValue.Circle.Runtime::class.resolved())
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            val input = input.genValue(current).value

            val minDistanceValue = minDistance.genValue(current)

            val minRadiusValue = radiusRange.genValue(current).min
            val maxRadiusValue = radiusRange.genValue(current).max

            val param1Value = param1.genValue(current)
            val param2Value = param2.genValue(current)

            val downscaleValue = downscale.genValue(current)

            current {
                current.scope {
                    val circles = uniqueVariable("hough_circles",
                        CPythonOpenCv.cv2.callValue("HoughCircles",
                            CPythonLanguage.NoType,
                            input.v,
                            CPythonOpenCv.cv2.HOUGH_GRADIENT,
                            double(downscaleValue).v,
                            double(minDistanceValue).v,
                            double(param1Value).v,
                            double(param2Value).v,
                            double(minRadiusValue).v,
                            double(maxRadiusValue).v
                        )
                    )

                    local(circles)

                    ifCondition(circles isNotInstanceOf nullType) {
                        circles set circles[csv(0.v, CPythonLanguage.sliceValue()), CPythonLanguage.NoType] // "[0, :]"
                    }

                    session.circles = GenValue.List.Runtime(circles.resolved(), GenValue.Circle.Runtime::class.resolved())
                }
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        return when (attrib) {
            output -> GenValue.List.Runtime.defer { current.sessionOf(this)?.circles }
            else -> noValue(attrib)
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)
        encoder.obj("minDistance", minDistance)
        encoder.obj("radiusRange", radiusRange)
        encoder.obj("downscale", downscale)
        encoder.obj("param1", param1)
        encoder.obj("param2", param2)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("input", input)
        decoder.obj("minDistance", minDistance)
        decoder.obj("radiusRange", radiusRange)
        decoder.obj("downscale", downscale)
        decoder.obj("param1", param1)
        decoder.obj("param2", param2)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var circles: GenValue.List.Runtime<GenValue.Circle.Runtime>
    }
}



