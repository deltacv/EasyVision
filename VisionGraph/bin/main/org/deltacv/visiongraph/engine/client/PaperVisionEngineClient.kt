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

package org.deltacv.visiongraph.engine.client

import org.deltacv.visiongraph.engine.bridge.PaperVisionEngineBridge
import org.deltacv.visiongraph.engine.ByteMessageTag
import org.deltacv.visiongraph.engine.ByteMessages
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.BufferOverflow
import org.deltacv.visiongraph.engine.client.message.PaperVisionEngineMessage
import org.deltacv.visiongraph.engine.client.response.PaperVisionEngineMessageResponse
import org.deltacv.visiongraph.util.event.PaperEventHandler
import org.deltacv.visiongraph.util.loggerForThis

class ClientByteMessageReceiver : ByteMessageReceiver()

class PaperVisionEngineClient(
    val bridge: PaperVisionEngineBridge
) {

    private data class AwaitingMessageData(
        val message: PaperVisionEngineMessage,
        val timestamp: Long
    )

    val logger by loggerForThis()

    val byteReceiver = ClientByteMessageReceiver()

    val onProcess = PaperEventHandler("PaperVisionEngineClient-OnProcess")

    private val messagesAwaitingResponse = mutableMapOf<Int, AwaitingMessageData>()
    private val mapMutex = Mutex()

    private val bytesChannel = Channel<ByteArray>(capacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun connect() {
        logger.info("Connecting through bridge ${bridge.javaClass.simpleName}")
        bridge.connectClient(this)
    }

    fun disconnect() {
        logger.info("Disconnecting from bridge ${bridge.javaClass.simpleName}")
        bridge.terminate(this)
    }

    fun acceptResponse(response: PaperVisionEngineMessageResponse) {
        val data = runBlocking { mapMutex.withLock { messagesAwaitingResponse[response.id] } } ?: return
        if (!data.message.persistent) {
            runBlocking { mapMutex.withLock { messagesAwaitingResponse.remove(response.id) } }
        }

        data.message.acceptResponse(response)
    }

    fun acceptBytes(bytes: ByteArray) {
        bytesChannel.trySend(bytes)
    }

    fun sendMessage(message: PaperVisionEngineMessage) {
        runBlocking { mapMutex.withLock { messagesAwaitingResponse[message.id] = AwaitingMessageData(message, System.currentTimeMillis()) } }
        bridge.sendMessage(this, message)
    }

    fun process() {
        val now = System.currentTimeMillis()
        val droppedOrphans = mutableListOf<Int>()

        // Snapshot under the lock so JPEG-worker mutations don't race with our iteration.
        val activeTrackingState = runBlocking { mapMutex.withLock { messagesAwaitingResponse.toMap() } }

        for ((id, data) in activeTrackingState) {
            val timeMillis = now - data.timestamp
            data.message.acceptElapsedTime(timeMillis)

            // Hard TTL bound to purge orphaned requests disconnected from network
            if (timeMillis > 5000L && !data.message.persistent) {
                droppedOrphans.add(id)
            }
        }

        if (droppedOrphans.isNotEmpty()) {
            runBlocking {
                mapMutex.withLock {
                    droppedOrphans.forEach { messagesAwaitingResponse.remove(it) }
                }
            }
        }

        val binaryMessages = buildList {
            while (true) {
                val bytes = bytesChannel.tryReceive().getOrNull() ?: break
                add(bytes)
            }
        }

        binaryMessages.forEach {
            val tag = ByteMessageTag(ByteMessages.tagFromBytes(it))
            val id = ByteMessages.idFromBytes(it)

            byteReceiver.callHandlers(id, tag.toString(), it, ByteMessages.messageLengthFromBytes(it))
        }

        onProcess.run()
    }
}