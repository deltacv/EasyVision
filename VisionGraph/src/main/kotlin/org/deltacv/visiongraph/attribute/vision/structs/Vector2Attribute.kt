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

import imgui.ImGui
import imgui.ImVec2
import imgui.type.ImInt
import org.deltacv.mai18n.tr
import org.deltacv.visiongraph.action.editor.CreateLinkAction
import org.deltacv.visiongraph.attribute.AttributeMode
import org.deltacv.visiongraph.attribute.AttributeType
import org.deltacv.visiongraph.attribute.EditorValue
import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.attribute.decomp.vision.structs.Vector2AttributeDecomposer
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.engine.client.message.TunerValue
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.id.Misc
import org.deltacv.visiongraph.node.Link
import org.deltacv.visiongraph.node.math.Vector2Node
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.intOrNull

@CodecType(instantiable = false)
class Vector2Attribute (
    override val mode: AttributeMode,
    override var attributeName: String? = null,
    val showInlineInputs: Boolean = true,
    val useSizeNaming: Boolean = false
) : TypedAttribute<GenValue.Vec2>(Companion) {

    companion object : AttributeType<Vector2Attribute> {
        override val icon = FontAwesomeIcons.DotCircle

        override fun new(mode: AttributeMode, variableName: String) = Vector2Attribute(mode, variableName)

        override fun newDecomposer() = Vector2AttributeDecomposer()
    }

    private val xId by Misc.newId()
    private val yId by Misc.newId()

    private val xValue = ImInt()
    private val yValue = ImInt()

    override fun drawAfterText() {
        if(mode == AttributeMode.INPUT) {
            ImGui.sameLine()

            fontAwesome.push()

            if(!hasLink && ImGui.button(FontAwesomeIcons.PencilAlt)) {
                val node = parentNode.editor.addNode(Vector2Node(useSizeNaming))

                parentNode.editor.onDraw.once {
                    CreateLinkAction(
                        Link(
                            (node as Vector2Node).result.id, id
                        )
                    ).enable()

                    node.pinToMouse = true
                    node.pinToMouseOffset = ImVec2(0f, -10f)
                }
            }

            ImGui.popFont()
        }
    }

    override fun drawAttribute() {
        super.drawAttribute()

        if(showInlineInputs && mode == AttributeMode.INPUT && !hasLink) {
            inlineIfNeeded()

            ImGui.pushItemWidth(80f)

            val xLabel = if(useSizeNaming) "att_width" else "X"

            if(ImGui.inputInt("###$xId", xValue, 0)) {
                emitChange(ChangeType.ValueChange)
            }
            if(ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(xLabel))
            }

            ImGui.sameLine()

            val yLabel = if(useSizeNaming) "att_height" else "Y"

            if(ImGui.inputInt("###$yId", yValue, 0)) {
                emitChange(ChangeType.ValueChange)
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(yLabel))
            }

            ImGui.popItemWidth()
        }
    }

    override fun readEditorValue() = EditorValue.List(listOf(
        EditorValue.Int(xValue.get()),
        EditorValue.Int(yValue.get())
    ))
    override fun readTunerValue() = TunerValue.ListValue(listOf(
        TunerValue.IntValue(xValue.get()),
        TunerValue.IntValue(yValue.get())
    ))

    override fun genValue(current: CodeGen.Current) =
        readGenValue<GenValue.Vec2>(current,
            GenValue.Vec2.Actual(
                GenValue.Int.Actual(xValue.get().resolved()),
                GenValue.Int.Actual(yValue.get().resolved())
            )
        )

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.int("x", xValue.get())
        encoder.int("y", yValue.get())
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        xValue.set(decoder.intOrNull("x") ?: 0)
        yValue.set(decoder.intOrNull("y") ?: 0)
    }

}