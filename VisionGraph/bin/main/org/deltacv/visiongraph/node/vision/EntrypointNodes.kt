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

package org.deltacv.visiongraph.node.vision

import imgui.ImVec2
import imgui.extension.imnodes.ImNodes
import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.math.DoubleAttribute
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.attribute.vision.structs.PointsAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.NoSession
import org.deltacv.visiongraph.codegen.Visibility
import org.deltacv.visiongraph.codegen.build.AccessorVariable
import org.deltacv.visiongraph.codegen.build.Parameter
import org.deltacv.visiongraph.codegen.build.Value
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.BaseLanguage
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v1.data.SerializeData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.objOrSkip

@PaperNode(
    name = "nod_pipelineinput",
    category = NodeCategory.FLOW,
    showInList = false
)
@CodecType
class InputMatNode(
    var windowSizeSupplier: (() -> ImVec2)? = null
) : DrawNode<NoSession>(isDeletable = false) {

    private var lastWindowSize: ImVec2? = null

    override fun init() {
        val onDraw = editor.onDraw {
            if (serializedId != null) {
                removeListener()
                return@onDraw
            }

            if (!isOnEditor) {
                removeListener()
                return@onDraw
            }

            windowSizeSupplier?.let {
                val nodeSize = ImVec2()
                ImNodes.getNodeDimensions(nodeSize, id)

                val windowSize = it()

                if (lastWindowSize == null || (lastWindowSize!!.x != windowSize.x || lastWindowSize!!.y != windowSize.y)) {
                    ImNodes.setNodeScreenSpacePos(id, nodeSize.x * 0.5f, windowSize.y / 2f - nodeSize.y / 2)
                }

                lastWindowSize = ImVec2(windowSize.x, windowSize.y)

                // by default, the node editor starts with 3 nodes
                // InputMatNode, OutputMatNode, flagsNode
                // if there are more than 3 nodes, we'll stop setting the position
                // since it's likely the user has just created a new project
                if (editor.nodes.inmutable.size > 3 || ImNodes.isNodeSelected(id)) {
                    removeListener()
                    editor.onEditorPan.run()
                }
            }
        }

        editor.onEditorPan.once {
            editor.onDraw.removeListener(onDraw)
        }
    }

    @SerializeData
    val output = MatAttribute(OUTPUT, "$[att_input]")

    override fun onEnable() {
        output.colorSpace = ColorSpace.RGBA
        +output.rebuildOnChange()
    }

    fun ensureAttributeExists() { // prevent weird oopsies due to the special way these persistent buddies are handled
        enable()
        output.enable()
    }

    override val generators = polyglot {
        generatorForAny { NoSession }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.objOrSkip("output", output)
    }

    override fun getGenValueOf(
        current: CodeGen.Current,
        attrib: Attribute
    ) = when (current.language) {
        is CPythonLanguage -> GenValue.Mat(
            AccessorVariable(CPythonLanguage.NoType, "input").resolved(),
            ColorSpace.RGBA.resolved()
        )

        else -> GenValue.Mat(AccessorVariable(JvmOpenCv.Mat, "input").resolved(), ColorSpace.RGBA.resolved())
    }
}

