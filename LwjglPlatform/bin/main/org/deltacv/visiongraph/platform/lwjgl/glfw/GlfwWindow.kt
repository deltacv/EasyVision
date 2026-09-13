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

import imgui.ImVec2
import org.deltacv.visiongraph.platform.PlatformFileChooserResult
import org.deltacv.visiongraph.platform.PlatformFileFilter
import org.deltacv.visiongraph.platform.PlatformWindow
import org.deltacv.visiongraph.platform.lwjgl.util.ImageData
import org.deltacv.visiongraph.platform.lwjgl.util.loadImageFromResource
import org.deltacv.visiongraph.platform.lwjgl.util.toBufferedImage
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.glfw.GLFWNativeCocoa.glfwGetCocoaWindow
import org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window
import org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window
import org.lwjgl.stb.STBImage.stbi_image_free
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Platform
import org.lwjgl.util.nfd.NFDFilterItem
import org.lwjgl.util.nfd.NFDSaveDialogArgs
import org.lwjgl.util.nfd.NativeFileDialog.*
import java.awt.Taskbar
import java.io.File
import java.nio.Buffer

class GlfwWindow(val ptrSupplier: () -> Long) : PlatformWindow {

    private var currentCloseListener: (() -> Boolean)? = null
    private var closeCallbackSet = false
    private var pendingIconImage: ImageData? = null
    private var hasDeferredIconUpdate = false

    private val isMac = System.getProperty("os.name").lowercase().contains("mac")
    private val isWindows = Platform.get() == Platform.WINDOWS

    override var title: String
        get() = glfwGetWindowTitle(ptrSupplier()) ?: ""
        set(value) {
            glfwSetWindowTitle(ptrSupplier(), value)
        }
    override var icon: String = ""
        set(value) {
            val image = loadImageFromResource(value)
            field = value

            if(Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().iconImage = image.toBufferedImage()
            }

            // "Cocoa: Regular windows do not have icons on macOS"
            if(isMac) {
                stbi_image_free(image.buffer)
                return
            }

            // "glfwSetWindowIcon fails to update the Windows taskbar
            // icon if events are not polled within a timeframe"
            // - https://github.com/glfw/glfw/issues/2753
            if (isWindows) {
                pendingIconImage?.let { stbi_image_free(it.buffer) }
                pendingIconImage = image
                hasDeferredIconUpdate = true
                return
            }

            applyIcon(image, pollEventsAfterSet = false)
        }

    private fun applyIcon(image: ImageData, pollEventsAfterSet: Boolean) {
        GLFWImage.malloc(1).use {
            image.buffer.rewind()
            it.position(0)
                .width(image.width)
                .height(image.height)
                .pixels(image.buffer)

            it.position(0)
            glfwSetWindowIcon(ptrSupplier(), it)

            if (pollEventsAfterSet) {
                glfwPollEvents()
            }

            stbi_image_free(image.buffer)
        }
    }

    private fun flushDeferredWindowOps() {
        if (!hasDeferredIconUpdate) return

        val image = pendingIconImage
        hasDeferredIconUpdate = false
        pendingIconImage = null

        if (image != null) {
            applyIcon(image, pollEventsAfterSet = true)
        }
    }

    private val w = BufferUtils.createIntBuffer(1)
    private val h = BufferUtils.createIntBuffer(1)

    override val size: ImVec2
        get() {
            (w as Buffer).position(0)
            (h as Buffer).position(0)

            glfwGetWindowSize(ptrSupplier(), w, h)
            return ImVec2(w.get(0).toFloat(), h.get(0).toFloat())
        }

    override var visible: Boolean
        get() = glfwGetWindowAttrib(ptrSupplier(), GLFW_VISIBLE) == GLFW_TRUE
        set(value) {
            if(value) {
                glfwShowWindow(ptrSupplier())
            } else {
                glfwHideWindow(ptrSupplier())
            }
        }

    private var hasCalledMaximized = false

    override var maximized: Boolean
        get() = glfwGetWindowAttrib(ptrSupplier(), GLFW_MAXIMIZED) == GLFW_TRUE
        set(value) {
            if(!hasCalledMaximized) {
                glfwSetWindowSizeCallback(ptrSupplier(), null) // get rid of the stupid imgui callback

                hasCalledMaximized = true
            }

            if(value) {
                glfwMaximizeWindow(ptrSupplier())
            } else {
                glfwRestoreWindow(ptrSupplier())
            }
        }

    override var focus: Boolean
        get() = glfwGetWindowAttrib(ptrSupplier(), GLFW_FOCUSED) == GLFW_TRUE
        set(value) {
            if(value) {
                glfwFocusWindow(ptrSupplier())
            } else {
                glfwFocusWindow(0)
            }
        }

    override fun requestFocus() {
        glfwFocusWindow(ptrSupplier())
    }

    override fun close() {
        glfwSetWindowShouldClose(ptrSupplier(), true)
    }

    override fun setCloseListener(listener: (() -> Boolean)?) {
        currentCloseListener = listener
    }

    private fun ensureCloseListenerInitialized() {
        if (!closeCallbackSet && currentCloseListener != null) {
            glfwSetWindowCloseCallback(ptrSupplier()) {
                val shouldClose = currentCloseListener?.invoke() ?: true
                glfwSetWindowShouldClose(ptrSupplier(), shouldClose)
            }
            closeCallbackSet = true
        }
    }

    fun processWindowOps() {
        flushDeferredWindowOps()
        ensureCloseListenerInitialized()
    }

    private val handleType by lazy {
        when(Platform.get()) {
            Platform.LINUX -> NFD_WINDOW_HANDLE_TYPE_X11
            Platform.MACOSX -> NFD_WINDOW_HANDLE_TYPE_COCOA
            Platform.WINDOWS -> NFD_WINDOW_HANDLE_TYPE_WINDOWS
            else -> NFD_WINDOW_HANDLE_TYPE_UNSET
        }.toLong()
    }

    private val nativeHandle by lazy {
        when(Platform.get()) {
            Platform.LINUX -> glfwGetX11Window(ptrSupplier())
            Platform.MACOSX -> glfwGetCocoaWindow(ptrSupplier())
            Platform.WINDOWS -> glfwGetWin32Window(ptrSupplier())
            else -> NULL
        }
    }

    override fun saveFileDialog(
        content: ByteArray,
        defaultName: String,
        vararg platformFileFilter: PlatformFileFilter
    ): PlatformFileChooserResult {
        stackPush().use { stack ->
            val filters = NFDFilterItem.malloc(platformFileFilter.size)
            for((i, filter) in platformFileFilter.withIndex()) {
                filters.get(i)
                    .name(stack.UTF8(filter.name))
                    .spec(stack.UTF8(filter.extensions.joinToString(",")))
            }

            val pp = stack.mallocPointer(1)
            val result = NFD_SaveDialog_With(pp, NFDSaveDialogArgs.calloc(stack)
                .filterList(filters)
                .defaultName(stack.UTF8(defaultName))
                .parentWindow {
                    it.type(handleType)
                    it.handle(nativeHandle)
                }
            )

            return when(result) {
                NFD_OKAY -> {
                    val path = pp.getStringUTF8(0)
                    val file = File(path)
                    file.writeBytes(content)

                    PlatformFileChooserResult.OK
                }
                else -> PlatformFileChooserResult.CANCELLED
            }
        }
    }

}



