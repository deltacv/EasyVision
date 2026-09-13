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

import com.google.gson.JsonObject
import imgui.ImGui
import imgui.flag.ImGuiCol
import org.deltacv.visiongraph.action.editor.CreateLinkAction
import org.deltacv.visiongraph.attribute.AnyAttribute
import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.AttributeMode
import org.deltacv.visiongraph.attribute.AttributeType
import org.deltacv.visiongraph.attribute.EditorValue
import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.engine.client.message.TunerValue
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.gui.style.rgbaColor
import org.deltacv.visiongraph.node.Link
import org.deltacv.visiongraph.serialization.v1.data.DataSerializable
import org.deltacv.visiongraph.serialization.v1.data.adapter.dataSerializableToJsonObject
import org.deltacv.visiongraph.serialization.v1.data.adapter.jsonObjectToDataSerializable
import org.deltacv.visiongraph.serialization.v1.AttributeSerializationData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@CodecType(instantiable = false)
open class ListAttribute<E: TypedAttribute<ER>, ER: GenValue>(
    override val mode: AttributeMode,
    override var attributeName: String? = null,
    val elementAttributeType: AttributeType<E>,
    length: Int? = null,
    val allowModification: Boolean = true
) : TypedAttribute<GenValue.List<ER>>(Companion) {

    companion object : AttributeType<ListAttribute<*, *>> {
        override val icon = FontAwesomeIcons.List
        override val allowsNew = false

        override val styleColor = rgbaColor(95, 158, 160, 180)
        override val styleHoveredColor = rgbaColor(95, 158, 160, 255)
    }

    override var icon = "${FontAwesomeIcons.ChevronLeft}[${elementAttributeType.icon}${FontAwesomeIcons.ChevronRight}"

    override val styleColor
        get() = if (elementAttributeType.isDefaultListColor) {
            Companion.styleColor
        } else elementAttributeType.listStyleColor

    override val styleHoveredColor
        get() = if (elementAttributeType.isDefaultListColor) {
            Companion.styleHoveredColor
        } else elementAttributeType.listStyleHoveredColor

    val listAttributes = mutableListOf<E>()
    val deleteQueue = mutableListOf<E>()

    private var lastHasLink = false

    private var lastLength: Int? = 0
    var fixedLength = length
        set(value) {
            field = value
            onEnable()
        }

    private val allowMutation get() = allowModification && fixedLength == null

    private var serializationData: Data? = null
    private var wasDecoded = false

    @Suppress("UNCHECKED_CAST")
    override fun onEnable() {
        if (serializationData != null) {
            listAttributes.clear()

            for (obj in serializationData!!.attributes) {
                val elem = createElement(false)
                jsonObjectToDataSerializable(obj, inst = elem as DataSerializable<Any>)
                elem.enable()
            }

            serializationData = null
        } else {
            if (wasDecoded) {
                for (attribute in listAttributes) {
                    attribute.enable() // enable decoded attributes
                }
                wasDecoded = false // Consume decoded state so future resizes work correctly
                // If the element was decoded, the fixedLength might not be up-to-date yet
                // if the parent node updates its properties lazily after decoding.
                // We sync lastLength with the actual decoded size to prevent incorrect resizing!
                lastLength = if (fixedLength != null) listAttributes.size else null
            } else {
                if (lastLength != fixedLength) {
                    if (fixedLength != null && (lastLength == null || lastLength == 0)) {
                        repeat(fixedLength!!) {
                            createElement()
                        }
                    } else if (lastLength != null) {
                        val delta = (fixedLength ?: 0) - (lastLength ?: 0)

                        if (delta < 0) {
                            repeat(-delta) {
                                val last = listAttributes[listAttributes.size - 1]
                                last.delete()

                                listAttributes.remove(last)
                                deleteQueue.add(last)
                            }
                        } else {
                            repeat(delta) {
                                if (deleteQueue.isNotEmpty()) {
                                    val last = deleteQueue.removeAt(deleteQueue.size - 1)
                                    last.restore()

                                    listAttributes.add(last)
                                } else {
                                    createElement()
                                }
                            }
                        }
                    } else {
                        for (attribute in listAttributes.toTypedArray<Attribute>()) {
                            attribute.delete()
                        }
                    }
                } else {
                    for (attribute in listAttributes) {
                        attribute.enable() 
                    }
                }
                
                lastLength = fixedLength
            }
        }
    }

    override fun delete() {
        super.delete()
        for(attribute in listAttributes) {
            attribute.delete()
        }
    }

    override fun restore() {
        super.restore()
        for(attribute in listAttributes) {
            attribute.restore()
        }
    }

    override fun draw() {
        super.draw()

        var ignoreNewLink = false

        // accepts links of elementAttributeType to redirect them into a list element
        if (mode == AttributeMode.INPUT && lastHasLink != hasLink && hasLink && availableLinkedAttribute !is ListAttribute<*, *>) {
            val linkedAttribute = availableLinkedAttribute!!

            // the user might be crazy and try to link an attribute that is already linked to one of our elements
            // this caused a funny bug during testing, so, please don't do that (not that you can anymore)
            val alreadyLinkedAttribute = listAttributes.find {
                it.availableLinkedAttribute == linkedAttribute
            }
            if(alreadyLinkedAttribute == null) {
                createElement(linkTo = linkedAttribute, relatedLink = linkedAttribute.links.last())
            }

            // delete the original link
            // this also handles the case in which the user was indeed crazy
            // and linked an attribute that was already linked to one of our elements
            links.last().delete()

            ignoreNewLink = true
        }

        for ((i, attrib) in listAttributes.withIndex()) {
            attrib.parentNode = parentNode

            if (lastHasLink != hasLink && !ignoreNewLink) {
                if (hasLink) {
                    // delete attributes if a link has been created
                    attrib.delete()
                } else {
                    // restore list attribs if they were previously deleted
                    // after destroying a link with another node
                    attrib.restore()
                }
            }

            if (!hasLink) { // only draw attributes if there's not a link attached
                val isDrawAttributeTextOverridden = drawAttributeText(i, attrib)

                if (isDrawAttributeTextOverridden) {
                    ImGui.sameLine()
                } else {
                    attrib.configureLayout(isInline = true)
                }

                attrib.draw()
            }
        }

        lastHasLink = hasLink
    }

    // accept either another ListAttribute with the same element type
    // or a TypedAttribute with the same type as the element type
    override fun acceptLink(other: Attribute): LinkAcceptance {
        val sameListType = other is ListAttribute<*, *> && other.elementAttributeType == elementAttributeType
        val sameTypedAttribute = other is TypedAttribute<*> && other.attributeType == elementAttributeType
        val anyAttribute = mode == AttributeMode.OUTPUT && other is AnyAttribute

        return if(sameListType || sameTypedAttribute || anyAttribute) {
            LinkAcceptance.Accept
        } else {
            LinkAcceptance.Reject
        }
    }

    open fun drawAttributeText(index: Int, attrib: Attribute): Boolean = false

    private fun validateAttributes() {
        for (attribute in listAttributes) {
            raiseAssert(
                attribute.attributeType == elementAttributeType,
                "All attributes in the list must be of the same type as the element type"
            )
        }
    }

    override fun drawAttribute() {
        super.drawAttribute()

        if (!hasLink && elementAttributeType.allowsNew && allowMutation && mode == AttributeMode.INPUT) {
            // idk wat the frame height is, i just stole it from
            // https://github.com/ocornut/imgui/blob/7b8bc864e9af6c6c9a22125d65595d526ba674c5/imgui_widgets.cpp#L3439

            val buttonSize = ImGui.getFrameHeight()

            val style = ImGui.getStyle()

            ImGui.sameLine(0.0f, style.itemInnerSpacingX * 2.0f)

            if (ImGui.button("+", buttonSize, buttonSize)) {
                // creates a new element with the + button
                // uses the "new" function from the attribute's companion Type
                createElement()
            }

            val hideMinusButton = listAttributes.isEmpty()

            // display the - button only if the attributes list is not empty
            if (hideMinusButton) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0x00000000)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0x00000000)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0x00000000)
                ImGui.pushStyleColor(ImGuiCol.Text, 0x00000000)
            }

            ImGui.sameLine(0.0f, style.itemInnerSpacingX)

            if (ImGui.button("-", buttonSize, buttonSize) && !hideMinusButton) {
                // remove the last element from the list when - is pressed
                listAttributes.removeLastOrNull()
                    ?.delete() // also delete it from the element id registry
            }

            if(hideMinusButton) {
                ImGui.popStyleColor()
                ImGui.popStyleColor()
                ImGui.popStyleColor()
                ImGui.popStyleColor()
            }
        }
    }

    private fun createElement(enable: Boolean = true, linkTo: Attribute? = null, relatedLink: Link? = null): E {
        val count = listAttributes.size.toString()
        val elementName = count + if (count.length == 1) " " else ""

        val element = elementAttributeType.new(mode, elementName)
        if(enable) element.enable() // enables the new element

        element.layout = element.layout.copy(showType = false) // hides the variable type
        element.onChange.attach {
            emitChange(element.peekChange())
        }

        listAttributes.add(element)

        if (linkTo != null) {
            // if the element is being created because of a link, create the link action
            // to link the new element to the linked attribute
            parentNode.editor.onDraw.once {
                val action = CreateLinkAction(Link(linkTo.id, element.id))

                if(relatedLink != null) {
                    val associatedAction = relatedLink.associatedAction
                    if(associatedAction != null) {
                        // sneakily insert ourselves into the action stack by killing and impersonating
                        // the original action that created the original link this avoids glitches when the
                        // user tries to undo/redo the creation of this new link. otherwise, the stack would
                        // try to address the original link, which doesn't exist anymore.
                        associatedAction.idContainer[associatedAction.id] = action
                    }
                }

                action.enable()
            }
        }

        onElementCreation(element)
        return element
    }

    fun findIndex(element: Attribute): Int? {
        val index = listAttributes.indexOf(element)
        return if(index == -1) null else index
    }

    open fun onElementCreation(element: Attribute) {}

    fun forEach(callback: (Attribute) -> Unit) = listAttributes.forEach(callback)

    @JvmName("forEachTyped")
    inline fun <reified T> forEach(callback: (T) -> Unit) = listAttributes.forEach {
        if(it is T) callback(it)
    }

    @Suppress("UNCHECKED_CAST")
    override fun genValue(current: CodeGen.Current): GenValue.List<ER> {
        return if (mode == AttributeMode.INPUT) {
            if (hasLink) {
                val linkedAttrib = availableLinkedAttribute

                raiseAssert(
                    linkedAttrib != null,
                    "List attribute must have another attribute attached"
                )

                validateAttributes()

                val value = linkedAttrib.genValue(current)

                raiseAssert(
                    value is GenValue.List<*>,
                    "Attribute attached is not a list"
                )

                value as GenValue.List<ER>
            } else {
                validateAttributes() // we can safely do an unchecked cast after validating the attributes

                // get the values of all the attributes and return a
                // GenValue.List with the attribute values in an array
                GenValue.List.Actual(listAttributes.map { it.genValue(current) }) as GenValue.List<ER>
            }
        } else {
            parentNode.genCodeIfNecessary(current)
            val value = getGenValueFromNode(current)
            raiseAssert(
                value is GenValue.List<*>,
                "Value returned from the node is not a list"
            )

            value as GenValue.List<ER>
        }
    }

    override fun readTunerValue() = TunerValue.ListValue(
        listAttributes.map { it.tunerValue ?: TunerValue.NullValue }
    )

    override fun readEditorValue() = EditorValue.List(
        listAttributes.map { it.editorValue ?: EditorValue.Null }
    )

    // ------------------ Serialization v1 ------------------

    override fun makeSerializationData(): AttributeSerializationData {
        val objects = mutableListOf<JsonObject>()

        for (attrib in listAttributes) {
            objects.add(dataSerializableToJsonObject(attrib).asJsonObject)
        }

        return Data(objects)
    }

    override fun takeSerializationData(data: AttributeSerializationData) {
        if (data is Data)
            serializationData = data
    }

    data class Data(var attributes: List<JsonObject>) : AttributeSerializationData()


    // ------------------ Serialization v2 ------------------

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.objList("attributes", listAttributes)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        wasDecoded = true

        // clear the list before decoding to avoid duplicates
        this.listAttributes.clear()

        // pass createElement instantiator to objList so that
        // the elements are decoded with the correct type
        // (createElement automatically appends to listAttributes)
        decoder.objList("attributes") {
            createElement(enable = false)
        }
    }

}



