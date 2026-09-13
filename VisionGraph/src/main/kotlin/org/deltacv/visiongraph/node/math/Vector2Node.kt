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

package org.deltacv.visiongraph.node.math

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.math.IntAttribute
import org.deltacv.visiongraph.attribute.vision.structs.Vector2Attribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.GenPreviz
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.BaseLanguage
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.objOrSkip

@PaperNode(
    name = "nod_vector2",
    category = NodeCategory.TRANSFORM,
    description = "des_vector2"
)
@CodecType
class Vector2Node(useSizeNaming: Boolean = false) : DrawNode<Vector2Node.Session>() {

    val xAttribute = IntAttribute(INPUT, if(useSizeNaming) "att_width" else "X")
    val yAttribute = IntAttribute(INPUT, if(useSizeNaming) "att_height" else "Y")

    val result = Vector2Attribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + xAttribute
        + yAttribute

        + result
    }

    override val generators = polyglot {
        generatorFor<BaseLanguage> {
            val session = Session()

            current {
                if(codeGen.isForPreviz) {
                    val xValue = xAttribute.genValue(current)
                    val yValue = yAttribute.genValue(current)

                    session.vector2 = GenPreviz.toPrevizVec2(
                        GenValue.Vec2.wrap(xValue, yValue, current),
                        xAttribute.tunerLabel(), yAttribute.tunerLabel(),
                        current
                    )
                } else {
                    session.vector2 = GenValue.Vec2.wrap(
                        xAttribute.genValue(current),
                        yAttribute.genValue(current),
                        current
                    )
                }
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            current.scope {
                session.vector2 = GenValue.Vec2.wrap(
                    xAttribute.genValue(current),
                    yAttribute.genValue(current),
                    current
                )
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if(attrib == result) {
            return current.nonNullSessionOf(this).vector2
        }

        noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("xAttribute", xAttribute)
        encoder.obj("yAttribute", yAttribute)
        encoder.obj("result", result)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.objOrSkip("xAttribute", xAttribute)
        decoder.objOrSkip("yAttribute", yAttribute)
        decoder.objOrSkip("result", result)
    }

    class Session : CodeGenSession {
        lateinit var vector2: GenValue.Vec2
    }

}



