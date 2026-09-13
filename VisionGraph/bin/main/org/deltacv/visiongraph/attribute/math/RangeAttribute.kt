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
import org.deltacv.visiongraph.gui.util.ImGuiEx
import org.deltacv.visiongraph.id.Misc
import org.deltacv.visiongraph.serialization.v1.data.SerializeData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.boolOrNull
import org.deltacv.visiongraph.serialization.v2.intOrNull
import org.deltacv.visiongraph.util.event.PaperEventHandler

@CodecType(instantiable = false)
class RangeAttribute(
    override val mode: AttributeMode,
    override var attributeName: String? = null,
    minDefault: Int = 0,
    maxDefault: Int = 255,
    val valueMutator: (Int) -> Double = { it.toDouble() }
) : TypedAttribute<GenValue.Range>(Companion) {
    companion object : AttributeType<RangeAttribute> {
        override val icon = FontAwesomeIcons.TextWidth
        override fun new(mode: AttributeMode, variableName: String) = RangeAttribute(mode, variableName)
    }

    private var usesToggleChanged = false
    val onToggleChange by lazy {
        usesToggleChanged = true
        PaperEventHandler("RangeAttribute-OnToggleChange")
    }

    var min = minDefault
        set(value) {
            field = value
            if(minValue.get() < value) {
                minValue.set(value)
            }
            if(maxValue.get() < value) {
                maxValue.set(value)
            }
        }
    var max = maxDefault
        set(value) {
            field = value
            if(maxValue.get() > value) {
                maxValue.set(value)
            }
            if(minValue.get() > value) {
                minValue.set(value)
            }
        }

    var useToggle = false
    var useSliders = true

    @SerializeData
    val toggleValue = ImBoolean(true)

    @SerializeData
    val minValue = ImInt(min)
    @SerializeData
    val maxValue = ImInt(max)

    private var prevToggle: Boolean? = null
    private var prevMin: Int? = null
    private var prevMax: Int? = null

    private val toggleId by Misc.newId()
    private val minId by Misc.newId()
    private val maxId by Misc.newId()

    override fun drawAttribute() {
        super.drawAttribute()

        if(useToggle && !toggleValue.get()) return

        if(!hasLink) {
            inlineIfNeeded()

            if(useSliders) {
                ImGuiEx.rangeSliders(
                    min, max,
                    minValue, maxValue,
                    minId, maxId,
                    width = 95f
                )
            } else {
                ImGuiEx.rangeTextInputs(
                    min, max,
                    minValue, maxValue,
                    minId, maxId,
                    width = 95f
                )
            }

            val mn = minValue.get()
            val mx = maxValue.get()

            if(mn != prevMin || mx != prevMax) {
                emitChange(ChangeType.ValueChange)
            }

            prevMin = mn
            prevMax = mx
        }
    }

    override fun drawAfterText() {
        if(mode == AttributeMode.INPUT && !hasLink && useToggle) {
            ImGui.sameLine()

            ImGui.checkbox("###$toggleId", toggleValue)

            if(prevToggle == null || prevToggle != toggleValue.get()) {
                emitChange(ChangeType.ValueChange)

                if(usesToggleChanged) {
                    onToggleChange.run()
                }
            }
            prevToggle = toggleValue.get()
        }
    }

    override fun readEditorValue() = EditorValue.Range(
        valueMutator(minValue.get()),
        valueMutator(maxValue.get())
    )

    override fun readTunerValue() = TunerValue.ListValue(listOf(
        TunerValue.DoubleValue(valueMutator(minValue.get())),
        TunerValue.DoubleValue(valueMutator(maxValue.get()))
    ))

    override fun genValue(current: CodeGen.Current) = readGenValue(
        current, GenValue.Range(
            GenValue.Double.Actual(valueMutator(minValue.get()).resolved()),
            GenValue.Double.Actual(valueMutator(maxValue.get()).resolved())
        )
    )

    // ------------------ Serialization v2 ------------------

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)

        encoder.int("min", minValue.get())
        encoder.int("max", maxValue.get())
        encoder.bool("toggle", toggleValue.get())
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)

        minValue.set(decoder.intOrNull("min") ?: 0)
        maxValue.set(decoder.intOrNull("max") ?: 0)
        toggleValue.set(decoder.boolOrNull("toggle") ?: false)
    }
}

fun RangeAttribute.rebuildOnToggleChange() = apply {
    onToggleChange {
        if(idContainer[id] != null) {
            rebuildPreviz()
        }
    }
}



