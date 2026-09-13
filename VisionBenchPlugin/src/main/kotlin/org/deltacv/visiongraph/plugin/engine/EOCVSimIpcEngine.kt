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
import org.deltacv.visiongraph.engine.MessageHandlerPaperVisionEngine
import org.deltacv.visiongraph.engine.client.message.PaperVisionEngineMessage
import org.deltacv.visiongraph.engine.client.response.PaperVisionEngineMessageResponse
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.lang.Exception
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class EOCVSimIpcEngine : MessageHandlerPaperVisionEngine() {

    val logger by loggerForThis()

    val server = WsServer(this).apply {
        start()
    }

    override fun sendBytes(bytes: ByteArray) {
        server.broadcast(bytes)
    }

    override fun sendResponse(response: PaperVisionEngineMessageResponse) {
        server.broadcast(ipcJson.encodeToString(PaperVisionEngineMessageResponse.serializer(), response))
    }

    class WsServer(val engine: EOCVSimIpcEngine) : WebSocketServer(InetSocketAddress("127.0.0.1",0)) {
        val logger by loggerForThis()

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake?) {
            // only allow connections from localhost
            val hostString = conn.localSocketAddress.hostString
            if(hostString != "127.0.0.1" && hostString != "localhost" && hostString != "0.0.0.0") {
                logger.warn("Connection from ${conn.remoteSocketAddress} refused, only localhost connections are allowed")
                conn.close(1013, "Ipc does not allow connections incoming from non-localhost addresses")
            }

            logger.info("New connection: ${conn.remoteSocketAddress}")
        }

        override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
            logger.info("Connection closed: ${conn?.remoteSocketAddress}")
        }

        override fun onMessage(conn: WebSocket, message: String) {
            try {
                val engineMessage = ipcJson.decodeFromString(PaperVisionEngineMessage.serializer(), message)
                logger.trace("Message {}: {}", engineMessage::class.simpleName, message)
                engine.acceptMessage(engineMessage)
            } catch(e: Throwable) {
                logger.error("Error decoding message: $message", e)
            }
        }

        override fun onError(conn: WebSocket?, ex: Exception?) {
            logger.error("Error on connection ${conn?.remoteSocketAddress}", ex)
        }

        override fun onStart() {
            logger.info("WebSocket server started on port ${address.port}")
        }
    }
}



