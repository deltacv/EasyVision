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

package org.deltacv.visiongraph.platform.lwjgl

import imgui.app.Application
import imgui.app.Configuration
import imgui.app.WindowGlfw
import org.deltacv.visiongraph.VisionGraph
import org.deltacv.visiongraph.engine.bridge.PaperVisionEngineBridge
import org.deltacv.visiongraph.io.KeyAction
import org.deltacv.visiongraph.platform.lwjgl.glfw.GlfwKeys
import org.deltacv.visiongraph.platform.lwjgl.glfw.GlfwWindow
import org.deltacv.visiongraph.platform.lwjgl.texture.OpenGLTextureFactory
import org.deltacv.visiongraph.platform.platformSetup
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWKeyCallback

class LWJGLPaperVisionApp @JvmOverloads constructor(
    val bridge: PaperVisionEngineBridge? = null,
    val showWelcomeWindow: Boolean = false
) : Application() {

    val setup = platformSetup("LWJGL") {
        window = glfwWindow
        textureFactory = OpenGLTextureFactory
        keys = GlfwKeys
        showWelcomeWindow = this@LWJGLPaperVisionApp.showWelcomeWindow
        engineBridge = bridge
    }

    private val handle get() = (window as WindowGlfw).handle
    val glfwWindow = GlfwWindow { handle }

    val visionGraph = VisionGraph(setup)

    override fun configure(config: Configuration) {
        config.title = ""
    }

    override fun initImGui(config: Configuration) {
        super.initImGui(config)
        visionGraph.init()
    }

    private var hasProcessed = false
    private var prevKeyCallback: GLFWKeyCallback? = null

    override fun process() {
        if(!hasProcessed) {
            visionGraph.firstProcess()
            hasProcessed = true
        }

        if (prevKeyCallback == null) {
            // register a new key callback that will call the previous callback and handle some special keys
            prevKeyCallback = glfwSetKeyCallback(handle, ::keyCallback)
        }

        glfwWindow.processWindowOps()

        visionGraph.process()
    }

    override fun postRun() {
        visionGraph.destroy()
    }

    private fun keyCallback(windowId: Long, key: Int, scancode: Int, action: Int, mods: Int) {
        if (prevKeyCallback != null) {
            prevKeyCallback!!.invoke(windowId, key, scancode, action, mods) //invoke the imgui callback
        }

        // thanks.
        visionGraph.keyManager.updateKey(scancode, when(action) { // mapping the glfw action Int to a KeyAction
            GLFW_PRESS -> KeyAction.PRESS
            GLFW_REPEAT -> KeyAction.PRESSING
            GLFW_RELEASE -> KeyAction.RELEASE
            else -> KeyAction.UNKNOWN
        })
    }
}



