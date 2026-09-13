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

package org.deltacv.visiongraph.io

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import org.deltacv.visiongraph.id.DrawableIdElementBase
import org.deltacv.visiongraph.id.container.IdContainer
import org.deltacv.visiongraph.id.container.IdContext
import org.deltacv.visiongraph.platform.ColorSpace
import org.deltacv.visiongraph.platform.PlatformTexture
import org.deltacv.visiongraph.platform.PlatformTextureFactory
import org.deltacv.visiongraph.util.MemoryPool
import org.deltacv.visiongraph.util.loggerFor
import org.deltacv.mackjpeg.MackJPEG
import org.deltacv.mackjpeg.PixelFormat
import java.nio.ByteBuffer

class TextureProcessorQueue(
    val textureFactory: PlatformTextureFactory
) : DrawableIdElementBase<TextureProcessorQueue>() {

    companion object {
        const val QUEUED_TEXTURE_CAPACITY = 15
        private val logger by loggerFor<TextureProcessorQueue>()
    }

    private val workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val memoryPool = MemoryPool(tierCapacity = 8)

    private val queuedTextures = Channel<FutureTexture>(
        capacity = QUEUED_TEXTURE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val textures = mutableMapOf<Int, PlatformTexture>()

    // ------------------------ BLOCKING WRAPPERS ------------------------

    private fun getOrCreateReusableBufferBlocking(
        size: Int,
        memoryBehavior: MemoryPool.MemoryBehavior
    ): ByteArray? = runBlocking {
        memoryPool.getOrCreate(size, memoryBehavior)
    }

    private fun returnReusableBufferBlocking(buffer: ByteArray) = runBlocking {
        memoryPool.returnBuffer(buffer)
    }

    private fun clearPoolBlocking() = runBlocking {
        memoryPool.clear()
    }

    // ------------------------ Render/update loop ------------------------

    override fun draw() {
        while (true) {
            val future = queuedTextures.tryReceive().getOrNull() ?: break
            try {
                val updated = processExistingTexture(future)
                if (!updated) createNewTexture(future)
            } catch (e: Exception) {
                logger.error("Error processing texture", e)
            } finally {
                returnReusableBuffer(future.data)
            }
        }
    }

    private fun processExistingTexture(future: FutureTexture): Boolean {
        val existing = textures[future.id] ?: return false

        if (existing.width == future.width && existing.height == future.height) {
            if (future.jpeg) {
                existing.setJpeg(future.data)
            } else {
                existing.set(future.data, future.colorSpace)
            }
            return true
        }

        existing.delete()
        return false
    }

    private fun createNewTexture(future: FutureTexture) {
        require(future.id >= 0) { "ID of new texture must be positive !" }

        val newTex: PlatformTexture = if (future.jpeg) {
            textureFactory.createFromJpegBytes(ByteBuffer.wrap(future.data, 0, future.dataSize))
        } else {
            textureFactory.create(future.width, future.height, future.data, future.colorSpace)
        }

        textures[future.id] = newTex
    }

    // ------------------------ Public API ------------------------

    fun offerJpeg(
        id: Int,
        width: Int,
        height: Int,
        data: ByteArray,
        memoryBehavior: MemoryPool.MemoryBehavior = MemoryPool.MemoryBehavior.ALLOCATE_WHEN_EXHAUSTED
    ) = offer(id, width, height, data, jpeg = true, memoryBehavior = memoryBehavior)

    fun offerJpeg(
        id: Int,
        width: Int,
        height: Int,
        data: ByteBuffer,
        memoryBehavior: MemoryPool.MemoryBehavior = MemoryPool.MemoryBehavior.ALLOCATE_WHEN_EXHAUSTED
    ) = offer(id, width, height, data, jpeg = true, memoryBehavior = memoryBehavior)

    fun offerJpegAsync(
        id: Int,
        width: Int,
        height: Int,
        data: ByteArray,
        dataOffset: Int = 0,
        dataLength: Int = data.size - dataOffset,
        memoryBehavior: MemoryPool.MemoryBehavior = MemoryPool.MemoryBehavior.ALLOCATE_WHEN_EXHAUSTED
    ) {
        val backend = MackJPEG.getSupportedBackend()
        if (backend == null) {
            offerJpeg(id, width, height, ByteBuffer.wrap(data, dataOffset, dataLength), memoryBehavior)
            return
        }

        workerScope.launch {
            val decompressor = backend.makeDecompressor() ?: return@launch

            decompressor.use {
                val offsetData = prepareOffsetData(data, dataOffset, dataLength, memoryBehavior) ?: return@use

                try {
                    decompressor.setJPEG(offsetData, dataLength)

                    val outputSize = decompressor.decodedWidth * decompressor.decodedHeight * 3
                    val buffer = memoryPool.getOrCreate(outputSize, memoryBehavior) ?: return@use

                    try {
                        decompressor.decompress(buffer, PixelFormat.RGB)
                    } catch (e: Exception) {
                        logger.warn("Failed to decompress JPEG #$id", e)
                        memoryPool.returnBuffer(buffer)
                        return@use
                    }

                    offerBuffer(
                        id,
                        decompressor.decodedWidth,
                        decompressor.decodedHeight,
                        buffer,
                        outputSize,
                        ColorSpace.RGB,
                        jpeg = false
                    )
                } finally {
                    if (dataOffset != 0) memoryPool.returnBuffer(offsetData)
                }
            }
        }
    }

    fun offer(
        id: Int,
        width: Int,
        height: Int,
        data: ByteBuffer,
        colorSpace: ColorSpace = ColorSpace.RGB,
        jpeg: Boolean = false,
        memoryBehavior: MemoryPool.MemoryBehavior = MemoryPool.MemoryBehavior.ALLOCATE_WHEN_EXHAUSTED
    ) {
        val size = data.remaining()
        val buffer = getOrCreateReusableBuffer(size, memoryBehavior) ?: return
        data.get(buffer, 0, size)
        offerBuffer(id, width, height, buffer, size, colorSpace, jpeg)
    }

    fun offer(
        id: Int,
        width: Int,
        height: Int,
        data: ByteArray,
        colorSpace: ColorSpace = ColorSpace.RGB,
        jpeg: Boolean = false,
        memoryBehavior: MemoryPool.MemoryBehavior = MemoryPool.MemoryBehavior.ALLOCATE_WHEN_EXHAUSTED
    ) {
        val buffer = getOrCreateReusableBuffer(data.size, memoryBehavior) ?: return
        System.arraycopy(data, 0, buffer, 0, data.size)
        offerBuffer(id, width, height, buffer, data.size, colorSpace, jpeg)
    }

    operator fun get(id: Int) = textures[id]

    fun clear() {
        workerScope.cancel()

        while (true) {
            val future = queuedTextures.tryReceive().getOrNull() ?: break
            returnReusableBuffer(future.data)
        }

        textures.values.forEach { it.delete() }
        textures.clear()

        clearPoolBlocking()
    }

    // ------------------------ Internal helpers ------------------------

    private fun offerBuffer(
        id: Int,
        width: Int,
        height: Int,
        buffer: ByteArray,
        dataSize: Int,
        colorSpace: ColorSpace,
        jpeg: Boolean
    ) {
        val result = queuedTextures.trySend(FutureTexture(id, width, height, buffer, dataSize, colorSpace, jpeg))
        if (result.isFailure) {
            returnReusableBuffer(buffer)
        }
    }

    private fun prepareOffsetData(
        data: ByteArray,
        offset: Int,
        dataLength: Int,
        memoryBehavior: MemoryPool.MemoryBehavior
    ): ByteArray? {
        if (offset == 0 && dataLength == data.size) return data

        val roundedLength = ((dataLength / 4096) + 1) * 4096

        val dest = getOrCreateReusableBuffer(roundedLength, memoryBehavior) ?: return null
        System.arraycopy(data, offset, dest, 0, dataLength)
        return dest
    }

    private fun returnReusableBuffer(buffer: ByteArray) =
        returnReusableBufferBlocking(buffer)

    private fun getOrCreateReusableBuffer(
        size: Int,
        memoryBehavior: MemoryPool.MemoryBehavior
    ) = getOrCreateReusableBufferBlocking(size, memoryBehavior)

    override val idContainer: IdContainer<TextureProcessorQueue> by lazy { IdContext.local.peekNonNull() }

    private class FutureTexture(
        val id: Int,
        val width: Int,
        val height: Int,
        val data: ByteArray,
        val dataSize: Int,
        val colorSpace: ColorSpace,
        val jpeg: Boolean
    )
}