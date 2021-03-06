package io.github.deltacv.easyvision.gui.util

import imgui.ImGui
import imgui.ImVec2
import io.github.deltacv.easyvision.id.DrawableIdElementBase
import io.github.deltacv.easyvision.id.IdElementContainer
import io.github.deltacv.easyvision.util.ElapsedTime
import io.github.deltacv.mai18n.tr

open class Popup(
    val text: String,
    val position: ImVec2,
    val timeSecs: Double,
    val label: String? = null,
    override val requestedId: Int? = null,
    override val idElementContainer: IdElementContainer<Popup> = popups
) : DrawableIdElementBase<Popup>() {

    private val timer = ElapsedTime()

    override fun onEnable() {
        timer.reset()
    }

    override fun draw() {
        ImGui.setNextWindowPos(position.x, position.y)

        ImGui.beginTooltip()
            drawContents()
        ImGui.endTooltip()

        if(timer.seconds > timeSecs)
            delete()
    }

    open fun drawContents() {
        ImGui.text(tr(text))
    }

    companion object {
        val WARN = 0
        val BUILD_ERR = 0

        fun warning(text: String, secsPerCharacter: Double = 0.16) {
            Popup(text, ImGui.getMousePos(), text.length * secsPerCharacter, requestedId = WARN).enable()
        }

        val popups = IdElementContainer<Popup>().apply {
            reserveId(WARN)
        }
    }

}