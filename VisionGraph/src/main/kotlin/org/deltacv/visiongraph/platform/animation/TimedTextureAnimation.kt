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

package org.deltacv.visiongraph.platform.animation

import org.deltacv.visiongraph.platform.PlatformTexture
import org.deltacv.visiongraph.util.ElapsedTime
import java.nio.ByteBuffer

class TimedTextureAnimation(
    val fps: Double,
    val textures: Array<PlatformTexture>
): PlatformTextureAnimation() {

    override var frame = 0
    private var halfFrames = 0.0

    override var isActive = true

    private val deltaTimer = ElapsedTime()

    override fun next() {
        frame += 1

        if(frame >= textures.size) {
            frame = 0
        }
    }

    override fun update() {
        if(isActive) {
            halfFrames += fps * deltaTimer.seconds

            frame = halfFrames.toInt()

            if(frame >= textures.size) {
                halfFrames -= frame
                frame = 0
            }
        }

        deltaTimer.reset()
    }

    override val width: Int get() = textures[frame].width
    override val height: Int get() = textures[frame].height

    override val textureId: Long get() = textures[frame].textureId

    override fun delete() {
        for(texture in textures) {
            texture.delete()
        }
        isActive = false
    }

}



