package org.deltacv.visiongraph.gui

import imgui.flag.ImGuiWindowFlags
import org.deltacv.visiongraph.gui.compose.dsl.immediateCompose
import org.deltacv.visiongraph.gui.compose.property.type.Text
import org.deltacv.visiongraph.gui.font.Font
import org.deltacv.visiongraph.util.event.PaperEventHandler
import org.deltacv.visiongraph.util.flags
import org.deltacv.mai18n.tr

class ConfirmationModalWindow(
    val message: String,
    title: String = "mis_confirmaction",
    val font: Font? = null
) : Window() {

    override var title = tr(title)

    override val windowFlags = flags(
        ImGuiWindowFlags.NoResize,
        ImGuiWindowFlags.NoMove,
        ImGuiWindowFlags.AlwaysAutoResize,
        ImGuiWindowFlags.NoCollapse
    )

    override val modal = ModalMode.Modal()

    val onConfirm = PaperEventHandler("ConfirmationModalWindow-$title-OnConfirm")
    val onCancel = PaperEventHandler("ConfirmationModalWindow-$title-OnCancel")

    override fun drawContents() = immediateCompose {
        alignedText(Text(message, font), 0.5)

        newLine()

        alignedRow(0.5) {
            button(Text("mis_confirm", font)) {
                onConfirm.run()
                delete()
            }

            button(Text("mis_cancel", font)) {
                onCancel.run()
                delete()
            }
        }
    }
}



