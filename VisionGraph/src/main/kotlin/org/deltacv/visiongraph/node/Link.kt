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

package org.deltacv.visiongraph.node

import imgui.extension.imnodes.ImNodes
import imgui.extension.imnodes.flag.ImNodesCol
import org.deltacv.visiongraph.action.Action
import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.id.DrawableIdElementBase
import org.deltacv.visiongraph.id.container.IdContext
import org.deltacv.visiongraph.serialization.v1.data.DataSerializable
import org.deltacv.visiongraph.serialization.v1.LinkSerializationData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataCodec
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.util.loggerForThis

@CodecType
class Link(
    a: Int = -1,
    b: Int = -1,
    val isDeletable: Boolean = true,
    override val shouldSerialize: Boolean = true
) : DrawableIdElementBase<Link>(),
    DataSerializable<LinkSerializationData>,
    DataCodec
{
    val logger by loggerForThis()

    val attribIdElementContainer = IdContext.local.peekNonNull<Attribute>()
    override val idContainer = IdContext.local.peekNonNull<Link>()

    var a = a
        private set

    var b = b
        private set

    val aAttrib get() = attribIdElementContainer[a]
    val bAttrib get() = attribIdElementContainer[b]

    var associatedAction: Action? = null

    constructor(data: LinkSerializationData) : this(data.from, data.to)

    fun getOtherAttribute(me: Attribute) = if(me == aAttrib) bAttrib else aAttrib

    override fun draw() {
        if(aAttrib == null || bAttrib == null) {
            return
        }

        val typedAttrib = when {
            aAttrib is TypedAttribute<*> -> aAttrib as TypedAttribute<*>
            bAttrib is TypedAttribute<*> -> bAttrib as TypedAttribute<*>
            else -> null
        }

        typedAttrib?.run {
            ImNodes.pushColorStyle(ImNodesCol.Link, linkColor)
            ImNodes.pushColorStyle(ImNodesCol.LinkHovered, linkHoveredColor)
            ImNodes.pushColorStyle(ImNodesCol.LinkSelected, linkHoveredColor)
        }

        ImNodes.link(id, a, b)

        if(typedAttrib != null) {
            ImNodes.popColorStyle()
            ImNodes.popColorStyle()
            ImNodes.popColorStyle()
        }
    }

    override fun onEnable() {
        restore()
    }

    override fun delete() {
        if(aAttrib?.links?.contains(this) == true) {
            aAttrib?.links?.remove(this)
            aAttrib?.onUnlink?.run()
        }
        if(bAttrib?.links?.contains(this) == true) {
            bAttrib?.links?.remove(this)
            bAttrib?.onUnlink?.run()
        }

        super.delete()
        triggerOnChange()
    }

    override fun restore() {
        if(aAttrib == null || bAttrib == null) return

        if(aAttrib === bAttrib) {
            logger.warn("Ignoring self-link (attribute {} to attribute {})", a, b)
            return
        }

        if(aAttrib?.links?.contains(this) == false) {
            aAttrib?.links?.add(this)
            aAttrib?.onLink?.run()
        }
        if(bAttrib?.links?.contains(this) == false) {
            bAttrib?.links?.add(this)
            bAttrib?.onLink?.run()
        }

        super.restore()
        triggerOnChange()
    }

    internal fun triggerOnChange() {
        aAttrib?.onChange?.run()
        bAttrib?.onChange?.run()
    }

    override fun serialize() = LinkSerializationData(a, b)

    override fun deserialize(data: LinkSerializationData) {
    }

    override fun toString() = "Link(from=$a, to=$b)"

    override fun encode(encoder: DataEncoder) {
        encoder.int("a", a)
        encoder.int("b", b)
    }

    override fun decode(decoder: DataDecoder) {
        a = decoder.int("a")
        b = decoder.int("b")

        logger.debug("Decoded Link with a=$a, b=$b")
    }

    companion object {
        fun getLinksBetween(a: Node<*>, b: Node<*>): List<Link> {
            val l = mutableListOf<Link>()

            for(link in IdContext.local.peekNonNull<Link>()) {
                val linkNodeA = link.aAttrib?.parentNode ?: continue
                val linkNodeB = link.bAttrib?.parentNode ?: continue

                if (
                    (a == linkNodeA && b == linkNodeB) || (b == linkNodeA && a == linkNodeB)
                ) {
                    l.add(link)
                }
            }

            return l
        }
    }

}



