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

package org.deltacv.visiongraph.plugin.project.recovery

import com.github.serivesmejia.eocvsim.util.JavaProcess
import com.github.serivesmejia.eocvsim.util.JavaProcess.SLF4JIOReceiver
import org.deltacv.common.util.loggerForThis
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.lang.Exception
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class RecoveryDaemonProcessManager(
    val classpath: String
) {

    val executor = Executors.newFixedThreadPool(2)!!

    private val server = WsServer(this)

    val logger by loggerForThis()

    fun start() {
        server.start()
    }

    private fun submitProcess(port: Int) {
        executor.submit {
            logger.info("Starting project recovery daemon process")

            val exit = JavaProcess.execClasspath(
                RecoveryDaemonClientMain::class.java,
                SLF4JIOReceiver(logger),
                classpath,
                listOf("-Dlogback.configurationFile=logback-nofile.xml"),
                listOf(port.toString())
            )

            logger.warn("Project recovery daemon process exited with code $exit")
        }
    }

    fun sendRecoveryData(recoveryData: RecoveryData) {
        for(conn in server.connections) {
            conn.send(Json.encodeToString(recoveryData))
        }
    }

    private class WsServer(
        val manager: RecoveryDaemonProcessManager
    ) : WebSocketServer(InetSocketAddress("127.0.0.1", 0)) {
        val logger by loggerForThis()

        override fun onOpen(ws: WebSocket, p1: ClientHandshake?) {
            logger.info("Client connected ${ws.localSocketAddress}")
        }

        override fun onClose(ws: WebSocket, p1: Int, p2: String?, p3: Boolean) {
            logger.info("Client disconnected ${ws.localSocketAddress}")
        }

        override fun onMessage(p0: WebSocket?, p1: String?) { }

        override fun onError(p0: WebSocket?, p1: Exception?) {
            logger.error("Error in recovery daemon server", p1)
        }

        override fun onStart() {
            logger.info("Recovery daemon server started in port $port")
            manager.submitProcess(port)
        }
    }

}



