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

package org.deltacv.visiongraph.attribute.vision.structs

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.AttributeMode
import org.deltacv.visiongraph.attribute.EditorValue
import org.deltacv.visiongraph.attribute.math.RangeAttribute
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.engine.client.message.TunerValue
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.node.vision.ColorSpace
import org.deltacv.visiongraph.serialization.v2.CodecType

@CodecType(instantiable = false)
class ScalarRangeAttribute(
    mode: AttributeMode,
    color: ColorSpace,
    variableName: String? = null
) : ListAttribute<RangeAttribute, GenValue.Range>(mode, variableName, RangeAttribute, color.channels) {

    var color = color
        set(value) {
            fixedLength = value.channels
            field = value
            updateElementNames()
        }

    private val monoFont by Font.findLazy("jetbrains-mono")

    override var icon = FontAwesomeIcons.GripVertical

    private fun updateElementNames() {
        for ((i, element) in listAttributes.withIndex()) {
            if (i < color.channelNames.size) {
                element.attributeName = color.channelNames[i]
                element.configureLayout(showLabel = true, isInline = true, showType = false, labelFont = monoFont)
            }
        }
    }

    override fun onElementCreation(element: Attribute) {
        updateElementNames()
    }

    fun bindColorSpace(other: MatAttribute) = apply {
        color = (other.editorValue as? EditorValue.Image)?.colorSpace ?: ColorSpace.GENERIC
        other.onChange {
            color = (other.editorValue as? EditorValue.Image)?.colorSpace ?: ColorSpace.GENERIC
        }
    }

    override fun genValue(current: CodeGen.Current): GenValue.ScalarRange {
        val values = super.genValue(current).toActualOrNull()!!.elements

        val range = GenValue.ScalarRange(
            values.getOrElse(0) { GenValue.Range.ZERO },
            values.getOrElse(1) { GenValue.Range.ZERO },
            values.getOrElse(2) { GenValue.Range.ZERO },
            values.getOrElse(3) { GenValue.Range.ZERO }
        )

        return readGenValue(current, range)
    }

    private var twoScalarsCached: Pair<String, String>? = null

    fun minMaxTunerLabels(): Pair<String, String> {
        if(twoScalarsCached != null) return twoScalarsCached!!

        val hexMin = "${id}_min"
        val hexMax = "${id}_max"

        onChange {
            val list = editorValue?.let {
                it as? EditorValue.List
            } ?: return@onChange

            val minValues = mutableListOf(0.0, 0.0, 0.0, 0.0)
            val maxValues = mutableListOf(0.0, 0.0, 0.0, 0.0)

            for((i, value) in list.values.withIndex()) {
                val value = value as? EditorValue.Range ?: continue

                minValues[i] = value.min
                maxValues[i] = value.max
            }

            broadcastTunerMessageFor(hexMin, TunerValue.ListValue(minValues.map { TunerValue.DoubleValue(it) }))
            broadcastTunerMessageFor(hexMax, TunerValue.ListValue(maxValues.map { TunerValue.DoubleValue(it) }))
        }

        twoScalarsCached = Pair(hexMin, hexMax)

        return twoScalarsCached!!
    }
}



