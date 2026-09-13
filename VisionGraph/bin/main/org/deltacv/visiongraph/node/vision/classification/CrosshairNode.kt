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
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.attribute.vision.structs.LineParametersAttribute
import org.deltacv.visiongraph.attribute.vision.structs.PointsAttribute
import org.deltacv.visiongraph.attribute.vision.structs.Vector2Attribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.BaseLanguage
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@PaperNode(
    name = "nod_crosshair",
    category = NodeCategory.CLASSIFICATION,
    description = "des_crosshair"
)
@CodecType
class CrosshairNode : DrawNode<CrosshairNode.Session>() {

    enum class DetectionMode {
        Inside, Nearest
    }

    val drawCrosshairOn = MatAttribute(INPUT, "$[att_drawon_image]")
    val input = ListAttribute(INPUT, "$[att_contours]", PointsAttribute)

    val crosshairPosition = Vector2Attribute(INPUT, "$[att_crosshairposition]")

    val crosshairScale = IntAttribute(INPUT, "$[att_scale]")

    val crosshairLineParams = LineParametersAttribute(INPUT, "$[att_crosshairline_params]")

    val detectionMode = EnumAttribute(INPUT, "$[att_detectionmode]", DetectionMode.entries)

    val outputCrosshair = ListAttribute(OUTPUT, "$[att_crosshair]", PointsAttribute)
    val outputCrosshairImage = MatAttribute(OUTPUT, "$[att_crosshairimage]")

    override fun onEnable() {
        + drawCrosshairOn.rebuildOnChange()
        + input.rebuildOnChange()
        + crosshairScale

        crosshairScale.value.set(5)

        + crosshairPosition
        + crosshairLineParams

        + detectionMode.rebuildOnChange()

        outputCrosshairImage.bindColorSpace(drawCrosshairOn)
        + outputCrosshairImage.enablePrevizButton().rebuildOnChange()
        + outputCrosshair.rebuildOnChange()
    }

