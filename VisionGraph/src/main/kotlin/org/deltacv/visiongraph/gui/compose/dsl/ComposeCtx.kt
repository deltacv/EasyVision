package org.deltacv.visiongraph.gui.compose.dsl

import imgui.ImGui
import imgui.ImVec2
import org.deltacv.visiongraph.gui.compose.Compose
import org.deltacv.visiongraph.gui.compose.item.*
import org.deltacv.visiongraph.gui.compose.item.layout.AlignedRowItem
import org.deltacv.visiongraph.gui.compose.property.Property
import org.deltacv.visiongraph.gui.compose.property.type.Text
import org.deltacv.visiongraph.gui.compose.property.type.asProperty

class ComposeCtx(val composer: Compose) {
    fun button(text: Property<Text>, onClick: () -> Unit = {}) {
        composer.add(ButtonItem(composer, text, onClick))
    }
    fun button(text: Text, onClick: () -> Unit = {}) = button(text.asProperty(), onClick)
    fun button(text: String, onClick: () -> Unit = {}) = button(Text(text), onClick)

    fun text(text: Property<Text>) {
        composer.add(TextItem(composer, text))
    }
    fun text(text: Text) = text(text.asProperty())
    fun text(text: String) = text(Text(text))

    fun alignedText(text: Property<Text>, alignment: Double) {
        alignedRow(alignment) {
            text(text)
        }
    }
    fun alignedText(text: Text, alignment: Double) = alignedText(text.asProperty(), alignment)
    fun alignedText(text: String, alignment: Double) = alignedText(Text(text), alignment)

    fun newLine() {
        composer.add(NewLineItem(composer))
    }

    fun alignedRow(
        alignment: Double,
        spacing: Double = 5.0,
        content: ComposeCtx.() -> Unit
    ) {
        val rowComposer = Compose(composer.availableSize)
        val rowCtx = ComposeCtx(rowComposer)

        rowCtx.content()

        composer.add(
            AlignedRowItem(
                parent = composer,
                children = rowComposer.children,
                alignment = alignment,
                spacing = spacing
            )
        )
    }
}

fun compose(
    size: ImVec2 = ImGui.getContentRegionAvail(),
    content: ComposeCtx.() -> Unit
): Compose {
    val composer = Compose(size)
    val ctx = ComposeCtx(composer)

    ctx.content()
    return composer
}

fun immediateCompose(
    size: ImVec2 = ImGui.getContentRegionAvail(),
    content: ComposeCtx.() -> Unit
) = compose(size, content).renderAll()



