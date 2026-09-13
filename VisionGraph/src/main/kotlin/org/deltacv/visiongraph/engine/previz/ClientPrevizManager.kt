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

import org.deltacv.visiongraph.codegen.CodeGenManager
import org.deltacv.visiongraph.codegen.language.Language
import org.deltacv.visiongraph.codegen.language.jvm.JavaLanguage
import org.deltacv.visiongraph.engine.client.ByteMessageReceiver
import org.deltacv.visiongraph.engine.client.PaperVisionEngineClient
import org.deltacv.visiongraph.engine.client.message.PrevizPingMessage
import org.deltacv.visiongraph.engine.client.message.PrevizSourceCodeMessage
import org.deltacv.visiongraph.engine.client.message.PrevizStartMessage
import org.deltacv.visiongraph.engine.client.message.PrevizStopMessage
import org.deltacv.visiongraph.engine.client.response.OkResponse
import org.deltacv.visiongraph.engine.client.response.PrevizStatisticsResponse
import org.deltacv.visiongraph.io.bufferedImageFromResource
import org.deltacv.visiongraph.util.ElapsedTime
import org.deltacv.visiongraph.util.event.PaperEventHandler
import org.deltacv.visiongraph.util.loggerForThis

class ClientPrevizManager(
    val defaultPrevizStreamWidth: Int,
    val defaultPrevizStreamHeight: Int,
    val codeGenManager: CodeGenManager,
    val client: PaperVisionEngineClient,
) {

    val offlineImages = arrayOf(
        bufferedImageFromResource("/img/TechnicalDifficulties.png"),
        bufferedImageFromResource("/img/PleaseHangOn.png")
    )

    var previzName: String? = null
        private set

    var stream = ClientPrevizStream("", client.byteReceiver, offlineImages = offlineImages)
        private set(value) {
            field = value
            onStreamChange.run()
        }

    val livePipelineStatistics = LivePipelineStatistics()

    val onPrevizStart = PaperEventHandler("ClientPrevizManager-OnPrevizStart")
    val onPrevizStop = PaperEventHandler("ClientPrevizManager-OnPrevizStop")

    val onStreamChange = PaperEventHandler("ClientPrevizManager-OnStreamChange")

    val logger by loggerForThis()

    var previzRunning = false
        private set

    private val firstPingTimer = ElapsedTime()
    private val pingTimer = ElapsedTime()

    fun startPreviz(previzName: String) {
        startPreviz(previzName, JavaLanguage)
    }

    fun startPreviz(previzName: String, streamWidth: Int, streamHeight: Int, streamSizing: ClientPrevizStream.Sizing) {
        startPreviz(
            previzName,
            codeGenManager.build(previzName, JavaLanguage, true),
            streamWidth,
            streamHeight,
            streamSizing
        )
    }

    fun startPreviz(previzName: String, language: Language) {
        startPreviz(previzName, codeGenManager.build(previzName, language, true))
    }

    fun startPreviz(
        previzName: String,
        sourceCode: String?,
        streamWidth: Int = defaultPrevizStreamWidth,
        streamHeight: Int = defaultPrevizStreamHeight,
        streamSizing: ClientPrevizStream.Sizing = ClientPrevizStream.Sizing.MINIMIZED
    ) {
        this.previzName = previzName

        if (sourceCode == null) {
            logger.warn("Failed to start previz session $previzName, source code is null (probably due to code gen error)")
            return
        }

        logger.info("Starting previz session $previzName")

        client.sendMessage(
            PrevizStartMessage(
                previzName,
                sourceCode,
                streamWidth,
                streamHeight
            ).onResponseWith<OkResponse> {
                client.onProcess.once {
                    logger.info("Previz session $previzName running")

                    previzRunning = true

                    onPrevizStart.run()

                    stream.stop()

                    stream = ClientPrevizStream(
                        previzName,
                        client.byteReceiver,
                        livePipelineStatistics,
                        width = streamWidth,
                        height = streamHeight,
                        offlineImages = offlineImages,
                        sizing = streamSizing
                    )

                    stream.start()
                    pingTimer.reset()
                    firstPingTimer.reset()
                }
            })
    }

    private fun restartWithStreamResolution(
        previzName: String = this.previzName!!,
        previzStreamWidth: Int = this.defaultPrevizStreamWidth,
        previzStreamHeight: Int = this.defaultPrevizStreamHeight,
        sizing: ClientPrevizStream.Sizing = stream.sizing
    ) {
        if (previzRunning) {
            logger.info("Restarting previz session $previzName with new stream resolution")

            onPrevizStop.once { // restart after fully stopped
                startPreviz(previzName, previzStreamWidth, previzStreamHeight, sizing)
            }

            stopPreviz()
        } else {
            logger.info("Starting previz session $previzName with new stream resolution")
            startPreviz(previzName, previzStreamWidth, previzStreamHeight, sizing)
        }
    }

    fun refreshPreviz() = previzName?.let {
        refreshPreviz(codeGenManager.build(it, JavaLanguage, true))
    }

    fun refreshPreviz(sourceCode: String?) {
        if (sourceCode == null) {
            logger.warn("Failed to refresh previz session $previzName, source code is null (probably due to code gen error)")
            return
        }

        if (previzRunning) {
            client.sendMessage(PrevizSourceCodeMessage(previzName!!, sourceCode))
        }
    }

    fun stopPreviz() {
        logger.info("Stopping previz session $previzName")

        client.sendMessage(PrevizStopMessage(previzName!!).onResponseWith<OkResponse> {
            client.onProcess.once {
                logger.info("Previz session $previzName stopped")
                stream.stop()

                previzRunning = false

                onPrevizStop.run()
            }
        })
    }

    fun update() {
        if (previzName != null && previzRunning && pingTimer.seconds > 1 && firstPingTimer.seconds > 3) {
            client.sendMessage(PrevizPingMessage(previzName!!).onResponseWith<PrevizStatisticsResponse> {
                livePipelineStatistics.fps = it.fps
                livePipelineStatistics.frameTimeMs = it.frameTimeMs
            })

            pingTimer.reset()
        }

        if (stream.popRequestedMaximize() && previzName != null && previzRunning) {
            logger.info("Maximizing previz session $previzName")

            restartWithStreamResolution(
                previzStreamWidth = stream.width * 2,
                previzStreamHeight = stream.height * 2,
                sizing = ClientPrevizStream.Sizing.MAXIMIZED
            )
        }

        if (stream.popRequestedMinimize() && previzName != null && previzRunning) {
            logger.info("Minimizing previz session $previzName")

            restartWithStreamResolution(
                previzStreamWidth = stream.width / 2,
                previzStreamHeight = stream.height / 2,
                sizing = ClientPrevizStream.Sizing.MINIMIZED
            )
        }
    }

}



