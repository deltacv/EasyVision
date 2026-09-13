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

package org.deltacv.visiongraph.engine

import org.deltacv.visiongraph.engine.bridge.PaperVisionEngineBridge
import org.deltacv.visiongraph.engine.client.response.ErrorResponse
import org.deltacv.visiongraph.engine.client.message.PaperVisionEngineMessage
import org.deltacv.visiongraph.engine.client.response.PaperVisionEngineMessageResponse
import kotlin.reflect.KClass

class LocalPaperVisionEngine : PaperVisionEngine {

    var bridge: PaperVisionEngineBridge? = null

    private val messageQueue = mutableListOf<PaperVisionEngineMessage>()
    private val messageHandlers = mutableMapOf<Class<out PaperVisionEngineMessage>, PaperVisionMessageHandlerCtx<*>.() -> Unit>()

    override fun acceptMessage(message: PaperVisionEngineMessage) {
        synchronized(messageQueue) {
            messageQueue.add(message)
        }
    }

    override fun sendResponse(response: PaperVisionEngineMessageResponse) {
        bridge?.acceptResponse(response)
    }

    override fun sendBytes(bytes: ByteArray) {
        bridge?.broadcastBytes(bytes)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: PaperVisionEngineMessage> setMessageHandlerOf(type: KClass<T>, handler: PaperVisionMessageHandlerCtx<T>.() -> Unit) {
        messageHandlers[type.java] = handler as PaperVisionMessageHandlerCtx<*>.() -> Unit
    }

    inline fun <reified T : PaperVisionEngineMessage> setMessageHandlerOf(noinline handler: PaperVisionMessageHandlerCtx<T>.() -> Unit) {
        setMessageHandlerOf(T::class, handler)
    }

    fun process() {
        val messages = synchronized(messageQueue) {
            messageQueue.toTypedArray()
        }

        messages.forEach { message ->
            try {
                // find the handler with the topmost class
                val handler =
                    messageHandlers.entries.find { (type, _) -> type.isAssignableFrom(message.javaClass) }?.value
                handler?.invoke(PaperVisionMessageHandlerCtx(this, message))
            } catch(e: Exception) {
                sendResponse(ErrorResponse(e.message ?: "An error occurred", e).apply {
                    id = message.id
                })
            } finally {
                synchronized(messageQueue) {
                    messageQueue.remove(message)
                }
            }
        }
    }

}

class PaperVisionMessageHandlerCtx<T: PaperVisionEngineMessage>(
    val engine: LocalPaperVisionEngine,
    val message: T
) {
    fun respond(response: PaperVisionEngineMessageResponse) {
        response.id = message.id
        engine.sendResponse(response)
    }
}



