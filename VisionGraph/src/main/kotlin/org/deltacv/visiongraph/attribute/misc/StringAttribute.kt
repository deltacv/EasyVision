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

package org.deltacv.visiongraph.attribute.misc

import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.type.ImString
import org.deltacv.visiongraph.attribute.AttributeMode
import org.deltacv.visiongraph.attribute.AttributeType
import org.deltacv.visiongraph.attribute.EditorValue
import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.serialization.v1.AttributeSerializationData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@CodecType(instantiable = false)
class StringAttribute(
    override val mode: AttributeMode,
    override var attributeName: String? = null
) : TypedAttribute<GenValue.String>(
    Companion,
    doEditorChangeChecking = true // takes advantage of readEditorValue for change checking, so we don't have to do it manually
) {

    companion object: AttributeType<StringAttribute> {
        override val icon = FontAwesomeIcons.EnvelopeOpenText

        override fun new(mode: AttributeMode, variableName: String) = StringAttribute(mode, variableName)
    }

    val value = ImString()

    private var nextValue: String? = null

    var disableInput = false
        set(value) {
            showAttributesCircles = !value
            field = value
        }

    override fun drawAttribute() {
        super.drawAttribute()

        if(!hasLink && mode == AttributeMode.INPUT) {
            inlineIfNeeded()

            ImGui.pushItemWidth(110.0f)

            ImGui.inputText("", value, if(disableInput) ImGuiInputTextFlags.ReadOnly else 0)

            ImGui.popItemWidth()

            if(nextValue != null) {
                value.set(nextValue!!)
                nextValue = null
            }
        }
    }

    override fun readEditorValue() = EditorValue.String(value.get())

    override fun genValue(current: CodeGen.Current) = readGenValue(
        current, GenValue.String(value.get().resolved())
    )

    // ------------------ Serialization v1 ------------------

    override fun makeSerializationData() = Data(value.get())

    override fun takeSerializationData(data: AttributeSerializationData) {
        if(data is Data) {
            nextValue = data.value
        }
    }

    data class Data(var value: String = "") : AttributeSerializationData()

    // ------------------ Serialization v2 ------------------

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)

        encoder.string("value", value.get())
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)

        nextValue = decoder.string("value")
    }

}



