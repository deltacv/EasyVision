package org.deltacv.visiongraph.node.vision.imageproc

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.rebuildOnLink
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.attribute.vision.structs.RectAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.objOrSkip

@PaperNode(
    name = "nod_extractregion",
    category = NodeCategory.IMAGE_PROC,
    description = "des_extractregion"
)
@CodecType
class ExtractRegionNode : DrawNode<ExtractRegionNode.Session>() {

    val input = MatAttribute(INPUT, "$[att_input]")
    val region = RectAttribute(INPUT, "$[att_region]")
    val output = MatAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + input.rebuildOnLink()
        + region.rebuildOnLink()

        output.bindColorSpace(input)
        + output.enablePrevizButton()
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            val session = Session()

            current {
                val inputMat = input.genValue(current)
                val regionRect = JvmOpenCv.toRectInst(region.genValue(current), current)

                val outputMat = uniqueVariable("${inputMat.value}Roi", JvmOpenCv.Mat.nullValue)

                group {
                    private(outputMat)
                }

                current.scope {
                    nameComment()

                    ifCondition(outputMat notEqualsTo nullValue) {
                        outputMat("release")
                    }

                    separate()

                    val regionRectVar = uniqueVariable("regionRect", regionRect.value.v)
                    local(regionRectVar)

                    ifCondition(regionRectVar notEqualsTo nullValue) {
                        outputMat instanceSet inputMat.value.v.callValue("submat", JvmOpenCv.Mat, regionRectVar)
                    }.elseCondition {
                        outputMat instanceSet inputMat.value.v
                    }

                    output.streamIfEnabled(outputMat, inputMat.color)
                }

                session.output = GenValue.Mat(outputMat.resolved(), inputMat.color)
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            val inputMat = input.genValue(current)
            val regionRect = CPythonOpenCv.toRectTuple(region.genValue(current), current)

            current {
                current.scope {
                    nameComment()

                    val rectVar = uniqueVariable("roi_rect", regionRect)
                    local(rectVar)

                    val roiVar = uniqueVariable("${inputMat.value.v}_roi", inputMat.value.v)
                    local(roiVar)

                    separate()

                    ifCondition(rectVar isNotInstanceOf  nullType) {
                        val rectTuple = CPythonLanguage.declaredTupleVariable(rectVar, "x", "y", "w", "h")
                        local(rectTuple)

                        val roi = inputMat.value.v[CPythonLanguage.NoType,
                            CPythonLanguage.sliceValue(
                                rectTuple.get("y"),
                                rectTuple.get("y") + rectTuple.get("h")
                            ),
                            CPythonLanguage.sliceValue(
                                rectTuple.get("x"),
                                rectTuple.get("x") + rectTuple.get("w")
                            )
                        ]

                        roiVar instanceSet roi
                    }

                    session.output = GenValue.Mat(roiVar.resolved(), inputMat.color)
                }
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute) = when(attrib) {
        output -> GenValue.Mat.defer { current.sessionOf(this)?.output }
        else -> noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)
        encoder.obj("region", region)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.objOrSkip("input", input)
        decoder.objOrSkip("region", region)
        decoder.objOrSkip("output", output)
    }

    class Session : CodeGenSession {
        lateinit var output: GenValue.Mat
    }
}



