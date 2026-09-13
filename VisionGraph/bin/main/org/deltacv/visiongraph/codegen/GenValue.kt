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

package org.deltacv.visiongraph.codegen

import org.deltacv.visiongraph.attribute.Attribute
import org.deltacv.visiongraph.codegen.build.Value
import org.deltacv.visiongraph.codegen.resolve.Resolvable
import org.deltacv.visiongraph.codegen.resolve.from
import org.deltacv.visiongraph.codegen.resolve.resolved
import org.deltacv.visiongraph.node.vision.ColorSpace
import kotlin.reflect.KClass

sealed class GenValue {

    open class Mat(val value: Resolvable<Value>, val color: Resolvable<ColorSpace>, val isBinary: Boolean = Boolean.FALSE) : GenValue() {
        open fun requireBinary(attribute: Attribute) {
            isBinary.value.letOrDefer {
                attribute.raiseAssert(it, "Mat is not binary as required, this causes runtime issues.")
            }
        }

        open fun requireNonBinary(attribute: Attribute) {
            isBinary.value.letOrDefer {
                attribute.raiseAssert(!it, "Mat is binary where it shouldn't be, this causes runtime issues.")
            }
        }

        companion object {
            fun defer(genValueResolver: () -> Mat?) = Mat(
                Resolvable.from { genValueResolver()?.value },
                Resolvable.from { genValueResolver()?.color },
                Boolean.defer { genValueResolver()?.isBinary }
            )
        }
    }

    sealed class KeyPoint : GenValue() {
        data class Actual(
            val x: Resolvable<Double>,
            val y: Resolvable<Double>,
            val size: Resolvable<Double>
        ) : GenValue.KeyPoint()

        data class Runtime(val value: Resolvable<Value>) : GenValue.KeyPoint() {
            companion object {
                fun defer(genValueResolver: () -> Runtime?) = Runtime(
                    Resolvable.from { genValueResolver()?.value }
                )
            }
        }
    }

    data class Point(val x: Resolvable<Double>, val y: Resolvable<Double>) : GenValue()

    sealed class Points : GenValue() {
        data class Actual(val points: Resolvable<Array<Point>>) : Points()
        data class Runtime(val value: Resolvable<Value>) : Points() {
            companion object {
                fun defer(genValueResolver: () -> Runtime?) = Runtime(
                    Resolvable.from { genValueResolver()?.value }
                )
            }
        }
    }

    sealed class Circle : GenValue() {
        data class Actual(val x: Double, val y: Double, val r: Double) : Circle()

        data class Runtime(val value: Resolvable<Value>) : Circle() {
            companion object {
                fun defer(genValueResolver: () -> Runtime?) = Runtime(
                    Resolvable.from { genValueResolver()?.value }
                )
            }
        }
    }

    sealed class RotatedRect : GenValue() {
        data class Components(
            val x: Double, val y: Double,
            val w: Double, val h: Double,
            val angle: Double
        ) : RotatedRect()

        data class Inst(val value: Resolvable<Value>) : RotatedRect() {
            companion object {
                fun defer(genValueResolver: () -> Inst?) = Inst(
                    Resolvable.from { genValueResolver()?.value }
                )
            }
        }
    }

    sealed class Rect : GenValue() {
        data class Components(val position: Vec2, val size: Vec2) : Rect() {
            companion object {
                fun wrap(x: Int, y: Int, w: Int, h: Int, languageHolder: CodeGen.LanguageHolder) =
                    Components(Vec2.wrap(x, y, languageHolder), Vec2.wrap(w, h, languageHolder))
            }
        }

        data class Inst(val value: Resolvable<Value>) : Rect() {
            companion object {
                fun defer(genValueResolver: () -> Inst?) = Inst(
                    Resolvable.from { genValueResolver()?.value }
                )
            }
        }
    }

    data class Enum<E : kotlin.Enum<E>>(val value: E) : GenValue()

