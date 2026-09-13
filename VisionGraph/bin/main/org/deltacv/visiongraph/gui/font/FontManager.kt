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

package org.deltacv.visiongraph.gui.font

import imgui.ImFont
import imgui.ImFontConfig
import imgui.ImGui
import org.deltacv.visiongraph.id.container.IdContext
import org.deltacv.visiongraph.id.StatedIdElementBase
import org.deltacv.visiongraph.util.loggerForThis

class FontManager {

    private val logger by loggerForThis()

    val fonts = mutableMapOf<String, Font>()

    fun makeFont(
        name: String,
        ttfPath: String,
        fontConfig: ImFontConfig,
        glyphRanges: ShortArray? = null
    ): Font {
        val hashName = "mai-$ttfPath-${fontConfig.sizePixels}"

        if(fonts.containsKey(hashName)) {
            return fonts[hashName]!!
        }

        val inputStream = FontManager::class.java.getResourceAsStream(ttfPath)
            ?: throw IllegalArgumentException("Font file $ttfPath not found in resources")

        inputStream.use {
            val imguiFont = if (glyphRanges != null) {
                ImGui.getIO().fonts.addFontFromMemoryTTF(
                    it.readAllBytes(),
                    fontConfig.sizePixels,
                    fontConfig,
                    glyphRanges
                )
            } else {
                ImGui.getIO().fonts.addFontFromMemoryTTF(it.readAllBytes(), fontConfig.sizePixels, fontConfig)
            }

            val font = Font(
                imguiFont,
                fontConfig,
                name,
                ttfPath,
                fontConfig.sizePixels
            )
            fonts[hashName] = font

            font.enable()

            logger.info("Loaded font '$name' (#${font.id}) from '$ttfPath'")

            return font
        }
    }

    fun makeDefaultFont(size: Int): Font {
        val key = "def-$size"
        if(fonts.containsKey(key)) {
            return fonts[key]!!
        }

        val fontConfig = ImFontConfig()
        fontConfig.sizePixels = size.toFloat()
        fontConfig.oversampleH = 1
        fontConfig.oversampleV = 1
        fontConfig.pixelSnapH = false

        val font = Font(ImGui.getIO().fonts.addFontDefault(fontConfig), fontConfig, "default-$size", null, size.toFloat())
        fonts[key] = font

        font.enable()

        logger.info("Loaded font default-$size (#${font.id})")

        return font
    }

}

@Suppress("unused")
class Font internal constructor(
    val imfont: ImFont,
    private val fontConfig: ImFontConfig,
    val name: String,
    private val ttfPath: String?,
    val size: Float
) : StatedIdElementBase<Font>() {
    override val idContainer get() = IdContext.local.peekNonNull<Font>()
    override val requestedId = name.hashCode()

    companion object {
        fun find(name: String) =
            IdContext.local.peekNonNull<Font>()[name] ?: throw IllegalArgumentException("Font '$name' not found")

        fun findLazy(name: String) = lazy { find(name) }
    }

    fun push(size: Float = this.size) {
        ImGui.pushFont(imfont, size)
    }

    fun pop() {
        if(ImGui.getFont().ptr == imfont.ptr) {
            ImGui.popFont()
        } else {
            throw IllegalStateException("Attempted to pop font '$name' but it was not on top of the stack")
        }
    }
}

fun defaultFontConfig(size: Float) = ImFontConfig().apply {
    // Use conservative oversampling to avoid stb_truetype packing/assert issues observed on some systems.
    // Higher oversample values increase atlas size and can trigger packing edge cases in stb.
    oversampleH = 1
    oversampleV = 1
    pixelSnapH = false
    sizePixels = size
}