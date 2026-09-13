/*
 * PaperVision
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

import org.deltacv.visiongraph.engine.client.response.ErrorResponse
import org.deltacv.visiongraph.engine.client.message.PaperVisionEngineMessage
import org.deltacv.visiongraph.engine.client.response.PaperVisionEngineMessageResponse
import org.deltacv.visiongraph.util.loggerForThis
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

abstract class MessageHandlerPaperVisionEngine : PaperVisionEngine {

    private val logger by loggerForThis()

    private val messageHandlers = mutableMapOf<KClass<out PaperVisionEngineMessage>, MessageHandlerCtx<*>.() -> Unit>()

    override fun acceptMessage(message: PaperVisionEngineMessage) {
        try {
            val handler =
                messageHandlers.entries.find { (messageClass, _) -> message::class == messageClass }?.value
            handler?.invoke(MessageHandlerCtx(this, message))
                ?: logger.warn("No handler found for message of type ${message::class.qualifiedName}")
        } catch(e: Exception) {
            sendResponse(ErrorResponse(e.message ?: "An error occurred", e).apply {
                id = message.id
            })
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: PaperVisionEngineMessage> setMessageHandlerOf(type: KClass<T>, handler: MessageHandlerCtx<T>.() -> Unit) {
        messageHandlers[type] = handler as MessageHandlerCtx<*>.() -> Unit
    }

    inline fun <reified T : PaperVisionEngineMessage> setMessageHandlerOf(noinline handler: MessageHandlerCtx<T>.() -> Unit) {
        setMessageHandlerOf(T::class, handler)
    }

}

class MessageHandlerCtx<T: PaperVisionEngineMessage>(
    val engine: MessageHandlerPaperVisionEngine,
    val message: T
) {
    fun respond(response: PaperVisionEngineMessageResponse) {
        response.id = message.id
        engine.sendResponse(response)
    }
}



