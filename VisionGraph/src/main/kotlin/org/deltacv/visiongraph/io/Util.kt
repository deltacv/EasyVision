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

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import java.awt.image.BufferedImage

import java.awt.Image
import java.awt.image.DataBufferByte
import javax.imageio.ImageIO

fun copyToFile(inp: InputStream, file: File, replaceIfExisting: Boolean = true) {
    if(file.exists() && !replaceIfExisting) return

    Files.copy(inp, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

fun copyToTempFile(inp: InputStream, name: String, replaceIfExisting: Boolean = true): File {
    val tmpDir = System.getProperty("java.io.tmpdir")
    val tempFile = File(tmpDir + File.separator + name)

    copyToFile(inp, tempFile, replaceIfExisting)

    return tempFile
}

val String.fileExtension: String? get() {
    return if(contains(".")) {
        substring(lastIndexOf(".") + 1)
    } else {
        null
    }
}

fun resourceToString(resourcePath: String): String {
    val stream = KeyManager::class.java.getResourceAsStream(resourcePath)!!
    return stream.bufferedReader().use { it.readText() }
}

fun bufferedImageFromResource(resourcePath: String): BufferedImage = ImageIO.read(
    KeyManager::class.java.getResourceAsStream(resourcePath)!!
)

fun BufferedImage.bytes(): ByteArray = (raster.dataBuffer as DataBufferByte).data

fun BufferedImage.scaleToFit(newWidth: Int, newHeight: Int): BufferedImage {
    val scalex = newWidth.toDouble() / width
    val scaley = newHeight.toDouble() / height
    val scale = scalex.coerceAtMost(scaley)

    val w = (width * scale).toInt()
    val h = (height * scale).toInt()

    val tmp = getScaledInstance(w, h, Image.SCALE_SMOOTH)

    val resized = BufferedImage(w, h, type)
    val g2d = resized.createGraphics()

    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()

    tmp.flush()

    return resized
}



