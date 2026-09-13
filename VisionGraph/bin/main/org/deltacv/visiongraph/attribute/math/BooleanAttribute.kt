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

package org.deltacv.visiongraph.attribute.math

import imgui.ImGui
import imgui.type.ImBoolean
import org.deltacv.visiongraph.attribute.AttributeMode
import org.deltacv.visiongraph.attribute.AttributeType
import org.deltacv.visiongraph.attribute.EditorValue
import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.engine.client.message.TunerValue
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.serialization.v1.data.SerializeData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@CodecType(instantiable = false)
class BooleanAttribute(
    override val mode: AttributeMode,
    override var attributeName: String? = null
) : TypedAttribute<GenValue.Boolean>(
    Companion,
    doEditorChangeChecking = true // takes advantage of readEditorValue for change checking, so we don't have to do it manually
) {

    companion object: AttributeType<BooleanAttribute> {
        override val icon = FontAwesomeIcons.ToggleOn

        override fun new(mode: AttributeMode, variableName: String) = BooleanAttribute(mode, variableName)
    }

    @SerializeData
    val value = ImBoolean()

    override fun drawAttribute() {
        super.drawAttribute()

        if(!hasLink && mode == AttributeMode.INPUT) {
            ImGui.checkbox("", value)
        }
    }

    override fun readEditorValue() = EditorValue.Boolean(value.get())

    override fun readTunerValue() = TunerValue.BooleanValue(value.get())

    override fun genValue(current: CodeGen.Current): GenValue.Boolean {
        if(isInput) {
            if(hasLink) {
                val linkedAttrib = availableLinkedAttribute

                raiseAssert(
                    linkedAttrib != null,
                    "Boolean attribute must have another attribute attached"
                )

                val value = linkedAttrib.genValue(current)
                raiseAssert(value is GenValue.Boolean, "Attribute attached is not a Boolean")

                return value
            } else {
                return if (value.get()) {
                    GenValue.Boolean.TRUE
                } else GenValue.Boolean.FALSE
            }
        } else {
            val value = getGenValueFromNode(current)
            raiseAssert(value is GenValue.Boolean, "Value returned from the node is not a Boolean")

            return value
        }
    }

    // ------------------ Serialization v2 ------------------
    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.bool("value", value.get())
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        value.set(decoder.bool("value"))
    }
}



