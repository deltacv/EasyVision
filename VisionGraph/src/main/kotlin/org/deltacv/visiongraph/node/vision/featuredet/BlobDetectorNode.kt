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
import org.deltacv.visiongraph.attribute.AttributeMode
import org.deltacv.visiongraph.attribute.math.RangeAttribute
import org.deltacv.visiongraph.attribute.math.rebuildOnToggleChange
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.attribute.vision.structs.KeyPointAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv
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
    name = "nod_blobdetector",
    category = NodeCategory.FEATURE_DET,
    description = "des_blobdetector"
)
@CodecType
class BlobDetectorNode : DrawNode<BlobDetectorNode.Session>() {

    val input = MatAttribute(INPUT, "$[att_input]")

    val area = RangeAttribute(INPUT, "$[att_area]")
    val threshold = RangeAttribute(INPUT, "$[att_threshold]")
    val circularity = RangeAttribute(INPUT, "$[att_circularity]") { it / 100.0 }
    val convexity = RangeAttribute(INPUT, "$[att_convexity]") { it / 100.0 }
    val inertia = RangeAttribute(INPUT, "$[att_inertia]") { it / 100.0 }

    val output = ListAttribute(AttributeMode.OUTPUT, "$[att_keypoints]", KeyPointAttribute)

    override fun onEnable() {
        + input.rebuildOnChange()

        + threshold
        threshold.min = 1
        threshold.max = 255

        + area.rebuildOnToggleChange()
        area.useSliders = false
        area.useToggle = true
        area.min = 1
        area.max = Int.MAX_VALUE

        + circularity.rebuildOnToggleChange()
        circularity.useToggle = true
        circularity.min = 1
        circularity.max = 100

        + convexity.rebuildOnToggleChange()
        convexity.useToggle = true
        convexity.min = 1
        convexity.max = 100

        + inertia.rebuildOnToggleChange()
        inertia.useToggle = true
        inertia.min = 1
        inertia.max = 100

        + output
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            val session = Session()

            current {
                val inputValue = input.genValue(current).value

                val areaRangeValue = area.genValue(current)
                val thresholdRangeValue = threshold.genValue(current)
                val circularityRangeValue = circularity.genValue(current)
                val convexityRangeValue = convexity.genValue(current)
                val inertiaRangeValue = inertia.genValue(current)

                val params = uniqueVariable("blobDetectorParams", JvmOpenCv.SimpleBlobDetector.Params.new())

                val detector = uniqueVariable("detector",
                    JvmOpenCv.SimpleBlobDetector.nullValue
                )
                val keyPoints = uniqueVariable("keyPoints",
                    JvmOpenCv.MatOfKeyPoint.new()
                )

                val pref = "blobDet"

                val minThreshold = uniqueVariable("${pref}MinThreshold", thresholdRangeValue.min.toFloat(current).v)
                val maxThreshold = uniqueVariable("${pref}MaxThreshold", thresholdRangeValue.max.toFloat(current).v)

                val minArea = uniqueVariable("${pref}MinArea", areaRangeValue.min.toFloat(current).v)
                val maxArea = uniqueVariable("${pref}MaxArea", areaRangeValue.max.toFloat(current).v)

                val minCircularity = uniqueVariable("${pref}MinCircularity", circularityRangeValue.min.toFloat(current).v)
                val maxCircularity = uniqueVariable("${pref}MaxCircularity", circularityRangeValue.max.toFloat(current).v)

                val minConvexity = uniqueVariable("${pref}MinConvexity", convexityRangeValue.min.toFloat(current).v)
                val maxConvexity = uniqueVariable("${pref}MaxConvexity", convexityRangeValue.max.toFloat(current).v)

                val minInertia = uniqueVariable("${pref}MinInertia", inertiaRangeValue.min.toFloat(current).v)
                val maxInertia = uniqueVariable("${pref}MaxInertia", inertiaRangeValue.max.toFloat(current).v)

                group {
                    // fyi with the indices;
                    // 0 = min, 1 = max
                    public(minArea, area.tunerLabel(0))
                    public(maxArea, area.tunerLabel(1))

                    public(minThreshold, threshold.tunerLabel(0))
                    public(maxThreshold, threshold.tunerLabel(1))

                    public(minCircularity, circularity.tunerLabel(0))
                    public(maxCircularity, circularity.tunerLabel(1))

                    public(minConvexity, convexity.tunerLabel(0))
                    public(maxConvexity, convexity.tunerLabel(1))

                    public(minInertia, inertia.tunerLabel(0))
                    public(maxInertia, inertia.tunerLabel(1))
                }

                group {
                    private(params)
                    private(detector)
                    private(keyPoints)
                }

                initScope {
                    detector instanceSet JvmOpenCv.SimpleBlobDetector.callValue("create", JvmOpenCv.SimpleBlobDetector, params)
                }

                current.scope {
                    nameComment()

                    params("set_minThreshold", minThreshold)
                    params("set_maxThreshold", maxThreshold)

                    separate()

                    params("set_filterByArea", boolean(area.toggleValue.get()))
                    params("set_minArea", minArea)
                    params("set_maxArea", maxArea)

                    separate()

                    params("set_filterByCircularity", boolean(circularity.toggleValue.get()))
                    params("set_minCircularity", minCircularity)
                    params("set_maxCircularity", maxCircularity)

                    separate()

                    params("set_filterByConvexity", boolean(convexity.toggleValue.get()))
                    params("set_minConvexity", minConvexity)
                    params("set_maxConvexity", maxConvexity)

                    separate()

                    params("set_filterByInertia", boolean(inertia.toggleValue.get()))
                    params("set_minInertiaRatio", minInertia)
                    params("set_maxInertiaRatio", maxInertia)

                    separate()

                    detector("setParams", params)

                    separate()

                    keyPoints("release")
                    detector("detect", inputValue.v, keyPoints)
                }

                session.output = GenValue.List.Runtime(keyPoints.resolved(), GenValue.KeyPoint.Runtime::class.resolved())
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current {
                val inputValue = input.genValue(current).value

                val thresholdRangeValue = threshold.genValue(current)
                val areaRangeValue = area.genValue(current)
                val circularityRangeValue = circularity.genValue(current)
                val convexityRangeValue = convexity.genValue(current)
                val inertiaRangeValue = inertia.genValue(current)

                val params = uniqueVariable("blob_detector_params",
                    CPythonOpenCv.cv2.callValue("SimpleBlobDetector_Params", CPythonLanguage.NoType)
                )

                val detector = uniqueVariable("blob_detector",
                    CPythonOpenCv.cv2.callValue("SimpleBlobDetector_create", CPythonLanguage.NoType, params)
                )

                initScope {
                    local(params)

                    separate()

                    params.propertyVariable("minThreshold", CPythonLanguage.NoType) set float(thresholdRangeValue.min.toFloat(current)).v
                    params.propertyVariable("maxThreshold", CPythonLanguage.NoType) set float(thresholdRangeValue.max.toFloat(current)).v

                    separate()

                    params.propertyVariable("filterByArea", CPythonLanguage.NoType) set boolean(area.toggleValue.get())
                    params.propertyVariable("minArea", CPythonLanguage.NoType) set float(areaRangeValue.min.toFloat(current)).v
                    params.propertyVariable("maxArea", CPythonLanguage.NoType) set float(areaRangeValue.max.toFloat(current)).v

                    separate()

                    params.propertyVariable("filterByCircularity", CPythonLanguage.NoType) set boolean(circularity.toggleValue.get())
                    params.propertyVariable("minCircularity", CPythonLanguage.NoType) set float(circularityRangeValue.min.toFloat(current)).v
                    params.propertyVariable("maxCircularity", CPythonLanguage.NoType) set float(circularityRangeValue.max.toFloat(current)).v

                    separate()

                    params.propertyVariable("filterByConvexity", CPythonLanguage.NoType) set boolean(convexity.toggleValue.get())
                    params.propertyVariable("minConvexity", CPythonLanguage.NoType) set float(convexityRangeValue.min.toFloat(current)).v
                    params.propertyVariable("maxConvexity", CPythonLanguage.NoType) set float(convexityRangeValue.max.toFloat(current)).v

                    separate()

                    params.propertyVariable("filterByInertia", CPythonLanguage.NoType) set boolean(inertia.toggleValue.get())
                    params.propertyVariable("minInertiaRatio", CPythonLanguage.NoType) set float(inertiaRangeValue.min.toFloat(current)).v
                    params.propertyVariable("maxInertiaRatio", CPythonLanguage.NoType) set float(inertiaRangeValue.max.toFloat(current)).v

                    local(detector)
                }

                current.scope {
                    nameComment()

                    val keyPoints = uniqueVariable("keypoints", detector.callValue("detect", CPythonLanguage.NoType, inputValue.v))

                    local(keyPoints)

                    session.output = GenValue.List.Runtime(keyPoints.resolved(), GenValue.KeyPoint.Runtime::class.resolved())
                }
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
        encoder.obj("area", area)
        encoder.obj("threshold", threshold)
        encoder.obj("circularity", circularity)
        encoder.obj("convexity", convexity)
        encoder.obj("inertia", inertia)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("input", input)
        decoder.obj("area", area)
        decoder.obj("threshold", threshold)
        decoder.obj("circularity", circularity)
        decoder.obj("convexity", convexity)
        decoder.obj("inertia", inertia)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var output: GenValue.List.Runtime<GenValue.KeyPoint.Runtime>
    }
}