@PaperNode(
    name = "nod_pipelineoutput",
    category = NodeCategory.FLOW,
    showInList = false
)
@CodecType
class OutputMatNode @JvmOverloads constructor(
    var windowSizeSupplier: (() -> ImVec2)? = null
) : DrawNode<NoSession>(isDeletable = false) {

    var streamId: Int? = null
    private var lastWindowSize: ImVec2? = null

    override fun init() {
        val onDrawId = editor.onDraw {
            if (serializedId != null) {
                removeListener()
                return@onDraw
            }

            if (!isOnEditor) {
                removeListener()
                return@onDraw
            }

            windowSizeSupplier?.let {
                val nodeSize = ImVec2()
                ImNodes.getNodeDimensions(nodeSize, id)

                val windowSize = it()

                if (lastWindowSize == null || (lastWindowSize!!.x != windowSize.x || lastWindowSize!!.y != windowSize.y)) {
                    ImNodes.setNodeScreenSpacePos(
                        id,
                        windowSize.x - nodeSize.x * 1.5f,
                        windowSize.y / 2f - nodeSize.y / 2f
                    )
                }

                lastWindowSize = ImVec2(windowSize.x, windowSize.y)

                // by default, the node editor starts with 4 nodes
                // InputMatNode, OutputMatNode, originNode, flagsNode
                // if there are more than 4 nodes, we'll stop adjusting the position
                // since it's likely the user is starting to work on their project
                if (editor.nodes.inmutable.size > 4 || ImNodes.isNodeSelected(id)) {
                    removeListener()
                    editor.onEditorPan.run()
                }
            }
        }

        editor.onEditorPan.once {
            editor.onDraw.removeListener(onDrawId)
        }
    }

    val input = MatAttribute(INPUT, "$[att_output]")
    val crosshair = PointsAttribute(INPUT, "$[att_crosshair]")
    val exportedData = ListAttribute(INPUT, "$[att_exporteddata]", DoubleAttribute)

    override fun onEnable() {
        + input.rebuildOnChange()
        + crosshair.rebuildOnChange()
        + exportedData.rebuildOnChange()
    }

    fun ensureAttributeExists() { // prevent weird oopsies due to the special way these persistent elements are handled
        enable()
        input.enable()
        crosshair.enable()
        exportedData.enable()
    }

    override val generators = polyglot {
        generatorFor<BaseLanguage> {
            val exportedDataValue = exportedData.genValue(current)

            current {
                val inputValue = input.genValue(current)
                val hasExportedData =
                    (exportedDataValue is GenValue.List.Actual<*> && exportedDataValue.elements.isNotEmpty())
                            || exportedData.hasLink

                if (hasExportedData) {
                    val exportedData = uniqueVariable("exportedData", DoubleType.newArray(0.v))

                    current.codeGen.classStartScope {
                        private(exportedData)
                    }

                    current.codeGen.classEndScope {
                        val dataParameter = Parameter(DoubleType.arrayType(), "data")

                        method(Visibility.PRIVATE, VoidType, "setExportedData", dataParameter, isSynchronized = true) {
                            exportedData instanceSet dataParameter
                        }

                        separate()

                        method(Visibility.PUBLIC, DoubleType.arrayType(), "getExportedData", isSynchronized = true) {
                            returnMethod(exportedData)
                        }

                        separate()

                        val indexParameter = Parameter(IntType, "index")
                        method(Visibility.PUBLIC, DoubleType, "getExportedData", indexParameter, isSynchronized = true) {
                            ifCondition(indexParameter greaterOrEqualThan exportedData.propertyValue("length", IntType)) {
                                returnMethod(0.0.v)
                            }.elseCondition {
                                returnMethod(exportedData[indexParameter, DoubleType])
                            }
                        }
                    }
                }

                current.scope(false) {
                    if (crosshair.availableLinkedAttributes.isNotEmpty()) {
                        val crosshairValue = crosshair.genValue(current)

                        ifCondition((crosshairValue.value.v notEqualsTo nullValue)) {
                            val boundingRect = uniqueVariable(
                                "boundingRect",
                                Imgproc.callValue("boundingRect", JvmOpenCv.Rect, crosshairValue.value.v)
                            )
                            local(boundingRect)

                            separate()

                            // Calculate the centroid of the contour
                            val centroidX =
                                uniqueVariable(
                                    "centroidX",
                                    (boundingRect.callValue("tl", JvmOpenCv.Point).propertyValue("x", DoubleType) +
                                            boundingRect.callValue("br", JvmOpenCv.Point)
                                                .propertyValue("x", DoubleType)) / 2.v
                                )
                            val centroidY =
                                uniqueVariable(
                                    "centroidY",
                                    (boundingRect.callValue("tl", JvmOpenCv.Point).propertyValue("y", DoubleType) +
                                            boundingRect.callValue("br", JvmOpenCv.Point)
                                                .propertyValue("y", DoubleType)) / 2.v
                                )

                            local(centroidX)
                            local(centroidY)

                            separate()

                            val centroid = uniqueVariable("centroid", JvmOpenCv.Point.new(centroidX, centroidY))
                            local(centroid)
                            val contourArea = uniqueVariable(
                                "contourArea",
                                Imgproc.callValue("contourArea", DoubleType, crosshairValue.value.v)
                            )
                            local(contourArea)

                            separate()

                            val crosshairSize = 10.v
                            val crosshairThickness = 5.v

                            val crosshairCol =
                                uniqueVariable("crosshairCol", JvmOpenCv.Scalar.new(0.0.v, 255.0.v, 0.0.v))
                            local(crosshairCol)

                            // draw crosshair on the centroid

                            separate()

                            Imgproc(
                                "line",
                                inputValue.value.v,
                                JvmOpenCv.Point.new(centroidX - crosshairSize, centroidY),
                                JvmOpenCv.Point.new(centroidX + crosshairSize, centroidY),
                                crosshairCol,
                                crosshairThickness
                            )
                            Imgproc(
                                "line",
                                inputValue.value.v,
                                JvmOpenCv.Point.new(centroidX, centroidY - crosshairSize),
                                JvmOpenCv.Point.new(centroidX, centroidY + crosshairSize),
                                crosshairCol,
                                crosshairThickness
                            )
                        }

                        separate()
                    }

                    if (hasExportedData) {
                        exportedDataValue.match(
                            ifActual = {
                                "setExportedData"(
                                    DoubleType.newArrayOfValues(*it.elements.map { element -> element.v }
                                        .toTypedArray())
                                )
                            },
                            ifRuntime = {
                                "setExportedData"(
                                    it.value.v.callValue("toArray", DoubleType.arrayType(), DoubleType.newArray(0.v))
                                )
                            }
                        )
                    }

                    streamMat(streamId!!, inputValue.value.v, inputValue.color)
                    returnMethod(inputValue.value.v)
                }
            }

            NoSession
        }

        @Suppress("UNCHECKED_CAST")
        generatorFor(CPythonLanguage) {
            current {
                val inputValue = input.genValue(current)
                val exportedDataValue = exportedData.genValue(current)

                current.scope(false) {
                    val llpython = uniqueVariable(
                        "llpython", exportedDataValue.match(
                            ifActual = {
                                val data = mutableListOf<Value>()

                                for (d in it.elements) {
                                    when (d) {
                                        is GenValue.Double.Actual -> data.add(d.value.v)
                                        is GenValue.Double.Runtime -> data.add(d.value.v)
                                    }
                                }

                                CPythonLanguage.NoType.newArrayOfValues(*data.toTypedArray())
                            },

                            ifRuntime = {
                                it.value.v
                            }
                        )
                    )

                    local(llpython)

                    separate()

                    val crosshairValueV = if (crosshair.allLinkedAttributes.isNotEmpty()) {
                        crosshair.genValue(current).value.v
                    } else {
                        CPythonLanguage.NoType.newArrayOfValues()
                    }

                    returnMethod(CPythonLanguage.tuple(crosshairValueV, inputValue.value.v, llpython))
                }

                NoSession
            }
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)

        encoder.obj("input", input)
        encoder.obj("crosshair", crosshair)
        encoder.obj("exportedData", exportedData)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)

        decoder.obj("input", input)
        decoder.obj("crosshair", crosshair)
        decoder.obj("exportedData", exportedData)
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute) = GenValue.None
}



