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
import org.deltacv.visiongraph.attribute.misc.EnumAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.rebuildOnLink
import org.deltacv.visiongraph.attribute.vision.structs.Vector2Attribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.GenPreviz
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@PaperNode(
    name = "nod_vector2math",
    category = NodeCategory.MATH,
    description = "des_vector2math"
)
@CodecType
class Vector2MathNode : DrawNode<Vector2MathNode.Session>() {

    val first = Vector2Attribute(INPUT, "$[att_first]")
    val operation = EnumAttribute(INPUT, "$[att_operation]", Operation.entries, Font.find("font-awesome")) { it.icon }
    val second = Vector2Attribute(INPUT, "$[att_second]")

    val result = Vector2Attribute(OUTPUT, "$[att_result]")

    override fun onEnable() {
        + first
        + operation.rebuildOnChange()
        + second

        + result.rebuildOnLink()
    }

    override val generators = polyglot {
        generatorForAny {
            val session = Session()

            val firstValue = GenPreviz.toPrevizVec2(first.genValue(current), first, current, prefix = "first")
            val operationValue = operation.genValue(current)
            val secondValue = GenPreviz.toPrevizVec2(second.genValue(current), second, current, prefix = "second")

            current {
                fun operate(first: GenValue.Int, second: GenValue.Int) = when(operationValue.value) {
                    Operation.PLUS -> first.v + second.v
                    Operation.MINUS -> first.v - second.v
                    Operation.MULTIPLY -> first.v * second.v
                    Operation.DIVIDE -> first.v / second.v
                }

                val xResultValue = operate(firstValue.x, secondValue.x)
                val yResultValue = operate(firstValue.y, secondValue.y)

                session.result = GenValue.Vec2.Runtime(
                    GenValue.Int.Runtime(xResultValue.resolved()),
                    GenValue.Int.Runtime(yResultValue.resolved())
                )
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute) = when (attrib) {
        result -> GenValue.Vec2.Runtime.defer { current.sessionOf(this)?.result }
        else -> noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("first", first)
        encoder.obj("operation", operation)
        encoder.obj("second", second)
        encoder.obj("result", result)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("first", first)
        decoder.obj("operation", operation)
        decoder.obj("second", second)
        decoder.obj("result", result)
    }

    class Session : CodeGenSession {
        lateinit var result: GenValue.Vec2.Runtime
    }
}



