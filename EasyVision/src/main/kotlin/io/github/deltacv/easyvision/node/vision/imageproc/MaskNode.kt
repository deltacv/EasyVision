package io.github.deltacv.easyvision.node.vision.imageproc

import io.github.deltacv.easyvision.attribute.Attribute
import io.github.deltacv.easyvision.attribute.rebuildOnChange
import io.github.deltacv.easyvision.attribute.vision.MatAttribute
import io.github.deltacv.easyvision.codegen.CodeGen
import io.github.deltacv.easyvision.codegen.CodeGenSession
import io.github.deltacv.easyvision.codegen.GenValue
import io.github.deltacv.easyvision.codegen.build.type.OpenCvTypes.Core
import io.github.deltacv.easyvision.codegen.build.type.OpenCvTypes.Mat
import io.github.deltacv.easyvision.node.Category
import io.github.deltacv.easyvision.node.DrawNode
import io.github.deltacv.easyvision.node.RegisterNode

@RegisterNode(
    name = "nod_binarymask",
    category = Category.IMAGE_PROC,
    description = "Takes a normal image and performs a mask based on a binary image, discards or includes areas from the normal image based on the binary image."
)
class MaskNode : DrawNode<MaskNode.Session>(){

    val inputMat = MatAttribute(INPUT, "$[att_input]")
    val maskMat  = MatAttribute(INPUT, "$[att_binarymask]")

    val outputMat = MatAttribute(OUTPUT, "$[att_output]").enablePrevizButton()

    override fun onEnable() {
        + inputMat.rebuildOnChange()
        + maskMat.rebuildOnChange()

        + outputMat.rebuildOnChange()
    }

    override fun genCode(current: CodeGen.Current) = current {
        val session = Session()

        val input = inputMat.value(current)
        input.requireNonBinary(inputMat)

        val mask = maskMat.value(current)
        mask.requireBinary(maskMat)

        val output = uniqueVariable("${input.value.value!!}Mask", Mat.new())

        group {
            private(output)
        }

        current.scope {
            output("release")
            Core("bitwise_and", input.value, input.value, output, mask.value)
            outputMat.streamIfEnabled(output, input.color)
        }

        session.outputMat = GenValue.Mat(output, input.color)

        session
    }

    override fun getOutputValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        genCodeIfNecessary(current)

        if(attrib == outputMat) {
            return lastGenSession!!.outputMat
        }

        noValue(attrib)
    }

    class Session : CodeGenSession {
        lateinit var outputMat: GenValue.Mat
    }

}