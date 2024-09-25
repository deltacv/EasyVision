/*
 * Copyright (c) 2021 Sebastian Erives
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.deltacv.papervision

import imgui.ImGui
import imgui.flag.ImGuiCond
import io.github.deltacv.papervision.attribute.Attribute
import io.github.deltacv.papervision.codegen.CodeGenManager
import io.github.deltacv.papervision.gui.*
import io.github.deltacv.papervision.gui.style.imnodes.ImNodesDarkStyle
import io.github.deltacv.papervision.gui.util.Popup
import io.github.deltacv.papervision.gui.util.Window
import io.github.deltacv.papervision.id.IdElementContainer
import io.github.deltacv.papervision.id.IdElementContainerStack
import io.github.deltacv.papervision.id.NoneIdElement
import io.github.deltacv.papervision.io.KeyManager
import io.github.deltacv.papervision.node.Link
import io.github.deltacv.papervision.node.Node
import io.github.deltacv.papervision.platform.*
import io.github.deltacv.papervision.serialization.PaperVisionSerializer
import io.github.deltacv.papervision.util.event.PaperVisionEventHandler
import io.github.deltacv.papervision.util.loggerForThis
import io.github.deltacv.mai18n.Language
import io.github.deltacv.papervision.engine.bridge.NoOpPaperVisionEngineBridge
import io.github.deltacv.papervision.engine.client.PaperVisionEngineClient
import io.github.deltacv.papervision.engine.client.message.PrevizAskNameMessage
import io.github.deltacv.papervision.engine.client.response.StringResponse
import io.github.deltacv.papervision.engine.previz.ClientPrevizManager
import io.github.deltacv.papervision.gui.eocvsim.ImageDisplay
import io.github.deltacv.papervision.gui.style.CurrentStyles
import io.github.deltacv.papervision.gui.util.Popup.Companion.WARN
import io.github.deltacv.papervision.io.TextureProcessorQueue
import io.github.deltacv.papervision.node.NodeRegistry

class PaperVision(
    private val setupCall: PlatformSetupCallback
) {

    companion object {
        var imnodesStyle
            set(value) { CurrentStyles.imnodesStyle = value }
            get() = CurrentStyles.imnodesStyle

        lateinit var defaultImGuiFont: Font
            private set

        val miscIds = IdElementContainer<NoneIdElement>()

        init {
            imnodesStyle = ImNodesDarkStyle
        }
    }

    val logger by loggerForThis()

    lateinit var setup: PlatformSetup
        private set
    lateinit var window: PlatformWindow
        private set
    lateinit var textureFactory: PlatformTextureFactory
        private set

    val onUpdate = PaperVisionEventHandler("PaperVision-OnUpdate")

    lateinit var textureProcessorQueue: TextureProcessorQueue
        private set

    lateinit var keyManager: KeyManager
        private set

    val codeGenManager = CodeGenManager(this)
    val fontManager = FontManager()

    val langManager = Language("/lang.csv", "en").makeTr()

    val nodeEditor by lazy { NodeEditor(this, keyManager) }
    val nodeList by lazy { NodeList(this, keyManager, NodeRegistry.nodes) }

    val nodes = IdElementContainer<Node<*>>()
    val attributes = IdElementContainer<Attribute>()
    val links = IdElementContainer<Link>()
    val windows = IdElementContainer<Window>()
    val textures = IdElementContainer<PlatformTexture>()
    val streamDisplays = IdElementContainer<ImageDisplay>()

    val popups = IdElementContainer<Popup>().apply {
        reserveId(WARN)
    }

    lateinit var engineClient: PaperVisionEngineClient

    lateinit var previzManager: ClientPrevizManager
        private set

    lateinit var defaultFont: Font
        private set

    fun init() {
        IdElementContainerStack.threadStack.push(nodes)
        IdElementContainerStack.threadStack.push(attributes)
        IdElementContainerStack.threadStack.push(links)
        IdElementContainerStack.threadStack.push(windows)
        IdElementContainerStack.threadStack.push(popups)
        IdElementContainerStack.threadStack.push(textures)
        IdElementContainerStack.threadStack.push(streamDisplays)

        logger.info("Starting PaperVision...")

        logger.info("Using the ${setupCall.name} platform")
        setup = setupCall.setup()

        keyManager = KeyManager(setup.keys ?: throw IllegalArgumentException("Platform ${setup.name} must provide PlatformKeys"))
        window = setup.window ?: throw IllegalArgumentException("Platform ${setup.name} must provide a Window")
        textureFactory = setup.textureFactory ?: throw IllegalArgumentException("Platform ${setup.name} must provide a TextureFactory")

        textureProcessorQueue = TextureProcessorQueue(textureFactory)
        textureProcessorQueue.subscribeTo(onUpdate)

        engineClient = PaperVisionEngineClient(setup.engineBridge ?: NoOpPaperVisionEngineBridge)
        previzManager = ClientPrevizManager(160, 120, codeGenManager, textureProcessorQueue, engineClient)

        engineClient.connect()

        // disable annoying ini file creation (hopefully shouldn't break anything)
        ImGui.getIO().iniFilename = null
        ImGui.getIO().logFilename = null

        // initializing fonts right after the imgui context is created
        // we can't create fonts mid-frame so that's kind of a problem
        defaultFont = fontManager.makeFont("/fonts/Calcutta-Regular.otf", 13f)
        defaultImGuiFont = fontManager.makeDefaultFont(13f)

        nodeEditor.enable()
        langManager.loadIfNeeded()

        nodeList.enable()

        IdElementContainerStack.threadStack.pop<Node<*>>()
        IdElementContainerStack.threadStack.pop<Attribute>()
        IdElementContainerStack.threadStack.pop<Link>()
        IdElementContainerStack.threadStack.pop<Window>()
        IdElementContainerStack.threadStack.pop<Popup>()
        IdElementContainerStack.threadStack.pop<PlatformTexture>()
        IdElementContainerStack.threadStack.pop<ImageDisplay>()
    }

    fun firstProcess() {
        window.title = "PaperVision"
        window.icon = "/ico/ico_ezv.png"
    }

    fun process() {
        IdElementContainerStack.threadStack.push(nodes)
        IdElementContainerStack.threadStack.push(attributes)
        IdElementContainerStack.threadStack.push(links)
        IdElementContainerStack.threadStack.push(windows)
        IdElementContainerStack.threadStack.push(popups)
        IdElementContainerStack.threadStack.push(textures)
        IdElementContainerStack.threadStack.push(streamDisplays)

        onUpdate.run()

        engineClient.process()

        ImGui.setNextWindowPos(0f, 0f, ImGuiCond.Always)

        val size = window.size
        ImGui.setNextWindowSize(size.x, size.y, ImGuiCond.Always)

        ImGui.pushFont(defaultFont.imfont)

        for(window in windows.inmutable) {
            window.draw()
        }
        for(tooltip in popups.inmutable) {
            tooltip.draw()
        }

        ImGui.popFont()

        if(keyManager.pressed(keyManager.keys.ArrowUp)) {
            println(PaperVisionSerializer.serialize(nodes.elements, links.elements))
        }

        keyManager.update()

        previzManager.update()

        IdElementContainerStack.threadStack.pop<Node<*>>()
        IdElementContainerStack.threadStack.pop<Attribute>()
        IdElementContainerStack.threadStack.pop<Link>()
        IdElementContainerStack.threadStack.pop<Window>()
        IdElementContainerStack.threadStack.pop<Popup>()
        IdElementContainerStack.threadStack.pop<PlatformTexture>()
        IdElementContainerStack.threadStack.pop<ImageDisplay>()
    }

    fun destroy() {
        nodeEditor.destroy()
    }

    fun startPrevizAsk() {
        engineClient.sendMessage(PrevizAskNameMessage().onResponseWith<StringResponse> { response ->
            logger.info("Engine responded with previz name ${response.value}")
            onUpdate.doOnce {
                previzManager.startPreviz(response.value)
                window.title = "PaperVision - ${response.value}"
            }
        })
    }
}