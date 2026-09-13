package org.deltacv.visiongraph.attribute.decomp.vision

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.decomp.AttributeDecomposer
import org.deltacv.visiongraph.attribute.math.IntAttribute
import org.deltacv.visiongraph.attribute.vision.structs.Vector2Attribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.vision.ColorSpace
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.objOrSkip

@CodecType
class MatAttributeDecomposer : AttributeDecomposer<MatAttributeDecomposer.Session>() {

    val size = Vector2Attribute(OUTPUT, "$[att_size]", useSizeNaming = true)
    val channels = IntAttribute(OUTPUT, "$[att_channels]")

    override fun onEnable() {
        + size
        + channels
    }

    override val generators = polyglot<GenValue, Session> {
        generatorFor(JavaLanguage) {
            assertGenValueType<GenValue.Mat>(genInput)

            val session = Session()

            current.scope {
                session.size = GenValue.Vec2.Runtime(
                    GenValue.Int.Runtime(genInput.value.v.callValue("cols", IntType).resolved()),
                    GenValue.Int.Runtime(genInput.value.v.callValue("rows", IntType).resolved()),
                )

                session.channels = GenValue.Int.Runtime(genInput.value.v.callValue("channels", IntType).resolved())
            }

            session
        }

        generatorFor(CPythonLanguage) {
            assertGenValueType<GenValue.Mat>(genInput)

            val session = Session()

            current.scope {
                val shape = genInput.value.v.propertyValue("shape", CPythonLanguage.NoType)

                session.size = GenValue.Vec2.Runtime(
                    GenValue.Int.Runtime(shape[1.v, IntType].resolved()),
                    GenValue.Int.Runtime(shape[0.v, IntType].resolved()),
                )

                val channels = genInput.color.map {
                    if(it == ColorSpace.GRAY) {
                        // Grayscale images have a single channel, but np represents them with
                        // a single value in the shape (height, width) without a channels dimension
                        1.v
                    } else shape[2.v, IntType]
                }

                session.channels = GenValue.Int.Runtime(channels)
            }

            session
        }
    }

    override fun getGenValueOf(
        current: CodeGen.Current,
        attrib: Attribute
    ) = when(attrib) {
        size -> GenValue.Vec2.Runtime.defer { current.sessionOf(this)?.size }
        channels -> GenValue.Int.Runtime.defer { current.sessionOf(this)?.channels }
        else -> noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        encoder.obj("size", size)
        encoder.obj("channels", channels)
    }

    override fun decode(decoder: DataDecoder) {
        decoder.objOrSkip("size", size)
        decoder.objOrSkip("channels", channels)
    }

    class Session : CodeGenSession {
        lateinit var size: GenValue.Vec2.Runtime
        lateinit var channels: GenValue.Int.Runtime
    }
}



