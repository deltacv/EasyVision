package org.deltacv.visiongraph.attribute

import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.gui.font.FontAwesomeIcons
import org.deltacv.visiongraph.serialization.v2.CodecType

@CodecType(instantiable = false)
class AnyAttribute(
    override val mode: AttributeMode,
    override var attributeName: String?,
    private val linkAcceptor: (Attribute) -> LinkAcceptance = { LinkAcceptance.Accept }
) : TypedAttribute<GenValue>(Companion) {

    companion object: AttributeType<AnyAttribute> {
        override val icon = FontAwesomeIcons.Asterisk
        override fun new(mode: AttributeMode, variableName: String) = AnyAttribute(mode, variableName)
    }

    override fun genValue(current: CodeGen.Current): GenValue = readGenValue(current)

    override fun acceptLink(other: Attribute) = linkAcceptor(other)
}



