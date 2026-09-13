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
import imgui.type.ImInt
import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.AttributeMode
import org.deltacv.visiongraph.attribute.AttributeType
import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.gui.style.rgbaColor
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.serialization.v1.data.SerializeData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.mai18n.tr
import org.deltacv.visiongraph.attribute.EditorValue

@CodecType(instantiable = false)
class EnumAttribute<T: Enum<T>>(
    override val mode: AttributeMode,
    override var attributeName: String?,
    val values: List<T>,
    val enumNameFont: Font? = null,
    val enumToNameConverter: (T) -> String = { it.name } // by default, just use the name of the enum value, but allow for customization
) : TypedAttribute<GenValue.Enum<T>>(
    Companion,
    doEditorChangeChecking = true // takes advantage of readEditorValue for change checking, so we don't have to do it manually
) {

    companion object: AttributeType<EnumAttribute<*>> {
        override val icon = FontAwesomeIcons.FlagCheckered
        override val allowsNew = false

        override val styleColor = rgbaColor(46, 139, 87, 180)
        override val styleHoveredColor = rgbaColor(46, 139, 87, 255)
    }

    private val valuesStrings = values.map {
        tr(enumToNameConverter(it))
    }.toTypedArray()

    @SerializeData
    val currentIndex = ImInt()

    val currentValue get() = values[currentIndex.get()]

    override fun drawAttribute() {
        super.drawAttribute()

        if(!hasLink) {
            enumNameFont?.push()
            ImGui.pushItemWidth(110.0f)

            ImGui.combo("", currentIndex, valuesStrings)

            enumNameFont?.let { ImGui.popFont() }
            ImGui.popItemWidth()
        }
    }

    override fun acceptLink(other: Attribute) = if(super.acceptLink(other).accepted && other is EnumAttribute<*> && values[0]::class == other.values[0]::class) {
        LinkAcceptance.Accept
    } else {
        LinkAcceptance.Reject
    }

    override fun readEditorValue() = EditorValue.Enum(currentValue)

    @Suppress("UNCHECKED_CAST")
    override fun genValue(current: CodeGen.Current): GenValue.Enum<T> {
        val expectedClass = values[0]::class

        if(isInput) {
            if(hasLink) {
                val linkedAttrib = availableLinkedAttribute

                raiseAssert(
                    linkedAttrib != null,
                    "Enum attribute must have another attribute attached"
                )

                val value = linkedAttrib.genValue(current)
                raiseAssert(value is GenValue.Enum<*>, "Attribute attached is not a valid Enum")

                raiseAssert(
                    value.value::class.java == expectedClass,
                    "Enum attribute attached (${value::class.java.simpleName}) is not the expected type of enum ($expectedClass)"
                )

                return value as GenValue.Enum<T>
            } else {
                val value = values[currentIndex.get()]

                raiseAssert(
                    value::class.java == expectedClass.java,
                    "Enum attribute attached (${value::class.java.simpleName}) is not the expected type of enum ($expectedClass)"
                )

                return GenValue.Enum(value)
            }
        } else {
            val value = getGenValueFromNode(current)
            raiseAssert(value is GenValue.Enum<*>, "Value returned from the node is not an enum")


            val valueEnum = value as GenValue.Enum<T>

            return valueEnum
        }
    }

    // ------------------ Serialization v2 ------------------
    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.int("currentIndex", currentIndex.get())
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        currentIndex.set(decoder.int("currentIndex"))
    }

}



