/*
 * PaperVision
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

package org.deltacv.visiongraph.gui.style

import imgui.ImColor
import imgui.ImGui
import imgui.ImVec4
import kotlin.math.roundToInt

fun rgbaColor(r: Int, g: Int, b: Int, a: Int) = ImColor.rgba(
    r.toFloat() / 255f,
    g.toFloat() / 255f,
    b.toFloat() / 255f,
    a.toFloat() / 255f
)

fun hexColor(hex: String, alpha: Float = 1f): Int {
    val col = ImColor.rgb(hex)
    val a = (alpha * 255).toInt()
    return (col and 0x00FFFFFF) or (a shl 24)
}

fun ImVec4.opacity(opacity: Float): ImVec4 {
    val clampedOpacity = opacity.coerceIn(0f, 1f)
    this.w = clampedOpacity
    return this
}

fun Int.opacity(opacity: Float): Int {
    // Ensure the opacity is within 0 and 1
    val clampedOpacity = opacity.coerceIn(0f, 1f)

    // Extract the color components assuming the format is RGBA
    val r = (this shr 24) and 0xFF
    val g = (this shr 16) and 0xFF
    val b = (this shr 8) and 0xFF
    val a = this and 0xFF

    // Apply the opacity to the alpha component
    val opaqueA = (a * clampedOpacity).roundToInt().coerceIn(0, 255)

    // Recombine the components back into a color integer
    return rgbaColor(r, g, b, opaqueA)
}

fun Int.darken(factor: Float): Int {
    // Ensure the factor is within 0 and 1
    val clampedFactor = factor.coerceIn(0f, 1f)

    // Extract the color components assuming the format is RGBA
    val r = (this shr 24) and 0xFF
    val g = (this shr 16) and 0xFF
    val b = (this shr 8) and 0xFF
    val a = this and 0xFF

    // Apply the darkening factor to each RGB component
    val darkenedR = (r * clampedFactor).roundToInt().coerceIn(0, 255)
    val darkenedG = (g * clampedFactor).roundToInt().coerceIn(0, 255)
    val darkenedB = (b * clampedFactor).roundToInt().coerceIn(0, 255)

    // Recombine the components back into a color integer
    return rgbaColor(darkenedR, darkenedG, darkenedB, a)
}



