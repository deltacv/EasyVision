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
import imgui.type.ImDouble
import imgui.type.ImFloat
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
import org.deltacv.visiongraph.id.Misc
import org.deltacv.visiongraph.serialization.v1.AttributeSerializationData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.doubleOrNull
import org.deltacv.visiongraph.serialization.v2.intOrNull
import org.deltacv.visiongraph.util.Range2d

@CodecType(instantiable = false)
class DoubleAttribute(
    override val mode: AttributeMode,
    override var attributeName: String? = null,
    initialValue: Double = 0.0
) : TypedAttribute<GenValue.Double>(
    Companion,
    doEditorChangeChecking = true // takes advantage of readEditorValue for change checking, so we don't have to do it manually
) {

    companion object : AttributeType<DoubleAttribute> {
        override val icon = FontAwesomeIcons.SquareRootAlt
        override fun new(mode: AttributeMode, variableName: String) = DoubleAttribute(mode, variableName)
    }

    val value = ImDouble(initialValue)
    private val sliderValue = ImFloat(initialValue.toFloat())

    private val sliderId by Misc.newId()

    private var nextValue: Double? = null

    var disableInput = false
        set(value) {
            showAttributesCircles = !value
            field = value
        }

    private var range = Range2d.DEFAULT_POSITIVE
    var isSlider = false
        private set

    var roundValues = false

    override fun drawAttribute() {
        super.drawAttribute()

        if (!hasLink && mode == AttributeMode.INPUT) {
            inlineIfNeeded()
            ImGui.pushItemWidth(110.0f)

            if (!isSlider || disableInput) {
                value.set(range.clip(value.get()))

                ImGui.inputDouble(
                    "",
                    value,
                    0.0,
                    0.0,
                    "%.6f",
                    if (disableInput) ImGuiInputTextFlags.ReadOnly else 0
                )
            } else {
                if(roundValues) {
                    val sliderValueInt = ImInt(value.get().toInt())

                    ImGui.sliderInt(
                        "###$sliderId",
                        sliderValueInt.data,
                        range.min.toInt(),
                        range.max.toInt()
                    )

                    sliderValue.set(sliderValueInt.get().toFloat())
                } else {
                    ImGui.sliderFloat(
                        "###$sliderId",
                        sliderValue.data,
                        range.min.toFloat(),
                        range.max.toFloat()
                    )
                }

                value.set(sliderValue.get().toDouble())
            }

            ImGui.popItemWidth()

            if (nextValue != null) {
                value.set(nextValue!!)
                sliderValue.set(nextValue!!.toFloat())

                nextValue = null
            }
        }
    }

    fun sliderMode(range: Range2d) {
        this.range = range
        isSlider = true
    }

    fun fieldMode(range: Range2d = Range2d.DEFAULT_POSITIVE) {
        this.range = range
        isSlider = false
    }

    override fun readEditorValue() = EditorValue.Double(value.get())

    override fun readTunerValue() = TunerValue.DoubleValue(value.get())

    override fun genValue(current: CodeGen.Current) = readGenValue<GenValue.Double>(
        current, GenValue.Double.Actual(value.get().resolved())
    )

    // ------------------ Serialization v1 ------------------

    override fun makeSerializationData() = Data(value.get())

    override fun takeSerializationData(data: AttributeSerializationData) {
        if (data is Data) {
            nextValue = data.value
        }
    }

    data class Data(var value: Double = 0.0) : AttributeSerializationData()

    // ------------------ Serialization v2 ------------------

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.double("value", value.get())
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        nextValue = decoder.doubleOrNull("value") ?: decoder.intOrNull("value")?.toDouble() ?: 0.0
    }
}