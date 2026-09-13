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
import org.deltacv.visiongraph.platform.PlatformTextureFactory
import org.deltacv.visiongraph.platform.lwjgl.util.ImageData
import org.deltacv.visiongraph.platform.lwjgl.util.loadImageFromResource
import org.lwjgl.opengl.GL12.*
import org.lwjgl.stb.STBImage.stbi_failure_reason
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.use

object OpenGLTextureFactory : PlatformTextureFactory {

    override fun create(width: Int, height: Int, bytes: ByteArray, colorSpace: ColorSpace): OpenGLTexture {
        val expectedSize = width * height * colorSpace.channels
        // Only copy the exact pixel data needed; pool buffers may be larger (power-of-two).
        val buffer = MemoryUtil.memAlloc(expectedSize)
        buffer.put(bytes, 0, expectedSize)
        buffer.flip()

        val texture = try {
            create(width, height, buffer, colorSpace)
        } finally {
            MemoryUtil.memFree(buffer)
        }

        return texture
    }

    override fun create(width: Int, height: Int, bytes: ByteBuffer, colorSpace: ColorSpace): OpenGLTexture {
        val id = glGenTextures()

        val expectedSize = width * height * colorSpace.channels
        if(expectedSize != bytes.remaining()) {
            throw IllegalArgumentException("Buffer size does not match resolution (expected $expectedSize, got ${bytes.remaining()}, channels: ${colorSpace.channels}, width: $width, height: $height)")
        }

        glBindTexture(GL_TEXTURE_2D, id)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)

        val format = when(colorSpace) {
            ColorSpace.RGB -> GL_RGB
            ColorSpace.RGBA -> GL_RGBA
            ColorSpace.BGR -> GL_BGR
            ColorSpace.BGRA -> GL_BGRA
        }

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, format, GL_UNSIGNED_BYTE, bytes)

        glBindTexture(GL_TEXTURE_2D, 0)

        return OpenGLTexture(id.toLong(), width, height)
    }

    override fun create(resource: String): OpenGLTexture {
        val image = loadImageFromResource(resource)

        return create(image.width, image.height, image.buffer)
    }

    override fun createFromJpegBytes(bytes: ByteBuffer): PlatformTexture {
        MemoryStack.stackPush().use {
            val buffer = it.malloc(bytes.capacity())
            buffer.put(bytes)
            buffer.flip()

            val comp = it.mallocInt(1)
            val w = it.mallocInt(1)
            val h = it.mallocInt(1)

            val img = stbi_load_from_memory(buffer, w, h, comp, 3)
                ?: throw RuntimeException("Failed to load image due to ${stbi_failure_reason()}")

            return create(w.get(), h.get(), img, ColorSpace.RGB)
        }
    }

}



