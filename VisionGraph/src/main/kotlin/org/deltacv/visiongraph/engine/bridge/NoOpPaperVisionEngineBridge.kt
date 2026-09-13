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

package org.deltacv.visiongraph.engine.bridge

import org.deltacv.visiongraph.engine.client.PaperVisionEngineClient
import org.deltacv.visiongraph.engine.client.message.PaperVisionEngineMessage
import org.deltacv.visiongraph.engine.client.response.PaperVisionEngineMessageResponse
import org.deltacv.visiongraph.util.event.PaperEventHandler

object NoOpPaperVisionEngineBridge : PaperVisionEngineBridge {
    override val isConnected: Boolean
        get() = false

    override val onClientProcess = PaperEventHandler("LocalPaperVisionEngineBridge-OnClientProcess")

    override fun connectClient(client: PaperVisionEngineClient) {
        // No-op
    }

    override fun terminate(client: PaperVisionEngineClient) {
        // No-op
    }

    override fun broadcastBytes(bytes: ByteArray) {
        // No-op
    }

    override fun sendMessage(client: PaperVisionEngineClient, message: PaperVisionEngineMessage) {
        // No-op
    }

    override fun acceptResponse(response: PaperVisionEngineMessageResponse) {
        // No-op
    }
}



