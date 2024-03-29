package io.github.deltacv.easyvision.node.vision.imageproc

import imgui.ImGui
import imgui.type.ImInt
import io.github.deltacv.easyvision.attribute.Attribute
import io.github.deltacv.easyvision.attribute.rebuildOnChange
import io.github.deltacv.easyvision.attribute.vision.MatAttribute
import io.github.deltacv.easyvision.attribute.vision.structs.ScalarRangeAttribute
import io.github.deltacv.easyvision.codegen.CodeGen
import io.github.deltacv.easyvision.codegen.CodeGenSession
import io.github.deltacv.easyvision.codegen.GenValue
import io.github.deltacv.easyvision.codegen.build.type.OpenCvTypes.Core
import io.github.deltacv.easyvision.codegen.build.type.OpenCvTypes.Imgproc
import io.github.deltacv.easyvision.codegen.build.type.OpenCvTypes.Mat
import io.github.deltacv.easyvision.codegen.build.type.OpenCvTypes.Scalar
import io.github.deltacv.easyvision.gui.util.ExtraWidgets
import io.github.deltacv.easyvision.node.RegisterNode
import io.github.deltacv.easyvision.node.Category
import io.github.deltacv.easyvision.node.DrawNode
import io.github.deltacv.easyvision.node.vision.ColorSpace
import io.github.deltacv.easyvision.serialization.data.SerializeData

@RegisterNode(
    name = "nod_colorthresh",
    category = Category.IMAGE_PROC,
    description = "Performs a threshold in the input image and returns a binary image, discarding the pixels that were outside the range in the color space specified."
)
class ThresholdNode : DrawNode<ThresholdNode.Session>() {

    val input = MatAttribute(INPUT, "$[att_input]")
    val scalar = ScalarRangeAttribute(INPUT, ColorSpace.values()[0], "$[att_threshold]")

    val output = MatAttribute(OUTPUT, "$[att_binaryoutput]")

    override fun onEnable() {
        + input.rebuildOnChange()
        + scalar
        + output.enablePrevizButton().rebuildOnChange()
    }

    @SerializeData
    private var colorValue = ImInt()

    private var lastColor = ColorSpace.values()[0]

    override fun drawNode() {
        input.drawHere()

        ImGui.newLine()
        ImGui.text("(Enum) Color Space")

        ImGui.pushItemWidth(110.0f)
        val color = ExtraWidgets.enumCombo(ColorSpace.values(), colorValue)
        ImGui.popItemWidth()

        ImGui.newLine()

        if(color != lastColor) {
            scalar.color = color
        }

        lastColor = color
    }

    override fun genCode(current: CodeGen.Current) = current {
        val session = Session()

        val range = scalar.value(current)

        var inputMat = input.value(current)
        inputMat.requireNonBinary(input)
        
        val matColor = inputMat.color
        val targetColor = lastColor
        
        val needsCvt = matColor != targetColor

        val cvtMat = uniqueVariable("${targetColor.name.lowercase()}Mat", Mat.new())
        val thresholdTargetMat = uniqueVariable("${targetColor.name.lowercase()}BinaryMat", Mat.new())

        val scalarLabels = scalar.labelsForTwoScalars()

        val lowerScalar = uniqueVariable("lower${targetColor.name}",
            Scalar.new(
                range.a.min.v,
                range.b.min.v,
                range.c.min.v,
                range.d.min.v,
            )
        )

        val upperScalar = uniqueVariable("upper${targetColor.name}",
            Scalar.new(
                range.a.max.v,
                range.b.max.v,
                range.c.max.v,
                range.d.max.v,
            )
        )

        group {
            // lower color scalar
            public(lowerScalar, scalarLabels.first)

            // upper color scalar
            public(upperScalar, scalarLabels.second)

            if (needsCvt) {
                private(cvtMat)
            }
            // output mat target
            private(thresholdTargetMat)
        }

        current.scope {
            if(needsCvt) {
                Imgproc("cvtColor", inputMat.value, cvtMat, cvtColorValue(matColor, targetColor))
                inputMat = GenValue.Mat(cvtMat, targetColor)
            }

            Core("inRange", inputMat.value, lowerScalar, upperScalar, thresholdTargetMat)
            output.streamIfEnabled(thresholdTargetMat, ColorSpace.GRAY)
        }

        session.outputMat = GenValue.Mat(thresholdTargetMat, targetColor, true)

        session
    }

    override fun getOutputValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        genCodeIfNecessary(current)

        if(attrib == output) {
            return lastGenSession!!.outputMat
        }

        noValue(attrib)
    }

    class Session : CodeGenSession {
        lateinit var outputMat: GenValue.Mat
    }

}