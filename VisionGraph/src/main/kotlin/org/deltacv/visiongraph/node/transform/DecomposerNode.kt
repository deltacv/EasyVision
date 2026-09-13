package org.deltacv.visiongraph.node.transform

import org.deltacv.visiongraph.attribute.AnyAttribute
import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.TypedAttribute
import org.deltacv.visiongraph.attribute.decomp.AttributeDecomposer
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.NoSession
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.serialization.v2.objTyped

@PaperNode(
    name = "nod_decomposer",
    category = NodeCategory.TRANSFORM,
    description = "des_decomposer"
)
@CodecType
class DecomposerNode : DrawNode<NoSession>() {

    var decomposer: AttributeDecomposer<*>? = null

    private var previousLinkedAttribute: Attribute? = null
    private var wasJustDecoded = false
    private var decodedWaitFrames = 0

    val input = AnyAttribute(INPUT, "$[att_attribute]", linkAcceptor = {
        if (it is TypedAttribute<*>) {
            val decomposer = it.attributeType.newDecomposer()

            if (decomposer != null) {
                Attribute.LinkAcceptance.Accept
            } else Attribute.LinkAcceptance.Reject("err_couldntlink_notdecomposable")
        } else Attribute.LinkAcceptance.Reject
    })

    override fun onEnable() {
        + input
        decomposer?.enable(this, input)
    }

    override fun drawNode() {
        val currentLinkedAttribute = input.availableLinkedAttribute

        if(wasJustDecoded) {
            if(currentLinkedAttribute != null) {
                wasJustDecoded = false
                decodedWaitFrames = 0
                previousLinkedAttribute = currentLinkedAttribute
            } else if(++decodedWaitFrames > 2) {
                wasJustDecoded = false
                decodedWaitFrames = 0
                decomposer?.disable()
                decomposer = null
            }
        } else if(currentLinkedAttribute != previousLinkedAttribute || decomposer == null) {
            decomposer?.disable()
            decomposer = null

            if(currentLinkedAttribute != null) {
                val newDecomposer = (currentLinkedAttribute as? TypedAttribute<*>)?.attributeType?.newDecomposer()

                if(newDecomposer != null) {
                    newDecomposer.enable(this, input)
                    this.decomposer = newDecomposer
                }
            }

            previousLinkedAttribute = currentLinkedAttribute
        }
    }

    override val generators = polyglot {
        generatorForAny { _, current ->
            decomposer?.let {
                current.codeGen.sessions[it] = it.genCode(input.genValue(current), current)
            }
            NoSession
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)

        decomposer?.let {
            encoder.obj("decomposer", it)
        }
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)

        decoder.obj("input", input)
        if(decoder.has("decomposer")) {
            decomposer = decoder.objTyped("decomposer")
        }

        wasJustDecoded = true
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        warnAssert(decomposer != null, "Decomposer is null")
        return decomposer?.getGenValueOf(current, attrib) ?: noValue(attrib)
    }

}



