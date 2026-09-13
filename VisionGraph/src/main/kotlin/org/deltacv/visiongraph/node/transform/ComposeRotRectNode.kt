package org.deltacv.visiongraph.node.transform

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.math.DoubleAttribute
import org.deltacv.visiongraph.attribute.vision.structs.RotatedRectAttribute
import org.deltacv.visiongraph.attribute.vision.structs.Vector2Attribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.GenPreviz
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.objOrSkip

@PaperNode(
    name = "nod_composerot_rect",
    category = NodeCategory.TRANSFORM,
    description = "des_composerot_rect"
)
@CodecType
class ComposeRotRectNode : DrawNode<ComposeRotRectNode.Session>() {

    val positionAtt = Vector2Attribute(INPUT, "$[att_position]")
    val sizeAtt = Vector2Attribute(INPUT, "$[att_size]", useSizeNaming = true)
    val angleAtt = DoubleAttribute(INPUT, "$[att_angle]")

    val output = RotatedRectAttribute(OUTPUT, "$[att_rotrect]")

    override fun onEnable() {
        + positionAtt
        + sizeAtt
        + angleAtt
        + output
    }

    override val generators = polyglot {
        generatorForAny {
            current {
                val session = Session()

                val positionValue = GenPreviz.toPrevizVec2(positionAtt.genValue(current), positionAtt, current, prefix = "rotRect")
                val sizeValue = GenPreviz.toPrevizVec2(sizeAtt.genValue(current), sizeAtt, current, prefix = "rotRectSize")
                val angleValue = GenPreviz.toPrevizDouble(angleAtt.genValue(current), angleAtt, current, variableName = "rotRectAngle")

                session.rotRect = GenValue.RotatedRect.Components(
                    positionValue.x.toDouble(current), positionValue.y.toDouble(current),
                    sizeValue.x.toDouble(current), sizeValue.y.toDouble(current),
                    angleValue
                )

                session
            }
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute) = when(attrib) {
        output -> current.nonNullSessionOf(this).rotRect
        else -> noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("position", positionAtt)
        encoder.obj("size", sizeAtt)
        encoder.obj("angle", angleAtt)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.objOrSkip("position", positionAtt)
        decoder.objOrSkip("size", sizeAtt)
        decoder.objOrSkip("angle", angleAtt)
        decoder.objOrSkip("output", output)
    }

    class Session : CodeGenSession {
        lateinit var rotRect: GenValue.RotatedRect.Components
    }
}