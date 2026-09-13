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

import com.qualcomm.robotcore.util.ElapsedTime
import com.qualcomm.robotcore.util.MovingStatistics
import org.deltacv.eocvsim.stream.ImageStreamer
import kotlinx.coroutines.*
import org.deltacv.visiongraph.engine.PaperVisionEngine
import org.deltacv.visiongraph.engine.ByteMessageTag
import org.deltacv.visiongraph.util.MemoryPool
import org.deltacv.visiongraph.util.loggerFor
import org.deltacv.vision.external.util.extension.aspectRatio
import org.deltacv.vision.external.util.extension.clipTo
import org.deltacv.mackjpeg.MackJPEG
import org.deltacv.mackjpeg.PixelFormat
import org.deltacv.mackjpeg.exception.JPEGException
import org.deltacv.visiongraph.engine.ByteMessages
import org.libjpegturbo.turbojpeg.TJ
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.openftc.easyopencv.MatRecycler
import java.nio.ByteBuffer
import kotlin.concurrent.getOrSet
import kotlin.math.roundToInt

class EOCVSimEngineImageStreamer(
    val previzNameProvider: () -> String,
    val resolution: Size,
    val ipcEngine: PaperVisionEngine,
    var streamQualityFormula: (Int) -> Int = { 50 }
) : ImageStreamer {

    companion object {
        val logger by loggerFor<EOCVSimEngineImageStreamer>()

        const val JPEG_WORKER_THREADS = 5

        init {
            if (MackJPEG.getSupportedBackend() == null) {
                logger.error("No JPEG backend is available for MackJPEG! Image streaming will not work!")
            } else {
                logger.info("Using JPEG backend: ${MackJPEG.getSupportedBackend()!!.name}")
            }
        }
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default.limitedParallelism(JPEG_WORKER_THREADS)
    )

    private val fallbackMatOfByte = ThreadLocal<MatOfByte>()

    private val ids = mutableMapOf<Int, Long>()

    private val latestMatMap = mutableMapOf<Int, Mat>()
    private val maskMatMap = mutableMapOf<Int, Mat>()

    private val changeRateAvgs = mutableMapOf<Int, MovingStatistics>()
    private val changeRateTimers = mutableMapOf<Int, ElapsedTime>()
    private val diffSlowDownTimers = mutableMapOf<Int, ElapsedTime>()
    private val hasSent = mutableMapOf<Int, Boolean>()

    private val changeCheckLock = mutableMapOf<Int, Any>()

    private val bufferPool = MemoryPool(JPEG_WORKER_THREADS * 3)
    private val matRecycler = MatRecycler(JPEG_WORKER_THREADS * 3)

    private val reportedJpegExceptions = mutableMapOf<String, Boolean>()

    val tag by lazy { ByteMessageTag.fromString(previzNameProvider()) }

    override fun sendFrame(
        id: Int,
        image: Mat,
        cvtCode: Int?
    ) {
        if (image.empty()) {
            return
        }

        val targetImage = matRecycler.takeMatOrNull() ?: return

        if (cvtCode != null) {
            Imgproc.cvtColor(image, targetImage, cvtCode)
        } else {
            image.copyTo(targetImage)
        }

        submitJpeg(id, targetImage)
    }

    private fun submitJpeg(id: Int, targetImage: MatRecycler.RecyclableMat) {
        scope.launch {
            try {
                processJpeg(id, targetImage)
            } finally {
                matRecycler.returnMat(targetImage)
            }
        }
    }

    private suspend fun processJpeg(id: Int, targetImage: MatRecycler.RecyclableMat) {
        (MackJPEG.getSupportedBackend()?.makeCompressor() ?: return).use { compressor ->
            if (!hasChanged(id, targetImage)) {
                return
            }

            if (targetImage.type() != CvType.CV_8UC3) {
                targetImage.convertTo(targetImage, CvType.CV_8UC3)
            }

            scaleToFit(targetImage, targetImage)

            val expectedSize = targetImage.rows() * targetImage.cols() * 3
            val imageBuffer = bufferPool.getOrCreate(
                expectedSize,
                MemoryPool.MemoryBehavior.ALLOCATE_WHEN_EXHAUSTED,
                MemoryPool.AllocationMode.EXACT
            ) ?: return

            try {
                targetImage.get(0, 0, imageBuffer)

                val width = targetImage.cols()
                val height = targetImage.rows()

                compressor.setImage(imageBuffer, width, height, PixelFormat.RGB)
                compressor.setQuality(streamQualityFormula(id).coerceIn(1, 100))

                val headerSize = ByteMessages.headerSize(tag)

                val jpegBuffer = bufferPool.getOrCreate(
                    TJ.bufSize(width, height, TJ.SAMP_420) + headerSize,
                    MemoryPool.MemoryBehavior.ALLOCATE_WHEN_EXHAUSTED
                ) ?: return

                try {
                    val jpegSize = try {
                        compressor.compress(jpegBuffer)

                        synchronized(reportedJpegExceptions) {
                            val resolved = reportedJpegExceptions.entries.filter { it.value }
                            for (entry in resolved) {
                                logger.info("MackJPEG compression error resolved for id=$id (was: ${entry.key})")
                                entry.setValue(false)
                            }
                        }

                        compressor.compressedSize
                    } catch (e: JPEGException) {
                        val msg = e.message ?: "unknown"

                        synchronized(reportedJpegExceptions) {
                            if (!reportedJpegExceptions.getOrDefault(msg, false)) {
                                reportedJpegExceptions[msg] = true
                                logger.error("MackJPEG compression error, falling back to OpenCV for id=$id: $msg", e)
                            }
                        }

                        val bytes = fallbackMatOfByte.getOrSet { MatOfByte() }

                        Imgproc.cvtColor(targetImage, targetImage, Imgproc.COLOR_RGB2BGR)
                        Imgcodecs.imencode(".jpg", targetImage, bytes)
                        bytes.get(0, 0, jpegBuffer)

                        fallbackMatOfByte.get()?.total()?.toInt() ?: 0
                    }

                    System.arraycopy(jpegBuffer, 0, jpegBuffer, headerSize, jpegSize)

                    val byteMessageBuffer = ByteBuffer.wrap(jpegBuffer)
                    ByteMessages.writeHeader(tag, id, jpegSize, byteMessageBuffer)

                    ipcEngine.sendBytes(jpegBuffer)

                    synchronized(ids) {
                        if (!ids.containsKey(id) || System.currentTimeMillis() - ids[id]!! > 5000) {
                            val status = if(ids.containsKey(id))
                                "Started"
                            else "Resumed"

                            val lastFrameInfo = if (ids.containsKey(id))
                                "${(System.currentTimeMillis() - ids[id]!!) / 1000.0}s ago"
                            else "never received"

                            logger.info("$status streaming for ${previzNameProvider()} id=$id (last frame was $lastFrameInfo)")
                        }

                        ids[id] = System.currentTimeMillis()
                    }
                } finally {
                    bufferPool.returnBuffer(jpegBuffer)
                }

            } finally {
                // ✅ FIXED MEMORY LEAK
                bufferPool.returnBuffer(imageBuffer)
            }
        }
    }

    // --- EVERYTHING BELOW UNCHANGED ---

    private fun hasChanged(id: Int, image: Mat): Boolean {
        val lock = changeCheckLock.getOrPut(id) { Any() }

        synchronized(lock) {
            if (!hasSent.getOrDefault(id, false)) {
                hasSent[id] = true
                return true
            }

            val maskMat = maskMatMap.getOrPut(id) { Mat() }
            val latestMat = latestMatMap.getOrPut(id) { Mat() }

            val changeRateTimer = changeRateTimers.getOrPut(id) { ElapsedTime() }
            val diffSlowDownTimer = diffSlowDownTimers.getOrPut(id) { ElapsedTime() }
            val changeRateAvg = changeRateAvgs.getOrPut(id) { MovingStatistics(50) }

            changeRateAvg.add(changeRateTimer.seconds())

            val mean = changeRateAvg.mean
            val isFastChange = mean <= 0.5

            val forceSend = changeRateTimer.seconds() >= 5.0

            try {
                if (isFastChange || forceSend) {
                    return true
                } else if (!latestMat.empty() && latestMat.size() == image.size()) {
                    if (changeRateTimer.seconds() > 3) {
                        val slowDown = ((changeRateTimer.seconds() - 3) * 0.1).coerceAtMost(0.5)
                        if (diffSlowDownTimer.seconds() <= slowDown) {
                            return false
                        } else {
                            diffSlowDownTimer.reset()
                        }
                    }

                    Core.absdiff(latestMat, image, maskMat)
                    Imgproc.cvtColor(maskMat, maskMat, Imgproc.COLOR_RGB2GRAY)

                    val diffPixels = Core.countNonZero(maskMat)

                    if (diffPixels > 0) {
                        changeRateTimer.reset()
                        return true
                    } else {
                        return false
                    }
                } else {
                    return true
                }
            } catch (e: Exception) {
                logger.error("hasChanged exception for stream id $id", e)
                return true
            } finally {
                image.copyTo(latestMat)
            }
        }
    }

    private fun scaleToFit(src: Mat, dst: Mat) {
        if (src.size() == resolution) {
            if (src != dst) src.copyTo(dst)
        } else {
            val targetR = resolution.aspectRatio()
            val inputR = src.aspectRatio()

            if (targetR == inputR) {
                Imgproc.resize(src, dst, resolution, 0.0, 0.0, Imgproc.INTER_AREA)
            } else {
                val inputW = src.size().width
                val inputH = src.size().height

                val widthRatio = resolution.width / inputW
                val heightRatio = resolution.height / inputH
                val bestRatio = widthRatio.coerceAtMost(heightRatio)

                val newSize = Size(inputW * bestRatio, inputH * bestRatio).clipTo(resolution)

                val xOffset = (resolution.width - newSize.width) / 2
                val yOffset = (resolution.height - newSize.height) / 2

                val resizedImg = matRecycler.takeMatOrNull()

                try {
                    resizedImg.create(newSize, src.type())
                    Imgproc.resize(src, resizedImg, newSize, 0.0, 0.0, Imgproc.INTER_AREA)

                    dst.create(resolution, src.type())
                    dst.setTo(Scalar(0.0, 0.0, 0.0, 255.0))

                    val rectX = xOffset.roundToInt().coerceAtLeast(0)
                    val rectY = yOffset.roundToInt().coerceAtLeast(0)
                    val rectWidth = newSize.width.roundToInt()
                    val rectHeight = newSize.height.roundToInt()

                    val submat = dst.submat(Rect(rectX, rectY, rectWidth, rectHeight))
                    resizedImg.copyTo(submat)
                } catch (e: Exception) {
                    logger.error("scaleToFit error", e)
                } finally {
                    resizedImg.returnMat()
                }
            }
        }
    }

    fun stop() {
        logger.info("Stopping EOCVSimEngineImageStreamer")
        scope.cancel()
    }

    fun refreshed() {
        ids.clear()
    }
}