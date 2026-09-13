package org.deltacv.visiongraph.node.display

import org.deltacv.visiongraph.attribute.EmptyInputAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.NoSession
import org.deltacv.visiongraph.gui.display.ImageDisplay
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v1.data.SerializeIgnore
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@PaperNode(
    name = "nod_previewdisplay",
    category = NodeCategory.HIGH_LEVEL_CV,
    showInList = false,
    instantiable = false
)
class ImageDisplayNode(
    val imageDisplay: ImageDisplay
) : DrawNode<NoSession>(joinActionStack = false) {

    val input = object: EmptyInputAttribute(this) {
        override fun drawAttribute() {
            imageDisplay.draw()
        }
    }

    override fun onEnable() {
        + input
    }

    override fun genCode(input: Unit, current: CodeGen.Current) = NoSession

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.ignore()
    }

}