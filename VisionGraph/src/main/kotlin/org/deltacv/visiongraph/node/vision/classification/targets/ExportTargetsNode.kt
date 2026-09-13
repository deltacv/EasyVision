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

import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.misc.StringAttribute
import org.deltacv.visiongraph.attribute.vision.structs.RectAttribute
import org.deltacv.visiongraph.attribute.vision.structs.RotatedRectAttribute
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.NoSession
import org.deltacv.visiongraph.codegen.build.AccessorVariable
import org.deltacv.visiongraph.codegen.build.DeclarableVariable
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
    name = "nod_exportrect_targets",
    category = NodeCategory.CLASSIFICATION,
    description = "des_exportrect_targets"
)
@CodecType
class ExportTargetsNode : DrawNode<NoSession>() {

    val inputTargets = ListAttribute(INPUT, "$[att_targets]", RectAttribute)
    val label = StringAttribute(INPUT, "$[att_label]")

    override fun onEnable() {
        + inputTargets
        + label
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val targetsValue = inputTargets.genValue(current)

                if(targetsValue !is GenValue.List.Runtime<*>) {
                    raise("") // TODO: Handle non-runtime lists
                }

                val labelValue = label.genValue(current).value

                labelValue.letOrDefer {
                    if(it.isEmpty()) {
                        label.raise("err_emptylabel")
                    }
                }

                current.jvmTargets {
                    current.scope {
                        forLoop(AccessorVariable(IntType, "i"), 0.v, targetsValue.value.v.callValue("size", IntType), 1.v) {
                            val rect = targetsValue.value.v.callValue("get", JvmOpenCv.Rect, it)
                            addRectTarget(string("${labelValue}_").plus(it), rect.castTo(JvmOpenCv.Rect, force = current.isForPreviz))
                        }
                    }
                }

                NoSession
            }
        }

        generatorFor(CPythonLanguage) {
            warn("err_targetsnot_supported")
            NoSession
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("inputTargets", inputTargets)
        encoder.obj("label", label)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputTargets", inputTargets)
        decoder.obj("label", label)
    }

}


@PaperNode(
    name = "nod_exportrot_recttargets",
    category = NodeCategory.CLASSIFICATION,
    description = "des_exportrot_recttargets"
)
class ExportRotTargetsNode : DrawNode<NoSession>() {

    val inputTargets = ListAttribute(INPUT, "$[att_targets]", RotatedRectAttribute)
    val label = StringAttribute(INPUT, "$[att_label]")

    override fun onEnable() {
        + inputTargets
        + label
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val targetsValue = inputTargets.genValue(current)

                if(targetsValue !is GenValue.List.Runtime<*>) {
                    raise("") // TODO: Handle non-runtime lists
                }

                val labelValue = label.genValue(current).value
                labelValue.letOrDefer {
                    if(it.isEmpty()) {
                        label.raise("err_emptylabel")
                    }
                }

                current.jvmTargets {
                    current.scope {
                        forLoop(DeclarableVariable(IntType, "i"), 0.v, targetsValue.value.v.callValue("size", IntType), 1.v) {
                            val rect = targetsValue.value.v.callValue("get", JvmOpenCv.RotatedRect, it)
                            addRotRectTarget(string("${labelValue}_").plus(it), rect.castTo(JvmOpenCv.RotatedRect, force = current.isForPreviz))
                        }
                    }
                }

                NoSession
            }
        }

        generatorFor(CPythonLanguage) {
            warn("err_targetsnot_supported")
            NoSession
        }
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("inputTargets", inputTargets)
        encoder.obj("label", label)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("inputTargets", inputTargets)
        decoder.obj("label", label)
    }

}



