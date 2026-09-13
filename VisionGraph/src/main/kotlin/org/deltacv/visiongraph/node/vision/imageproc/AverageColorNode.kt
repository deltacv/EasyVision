package org.deltacv.visiongraph.node.vision.imageproc

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.attribute.vision.structs.ScalarAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonOpenCv.cv2
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.node.vision.ColorSpace
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.objOrSkip

@PaperNode(
    name = "nod_avgcolor",
    category = NodeCategory.IMAGE_PROC,
    description = "des_avgcolor"
)
@CodecType
class AverageColorNode : DrawNode<AverageColorNode.Session>() {

    val input = MatAttribute(INPUT, "$[att_input]")
    val output = ScalarAttribute(OUTPUT, ColorSpace.GENERIC, "$[att_output]")

    override fun onEnable() {
        + input.rebuildOnChange()
        + output
        
        output.bindColorSpace(input)
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            val session = Session()

            current {
                val inputValue = input.genValue(current)

                val outputVar = uniqueVariable("${inputValue.value.v}Avg", JvmOpenCv.Scalar.new(0.v, 0.v, 0.v, 0.v))

                group {
                    private(outputVar)
                }

                current.scope {
                    nameComment()
                    outputVar instanceSet JvmOpenCv.Core.callValue("mean", JvmOpenCv.Scalar, inputValue.value.v)
                }

                session.output = GenValue.Scalar.Inst(outputVar.resolved())

                for (i in 0 until 4) {
                    session.outputElements[i] = GenValue.Double.Runtime(
                        outputVar.resolved().map { it.propertyValue("val", DoubleType.arrayType())[i.v, DoubleType] }
                    )
                }
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current {
                val inputValue = input.genValue(current)

                current.scope {
                    nameComment()

                    val outputVar = uniqueVariable("${inputValue.value.v}_avg", cv2.callValue("mean", CPythonLanguage.NoType, inputValue.value.v))
                    local(outputVar)

                    session.output = GenValue.Scalar.Inst(outputVar.resolved())

                    for (i in 0 until 4) {
                        session.outputElements[i] = GenValue.Double.Runtime(
                            outputVar.resolved().map { it[i.v, DoubleType] }
                        )
                    }
                }
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute) = when {
        attrib == output -> GenValue.Scalar.Inst.defer { current.sessionOf(this)?.output }
        else -> {
            val index = output.findIndex(attrib)
            if (index != null) {
                GenValue.Double.Runtime.defer { current.sessionOf(this)?.outputElements?.get(index) }
            } else noValue(attrib)
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.objOrSkip("input", input)
        decoder.objOrSkip("output", output)
    }

    class Session : CodeGenSession {
        lateinit var output: GenValue.Scalar.Inst
        val outputElements = mutableMapOf<Int, GenValue.Double.Runtime>()
    }

}