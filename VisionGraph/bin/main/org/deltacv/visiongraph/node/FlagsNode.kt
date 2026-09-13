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

package org.deltacv.visiongraph.node

import org.deltacv.visiongraph.serialization.v1.data.SerializeData
import org.deltacv.visiongraph.serialization.v2.CodecType
import org.deltacv.visiongraph.serialization.v2.DataDecoder
import org.deltacv.visiongraph.serialization.v2.DataEncoder

@PaperNode(
    name = "Flags",
    description = "A node that holds flags",
    category = NodeCategory.MISC,
    showInList = false
)
@CodecType
class FlagsNode : InvisibleNode(shouldDraw = false) {

    override val requestedId = 171

    val flags = mutableMapOf<String, Boolean>()
    val numFlags = mutableMapOf<String, Double>()

    override fun encode(encoder: DataEncoder) {
        super.encode(encoder)
        encoder.unignore() // InvisibleNode requests ignore by default, we dont really want that anymore

        for((key, value) in flags) {
            encoder.bool("f_$key", value)
        }

        for((key, value) in numFlags) {
            encoder.double("n_$key", value)
        }
    }

    override fun decode(decoder: DataDecoder) {
        super.decode(decoder)

        val flagsKeys = decoder.boolEntries()
        for((key, value) in flagsKeys) {
            val flagKey = key.removePrefix("f_")
            flags[flagKey] = value
        }

        val numFlagsKeys = decoder.doubleEntries()
        for((key, value) in numFlagsKeys) {
            val flagKey = key.removePrefix("n_")
            numFlags[flagKey] = value
        }
    }

}



