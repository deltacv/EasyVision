package org.deltacv.visiongraph.attribute.decomp.vision.structs

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.decomp.AttributeDecomposer
import org.deltacv.visiongraph.attribute.math.IntAttribute
import org.deltacv.visiongraph.attribute.vision.structs.Vector2Attribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.objOrSkip

@CodecType
class Vector2AttributeDecomposer : AttributeDecomposer<Vector2AttributeDecomposer.Session>() {

    val x = IntAttribute(OUTPUT, "X")
    val y = IntAttribute(OUTPUT, "Y")

    override fun onEnable() {
        + x
        + y

        if (linkedAttribute is Vector2Attribute && (linkedAttribute as Vector2Attribute).useSizeNaming) {
            x.attributeName = "$[att_width]"
            y.attributeName = "$[att_height]"
        }
    }

    override val generators = polyglot<GenValue, Session> {
        generatorForAny {
            assertGenValueType<GenValue.Vec2>(genInput)

            val session = Session()

            session.x = genInput.x.toRuntime(current)
            session.y = genInput.y.toRuntime(current)

            session
        }
    }

    override fun getGenValueOf(
        current: CodeGen.Current,
        attrib: Attribute
    ) = when (attrib) {
        x -> GenValue.Int.Runtime.defer { current.sessionOf(this)?.x }
        y -> GenValue.Int.Runtime.defer { current.sessionOf(this)?.y }
        else -> noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        encoder.obj("x", x)
        encoder.obj("y", y)
    }

    override fun decode(decoder: DataDecoder) {
        decoder.objOrSkip("x", x)
        decoder.objOrSkip("y", y)
    }

    class Session : CodeGenSession {
        lateinit var x: GenValue.Int.Runtime
        lateinit var y: GenValue.Int.Runtime
    }
}