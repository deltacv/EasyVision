package org.deltacv.visiongraph.gui.compose.item

import imgui.ImVec2

interface Item {
    val parent: ContainerItem
    val hasParent get() = parent != this

    val isSpatial get() = measure() != null

    fun measure(): ImVec2?
    fun render()

    fun postRender() { }
}

interface ContainerItem : Item {
    val children: List<Item>

    val actionChildren get() = children.filter { it.measure() == null }
    val spatialChildren get() = children.filter { it.measure() != null }

    override fun render() {
        for(child in children) {
            child.render()
        }
    }

    override fun postRender() {
        for(child in children) {
            child.postRender()
        }
    }
}



