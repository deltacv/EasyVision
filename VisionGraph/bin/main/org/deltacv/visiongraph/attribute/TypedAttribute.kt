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

package org.deltacv.visiongraph.attribute

import imgui.ImGui
import imgui.ImVec2
import imgui.extension.imnodes.ImNodes
import imgui.extension.imnodes.flag.ImNodesCol
import org.deltacv.visiongraph.VisionGraph
import org.deltacv.visiongraph.attribute.decomp.AttributeDecomposer
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.mai18n.tr
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.engine.client.message.TunerChangeValueMessage
import org.deltacv.visiongraph.engine.client.message.TunerValue
import org.deltacv.visiongraph.gui.font.Font

interface AttributeType<A: TypedAttribute<*>> {
    val icon: String
    val allowsNew: Boolean get() = true

    val styleColor: Int get() = VisionGraph.imnodesStyle.pin
    val styleHoveredColor: Int get() = VisionGraph.imnodesStyle.pinHovered

    val listStyleColor: Int get() = VisionGraph.imnodesStyle.pin
    val listStyleHoveredColor: Int get() = VisionGraph.imnodesStyle.pinHovered

    val isDefaultListColor: Boolean get() =
        listStyleColor == VisionGraph.imnodesStyle.pin
            && listStyleHoveredColor == VisionGraph.imnodesStyle.pinHovered

    fun new(mode: AttributeMode, variableName: String): A {
        throw UnsupportedOperationException("Cannot instantiate this attribute with new")
    }

    fun newDecomposer(): AttributeDecomposer<*>? = null
}

