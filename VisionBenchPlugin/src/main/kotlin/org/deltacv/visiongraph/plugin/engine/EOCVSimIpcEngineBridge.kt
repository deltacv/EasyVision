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

package org.deltacv.visiongraph.plugin.engine

import org.deltacv.visiongraph.util.loggerForThis
import org.deltacv.visiongraph.engine.bridge.PaperVisionEngineBridge
import org.deltacv.visiongraph.engine.client.PaperVisionEngineClient
import org.deltacv.visiongraph.engine.client.message.TunerChangeValueMessage
import org.deltacv.visiongraph.engine.client.message.PaperVisionEngineMessage
import org.deltacv.visiongraph.engine.client.response.PaperVisionEngineMessageResponse
import org.deltacv.visiongraph.plugin.engine.message.EditorChangeMessage
import org.deltacv.visiongraph.util.event.PaperEventHandler
import org.java_websocket.client.WebSocketClient
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class EOCVSimIpcEngineBridge(private val port: Int) : PaperVisionEngineBridge {

    companion object {
        var logHighFrequencyMessages = true

        val blacklistedSentTooOftenMessages = listOf(
            EditorChangeMessage::class,
            TunerChangeValueMessage::class
        )
    }
    
    private val highFrequencyMessages = mutableMapOf<String, MutableList<Long>>()

    private val clients = mutableListOf<PaperVisionEngineClient>()

    override val onClientProcess = PaperEventHandler("LocalPaperVisionEngineBridge-OnClientProcess")

    private var wsClient = WsClient(port, this)

    val logger by loggerForThis()

    override val isConnected: Boolean
        get() = wsClient.isOpen

    @Synchronized
    override fun connectClient(client: PaperVisionEngineClient) {
        clients.add(client)

        ensureConnected()

        client.onProcess {
            if(!clients.contains(client)) {
                removeListener()
                return@onProcess
            }

            onClientProcess.run()
        }
    }

    @Synchronized
    override fun terminate(client: PaperVisionEngineClient) {
        clients.remove(client)
    }

    @Synchronized
    override fun sendMessage(client: PaperVisionEngineClient, message: PaperVisionEngineMessage) {
        if(!clients.contains(client)) {
            throw IllegalArgumentException("Client is not connected to this bridge")
        }

        ensureConnected()

        val messageJson = ipcJson.encodeToString(PaperVisionEngineMessage.serializer(), message)

        wsClient.send(messageJson)

        if(logHighFrequencyMessages && message::class !in blacklistedSentTooOftenMessages) {
            val messages = highFrequencyMessages.getOrPut(message::class.java.name) { mutableListOf() }
            messages.add(System.currentTimeMillis())

            if(messages.size > 20) { // buffer size
                messages.removeAt(0)
            }

            // calculate avg time delta
            val avgDelta = messages.zipWithNext().map {
                it.second - it.first
            }.average()

            if(avgDelta < 100) {
                // get calling class
                val callingClass = Thread.currentThread().stackTrace.getOrNull(2) ?: return
                logger.warn("Sent too often: ${message::class.java.name} - avg $avgDelta. Last sent at ${callingClass.className} line ${callingClass.lineNumber}")
            }
        }
    }

    private fun ensureConnected() {
        if(!wsClient.isOpen) {
            wsClient = WsClient(port, this)
            wsClient.connectBlocking(5, TimeUnit.SECONDS)
        }
    }

    override fun broadcastBytes(bytes: ByteArray) { 
        for(client in clients) {
            client.acceptBytes(bytes)
        }
    }

    override fun acceptResponse(response: PaperVisionEngineMessageResponse) {
        for(client in clients) {
            client.acceptResponse(response)
        }
    }

    class WsClient(
        val port: Int,
        val bridge: EOCVSimIpcEngineBridge
    ) : WebSocketClient(URI.create("ws://127.0.0.1:$port")) {

        private val logger = LoggerFactory.getLogger(this::class.java)

        override fun onOpen(handshakedata: org.java_websocket.handshake.ServerHandshake?) {
            logger.info("Connected to server in $port")
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            logger.info("Disconnected from server")
        }

        override fun onMessage(message: String) {
            val response = ipcJson.decodeFromString(PaperVisionEngineMessageResponse.serializer(), message)
            bridge.acceptResponse(response)
        }

        override fun onMessage(bytes: ByteBuffer) {
            bridge.broadcastBytes(bytes.array())
        }

        override fun onError(ex: Exception) {
            logger.error("Error on connection", ex)
        }
    }
}



