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
import org.deltacv.visiongraph.attribute.rebuildOnLink
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.attribute.vision.structs.PointsAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Mat
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.MatOfPoint
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
    name = "nod_findcontours",
    category = NodeCategory.FEATURE_DET,
    description = "des_findcontours"
)
@CodecType
class FindContoursNode : DrawNode<FindContoursNode.Session>() {

    val inputMat = MatAttribute(INPUT, "$[att_binaryinput]")
    val outputPoints = ListAttribute(OUTPUT, "$[att_contours]", PointsAttribute)

    override fun onEnable() {
        + inputMat.rebuildOnLink()
        + outputPoints
    }

    override val generators = polyglot {
        generatorFor<BaseLanguage> {
            current {
                val session = Session()

                val input = inputMat.genValue(current)
                input.requireBinary(inputMat)

                val list = uniqueVariable("contours", JavaTypes.ArrayList(MatOfPoint).new())
                val hierarchyMat = uniqueVariable("hierarchy", Mat.new())

                group {
                    private(list)
                    private(hierarchyMat)
                }

                current.scope {
                    nameComment()

                    list("clear")
                    hierarchyMat("release")

                    Imgproc("findContours", input.value.v, list, hierarchyMat, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                }

                session.contoursList = GenValue.List.Runtime(list.resolved(), GenValue.Points.Actual::class.resolved())

                session
            }
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            val input = inputMat.genValue(current)
            input.requireBinary(inputMat)

            current {
                current.scope {
                    nameComment()

                    val contours = tryName("contours")
                    val hierarchy = tryName("hierarchy")

                    val result = CPythonLanguage.declaredTupleVariable(cv2.callValue(
                        "findContours",
                        CPythonLanguage.NoType,
                        input.value.v,
                        cv2.RETR_EXTERNAL,
                        cv2.CHAIN_APPROX_SIMPLE
                    ), contours, hierarchy)

                    local(result)

                    session.contoursList = GenValue.List.Runtime(result.get(contours).resolved(), GenValue.Points.Actual::class.resolved())
                }

                session
            }
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if(attrib == outputPoints) {
            return GenValue.List.Runtime.defer { current.sessionOf(this)?.contoursList }
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("inputMat", inputMat)
        encoder.obj("outputPoints", outputPoints)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputMat", inputMat)
        decoder.obj("outputPoints", outputPoints)
    }

    class Session : CodeGenSession {
        lateinit var contoursList: GenValue.List.Runtime<GenValue.Points.Actual>
    }

}