    sealed class Number : GenValue() {
        abstract val isActual: Boolean
        abstract fun toRuntime(langHolder: CodeGen.LanguageHolder): Number

        abstract fun value(langHolder: CodeGen.LanguageHolder): Value

        abstract fun toInt(langHolder: CodeGen.LanguageHolder): Int
        abstract fun toDouble(langHolder: CodeGen.LanguageHolder): Double
        abstract fun toFloat(langHolder: CodeGen.LanguageHolder): Float
    }

    sealed class Int : Number() {
        override fun value(langHolder: CodeGen.LanguageHolder): Value = langHolder.language {
            int(this@Int).v
        }

        data class Actual(val value: Resolvable<kotlin.Int>) : Int() {
            override val isActual = Boolean.TRUE

            override fun toRuntime(langHolder: CodeGen.LanguageHolder) = Runtime(value.map { langHolder.language.int(it) }, Boolean.TRUE)

            override fun toInt(langHolder: CodeGen.LanguageHolder) = this
            override fun toDouble(langHolder: CodeGen.LanguageHolder) = Double.Actual(value.map { it.toDouble() })
            override fun toFloat(langHolder: CodeGen.LanguageHolder) = Float.Actual(value.map { it.toFloat() })

            companion object {
                fun defer(genValueResolver: () -> Actual?) = Actual(
                    Resolvable.from { genValueResolver()?.value }
                )
            }
        }

        data class Runtime(
            val value: Resolvable<Value>,
            override val isActual: Boolean = Boolean.FALSE
        ) : Int() {
            override fun toRuntime(langHolder: CodeGen.LanguageHolder) = this

            override fun toInt(langHolder: CodeGen.LanguageHolder) = this

            override fun toDouble(langHolder: CodeGen.LanguageHolder) = Double.Runtime(
                value.map { langHolder.language.double(it) }
            )

            override fun toFloat(langHolder: CodeGen.LanguageHolder) = Float.Runtime(
                value.map { langHolder.language.float(it) }
            )

            companion object {
                fun defer(genValueResolver: () -> Runtime?) = Runtime(
                    Resolvable.from { genValueResolver()?.value }
                )
            }
        }

        abstract override fun toRuntime(langHolder: CodeGen.LanguageHolder): Runtime

        companion object {
            val ZERO = Actual(0.resolved())
        }
    }

    sealed class Float : Number() {
        override fun value(langHolder: CodeGen.LanguageHolder): Value = langHolder.language {
            float(this@Float).v
        }

        data class Actual(val value: Resolvable<kotlin.Float>) : Float() {
            override val isActual = Boolean.TRUE

            override fun toRuntime(langHolder: CodeGen.LanguageHolder) = Runtime(value.map { langHolder.language.float(it) }, Boolean.TRUE)

            override fun toInt(langHolder: CodeGen.LanguageHolder) = Int.Actual(value.map { it.toInt() })
            override fun toDouble(langHolder: CodeGen.LanguageHolder) = Double.Actual(value.map { it.toDouble() })
            override fun toFloat(langHolder: CodeGen.LanguageHolder) = this

            companion object {
                fun defer(genValueResolver: () -> Actual?) = Actual(
                    Resolvable.from { genValueResolver()?.value }
                )
            }
        }

        data class Runtime(
            val value: Resolvable<Value>,
            override val isActual: Boolean = Boolean.FALSE
        ) : Float() {
            override fun toRuntime(langHolder: CodeGen.LanguageHolder) = this

            override fun toInt(langHolder: CodeGen.LanguageHolder) = Int.Runtime(
                value.map { langHolder.language.int(it) }
            )
            override fun toDouble(langHolder: CodeGen.LanguageHolder) = Double.Runtime(
                value.map { langHolder.language.double(it) }
            )
            override fun toFloat(langHolder: CodeGen.LanguageHolder) = this

            companion object {
                fun defer(genValueResolver: () -> Runtime?) = Runtime(
                    Resolvable.from { genValueResolver()?.value }
                )
            }
        }

