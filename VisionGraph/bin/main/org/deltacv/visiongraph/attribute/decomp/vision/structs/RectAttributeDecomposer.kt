package org.deltacv.visiongraph.attribute.decomp.vision.structs

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.decomp.AttributeDecomposer
import org.deltacv.visiongraph.attribute.vision.structs.Vector2Attribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@CodecType
class RectAttributeDecomposer : AttributeDecomposer<RectAttributeDecomposer.Session>() {

    val position = Vector2Attribute(OUTPUT, "$[att_position]")
    val size = Vector2Attribute(OUTPUT, "$[att_size]", useSizeNaming = true)

    override fun onEnable() {
        + position
        + size
    }

    override val generators = polyglot<GenValue, Session> {
        generatorForAny {
            assertGenValueType<GenValue.Rect>(genInput)

            val session = Session()

            current.scope {
                when(genInput) {
                    is GenValue.Rect.Components -> {
                        session.position = genInput.position.toRuntime(current)
                        session.size = genInput.size.toRuntime(current)
                    }
                    is GenValue.Rect.Inst -> {
                        val rect = genInput.value.v

                        val (xValue, yValue, widthValue, heightValue) = when(language) {
                            is JavaLanguage -> {
                                listOf(
                                    double(rect.propertyValue("x", IntType)),
                                    double(rect.propertyValue("y", IntType)),
                                    double(rect.propertyValue("width", IntType)),
                                    double(rect.propertyValue("height", IntType)),
                                )
                            }
                            is CPythonLanguage -> {
                                listOf(
                                    // Python OpenCV's Rect is a tuple of (x, y, w, h)
                                    rect[0.v, DoubleType],
                                    rect[1.v, DoubleType],
                                    rect[2.v, DoubleType],
                                    rect[3.v, DoubleType],
                                )
                            }
                            else -> throwLanguageNotSupported()
                        }

                        val x = GenValue.Int.Runtime(xValue.resolved())
                        val y = GenValue.Int.Runtime(yValue.resolved())
                        val position = GenValue.Vec2.Runtime(x, y)

                        val width = GenValue.Int.Runtime(widthValue.resolved())
                        val height = GenValue.Int.Runtime(heightValue.resolved())
                        val size = GenValue.Vec2.Runtime(width, height)

                        session.position = position
                        session.size = size
                    }
                }
            }

            session
        }
    }

    override fun getGenValueOf(
        current: CodeGen.Current,
        attrib: Attribute
    ) = when(attrib) {
        position -> GenValue.Vec2.Runtime.defer { current.sessionOf(this)?.position }
        size -> GenValue.Vec2.Runtime.defer { current.sessionOf(this)?.size }
        else -> noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        encoder.obj("position", position)
        encoder.obj("size", size)
    }

    override fun decode(decoder: DataDecoder) {
        decoder.obj("position", position)
        decoder.obj("size", size)
    }

    class Session : CodeGenSession {
        lateinit var position: GenValue.Vec2.Runtime
        lateinit var size: GenValue.Vec2.Runtime
    }
}



