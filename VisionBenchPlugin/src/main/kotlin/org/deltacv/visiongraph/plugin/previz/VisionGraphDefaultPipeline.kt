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

import android.graphics.*
import com.qualcomm.robotcore.eventloop.opmode.Disabled
import org.deltacv.common.util.loggerForThis
import org.firstinspires.ftc.robotcore.external.Telemetry
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.openftc.easyopencv.OpenCvPipeline

@Disabled
class VisionGraphDefaultPipeline(
    val telemetry: Telemetry
) : OpenCvPipeline() {

    val logger by loggerForThis()

    private lateinit var textPaint: Paint

    lateinit var drawMat: Mat

    override fun init(mat: Mat) {
        drawMat = Mat(Size(2160.0, 1800.0), mat.type())
        drawMat.setTo(Scalar(0.0, 0.0, 0.0, 0.0))

        try {
            val bytes = VisionGraphDefaultPipeline::class.java.getResourceAsStream("/ico/ico_ezv.png")!!.use {
                it.readBytes()
            }

            val logoBytes = MatOfByte(*bytes)
            val logoMat = Imgcodecs.imdecode(logoBytes, Imgcodecs.IMREAD_UNCHANGED)
            logoBytes.release()

            Imgproc.cvtColor(logoMat, logoMat, Imgproc.COLOR_BGR2RGBA)

            Imgproc.resize(logoMat, logoMat, Size(logoMat.size().width / 2, logoMat.size().width / 2))

            // Draw the logo centered
            val x = (drawMat.width() - logoMat.width()) / 2
            val y = (drawMat.height() - logoMat.height()) / 2

            val roi = drawMat.submat(y, y + logoMat.height(), x, x + logoMat.width())
            logoMat.copyTo(roi)

            logoMat.release()
        } catch(e: Exception) {
            logger.warn("Failed to load logo", e)
        }

        textPaint = Paint()
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.DEFAULT_ITALIC
        textPaint.textSize = 30f
        textPaint.isAntiAlias = true
    }

    override fun processFrame(input: Mat): Mat {
        telemetry.addLine("Making computer vision accessible to everyone")
        telemetry.update()

        return drawMat
    }

    override fun onDrawFrame(
        canvas: Canvas,
        onscreenWidth: Int,
        onscreenHeight: Int,
        scaleBmpPxToCanvasPx: Float,
        scaleCanvasDensity: Float,
        userContext: Any?
    ) {
        canvas.drawText("Select a project to preview", 5f, 33f, textPaint)
    }

}