        abstract override fun toRuntime(langHolder: CodeGen.LanguageHolder): Runtime

        companion object {
            val ZERO = Actual(Resolvable.Now(0.0f))
        }
    }

    sealed class Double : Number() {
        override fun value(langHolder: CodeGen.LanguageHolder) = langHolder.language {
            double(this@Double).v
        }

        data class Actual(val value: Resolvable<kotlin.Double>) : Double() {
            override val isActual = Boolean.TRUE

            companion object {
                fun defer(genValueResolver: () -> Actual?) = Actual(
                    Resolvable.from { genValueResolver()?.value }
                )
            }

            override fun toRuntime(langHolder: CodeGen.LanguageHolder) = Runtime(value.map { langHolder.language.double(it) }, Boolean.TRUE)

            override fun toInt(langHolder: CodeGen.LanguageHolder) = Int.Actual(value.map { it.toInt() })
            override fun toDouble(langHolder: CodeGen.LanguageHolder) = this
            override fun toFloat(langHolder: CodeGen.LanguageHolder) = Float.Actual(value.map { it.toFloat() })
        }

        data class Runtime(
            val value: Resolvable<Value>,
            override val isActual: Boolean = Boolean.FALSE
        ) : Double() {
            companion object {
                fun defer(genValueResolver: () -> Runtime?) = Runtime(
                    Resolvable.from { genValueResolver()?.value }
                )
            }

            override fun toRuntime(langHolder: CodeGen.LanguageHolder) = this

            override fun toInt(langHolder: CodeGen.LanguageHolder) = Int.Runtime(
                value.map { langHolder.language.int(it) }
            )
            override fun toDouble(langHolder: CodeGen.LanguageHolder) = this
            override fun toFloat(langHolder: CodeGen.LanguageHolder) = Float.Runtime(
                value.map { langHolder.language.float(it) }
            )
        }

        abstract override fun toRuntime(langHolder: CodeGen.LanguageHolder): Runtime

        companion object {
            val ZERO = Actual(0.0.resolved())

            fun defer(genValueResolver: () -> Actual?) = Actual(
                Resolvable.from { genValueResolver()?.value }
            )
        }
    }

    data class String(val value: Resolvable<kotlin.String>) : GenValue()

    sealed class LineParameters : GenValue() {
        companion object {
            fun wrap(color: Scalar, thickness: Int, langHolder: CodeGen.LanguageHolder): LineParameters {
                return when (color) {
                    is Scalar.Components -> {
                        Components(color, thickness)
                    }
                    is Scalar.Inst -> {
                        Runtime(color, thickness.toRuntime(langHolder))
                    }
                }
            }
        }

        data class Components(val color: Scalar.Components, val thickness: Int) : LineParameters()

        data class Runtime(val color: Scalar.Inst, val thicknessValue: Int.Runtime) : LineParameters() {
            companion object {
                fun defer(genValueResolver: () -> Runtime?) = Runtime(
                    Scalar.Inst.defer { genValueResolver()?.color },
                    Int.Runtime.defer { genValueResolver()?.thicknessValue }
                )
            }
        }
    }

    sealed class Scalar(actual: Actual<Double>? = null, runtime: Runtime<Double>? = null) : List.Either<Double>(actual, runtime) {
        class Components(
            val a: Double,
            val b: Double,
            val c: Double,
            val d: Double
        ) : Scalar(actual = Actual(listOf(a, b, c, d)))

        class Inst(val value: Resolvable<Value>) : Scalar(runtime = Runtime(value)) {
            companion object {
                fun defer(genValueResolver: () -> Inst?) = Inst(
                    Resolvable.from { genValueResolver()?.value }
                )
            }
        }
    }

