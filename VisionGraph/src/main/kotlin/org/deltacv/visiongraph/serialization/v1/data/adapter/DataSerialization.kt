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

package org.deltacv.visiongraph.serialization.v1.data.adapter

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import org.deltacv.visiongraph.serialization.v1.data.DataSerializable
import org.deltacv.visiongraph.serialization.v1.data.SerializeData
import org.deltacv.visiongraph.serialization.v1.data.SerializeIgnore
import org.deltacv.visiongraph.util.hasSuperclass

fun DataSerializable<*>.toJsonObject(): JsonObject {
    val obj = JsonObject()

    for(field in this::class.java.declaredFields) {
        field.isAccessible = true
        val value = field.get(this) ?: continue

        if(field.isAnnotationPresent(SerializeIgnore::class.java) || field.type.isAnnotationPresent(SerializeIgnore::class.java)) {
            continue
        }

        if(field.type.hasSuperclass(DataSerializable::class.java)) {
            if(!(value as DataSerializable<*>).shouldSerialize) {
                continue
            }

            obj.add(field.name, dataSerializableToJsonObject(value))
        } else if(field.isAnnotationPresent(SerializeData::class.java)) {
            obj.add(field.name, dataSerializableGson.toJsonTree(value))
        }
    }

    return obj
}

fun dataSerializableToJsonObject(value: DataSerializable<*>, context: JsonSerializationContext? = null): JsonElement {
    val data = value.serialize()
    val dataObject = JsonObject()

    dataObject.addProperty("dataClass", data::class.java.name)

    try {
        val jsonData = if(context != null) context.serialize(data) else dataSerializableGson.toJsonTree(data)
        if(jsonData.isJsonObject) {
            if((jsonData as JsonObject).size() > 0) {
                dataObject.add("data", jsonData)
            }
        } else {
            dataObject.add("data", jsonData)
        }
    } catch(e: Exception) {
        throw RuntimeException("Exception while processing data object ${data::class.java.typeName}", e)
    }

    dataObject.addProperty("objectClass", value::class.java.name)

    val jsonObject = value.toJsonObject()
    if(jsonObject.size() > 0) {
        dataObject.add("object", jsonObject)
    }

    return dataObject
}



