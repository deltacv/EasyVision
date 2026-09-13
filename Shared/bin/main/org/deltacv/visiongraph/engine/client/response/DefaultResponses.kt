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

package org.deltacv.visiongraph.engine.client.response

import org.deltacv.visiongraph.serialization.PolymorphicSerializable
import kotlinx.serialization.Serializable

@Serializable
@PolymorphicSerializable(PaperVisionEngineMessageResponse::class)
open class OkResponse(val info: String = "OK") : PaperVisionEngineMessageResponse() {
    override val status = true

    override fun toString() = "OkResponse(type=\"${this::class.java.typeName}\", info=\"$info\")"
}

@Serializable
@PolymorphicSerializable(PaperVisionEngineMessageResponse::class)
open class ErrorResponse(val reason: String, val stackTrace: Array<String>? = null) : PaperVisionEngineMessageResponse() {
    override val status = false

    constructor(reason: String, throwable: Throwable) : this(reason, throwable.stackTrace.map { it.toString() }.toTypedArray())

    override fun toString() = "ErrorResponse(type=\"${this::class.java.typeName}\", reason=\"$reason\", exception=\"${stackTrace?.getOrNull(0)}\")"
}