    override val generators = polyglot {
        generatorFor<BaseLanguage> {
            val session = Session()

            val inputPoints = input.genValue(current)

            if (inputPoints !is GenValue.List.Runtime<*>) {
                raise("") // TODO: Handle non-runtime lists
            }

            val drawOn = drawCrosshairOn.genValue(current)

            val crosshairLineParams = JvmOpenCv.toRuntimeLineParameters(crosshairLineParams.genValue(current), current)

            val crosshairSizeValue = crosshairScale.genValue(current)

            current {
                val drawOnValue = drawOn.value.v

                val crosshair = uniqueVariable("crosshair", JavaTypes.ArrayList(JvmOpenCv.MatOfPoint).new())
                val crosshairImage = uniqueVariable("crosshairImage", JvmOpenCv.Mat.new())
                val crosshairSize = uniqueVariable("crosshairSize", int(crosshairSizeValue).v)

                group {
                    private(crosshair)
                    private(crosshairImage)

                    public(crosshairSize, crosshairScale.tunerLabel())
                }

                current.scope {
                    nameComment()

                    drawOnValue("copyTo", crosshairImage)

                    separate()

                    val crosshairPositionVector = crosshairPosition.genValue(current).toRuntime(current)

                    val crosshairPoint = uniqueVariable(
                        "crosshairPoint", run {
                            // draw crosshair at center with vector offset
                            val rows = drawOnValue.callValue("rows", IntType)
                            val cols = drawOnValue.callValue("cols", IntType)

                            JvmOpenCv.Point.new(cols / 2.v + crosshairPositionVector.x.v, rows / 2.v + crosshairPositionVector.y.v)
                        }
                    )

                    local(crosshairPoint)

                    val rows = drawOnValue.callValue("rows", IntType)
                    val cols = drawOnValue.callValue("cols", IntType)

                    // Define a scale factor based on the image dimensions
                    val scaleFactor = uniqueVariable("scaleFactor", (rows + cols) / 2.v)
                    local(scaleFactor)

                    separate()

                    // Adjust crosshairSize based on the scale factor
                    val adjustedCrosshairSize = uniqueVariable("adjustedCrosshairSize", crosshairSize * scaleFactor / 100.v)
                    local(adjustedCrosshairSize)

                    separate()

                    val crosshairCol = crosshairLineParams.color
                    val crosshairThickness = crosshairLineParams.thicknessValue

                    Imgproc(
                        "line",
                        crosshairImage,
                        JvmOpenCv.Point.new(
                            crosshairPoint.propertyValue("x", DoubleType) - adjustedCrosshairSize,
                            crosshairPoint.propertyValue("y", DoubleType)
                        ),
                        JvmOpenCv.Point.new(
                            crosshairPoint.propertyValue("x", DoubleType) + adjustedCrosshairSize,
                            crosshairPoint.propertyValue("y", DoubleType)
                        ),
                        JvmOpenCv.Scalar(crosshairCol, current),
                        crosshairThickness.value.v
                    )

                    Imgproc(
                        "line",
                        crosshairImage,
                        JvmOpenCv.Point.new(
                            crosshairPoint.propertyValue("x", DoubleType),
                            crosshairPoint.propertyValue("y", DoubleType) - adjustedCrosshairSize
                        ),
                        JvmOpenCv.Point.new(
                            crosshairPoint.propertyValue("x", DoubleType),
                            crosshairPoint.propertyValue("y", DoubleType) + adjustedCrosshairSize
                        ),
                        JvmOpenCv.Scalar(crosshairCol, current),
                        crosshairThickness.value.v
                    )

                    separate()

                    crosshair("clear")

                    separate()

                    val currDist = if(detectionMode.genValue(current).value == DetectionMode.Nearest) {
                        uniqueVariable("currDist", 0.0.v)
                    } else {
                        null
                    }

                    val closestContour = if(detectionMode.genValue(current).value == DetectionMode.Nearest) {
                        uniqueVariable("closestContour", JvmOpenCv.MatOfPoint.nullValue)
                    } else {
                        null
                    }

                    if(currDist != null) {
                        local(currDist)
                    }
                    if(closestContour != null) {
                        local(closestContour)
                        separate()
                    }

                    foreach(variable(JvmOpenCv.MatOfPoint, "contour"), inputPoints.value.v) {
                        // Get the bounding rectangle of the current contour
                        val boundingRect = uniqueVariable(
                            "boundingRect", Imgproc.callValue("boundingRect", JvmOpenCv.Rect, it)
                        )
                        local(boundingRect)

                        separate()

                        when(detectionMode.genValue(current).value) {
                            DetectionMode.Inside -> {
                                // Check if the crosshair rectangle is inside the bounding rectangle
                                ifCondition(
                                    boundingRect.callValue("contains", BooleanType, crosshairPoint).condition()
                                ) {
                                    // Add the contour to the crosshair if the bounding rectangle contains the crosshair
                                    crosshair("add", it)
                                }
                            }
                            DetectionMode.Nearest -> {
                                // get distance
                                val distance = uniqueVariable("newDist", JavaTypes.Math.callValue("sqrt", DoubleType,
                                    JavaTypes.Math.callValue("pow", DoubleType,
                                        crosshairPoint.propertyValue("x", DoubleType) - (boundingRect.propertyValue("x", IntType) + boundingRect.propertyValue("width", IntType) / 2.v),
                                        2.v
                                    ) + JavaTypes.Math.callValue("pow", DoubleType,
                                        crosshairPoint.propertyValue("y", DoubleType) - (boundingRect.propertyValue("y", IntType) + boundingRect.propertyValue("height", IntType) / 2.v),
                                        2.v
                                    )
                                ))

                                local(distance)

                                ifCondition((closestContour!! equalsTo JvmOpenCv.MatOfPoint.nullValue) or (JavaTypes.Math.callValue("abs", DoubleType, distance) lessOrEqualThan currDist!!)) {
                                    currDist set JavaTypes.Math.callValue("abs", DoubleType, distance)
                                    closestContour set it
                                }
                            }
                        }
                    }

                    if(DetectionMode.Nearest == detectionMode.genValue(current).value) {
                        ifCondition(closestContour!! notEqualsTo JvmOpenCv.MatOfPoint.nullValue) {
                            crosshair("add", closestContour)
                        }
                    }

                    outputCrosshairImage.streamIfEnabled(crosshairImage, drawOn.color)

                    session.outputCrosshair = GenValue.List.Runtime(crosshair.resolved(), GenValue.Points.Runtime::class.resolved())
                    session.outputCrosshairImage = GenValue.Mat(crosshairImage.resolved(), drawOn.color, drawOn.isBinary)
                }
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            val inputPoints = input.genValue(current)

            if (inputPoints !is GenValue.List.Runtime<*>) {
                raise("") // TODO: Handle non-runtime lists
            }

            val crosshairPositionVector = crosshairPosition.genValue(current) as GenValue.Vec2.Actual

            val drawOn = drawCrosshairOn.genValue(current)

            val crosshairLineParams = crosshairLineParams.genValue(current) as GenValue.LineParameters.Components
            val crosshairSizeValue = crosshairScale.genValue(current)

            current {
                val drawOnValue = drawOn.value.v

                val crosshair = uniqueVariable("crosshair", CPythonLanguage.NoType.newArrayOfValues())
                val crosshairImage = uniqueVariable("crosshair_image", drawOnValue.callValue("copy", CPythonLanguage.NoType))

                current.scope {
                    nameComment()

                    local(crosshair)
                    local(crosshairImage)

                    separate()

                    val rowsCols = CPythonLanguage.declaredTupleVariable(
                        crosshairImage.propertyValue("shape", CPythonLanguage.NoType),
                        "height", "width", "channels"
                    )

                    local(rowsCols)

                    val rows = rowsCols.get("height")
                    val cols = rowsCols.get("width")

                    separate()

                    val (crosshairPointX, crosshairPointY) = Pair(
                        (cols / 2.v) + crosshairPositionVector.x.v,
                        (rows / 2.v) + crosshairPositionVector.y.v
                    )

                    val pointX = uniqueVariable("crosshair_point_x", crosshairPointX)
                    val pointY = uniqueVariable("crosshair_point_y", crosshairPointY)

                    local(pointX)
                    local(pointY)

                    // Define a scale factor based on the image dimensions
                    val scaleFactor = uniqueVariable("scale_factor", (rows + cols) / 2.v)
                    local(scaleFactor)

                    separate()

                    // Adjust crosshairSize based on the scale factor
                    val adjustedCrosshairSize =
                        uniqueVariable("adjusted_crosshair_size", int(crosshairSizeValue).v * scaleFactor / 100.v)
                    local(adjustedCrosshairSize)

                    separate()

                    val crosshairCol = CPythonOpenCv.scalarTuple(crosshairLineParams.color, current)
                    val crosshairThickness = crosshairLineParams.thickness.v

                    CPythonOpenCv.cv2(
                        "line",
                        crosshairImage,
                        CPythonLanguage.tuple(
                            int(pointX - adjustedCrosshairSize),
                            int(pointY)
                        ),
                        CPythonLanguage.tuple(
                            int(pointX + adjustedCrosshairSize),
                            int(pointY)
                        ),
                        crosshairCol,
                        crosshairThickness
                    )

                    CPythonOpenCv.cv2(
                        "line",
                        crosshairImage,
                        CPythonLanguage.tuple(
                            int(pointX),
                            int(pointY - adjustedCrosshairSize)
                        ),
                        CPythonLanguage.tuple(
                            int(pointX),
                            int(pointY + adjustedCrosshairSize)
                        ),
                        crosshairCol,
                        crosshairThickness
                    )

                    separate()

                    foreach(variable(CPythonLanguage.NoType, "contour"), inputPoints.value.v) {
                        // Get the bounding rectangle of the current contour
                        val boundingRect = CPythonLanguage.declaredTupleVariable(
                            CPythonOpenCv.cv2.callValue("boundingRect", CPythonLanguage.NoType, it),
                            "x", "y", "w", "h"
                        )
                        local(boundingRect)

                        separate()
                        // Extract x, y, w, h values
                        val x = boundingRect.get("x")
                        val y = boundingRect.get("y")
                        val w = boundingRect.get("w")
                        val h = boundingRect.get("h")

                        // Check if the crosshair rectangle is inside the bounding rectangle
                        // perform aabb check
                        ifCondition(
                            (pointX greaterOrEqualThan x)
                                    and (pointX lessOrEqualThan (x + w))
                                    and (pointY greaterOrEqualThan y)
                                    and (pointY lessOrEqualThan (y + h))
                        ) {
                            // Add the contour to the crosshair if the bounding rectangle contains the crosshair
                            crosshair("append", it)
                        }
                    }

                    session.outputCrosshair = GenValue.List.Runtime(crosshair.resolved(), GenValue.Points.Runtime::class.resolved())
                    session.outputCrosshairImage = GenValue.Mat(crosshairImage.resolved(), drawOn.color, drawOn.isBinary)
                }
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        return when (attrib) {
            outputCrosshair -> GenValue.List.Runtime.defer { current.sessionOf(this)?.outputCrosshair }
            outputCrosshairImage -> GenValue.Mat.defer { current.sessionOf(this)?.outputCrosshairImage }
            else -> noValue(attrib)
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("drawCrosshairOn", drawCrosshairOn)
        encoder.obj("input", input)
        encoder.obj("crosshairPosition", crosshairPosition)
        encoder.obj("crosshairScale", crosshairScale)
        encoder.obj("crosshairLineParams", crosshairLineParams)
        encoder.obj("detectionMode", detectionMode)
        encoder.obj("outputCrosshair", outputCrosshair)
        encoder.obj("outputCrosshairImage", outputCrosshairImage)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("drawCrosshairOn", drawCrosshairOn)
        decoder.obj("input", input)
        decoder.obj("crosshairPosition", crosshairPosition)
        decoder.obj("crosshairScale", crosshairScale)
        decoder.obj("crosshairLineParams", crosshairLineParams)
        decoder.obj("detectionMode", detectionMode)
        decoder.obj("outputCrosshair", outputCrosshair)
        decoder.obj("outputCrosshairImage", outputCrosshairImage)
    }

    class Session : CodeGenSession {
        lateinit var outputCrosshair: GenValue.List.Runtime<GenValue.Points.Runtime>
        lateinit var outputCrosshairImage: GenValue.Mat
    }

}



