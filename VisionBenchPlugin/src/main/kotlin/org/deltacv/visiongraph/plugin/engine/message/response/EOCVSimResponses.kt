/*
 * VisionGraph
 * Copyright (C) 2024 Sebastian Erives, deltacv

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.deltacv.visiongraph.plugin.engine.message.response

import org.deltacv.visiongraph.engine.client.message.PaperVisionEngineMessage
import org.deltacv.visiongraph.engine.client.response.OkResponse
import org.deltacv.visiongraph.engine.client.response.PaperVisionEngineMessageResponse
import org.deltacv.visiongraph.plugin.engine.message.IpcInputSourceData
import org.deltacv.visiongraph.serialization.PolymorphicSerializable
import kotlinx.serialization.Serializable

@Serializable
@PolymorphicSerializable(PaperVisionEngineMessageResponse::class)
class InputSourcesListResponse(
    val sources: Array<IpcInputSourceData>
) : OkResponse()



