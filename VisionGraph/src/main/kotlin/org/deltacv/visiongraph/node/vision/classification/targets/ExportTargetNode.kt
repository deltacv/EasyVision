/*
 * VisionGraph
 * Copyright (C) 2026 Sebastian Erives, deltacv
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.deltacv.visiongraph.node.vision.classification.targets

import org.deltacv.visiongraph.attribute.misc.StringAttribute
import org.deltacv.visiongraph.attribute.vision.structs.RectAttribute
import org.deltacv.visiongraph.attribute.vision.structs.RotatedRectAttribute
import org.deltacv.visiongraph.codegen.NoSession
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.dsl.jvm.jvmTargets
import org.deltacv.visiongraph.codegen.language.interpreted.CPythonLanguage
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@PaperNode(
    name = "nod_exportrect_target",
    category = NodeCategory.CLASSIFICATION,
    description = "des_exportrect_target"
)
@CodecType
class ExportTargetNode : DrawNode<NoSession>() {

    val inputTarget = RectAttribute(INPUT, "$[att_target]")
    val label = StringAttribute(INPUT, "$[att_label]")

    override fun onEnable() {
        + inputTarget
        + label
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current.jvmTargets {
                current.scope {
                    val rect = JvmOpenCv.toRectInst(inputTarget.genValue(current), current).value.v
                    addRectTarget(string(label.genValue(current).value.v), rect.castTo(JvmOpenCv.Rect, force = current.isForPreviz))
                }
            }

            NoSession
        }

        generatorFor(CPythonLanguage) {
            warn("err_targetsnot_supported")
            NoSession
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("inputTarget", inputTarget)
        encoder.obj("label", label)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputTarget", inputTarget)
        decoder.obj("label", label)
    }

}


@PaperNode(
    name = "nod_exportrot_recttarget",
    category = NodeCategory.CLASSIFICATION,
    description = "des_exportrot_recttarget"
)
class ExportRotTarget : DrawNode<NoSession>() {

    val inputTarget = RotatedRectAttribute(INPUT, "$[att_target]")
    val label = StringAttribute(INPUT, "$[att_label]")

    override fun onEnable() {
        + inputTarget
        + label
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current.jvmTargets {
                current.scope {
                    val rect = inputTarget.genValue(current).value.v
                    addRotRectTarget(string(label.genValue(current).value.v), rect.castTo(JvmOpenCv.RotatedRect, force = current.isForPreviz))
                }
            }

            NoSession
        }

        generatorFor(CPythonLanguage) {
            warn("err_targetsnot_supported")
            NoSession
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("inputTarget", inputTarget)
        encoder.obj("label", label)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputTarget", inputTarget)
        decoder.obj("label", label)
    }

}



