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

package org.deltacv.visiongraph.platform.lwjgl.texture

import org.deltacv.visiongraph.platform.ColorSpace
import org.deltacv.visiongraph.platform.PlatformTexture
import org.lwjgl.opengl.GL12.*
import org.lwjgl.stb.STBImage.stbi_failure_reason
import org.lwjgl.stb.STBImage.stbi_image_free
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.use

data class OpenGLTexture(
    override val textureId: Long,
    override val width: Int,
    override val height: Int
) : PlatformTexture() {

    override fun set(bytes: ByteArray, colorSpace: ColorSpace) {
        val expectedSize = width * height * colorSpace.channels
        if (bytes.size < expectedSize) {
            throw IllegalArgumentException("Buffer too small (expected >= $expectedSize, got ${bytes.size}, channels: ${colorSpace.channels}, width: $width, height: $height)")
        }

        // Only copy the exact pixel data needed; the pool buffer may be larger (power-of-two).
        val buffer = MemoryUtil.memAlloc(expectedSize)
        try {
            buffer.put(bytes, 0, expectedSize).flip()
            set(buffer, colorSpace)
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    override fun set(bytes: ByteBuffer, colorSpace: ColorSpace) {
        val expectedSize = width * height * colorSpace.channels
        if (expectedSize != bytes.remaining()) {
            throw IllegalArgumentException("Buffer size does not match resolution (expected $expectedSize, got ${bytes.remaining()}, channels: ${colorSpace.channels}, width: $width, height: $height)")
        }

        glBindTexture(GL_TEXTURE_2D, textureId.toInt())

        val format = when (colorSpace) {
            ColorSpace.RGB -> GL_RGB
            ColorSpace.RGBA -> GL_RGBA
            ColorSpace.BGR -> GL_BGR
            ColorSpace.BGRA -> GL_BGRA
        }

        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, format, GL_UNSIGNED_BYTE, bytes)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    private fun loadJpeg(bytes: ByteBuffer, callback: (ByteBuffer) -> Unit) {
        MemoryStack.stackPush().use {
            val comp = it.mallocInt(1)
            val w = it.mallocInt(1)
            val h = it.mallocInt(1)
            val img = stbi_load_from_memory(bytes, w, h, comp, 3)
                ?: throw RuntimeException("Failed to load image due to ${stbi_failure_reason()}")

            try {
                callback(img)
            } finally {
                stbi_image_free(img)
            }
        }
    }

    override fun setJpeg(bytes: ByteArray) {
        // bytes may be a pool buffer larger than the actual JPEG data; only send the
        // meaningful prefix to stbi (stbi still finds the EOI marker correctly,
        // but trimming avoids uploading megabytes of zero-padding).
        val buffer = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()
        try {
            loadJpeg(buffer) { set(it, ColorSpace.RGB) }
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    override fun setJpeg(bytes: ByteBuffer) {
        loadJpeg(bytes) { set(it, ColorSpace.RGB) }
    }

    override fun delete() {
        glDeleteTextures(textureId.toInt())
    }
}



