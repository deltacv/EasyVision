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
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.engine.client.message.TunerValue
import org.deltacv.visiongraph.exception.AttributeGenException
import org.deltacv.visiongraph.id.DrawableIdElementBase
import org.deltacv.visiongraph.id.container.IdContext
import org.deltacv.visiongraph.node.Link
import org.deltacv.visiongraph.node.Node
import org.deltacv.visiongraph.serialization.v1.AttributeSerializationData
import org.deltacv.visiongraph.serialization.v1.data.DataSerializable
import org.deltacv.visiongraph.serialization.v1.BasicAttribData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.CodecTypeRegistry
import org.deltacv.visiongraph.serialization.v2.DataCodec
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.util.DelegatedChangeEmitter
import org.deltacv.visiongraph.util.QueuedChangeEmitter
import org.deltacv.visiongraph.util.event.PaperEventHandler
import org.deltacv.visiongraph.util.loggerForThis
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

enum class AttributeMode { INPUT, OUTPUT }

abstract class Attribute :
    DrawableIdElementBase<Attribute>(),
    DelegatedChangeEmitter<Attribute.ChangeType>,
    DataSerializable<AttributeSerializationData>,
    DataCodec
{

    private val logger by loggerForThis()

    override val idContainer get() = IdContext.local.peekNonNull<Attribute>()

    override val requestedId get() = if(forgetSerializedId || (hasParentNode && parentNode.forgetSerializedId))
        null // generate new id
    else serializedId

    private var serializedId: Int? = null

    abstract val mode: AttributeMode

    lateinit var parentNode: Node<*>
        internal set

    val hasParentNode get() = ::parentNode.isInitialized

    val links = mutableListOf<Link>()
    val enabledLinks get() = links.filter { it.isEnabled }

    val hasLink get() = enabledLinks.isNotEmpty()

    val isInput by lazy { mode == AttributeMode.INPUT }
    val isOutput by lazy { !isInput }

    val isOnEditor get() = parentNode.isOnEditor
    val editor get() = parentNode.editor

    enum class DrawState { IDLE, DRAWING, SKIPPED }

    var showAttributesCircles = true

    var forgetSerializedId = false
        private set

    var drawState = DrawState.IDLE
        private set

    private var lastFrameDrawn = -1
    private var isProcessingEditorValue = false
    private var isProcessingTunerValue = false

    val onDelete = PaperEventHandler("OnDelete-${this::class.simpleName}")

    val onLink = PaperEventHandler("OnLink-${this::class.simpleName}")
    val onUnlink = PaperEventHandler("OnUnlink-${this::class.simpleName}")

    val position = ImVec2()

    override val changeEmitterDelegate = QueuedChangeEmitter<ChangeType>(defaultPeekChange = ChangeType.ValueChange)

    abstract fun drawAttribute()

    fun drawHere() {
        draw()
    }

    override fun draw() {
        val currentFrame = ImGui.getFrameCount()
        if (lastFrameDrawn == currentFrame) {
            drawState = DrawState.SKIPPED
            return
        }
        lastFrameDrawn = currentFrame
        drawState = DrawState.DRAWING

        processChanges()

        if(parentNode.showAttributesCircles && showAttributesCircles) {
            if (mode == AttributeMode.INPUT) {
                ImNodes.beginInputAttribute(id)
            } else {
                ImNodes.beginOutputAttribute(id)
            }
            ImGui.getCursorPos(position)
        }

        drawAttribute()

        if(parentNode.showAttributesCircles && showAttributesCircles) {
            if (mode == AttributeMode.INPUT) {
                ImNodes.endInputAttribute()
            } else {
                ImNodes.endOutputAttribute()
            }
        }

        drawState = DrawState.IDLE
    }

    override fun delete() {
        onDelete.run()
        idContainer.removeId(id)

        for(link in enabledLinks.toTypedArray()) {
            link.delete()
        }
    }

    override fun restore() {
        idContainer[id] = this

        for(link in links.toTypedArray()) {
            if(link.getOtherAttribute(this)?.isEnabled == true)
                link.restore()
        }
    }

    val availableLinkedAttribute: Attribute? get() {
        if(!isInput) {
            raise("Output attributes might have more than one link, use linkedAttributes instead")
        }

        if(!hasLink) {
            return null
        }

        val link = enabledLinks[0]

        return if(link.aAttrib == this) {
            link.bAttrib
        } else link.aAttrib
    }

    val allLinkedAttributes get() = links.map {
        if(it.aAttrib == this) {
            it.bAttrib
        } else it.aAttrib
    }

    val availableLinkedAttributes get() = enabledLinks.map {
        if(it.aAttrib == this) {
            it.bAttrib
        } else it.aAttrib
    }

    fun raise(message: String): Nothing = throw AttributeGenException(this, message)

    fun warn(message: String) {
        logger.warn(message) // TODO: Warnings system...
    }

    @OptIn(ExperimentalContracts::class)
    fun raiseAssert(condition: Boolean, message: String) {
        contract {
            returns() implies condition
        }

        if(!condition) {
            raise(message)
        }
    }
    fun warnAssert(condition: Boolean, message: String) {
        if(!condition) {
            warn(message)
        }
    }

    fun requireAttachedAttribute() = raiseAssert(hasLink, "err_musthave_attachedattrib")

    abstract fun acceptLink(other: Attribute): LinkAcceptance

    abstract fun genValue(current: CodeGen.Current): GenValue

    open val editorValue: EditorValue?
        get() {
            if(isProcessingEditorValue) return EditorValue.Null
            isProcessingEditorValue = true
            try {
                return internalEditorValue
            } finally {
                isProcessingEditorValue = false
            }
        }

    protected open val internalEditorValue: EditorValue? get() = null

    open val tunerValue: TunerValue?
        get() {
            if(isProcessingTunerValue) return TunerValue.NullValue
            isProcessingTunerValue = true
            try {
                return internalTunerValue
            } finally {
                isProcessingTunerValue = false
            }
        }

    protected open val internalTunerValue: TunerValue? get() = null

    fun rebuildPreviz() {
        if(!isOnEditor) return

        // schedule for a frame later
        editor.visionGraph.onUpdate.once {
            parentNode.editor.visionGraph.previzManager.refreshPreviz()
        }
    }

    fun getGenValueFromNode(current: CodeGen.Current): GenValue {
        parentNode.genCodeIfNecessary(current)
        return parentNode.getGenValueOf(current, this)
    }

    // ------------------ Serialization v1 ------------------

    open fun makeSerializationData(): AttributeSerializationData = BasicAttribData(id)
    open fun takeSerializationData(data: AttributeSerializationData) { /* do nothing */ }

    /**
     * Call before enable()
     */
    final override fun deserialize(data: AttributeSerializationData) {
        serializedId = data.id
        takeSerializationData(data)
    }

    final override fun serialize(): AttributeSerializationData {
        val data = makeSerializationData()
        data.id = id

        return data
    }


    // ------------------ Serialization v2 ------------------

    override fun encode(encoder: DataEncoder) {
        encoder.int("id", id)
    }

    override fun decode(decoder: DataDecoder) {
        serializedId = decoder.int("id")
        idContainer.reserveId(serializedId!!)
        logger.debug("Decoded attribute with id {} ({})", serializedId, CodecTypeRegistry.nameOf(this::class))
    }

    fun forgetSerializedId() {
        forgetSerializedId = true
    }

    override fun toString() = "Attribute(type=${this::class.java.typeName}, id=$id)"

    sealed class LinkAcceptance(val accepted: Boolean) {
        companion object {
            val Reject = Reject()
        }

        object Accept: LinkAcceptance(true)
        class Reject(val reason: String = "err_couldntlink_didntmatch"): LinkAcceptance(false)
    }

    sealed class ChangeType {
        object ValueChange: ChangeType()
        object LinkChange: ChangeType()
    }
}

fun <T: Attribute> T.rebuildOnLink(): T = apply {
    onChange {
        if(this@rebuildOnLink.isEnabled && this@rebuildOnLink.peekChange() is Attribute.ChangeType.LinkChange) {
            rebuildPreviz()
        }
    }
}

fun <T: Attribute> T.rebuildOnChange(): T = apply {
    onChange {
        if(this@rebuildOnChange.isEnabled) {
            rebuildPreviz()
        }
    }
}

@CodecType(instantiable = false)
open class EmptyInputAttribute(
    parent: Node<*>? = null
) : Attribute() {
    override val mode = AttributeMode.INPUT

    init {
        parent?.let {
            parentNode = it
        }
    }

    override fun drawAttribute() { }

    override fun acceptLink(other: Attribute) = LinkAcceptance.Accept

    override fun genValue(current: CodeGen.Current): GenValue {
        throw NotImplementedError("genValue() is not implemented for EmptyInputAttribute")
    }
}



