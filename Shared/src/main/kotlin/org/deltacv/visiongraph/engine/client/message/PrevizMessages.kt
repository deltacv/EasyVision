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

package org.deltacv.visiongraph.engine.client.message

import org.deltacv.visiongraph.serialization.PolymorphicSerializable
import kotlinx.serialization.Contextual
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement

@Serializable
@PolymorphicSerializable(PaperVisionEngineMessage::class)
class PrevizPingMessage(
    val previzName: String
) : PaperVisionEngineMessageBase()

@Serializable
@PolymorphicSerializable(PaperVisionEngineMessage::class)
class PrevizStartMessage(
    val previzName: String,
    val sourceCode: String,
    val streamWidth: Int,
    val streamHeight: Int
) : PaperVisionEngineMessageBase()

@Serializable
@PolymorphicSerializable(PaperVisionEngineMessage::class)
class PrevizSourceCodeMessage(
    val previzName: String,
    val sourceCode: String
) : PaperVisionEngineMessageBase()

@Serializable
@PolymorphicSerializable(PaperVisionEngineMessage::class)
class PrevizStopMessage(
    val previzName: String
) : PaperVisionEngineMessageBase()

@Serializable
@PolymorphicSerializable(PaperVisionEngineMessage::class)
class PrevizAskNameMessage : PaperVisionEngineMessageBase()

@Serializable
sealed class TunerValue {

    abstract fun asAny(): Any?

    @Serializable
    data class IntValue(val value: Int) : TunerValue() {
        override fun asAny(): Any = value
    }

    @Serializable
    data class DoubleValue(val value: Double) : TunerValue() {
        override fun asAny(): Any = value
    }

    @Serializable
    data class BooleanValue(val value: Boolean) : TunerValue() {
        override fun asAny(): Any = value
    }

    @Serializable
    data class StringValue(val value: String) : TunerValue() {
        override fun asAny(): Any = value
    }

    @Serializable
    data class ListValue(val values: List<TunerValue>) : TunerValue() {
        override fun asAny(): List<Any?> = values.map { it.asAny() }
    }

    @Serializable
    object NullValue : TunerValue() {
        override fun asAny() = null
    }

}

@Serializable
@PolymorphicSerializable(PaperVisionEngineMessage::class)
class TunerChangeValueMessage(
    val label: String,
    val value: TunerValue
) : PaperVisionEngineMessageBase()



