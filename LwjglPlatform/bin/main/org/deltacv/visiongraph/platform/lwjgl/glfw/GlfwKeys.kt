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

package org.deltacv.visiongraph.platform.lwjgl.glfw

import org.deltacv.visiongraph.platform.PlatformKeys
import org.lwjgl.glfw.GLFW.*

object GlfwKeys : PlatformKeys {

    val isMac = System.getProperty("os.name").contains("Mac")

    override val ArrowUp = glfwGetKeyScancode(GLFW_KEY_UP) //111
    override val ArrowDown = glfwGetKeyScancode(GLFW_KEY_DOWN)// 116
    override val ArrowLeft = glfwGetKeyScancode(GLFW_KEY_LEFT) // 113
    override val ArrowRight = glfwGetKeyScancode(GLFW_KEY_RIGHT) //114

    override val Escape =  glfwGetKeyScancode(GLFW_KEY_ESCAPE) //9
    override val Spacebar = glfwGetKeyScancode(GLFW_KEY_SPACE) //65
    override val Delete =  glfwGetKeyScancode(GLFW_KEY_DELETE) //119

    override val LeftShift =  glfwGetKeyScancode(GLFW_KEY_LEFT_SHIFT) //50
    override val RightShift = glfwGetKeyScancode(GLFW_KEY_RIGHT_SHIFT) //62

    override val LeftControl = glfwGetKeyScancode(GLFW_KEY_LEFT_CONTROL) //37
    override val RightControl = glfwGetKeyScancode(GLFW_KEY_RIGHT_SHIFT) //105

    override val LeftSuper = glfwGetKeyScancode(GLFW_KEY_LEFT_SUPER) //133
    override val RightSuper = glfwGetKeyScancode(GLFW_KEY_RIGHT_SUPER) //134

    override val NativeLeftSuper by lazy {
        if(isMac) glfwGetKeyScancode(GLFW_KEY_LEFT_SUPER) else glfwGetKeyScancode(GLFW_KEY_LEFT_CONTROL)
    }
    override val NativeRightSuper by lazy {
        if(isMac) glfwGetKeyScancode(GLFW_KEY_RIGHT_SUPER) else glfwGetKeyScancode(GLFW_KEY_RIGHT_CONTROL)
    }

    override val Z = glfwGetKeyScancode(GLFW_KEY_Z) //44
    override val Y = glfwGetKeyScancode(GLFW_KEY_Y) //52
    override val X = glfwGetKeyScancode(GLFW_KEY_X) //45
    override val C = glfwGetKeyScancode(GLFW_KEY_C) //46
    override val V = glfwGetKeyScancode(GLFW_KEY_V) //47
}



