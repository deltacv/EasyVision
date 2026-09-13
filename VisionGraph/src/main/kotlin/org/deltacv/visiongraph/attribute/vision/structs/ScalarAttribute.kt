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
import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.attribute.math.DoubleAttribute
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.vision.MatAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.node.vision.ColorSpace
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.util.Range2d

@CodecType(instantiable = false)
class ScalarAttribute(
    mode: AttributeMode,
    color: ColorSpace,
    variableName: String? = null
) : ListAttribute<DoubleAttribute, GenValue.Double>(mode, variableName, DoubleAttribute, color.channels) {

    var colorSpace = color
        set(value) {
            fixedLength = value.channels
            field = value
            updateElementNames()
        }

    override var icon = FontAwesomeIcons.GripHorizontal

    private val monoFont by Font.findLazy("jetbrains-mono")

    private fun updateElementNames() {
        for ((i, element) in listAttributes.withIndex()) {
            if (i < colorSpace.channelNames.size) {
                element.attributeName = colorSpace.channelNames[i]
                element.configureLayout(showLabel = true, isInline = true, showType = false, labelFont = monoFont)
            }
        }
    }

    override fun onElementCreation(element: Attribute) {
        updateElementNames()

        if(element is DoubleAttribute) {
            element.roundValues = true
            element.sliderMode(Range2d(0.0, 255.0))
        }
    }

    fun bindColorSpace(other: MatAttribute) = apply {
        colorSpace = (other.editorValue as? EditorValue.Image)?.colorSpace ?: ColorSpace.GENERIC
        other.onChange {
            colorSpace = (other.editorValue as? EditorValue.Image)?.colorSpace ?: ColorSpace.GENERIC
        }
    }

    override fun genValue(current: CodeGen.Current): GenValue.Scalar {
        val values = super.genValue(current).toActualOrNull()?.elements

        val value = GenValue.Scalar.Components(
            values?.getOrElse(0) { GenValue.Double.ZERO } ?: GenValue.Double.ZERO,
            values?.getOrElse(1) { GenValue.Double.ZERO } ?: GenValue.Double.ZERO,
            values?.getOrElse(2) { GenValue.Double.ZERO } ?: GenValue.Double.ZERO,
            values?.getOrElse(3) { GenValue.Double.ZERO } ?: GenValue.Double.ZERO,
        )

        return readGenValue<GenValue.Scalar>(current, value)
    }

}



