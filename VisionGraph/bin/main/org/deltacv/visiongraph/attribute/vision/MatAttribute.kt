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

package org.deltacv.visiongraph.attribute.vision

import imgui.ImGui
import org.deltacv.visiongraph.attribute.AttributeMode
import org.deltacv.visiongraph.attribute.AttributeType
import org.deltacv.visiongraph.attribute.EditorValue
import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.attribute.decomp.vision.MatAttributeDecomposer
import org.deltacv.visiongraph.attribute.misc.EnumAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.gui.style.rgbaColor
import org.deltacv.visiongraph.gui.util.ImGuiEx
import org.deltacv.visiongraph.node.display.ImageDisplayNode
import org.deltacv.visiongraph.node.vision.ColorSpace
import org.deltacv.visiongraph.serialization.v2.CodecType

@CodecType(instantiable = false)
class MatAttribute(
    override val mode: AttributeMode,
    override var attributeName: String? = null,
    var allowPrevizButton: Boolean = false
) : TypedAttribute<GenValue.Mat>(Companion, doEditorChangeChecking = true) {

    var colorSpace: ColorSpace = ColorSpace.GENERIC
        set(value) {
            field = value
            onChange.run()
        }

    var isBinary: Boolean = false
        set(value) {
            field = value
            onChange.run()
        }

    companion object: AttributeType<MatAttribute> {
        override val icon = FontAwesomeIcons.Image

        override val styleColor = rgbaColor(0, 151, 167, 180)
        override val styleHoveredColor = rgbaColor(0, 151, 167, 255)

        override fun new(mode: AttributeMode, variableName: String) = MatAttribute(mode, variableName)

        override fun newDecomposer() = MatAttributeDecomposer()
    }

    var isPrevizEnabled = false
        private set

    private var prevIsPrevizEnabled = false

    var wasPrevizJustEnabled = false
        private set

    var displayWindow: ImageDisplayNode? = null
        private set

    override fun drawAfterText() {
        if(mode == AttributeMode.OUTPUT && allowPrevizButton && isOnEditor) {
            ImGui.sameLine()

            fontAwesome.push()
                val text = if (isPrevizEnabled) FontAwesomeIcons.EyeSlash else FontAwesomeIcons.Eye

                isPrevizEnabled = ImGuiEx.toggleButton(
                    text, isPrevizEnabled
                )

                drawAfterTextSize = ImGui.getItemRectSize()
            ImGui.popFont()
        }

        val wasButtonToggled = (isPrevizEnabled != prevIsPrevizEnabled)
        wasPrevizJustEnabled = wasButtonToggled && isPrevizEnabled

        if(wasPrevizJustEnabled) {
            displayWindow = parentNode.editor.startImageDisplayFor(this)

            displayWindow!!.onDelete.once {
                isPrevizEnabled = false
                displayWindow = null
            }
        } else if(wasButtonToggled) {
            displayWindow?.delete()
            displayWindow = null
        }

        if(wasButtonToggled) {
            parentNode.editor.onDraw.once {
                onChange.run()
            }
        }

        prevIsPrevizEnabled = isPrevizEnabled
    }

    override fun readEditorValue() = EditorValue.Image(colorSpace, isBinary)

    override fun genValue(current: CodeGen.Current) = readGenValue<GenValue.Mat>(current)

    fun bindColorSpace(other: EnumAttribute<ColorSpace>) = apply {
        colorSpace = other.currentValue
        other.onChange {
            colorSpace = other.currentValue
        }
    }

    fun bindColorSpace(other: MatAttribute) = apply {
        val otherVal = other.editorValue as? EditorValue.Image
        colorSpace = otherVal?.colorSpace ?: ColorSpace.GENERIC
        isBinary = otherVal?.isBinary ?: false
        
        other.onChange {
            val v = other.editorValue as? EditorValue.Image
            colorSpace = v?.colorSpace ?: ColorSpace.GENERIC
            isBinary = v?.isBinary ?: false
        }
    }

    fun enablePrevizButton() = apply { allowPrevizButton = true }

    override fun restore() {
        super.restore()

        isPrevizEnabled = false
        prevIsPrevizEnabled = false
        displayWindow = null
    }

}