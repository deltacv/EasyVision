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

package org.deltacv.visiongraph.util

interface Range<T: Number> {
    val min: T
    val max: T

    fun clip(x: T): T
}

data class Range2i(override val min: Int, override val max: Int): Range<Int> {
    companion object {
        val DEFAULT = Range2i(Int.MIN_VALUE, Int.MAX_VALUE)
        val DEFAULT_POSITIVE = Range2i(0, Int.MAX_VALUE)
    }

    override fun clip(x: Int) = if(x < min) min else if(x > max) max else x
}

data class Range2d(override val min: Double, override val max: Double) : Range<Double> {
    companion object {
        val DEFAULT = Range2d(Double.MIN_VALUE, Double.MAX_VALUE)
        val DEFAULT_POSITIVE = Range2d(0.0, Double.MAX_VALUE)
    }

    override fun clip(x: Double) = if(x < min) min else if(x > max) max else x
}



