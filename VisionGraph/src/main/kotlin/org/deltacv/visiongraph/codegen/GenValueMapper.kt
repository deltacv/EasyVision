package org.deltacv.visiongraph.codegen

import org.deltacv.visiongraph.attribute.Attribute

interface GenValueMapper {
    fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue
}



