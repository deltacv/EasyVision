package org.deltacv.visiongraph.node.transform

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.vision.structs.RectAttribute
import org.deltacv.visiongraph.attribute.vision.structs.Vector2Attribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.GenPreviz
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.objOrSkip

@PaperNode(
    name = "nod_composerect",
    category = NodeCategory.TRANSFORM,
    description = "des_composerect"
)
@CodecType
class ComposeRectNode : DrawNode<ComposeRectNode.Session>() {

    val positionAtt = Vector2Attribute(INPUT, "$[att_position]")
    val sizeAtt = Vector2Attribute(INPUT, "$[att_size]", useSizeNaming = true)

    val output = RectAttribute(OUTPUT, "$[att_rect]")

    override fun onEnable() {
        + positionAtt
        + sizeAtt
        + output
    }

    override val generators = polyglot {
        generatorForAny {
            val session = Session()

            var positionValue = GenPreviz.toPrevizVec2(positionAtt.genValue(current), positionAtt, current, prefix = "rect")
            var sizeValue = GenPreviz.toPrevizVec2(sizeAtt.genValue(current), sizeAtt, current, prefix = "rectSize")

            session.rect = GenValue.Rect.Components.wrap(
                positionValue.x, positionValue.y,
                sizeValue.x, sizeValue.y,
                current
            )

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute) = when (attrib) {
        output -> current.nonNullSessionOf(this).rect // cannot defer rect
        else -> noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("position", positionAtt)
        encoder.obj("size", sizeAtt)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.objOrSkip("position", positionAtt)
        decoder.objOrSkip("size", sizeAtt)
        decoder.objOrSkip("output", output)
    }

    class Session : CodeGenSession {
        lateinit var rect: GenValue.Rect.Components
    }
}