    sealed class Vec2(val x: Int, val y: Int) : GenValue() {
        companion object {
            fun wrap(x: Int, y: Int, languageHolder: CodeGen.LanguageHolder): Vec2 {
                return if (x is Int.Actual && y is Int.Actual) {
                    Actual(x, y)
                } else {
                    Runtime(x.toRuntime(languageHolder), y.toRuntime(languageHolder))
                }
            }
        }

        data class Actual(val actualX: Int.Actual, val actualY: Int.Actual) : Vec2(actualX, actualY) {
            companion object {
                fun defer(genValueResolver: () -> Actual?) = Actual(
                    Int.Actual.defer { genValueResolver()?.actualX },
                    Int.Actual.defer { genValueResolver()?.actualY }
                )
            }

            override fun toRuntime(langHolder: CodeGen.LanguageHolder) = Runtime(
                x.toRuntime(langHolder),
                y.toRuntime(langHolder)
            )
        }

        data class Runtime(val runtimeX: Int.Runtime, val runtimeY: Int.Runtime) : Vec2(runtimeX, runtimeY) {
            companion object {
                fun defer(genValueResolver: () -> Runtime?) = Runtime(
                    Int.Runtime.defer { genValueResolver()?.runtimeX },
                    Int.Runtime.defer { genValueResolver()?.runtimeY }
                )
            }

            override fun toRuntime(langHolder: CodeGen.LanguageHolder) = this
        }

        abstract fun toRuntime(langHolder: CodeGen.LanguageHolder): Runtime
    }

    data class Range(val min: Double, val max: Double) : GenValue() {
        companion object {
            val ZERO = Range(Double.ZERO, Double.ZERO)
        }
    }

    data class ScalarRange(val a: Range, val b: Range, val c: Range, val d: Range) :
        List.Actual<Range>(listOf(a, b, c, d)) {
        companion object {
            val ZERO = ScalarRange(Range.ZERO, Range.ZERO, Range.ZERO, Range.ZERO)
        }
    }

    open class Boolean(val value: Resolvable<kotlin.Boolean>) : GenValue() {
        object TRUE : Boolean(Resolvable.Now(true))
        object FALSE : Boolean(Resolvable.Now(false))

        companion object {
            fun defer(genValueResolver: () -> Boolean?) = Boolean(
                Resolvable.from { genValueResolver()?.value }
            )
        }

        fun not() = Boolean(value.map { !it })
    }

    sealed class List<E: GenValue> : GenValue() {
        companion object {
            inline fun <reified T : GenValue> Runtime(value: Resolvable<Value>): Runtime<T> =
                Runtime(value, Resolvable.Now(T::class))
        }

        fun <R> match(
            ifActual: (Actual<E>) -> R,
            ifRuntime: (Runtime<E>) -> R,
        ): R = when (this) {
            is Actual -> ifActual(this)
            is Runtime -> ifRuntime(this)
            is Either -> if (isActual) ifActual(actual!!) else ifRuntime(runtime!!)
        }

        fun toActualOrNull(): Actual<E>? {
            return when (this) {
                is Actual -> this
                is Either -> if (isActual) actual else null
                is Runtime -> null
            }
        }

        open class Either<E: GenValue>(val actual: Actual<E>?, val runtime: Runtime<E>?) : List<E>() {
            init {
                if ((actual == null && runtime == null) || (actual != null && runtime != null)) {
                    throw IllegalArgumentException("Either actual or runtime must be provided, not both at the same time")
                }
            }

            val isActual = actual != null
            val isRuntime = runtime != null
        }

        open class Actual<E : GenValue>(val elements: kotlin.collections.List<E>) : List<E>()

        data class Runtime<E : GenValue>(val value: Resolvable<Value>, val typeClass: Resolvable<KClass<E>>) : List<E>() {
            companion object {
                fun <E : GenValue> defer(
                    genValueResolver: () -> Runtime<E>?
                ): Runtime<E> = Runtime(
                    Resolvable.from { genValueResolver()?.value },
                    Resolvable.from { genValueResolver()?.typeClass }
                )
            }
        }
    }

    object None : GenValue()

}



