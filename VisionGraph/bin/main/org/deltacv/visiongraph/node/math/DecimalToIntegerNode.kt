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
import org.deltacv.visiongraph.attribute.math.DoubleAttribute
import org.deltacv.visiongraph.attribute.math.IntAttribute
import org.deltacv.visiongraph.attribute.misc.EnumAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.GenPreviz
import org.deltacv.visiongraph.codegen.build.language.cpython.CPythonTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.BaseLanguage
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

enum class RoundingBehavior(val icon: String) {
    ROUND("mis_round"),
    FLOOR("mis_floor"),
    CEIL("mis_ceil")
}

@PaperNode(
    name = "nod_decimalto_integer",
    category = NodeCategory.MATH,
    description = "des_decimalto_integer"
)
@CodecType
class DecimalToIntegerNode : DrawNode<DecimalToIntegerNode.Session>(){

    val input = DoubleAttribute(INPUT, "$[att_input]")
    val roundingBehavior = EnumAttribute(INPUT, "$[att_roundingbehavior]", RoundingBehavior.entries) { it.icon }
    val output = IntAttribute(OUTPUT, "$[att_output]")

    override fun onEnable() {
        + input
        + roundingBehavior.rebuildOnChange()
        + output
    }

    override val generators = polyglot {
        generatorFor<BaseLanguage> {
            val session = Session()

            val inputValue = input.genValue(current)
            val roundingBehaviorValue = roundingBehavior.genValue(current)

            current {
                var inputV = GenPreviz.toPrevizDouble(
                    inputValue, input, current, variableName = "decimalToIntegerTarget"
                ).v

                val result = when(roundingBehaviorValue.value) {
                    RoundingBehavior.ROUND -> JavaTypes.Math.callValue("round", LongType, inputV)
                    RoundingBehavior.FLOOR -> JavaTypes.Math.callValue("floor", DoubleType, inputV).castTo(IntType)
                    RoundingBehavior.CEIL -> JavaTypes.Math.callValue("ceil", DoubleType, inputV).castTo(IntType)
                }

                session.output = GenValue.Int.Runtime(result.resolved())
            }

            session
        }

        generatorFor(CPythonLanguage) {
            val session = Session()

            val inputValue = input.genValue(current)
            val roundingBehaviorValue = roundingBehavior.genValue(current)

            current {
                val inputV = inputValue.v

                val result = when(roundingBehaviorValue.value) {
                    RoundingBehavior.ROUND -> "round".callValue(CPythonLanguage.NoType, inputV)
                    RoundingBehavior.FLOOR -> int(CPythonTypes.math.callValue("floor", CPythonLanguage.NoType, inputV))
                    RoundingBehavior.CEIL -> int(CPythonTypes.math.callValue("ceil", CPythonLanguage.NoType, inputV))
                }

                session.output = GenValue.Int.Runtime(result.resolved())
            }

            session
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute) = when(attrib) {
        output -> GenValue.Int.Runtime.defer { current.sessionOf(this)?.output }
        else -> noValue(attrib)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)
        encoder.obj("roundingBehavior", roundingBehavior)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("input", input)
        decoder.obj("roundingBehavior", roundingBehavior)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var output: GenValue.Int.Runtime
    }
}



