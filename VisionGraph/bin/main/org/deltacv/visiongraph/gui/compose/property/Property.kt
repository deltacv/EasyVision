package org.deltacv.visiongraph.gui.compose.property

fun interface Property<T> {
    fun get(): T
}

class ConstantProperty<T>(private val value: T) : Property<T> {
    override fun get() = value
}