abstract class TypedAttribute<R: GenValue>(
    val attributeType: AttributeType<*>,
    val doLinkChangeChecking: Boolean = true,
    val doEditorChangeChecking: Boolean = false
) : Attribute() {

    abstract var attributeName: String?

    open val styleColor get() = attributeType.styleColor
    open val styleHoveredColor get() = attributeType.styleHoveredColor

    open val linkColor get() = styleColor
    open val linkHoveredColor get() = styleHoveredColor

    data class Layout(
        val showLabel: Boolean = true,
        val showType: Boolean = true,
        val isInline: Boolean = false,
        val labelFont: Font? = null
    )

    var layout = Layout()

    fun configureLayout(
        showLabel: Boolean = layout.showLabel,
        showType: Boolean = layout.showType,
        isInline: Boolean = layout.isInline,
        labelFont: Font? = layout.labelFont
    ) {
        layout = layout.copy(showLabel = showLabel, showType = showType, isInline = isInline, labelFont = labelFont)
    }

    open var icon = attributeType.icon

    open var drawAfterTextSize = ImVec2()
        protected set

    enum class Stage { INIT, MEASURE, READY }
    private var stage = Stage.INIT

    private var cachedTunerLabels = mutableMapOf<Int?, String>()

    protected val finalVarName get() =
        attributeName ?: if (mode == AttributeMode.INPUT) "$[mis_input]" else "$[mis_output]"

    val nodeSize = ImVec2()

    private var previousLinkedAttributes: List<Attribute?> = emptyList()
    private var previousGet: Any? = null

    protected val monospaceFont by Font.findLazy("jetbrains-mono")
    protected val fontAwesome by Font.findLazy("font-awesome")

    override fun draw() {
        ImNodes.pushColorStyle(ImNodesCol.Pin, styleColor)
        ImNodes.pushColorStyle(ImNodesCol.PinHovered, styleHoveredColor)

        super.draw()

        ImNodes.popColorStyle()
        ImNodes.popColorStyle()
    }

    override fun drawAttribute() {
        when(stage) {
            Stage.INIT -> stage = Stage.MEASURE
            Stage.MEASURE -> {
                ImNodes.getNodeDimensions(nodeSize, parentNode.id)
                stage = Stage.READY
            }
            Stage.READY -> {}
        }

        val pushedFont = layout.labelFont ?: (if(layout.isInline) monospaceFont else null)

        pushedFont?.push()

        if(layout.showLabel) {
            drawLabel(tr(finalVarName))
        } else if(!layout.isInline) {
            ImGui.text("")
        } else {
            drawAfterText()
        }

        pushedFont?.pop()

        if(doLinkChangeChecking) {
            val currentLinkedAttribs = availableLinkedAttributes

            var linksChanged = false

            if (previousLinkedAttributes.size != currentLinkedAttribs.size) {
                linksChanged = true
            } else {
                outer@ for (previousLink in previousLinkedAttributes) {
                    for (currentLink in currentLinkedAttribs) {
                        if (previousLink === currentLink) continue@outer
                    }
                    linksChanged = true
                    break
                }
            }

            if (linksChanged) {
                emitChange(ChangeType.LinkChange)
            }

            previousLinkedAttributes = currentLinkedAttribs.toList()
        }

        if(doEditorChangeChecking) {
            val currentGet = editorValue

            if(currentGet != previousGet) {
                emitChange(ChangeType.ValueChange)
            }

            previousGet = currentGet
        }
    }

    open fun drawAfterText() { }

    protected fun drawLabel(text: String, customIcon: String = icon, customIconFont: Font = fontAwesome) {
        val hasIcon = customIcon.isNotEmpty()

        if(mode == AttributeMode.INPUT) {
            if(hasIcon) {
                customIconFont.push()
                ImGui.text(customIcon)
                ImGui.popFont()
                ImGui.sameLine()
            }

            ImGui.text(text)
            drawAfterText()
        } else {
            var labelWidth = ImGui.calcTextSize(text).x

            if(hasIcon) {
                customIconFont.push()
                labelWidth += ImGui.calcTextSize(customIcon).x + ImGui.getStyle().itemSpacingX
                ImGui.popFont()
            }

            labelWidth += drawAfterTextSize.x

            // Align to the right side of the node.
            // 8.0f is a common horizontal padding for ImNodes pins from the node edge.
            val padding = 8.0f
            val indentValue = nodeSize.x - labelWidth - padding

            // In ImGui 1.92+, nodeSize can grow unboundedly if indented, creating feedback loop.
            // Cap indent to prevent uncontrolled node expansion.
            val maxIndent = 200.0f
            val actualIndent = if(indentValue > 0 && indentValue <= maxIndent) {
                ImGui.indent(indentValue)
                indentValue
            } else {
                0.0f
            }

            ImGui.text(text)

            if(hasIcon) {
                ImGui.sameLine()
                customIconFont.push()
                ImGui.text(customIcon)
                ImGui.popFont()
            }

            drawAfterText()

            if(actualIndent > 0) {
                ImGui.unindent(actualIndent)
            }
        }
    }

    protected fun inlineIfNeeded() {
        if(layout.isInline) {
            ImGui.sameLine()
        }
    }

    abstract override fun genValue(current: CodeGen.Current): R

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified R : GenValue> readGenValue(
        current: CodeGen.Current,
        inputFieldValue: R? = null
    ): R {
        if(isInput) {
            return if(hasLink || inputFieldValue == null) {
                val linkedAttrib = availableLinkedAttribute

                raiseAssert(
                    linkedAttrib != null,
                    tr("err_musthave_attachedattrib")
                )

                if(linkedAttrib === this) {
                    raise("err_cannotlink_toself")
                }

                if(current.codeGen.isBusy(this)) {
                    raise("err_recursiondetected")
                }

                current.codeGen.markBusy(this)

                try {
                    val value = linkedAttrib.genValue(current)
                    raiseAssert(value is R, tr("err_attachedattrib_isnot", R::class.simpleName ?: "unknown"))

                    value
                } finally {
                    current.codeGen.unmarkBusy(this)
                }
            } else {
                inputFieldValue
            }
        } else {
            val value = getGenValueFromNode(current)
            raiseAssert(value is R, tr("err_valreturned_isnot", R::class.simpleName ?: "unknown"))

            return value
        }
    }

    // acceptLink is overridden to allow for ListAttribute to accept TypedAttribute
    override fun acceptLink(other: Attribute): LinkAcceptance {
        // basic type check
        val sameTypedAttribute =
            other is TypedAttribute<*> && other.attributeType == attributeType

        val sameClass =
            this::class == other::class

        // we'll let the AnyAttribute determine if it can link or not
        val outputToAny =
            mode == AttributeMode.OUTPUT && other is AnyAttribute

        // allow linking to ListAttribute if the types are the same so it becomes an element of the list
        // ONLY IF THIS ELEMENT IS OUTPUT otherwise it allows to link a list output to an individual input
        val outputToMatchingList =
            mode == AttributeMode.OUTPUT &&
                    other is ListAttribute<*, *> &&
                    other.elementAttributeType == attributeType

        return if (
            sameTypedAttribute ||
            sameClass ||
            outputToAny ||
            outputToMatchingList
        ) {
            LinkAcceptance.Accept
        } else {
            LinkAcceptance.Reject()
        }
    }

    open fun readEditorValue(): EditorValue? = null
    open fun readTunerValue(): TunerValue? = null

    override val internalEditorValue get() = when {
        mode == AttributeMode.INPUT -> availableLinkedAttribute?.editorValue ?: readEditorValue()
        else -> readEditorValue()
    }
    override val internalTunerValue get() = when {
        mode == AttributeMode.INPUT -> readTunerValue()
        else -> null
    }

    fun tunerLabel(indexIfApplicable: Int? = null): String {
        if(!cachedTunerLabels.containsKey(indexIfApplicable)) {
            val label = id.toString() + (indexIfApplicable?.let { "_$it" } ?: "")
            cachedTunerLabels[indexIfApplicable] = label

            onChange {
                val value = tunerValue

                // if value is null we have an oopsie and we should just rebuild
                // however, we need to always rebuild on link changes, no other way to handle it
                if(value == null || peekChange() is ChangeType.LinkChange) {
                    rebuildPreviz()
                    return@onChange
                }

                broadcastTunerMessageFor(label, value, indexIfApplicable)
            }
        }

        return cachedTunerLabels[indexIfApplicable]!!
    }

    protected fun broadcastTunerMessageFor(label: String, value: TunerValue, indexIfApplicable: Int? = null) {
        if(!isOnEditor) return

        parentNode.editor.visionGraph.engineClient.sendMessage(
            when (value) {
                is TunerValue.ListValue -> {
                    if(indexIfApplicable != null) {
                        TunerChangeValueMessage(label, value.values.getOrNull(indexIfApplicable)
                            ?: throw IndexOutOfBoundsException("Index $indexIfApplicable is out of bounds for list of size ${value.values.size}")
                        )
                    } else {
                        TunerChangeValueMessage(label, value)
                    }
                }
                else -> TunerChangeValueMessage(label, value)
            }
        )
    }

}



