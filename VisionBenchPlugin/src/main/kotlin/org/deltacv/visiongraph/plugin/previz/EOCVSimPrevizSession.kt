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

package org.deltacv.visiongraph.plugin.previz

import org.deltacv.eocvsim.plugin.api.EOCVSimApi
import org.deltacv.eocvsim.plugin.api.PipelineManagerApi
import org.deltacv.eocvsim.stream.ImageStreamer
import org.deltacv.eocvsim.virtualreflect.VirtualReflectContext
import org.deltacv.eocvsim.virtualreflect.jvm.JvmVirtualReflection
import org.deltacv.visiongraph.plugin.VisionGraphProcessRunner
import org.deltacv.visiongraph.plugin.project.VisionGraphProjectManager
import org.deltacv.visiongraph.util.loggerForThis
import org.openftc.easyopencv.OpenCvPipeline

class EOCVSimPrevizSession(
    val sessionName: String,
    val eocvSimApi: EOCVSimApi,
    val projectManager: VisionGraphProjectManager,
    val streamer: ImageStreamer = NoOpEngineImageStreamer,
    initialSourceCode: String
) {

    var previzRunning = false
        private set

    private var allClasses = mutableSetOf<Class<*>>()

    private var latestClass: PipelineManagerApi.PipelineClass? = null
    private var latestSourceCode: String? = null

    var latestPipeline: OpenCvPipeline? = null
        private set
    var latestVirtualReflect: VirtualReflectContext? = null
        private set

    val logger by loggerForThis()

    private var isChangingPipeline = false

    init {
        eocvSimApi.pipelineManagerApi.onPipelineChangeHook {
            if (!previzRunning) {
                it.detach()
                return@onPipelineChangeHook
            }

            if (latestClass == null || isChangingPipeline) return@onPipelineChangeHook

            val current = eocvSimApi.pipelineManagerApi.currentPipelineInstance ?: return@onPipelineChangeHook

            if (previzRunning && current::class.java != latestClass) {
                // Temporarily disable the listener
                isChangingPipeline = true

                eocvSimApi.pipelineManagerApi.changePipelineAnonymous(latestClass!!, force = true)

                latestPipeline = eocvSimApi.pipelineManagerApi.currentPipelineInstance

                latestVirtualReflect = if (latestPipeline != null) {
                    JvmVirtualReflection.contextOf(latestPipeline!!)
                } else {
                    null
                }

                // Re-enable the listener after the change
                isChangingPipeline = false
            }
        }

        eocvSimApi.pipelineManagerApi.onPauseHook {
            if (previzRunning) {
                eocvSimApi.pipelineManagerApi.resume()
            } else {
                it.detach()
            }
        }

        VisionGraphProcessRunner.onPaperVisionExit.once {
            stopPreviz()
        }

        startPreviz(initialSourceCode)
    }

    private fun startPreviz(sourceCode: String) {
        previzRunning = true

        logger.info("Starting previz session $sessionName with streamer ${streamer.javaClass.simpleName}")

        refreshPreviz(sourceCode)
    }

    fun refreshPreviz(sourceCode: String) {
        if (!previzRunning) return

        projectManager.plugin.isRunningPreviewPipeline = false

        projectManager.saveLatestSource(sourceCode)

        eocvSimApi.mainLoopHook.once {
            logger.info("Refreshing previz session $sessionName with new source code")

            val newClass = try {
                SinglePipelineCompiler.compilePipeline(sourceCode)
            } catch(e: Exception) {
                logger.error("Exception while building new source code", e)
                return@once
            }

            latestClass = newClass
            allClasses.add(newClass)

            latestSourceCode = sourceCode

            if (streamer is EOCVSimEngineImageStreamer) {
                streamer.refreshed()
            }

            latestClass?.let { eocvSimApi.pipelineManagerApi.removePipeline(it, PipelineManagerApi.PipelineSource.CLASSPATH) }

            eocvSimApi.pipelineManagerApi.addPipelineInstantiator(
                newClass,
                StreamableNoReflectPipelineInstantiator(eocvSimApi.owner, streamer)
            )

            isChangingPipeline = true

            eocvSimApi.mainLoopHook.once {
                eocvSimApi.pipelineManagerApi.changePipelineAnonymous(
                    newClass,
                    force = true
                )

                eocvSimApi.mainLoopHook.once {
                    // defer to next frame to deactivate after eocv sim activates
                    eocvSimApi.visualizerApi.viewportApi.deactivate()
                }

                latestPipeline = eocvSimApi.pipelineManagerApi.currentPipelineInstance!!
                latestVirtualReflect = JvmVirtualReflection.contextOf(latestPipeline!!)

                isChangingPipeline = false
            }
        }
    }

    fun handlePrevizPing() {
        eocvSimApi.mainLoopHook.once {
            if (!isChangingPipeline &&
                eocvSimApi.pipelineManagerApi.currentPipelineInstance?.javaClass != latestClass &&
                latestSourceCode != null
            ) {
                refreshPreviz(latestSourceCode!!)
            }
        }
    }

    fun stopPreviz() {
        if (!previzRunning) return
        previzRunning = false

        logger.info("Stopping previz session $sessionName")

        if (streamer is EOCVSimEngineImageStreamer) {
            streamer.stop()
        }

        eocvSimApi.mainLoopHook.once {
            eocvSimApi.pipelineManagerApi.changePipeline(0, force = true)
        }
    }

}



