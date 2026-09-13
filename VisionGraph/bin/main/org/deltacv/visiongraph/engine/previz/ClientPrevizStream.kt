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

package org.deltacv.visiongraph.engine.previz

import org.deltacv.visiongraph.engine.client.ByteMessageReceiver
import org.deltacv.visiongraph.engine.client.ByteMessageHandler
import org.deltacv.visiongraph.engine.ByteMessages
import org.deltacv.visiongraph.id.container.IdContext
import org.deltacv.visiongraph.io.TextureProcessorQueue
import org.deltacv.visiongraph.io.bytes
import org.deltacv.visiongraph.io.scaleToFit
import org.deltacv.visiongraph.platform.ColorSpace
import org.deltacv.visiongraph.platform.PlatformTexture
import org.deltacv.visiongraph.platform.animation.TimedTextureAnimation
import org.deltacv.visiongraph.util.loggerForThis
import java.awt.image.BufferedImage

class ClientPrevizStream(
    val sessionName: String,
    val byteReceiver: ByteMessageReceiver,
    val statistics: LivePipelineStatistics = LivePipelineStatistics(0f, 0L),
    val width: Int = 160,
    val height: Int = 120,
    val sizing: Sizing = Sizing.MINIMIZED,
    val offlineImages: Array<BufferedImage>? = null,
    val offlineImagesFps: Double = 1.0
) {

    enum class Sizing {
        MINIMIZED, MAXIMIZED
    }

    val logger by loggerForThis()

    var isStarted = false
        private set

    private var requestedMaximize = false
    private var requestedMinimize = false

    // get the texture container of the current thread
    val textureQueue = IdContext.local.peekSingleNonNull<TextureProcessorQueue>()

    var offlineTexture: PlatformTexture? = null
        private set

    private val startedStreamIds = mutableMapOf<Int, Long>()

    init {
        initOfflineImages()
    }

    private fun initOfflineImages() {
        if (offlineImages != null && offlineImages.isNotEmpty()) {
            if (offlineImages.size == 1) {
                var img = offlineImages[0]

                if (img.width != width || img.height != height) {
                    img = img.scaleToFit(width, height)
                }

                offlineTexture = textureQueue.textureFactory.create(width, height, img.bytes(), ColorSpace.BGR)
            } else {
                val textures = mutableListOf<PlatformTexture>()

                for (image in offlineImages) {
                    var img = image

                    if (img.width != width || img.height != height) {
                        img = img.scaleToFit(width, height)
                    }

                    textures.add(textureQueue.textureFactory.create(width, height, img.bytes(), ColorSpace.RGB))
                }

                offlineTexture = TimedTextureAnimation(offlineImagesFps, textures.toTypedArray()).apply {
                    enable()
                }
            }
        }
    }

    private val byteMessageHandler: ByteMessageHandler = { id, tag, bytes, length ->
        if (tag.startsWith(sessionName)) {
            if(!startedStreamIds.containsKey(id) || System.currentTimeMillis() - startedStreamIds[id]!! > 5000) {
                val lastFrameInfo = if(startedStreamIds.containsKey(id))
                    "${(System.currentTimeMillis() - startedStreamIds[id]!!) / 1000.0 }s ago"
                else
                    "never received"

                logger.info("Started/resumed stream reception id=$id for $sessionName (last frame was $lastFrameInfo)")
            }

            startedStreamIds[id] = System.currentTimeMillis()
            // offer to texture queue
            textureQueue.offerJpegAsync(id, width, height, bytes,
                dataOffset = ByteMessages.messageOffsetFromBytes(bytes),
                dataLength = length
            )
        }
    }

    fun start() {
        logger.info("Starting pipeline stream of $sessionName at {}x{}", width, height)

        byteReceiver.addHandler(byteMessageHandler)

        isStarted = true
    }

    fun stop() {
        isStarted = false

        byteReceiver.removeHandler(byteMessageHandler)
        byteReceiver.stop()
    }

    fun isAtOfflineTexture(id: Int) = !isStarted

    fun textureOf(id: Int) = if (isStarted)
        textureQueue[id] ?: offlineTexture
    else offlineTexture

    fun maximize() {
        requestedMaximize = true
    }

    fun minimize() {
        requestedMinimize = true
    }

    fun popRequestedMaximize() = requestedMaximize.also { requestedMaximize = false }
    fun popRequestedMinimize() = requestedMinimize.also { requestedMinimize = false }

}



