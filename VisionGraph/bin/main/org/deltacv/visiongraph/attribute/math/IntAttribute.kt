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
import imgui.flag.ImGuiInputTextFlags
import imgui.type.ImInt
import org.deltacv.visiongraph.attribute.AttributeMode
import org.deltacv.visiongraph.attribute.AttributeType
import org.deltacv.visiongraph.attribute.EditorValue
import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.engine.client.message.TunerValue
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.serialization.v1.AttributeSerializationData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.doubleOrNull
import org.deltacv.visiongraph.serialization.v2.intOrNull
import org.deltacv.visiongraph.util.Range2i

@CodecType(instantiable = false)
class IntAttribute(
    override val mode: AttributeMode,
    override var attributeName: String? = null,
    initialValue: Int = 0
) : TypedAttribute<GenValue.Int>(
    Companion,
    doEditorChangeChecking = true // takes advantage of readEditorValue for change checking, so we don't have to do it manually
) {

    companion object: AttributeType<IntAttribute> {
        override val icon = FontAwesomeIcons.Hashtag
        override fun new(mode: AttributeMode, variableName: String) = IntAttribute(mode, variableName)
    }

    val value = ImInt(initialValue)
    private val sliderValue = ImInt(initialValue)
    private var nextValue: Int? = null

    var disableInput = false
        set(value) {
            showAttributesCircles = !value
            field = value
        }

    private var range: Range2i = Range2i.DEFAULT_POSITIVE
    var isSlider = false
        private set

    override fun drawAttribute() {
        super.drawAttribute()

        if(!hasLink && mode == AttributeMode.INPUT) {
            inlineIfNeeded()

            ImGui.pushItemWidth(110.0f)

            if(!isSlider || disableInput) {
                value.set(range.clip(value.get()))

                ImGui.inputInt("", value, 1, 100, if(disableInput) ImGuiInputTextFlags.ReadOnly else 0)
            } else {
                ImGui.sliderInt("", sliderValue.data, range.min, range.max)
                value.set(sliderValue.get())
            }

            ImGui.popItemWidth()

            if(nextValue != null) {
                value.set(nextValue!!)
                sliderValue.set(nextValue!!)
                nextValue = null
            }
        }
    }

    fun sliderMode(range: Range2i) {
        this.range = range
        isSlider = true
    }

    fun fieldMode(range: Range2i = Range2i.DEFAULT_POSITIVE) {
        this.range = range
        isSlider = false
    }

    override fun readEditorValue() = EditorValue.Int(value.get())
    override fun readTunerValue() = TunerValue.IntValue(value.get())

    override fun genValue(current: CodeGen.Current) = readGenValue<GenValue.Int>(
        current, GenValue.Int.Actual(value.get().resolved())
    )

    // ------------------ Serialization v1 ------------------

    override fun makeSerializationData() = Data(value.get())

    override fun takeSerializationData(data: AttributeSerializationData) {
        if(data is Data) {
            nextValue = data.value
        }
    }

    data class Data(var value: Int = 0) : AttributeSerializationData()


    // ------------------ Serialization v2 ------------------

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.int("value", value.get())
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        nextValue = decoder.intOrNull("value") ?: decoder.doubleOrNull("value")?.toInt() ?: 0
    }

}



