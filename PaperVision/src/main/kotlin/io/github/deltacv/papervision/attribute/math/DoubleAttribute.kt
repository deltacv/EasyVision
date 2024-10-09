package io.github.deltacv.papervision.attribute.math

import imgui.ImGui
import imgui.type.ImDouble
import imgui.type.ImFloat
import io.github.deltacv.papervision.PaperVision
import io.github.deltacv.papervision.attribute.AttributeMode
import io.github.deltacv.papervision.attribute.AttributeType
import io.github.deltacv.papervision.attribute.TypedAttribute
import io.github.deltacv.papervision.codegen.CodeGen
import io.github.deltacv.papervision.codegen.GenValue
import io.github.deltacv.papervision.gui.FontAwesomeIcons
import io.github.deltacv.papervision.util.Range2d

class DoubleAttribute(
    override val mode: AttributeMode,
    override var variableName: String? = null
) : TypedAttribute(Companion) {

    companion object: AttributeType {
        override val icon = FontAwesomeIcons.SquareRootAlt

        override fun new(mode: AttributeMode, variableName: String) = DoubleAttribute(mode, variableName)
    }

    val value = ImDouble()
    private val sliderValue = ImFloat()

    private val sliderId by PaperVision.miscIds.nextId()

    private var range: Range2d? = null

    override fun drawAttribute() {
        super.drawAttribute()
        checkChange()

        if(!hasLink && mode == AttributeMode.INPUT) {
            sameLineIfNeeded()

            ImGui.pushItemWidth(110.0f)

            if(range == null) {
                ImGui.inputDouble("", value)
            } else {
                ImGui.sliderFloat("###$sliderId", sliderValue.data, range!!.min.toFloat(), range!!.max.toFloat())
                value.set(sliderValue.get().toDouble())
            }

            ImGui.popItemWidth()
        }
    }

    fun sliderMode(range: Range2d) {
        this.range = range
    }

    fun normalMode() {
        this.range = null
    }

    override fun thisGet() = value.get()

    override fun value(current: CodeGen.Current) = value(
        current, "a Double", GenValue.Double(value.get())
    ) { it is GenValue.Double }

}