package org.deltacv.visiongraph.gui.compose.property.type

import org.deltacv.visiongraph.gui.compose.property.ConstantProperty

interface PropertyType

fun <T: PropertyType> T.asProperty() = ConstantProperty(this)



