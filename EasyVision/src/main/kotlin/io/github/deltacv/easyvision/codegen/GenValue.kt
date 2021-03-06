package io.github.deltacv.easyvision.codegen

import io.github.deltacv.easyvision.attribute.Attribute
import io.github.deltacv.easyvision.codegen.build.ConValue
import io.github.deltacv.easyvision.codegen.build.Value
import io.github.deltacv.easyvision.node.vision.Colors
import kotlin.reflect.KClass

sealed class GenValue {

    data class Mat(val value: Value, val color: Colors, val isBinary: kotlin.Boolean = false) : GenValue() {
        fun requireBinary(attribute: Attribute) {
            attribute.warnAssert(
                isBinary,
                "Input Mat is not binary as required, this might cause runtime issues."
            )
        }

        fun requireNonBinary(attribute: Attribute) {
            attribute.warnAssert(
                !isBinary,
                "Input Mat is binary where it shouldn't be, this might cause runtime issues."
            )
        }
    }

    data class Point(val x: Double, val y: Double) : GenValue()

    sealed class GPoints : GenValue() {
        data class Points(val points: Array<Point>) : GPoints()
        data class RuntimePoints(val value: Value) : GPoints()
    }

    sealed class GRects : GenValue() {
        data class Rect(val x: Double, val y: Double, val w: Double, val h: Double) : GRects()
        data class RotatedRect(val x: Double, val y: Double,
                               val w: Double, val h: Double,
                               val angle: Double) : GRects()

        data class RuntimeRect(val value: Value) : GRects()
        data class RuntimeRotatedRect(val value: Value) : GRects()
    }

    data class Enum<E: kotlin.Enum<E>>(val value: E, val clazz: Class<*>) : GenValue()

    data class Int(val value: kotlin.Int) : GenValue()
    data class Float(val value: kotlin.Float) : GenValue()
    data class Double(val value: kotlin.Double) : GenValue()

    data class Scalar(
        val a: kotlin.Double,
        val b: kotlin.Double,
        val c: kotlin.Double,
        val d: kotlin.Double
    ) : GenValue() {
        companion object {
            val ZERO = Scalar(0.0, 0.0, 0.0, 0.0)
        }
    }

    data class Range(val min: kotlin.Double, val max: kotlin.Double) : GenValue(){
        companion object {
            val ZERO = Range(0.0, 0.0)
        }
    }

    data class ScalarRange(val a: Range, val b: Range, val c: Range, val d: Range) : GenValue() {
        companion object {
            val ZERO = ScalarRange(Range.ZERO, Range.ZERO, Range.ZERO, Range.ZERO)
        }
    }

    sealed class Boolean(val value: kotlin.Boolean) : GenValue() {
        object True : Boolean(true)
        object False : Boolean(false)
    }
    
    sealed class GLists : GenValue() {
        open class ListOf<T : GenValue>(val elements: Array<T>) : GLists()
        data class List(val elems: Array<GenValue>) : ListOf<GenValue>(elems)

        data class RuntimeListOf<T : GenValue>(val value: Value, val typeClass: KClass<T>) : GLists()
    }

    object None : GenValue()

}