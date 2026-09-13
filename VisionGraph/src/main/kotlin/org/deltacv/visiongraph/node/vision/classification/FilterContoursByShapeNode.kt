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

package org.deltacv.visiongraph.node.vision.classification

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.attribute.math.IntAttribute
import org.deltacv.visiongraph.attribute.misc.EnumAttribute
import org.deltacv.visiongraph.attribute.misc.ListAttribute
import org.deltacv.visiongraph.attribute.rebuildOnChange
import org.deltacv.visiongraph.attribute.vision.structs.PointsAttribute
import org.deltacv.visiongraph.codegen.CodeGen
import org.deltacv.visiongraph.codegen.CodeGenSession
import org.deltacv.visiongraph.codegen.GenValue
import org.deltacv.visiongraph.codegen.build.language.jvm.JavaTypes
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Imgproc
import org.deltacv.visiongraph.codegen.build.language.jvm.JvmOpenCv.Size
import org.deltacv.visiongraph.codegen.dsl.polyglot
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.NodeCategory
import org.deltacv.visiongraph.node.DrawNode
import org.deltacv.visiongraph.node.PaperNode
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder
import org.deltacv.visiongraph.util.Range2i

enum class Shape(val sides: Int?) {
    Circle(0), Triangle(3), Rectangle(4), Square(4), Polygon(null)
}

@PaperNode(
    name = "nod_groupcontours_byshape",
    category = NodeCategory.CLASSIFICATION,
    description = "des_groupcontours_byshape"
)
@CodecType
class FilterContoursByShapeNode : DrawNode<FilterContoursByShapeNode.Session>() {

    val input = ListAttribute(INPUT, "$[att_contours]", PointsAttribute)

    val shape = EnumAttribute(INPUT, "$[att_shape]", Shape.entries)
    val sides = IntAttribute(INPUT, "$[att_sides]")

    val accuracy = IntAttribute(INPUT, "$[att_accuracy]")

    val output = ListAttribute(OUTPUT, "$[att_filteredcontours]", PointsAttribute)

    private var previousSides = 0

    override fun onEnable() {
        + input.rebuildOnChange()

        + shape.rebuildOnChange()

        shape.onChange { refreshSidesField() }

        + sides.rebuildOnChange()

        sides.onChange {
            if (sides.readEditorValue().value < 3  && previousSides >= 3) {
                sides.value.set(0)
                shape.currentIndex.set(Shape.Circle.ordinal)
            } else if (sides.readEditorValue().value in 1..2) {
                sides.value.set(3)
                shape.currentIndex.set(Shape.Triangle.ordinal)
            } else {
                for ((i, shapeE) in Shape.entries.withIndex()) {
                    if (sides.readEditorValue().value == shapeE.sides) {
                        shape.currentIndex.set(i)
                        return@onChange
                    }
                }

                shape.currentIndex.set(Shape.Polygon.ordinal)
            }

            previousSides = sides.value.get()
        }

        + accuracy
        accuracy.sliderMode(Range2i(1, 100))

        + output.rebuildOnChange()

        refreshSidesField()
    }

    private var firstDraw = true

    override fun draw() {
        super.draw()

        if(firstDraw) {
            previousSides = sides.value.get()
            firstDraw = false
        }

        if(sides.readEditorValue().value < 0) sides.value.set(0)
    }

    override val generators = polyglot {
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                val inputContours = input.genValue(current)

                if(inputContours !is GenValue.List.Runtime<*>){
                    raise("")
                }

                val shapeValue = shape.genValue(current).value
                val sidesValue = sides.genValue(current)
                val accuracyValue = accuracy.genValue(current)

                val list = uniqueVariable("filtered${shapeValue.name}Contours", JavaTypes.ArrayList(JvmOpenCv.MatOfPoint).new())

                val contours2f = uniqueVariable("contours2f", JvmOpenCv.MatOfPoint2f.new())
                val approxPolyDp = uniqueVariable("approxPolyDp", JvmOpenCv.MatOfPoint2f.new())
                val approxPolyDp2f = uniqueVariable("approxPolyDp2f", JvmOpenCv.MatOfPoint2f.new())

                group {
                    private(approxPolyDp)
                    private(approxPolyDp2f)
                    private(contours2f)

                    private(list)
                }

                current.scope {
                    nameComment()

                    list("clear")

                    foreach(variable(JvmOpenCv.MatOfPoint, "contour"), inputContours.value.v) {
                        it("convertTo", contours2f, cvTypeValue("CV_32FC2"))

                        separate()

                        Imgproc("approxPolyDP", contours2f, approxPolyDp2f, ((double(100.0) - accuracyValue.v) / double(100.0)) * Imgproc.callValue("arcLength", DoubleType, contours2f, trueValue), trueValue)
                        approxPolyDp2f("convertTo", approxPolyDp, cvTypeValue("CV_32S"))

                        separate()

                        ifCondition(approxPolyDp.callValue("size", Size).propertyValue("height", IntType) equalsTo sidesValue.v) {
                            list("add", it)
                        }

                        separate()

                        contours2f("release")
                        approxPolyDp("release")
                        approxPolyDp2f("release")
                    }
                }

                session.output = GenValue.List.Runtime(list.resolved(), GenValue.Points.Runtime::class.resolved())

                session
            }
        }
    }

    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if(attrib == output) {
            return GenValue.List.Runtime.defer { current.sessionOf(this)?.output }
        }

        noValue(attrib)
    }

    private fun refreshSidesField() {
        sides.showAttributesCircles = shape.currentValue == Shape.Polygon
        sides.value.set(shape.currentValue.sides ?: if(sides.value.get() >= 5) sides.value.get() else 5)
    }

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.obj("input", input)
        encoder.obj("shape", shape)
        encoder.obj("sides", sides)
        encoder.obj("accuracy", accuracy)
        encoder.obj("output", output)
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)
        decoder.obj("input", input)
        decoder.obj("accuracy", accuracy)
        decoder.obj("sides", sides)
        decoder.obj("shape", shape)
        decoder.obj("output", output)
    }

    class Session : CodeGenSession {
        lateinit var output: GenValue.List.Runtime<GenValue.Points.Runtime>
    }

}



