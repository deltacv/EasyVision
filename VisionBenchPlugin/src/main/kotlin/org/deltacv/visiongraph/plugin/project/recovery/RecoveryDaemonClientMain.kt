package org.deltacv.visiongraph.plugin.project.recovery

import kotlinx.serialization.json.Json
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeText

class RecoveryDaemonClientMain(port: Int) : WebSocketClient(URI("ws://127.0.0.1:" + port)) {
    override fun onOpen(handshakedata: ServerHandshake?) {
        logger.info("Connected to recovery daemon server at port {}", uri.getPort())
    }

    override fun onMessage(message: String) {
        try {
            val recoveryData: RecoveryData = Json.decodeFromString<RecoveryData>(message)
            val recoveryPath = Paths.get(recoveryData.recoveryFolderPath)

            if (!Files.exists(recoveryPath)) {
                Files.createDirectories(recoveryPath)
            }

            val recoveryFilePath = recoveryPath.resolve(recoveryData.recoveryFileName)
            recoveryFilePath.writeText(Json.encodeToString(recoveryData.projectData))
        } catch (e: Exception) {
            logger.error("Failed to save recovery data", e)
        }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
    }

    override fun onError(ex: Exception?) {
    }

    companion object {
        const val MAX_CONNECTION_ATTEMPTS_BEFORE_EXITING: Int = 3

        val logger: Logger = LoggerFactory.getLogger(RecoveryDaemonClientMain::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("apple.awt.UIElement", "true")

            val port = if (args.isNotEmpty()) args[0].toInt() else 17112

            var client: RecoveryDaemonClientMain? = null
            var connectionAttempts = 0

            try {
                while (!Thread.interrupted()) {
                    if (connectionAttempts >= MAX_CONNECTION_ATTEMPTS_BEFORE_EXITING) {
                        logger.warn("Failed to connect to recovery daemon after " + MAX_CONNECTION_ATTEMPTS_BEFORE_EXITING + " attempts. Exiting...")
                        System.exit(0)
                    }

                    if (client == null || client.isClosed()) {
                        client = RecoveryDaemonClientMain(port)
                        client.connect()
                        connectionAttempts += 1
                    }

                    Thread.sleep(100)
                }
            } catch (e: URISyntaxException) {
                throw RuntimeException(e)
            } catch (ignored: InterruptedException) {
            }
        }
    }
}



