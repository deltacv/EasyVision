package org.deltacv.visiongraph.serialization.v2.json

import kotlinx.serialization.json.*
import org.deltacv.visiongraph.serialization.v2.*

class JsonDataDecoder(
    private val obj: JsonObject
) : DataDecoder {

    override fun has(name: String) = obj.containsKey(name)

    override fun int(name: String) = safe(name) { obj[name]!!.jsonPrimitive.int }
    override fun float(name: String) = safe(name) { obj[name]!!.jsonPrimitive.float }
    override fun double(name: String) = safe(name) { obj[name]!!.jsonPrimitive.double }
    override fun string(name: String) = safe(name) { obj[name]!!.jsonPrimitive.content }
    override fun bool(name: String) = safe(name) { obj[name]!!.jsonPrimitive.boolean }

    override fun obj(name: String): DataCodec = safe(name) {
        val wrapper = obj[name]!!.jsonObject
        val type = wrapper["_type"]!!.jsonPrimitive.content
        val data = wrapper["_data"]!!.jsonObject

        val instance = CodecTypeRegistry.create(type)
        instance.decode(JsonDataDecoder(data))
        instance
    }

    override fun obj(name: String, target: DataCodec) = safe(name) {
        val wrapper = obj[name]!!.jsonObject
        val data = wrapper["_data"]!!.jsonObject
        target.decode(JsonDataDecoder(data))
    }

    // ---- Typed list readers ----

    override fun intList(name: String) = safe(name) {
        obj[name]!!.jsonArray.map { it.jsonPrimitive.int }
    }

    override fun floatList(name: String) = safe(name) {
        obj[name]!!.jsonArray.map { it.jsonPrimitive.float }
    }

    override fun doubleList(name: String) = safe(name) {
        obj[name]!!.jsonArray.map { it.jsonPrimitive.double }
    }

    override fun stringList(name: String) = safe(name) {
        obj[name]!!.jsonArray.map { it.jsonPrimitive.content }
    }

    override fun boolList(name: String) = safe(name) {
        obj[name]!!.jsonArray.map { it.jsonPrimitive.boolean }
    }

    override fun objList(name: String) = safe(name) {
        obj[name]!!.jsonArray.map { element ->
            val wrapper = element.jsonObject
            val type = wrapper["_type"]!!.jsonPrimitive.content
            val data = wrapper["_data"]!!.jsonObject

            val instance = CodecTypeRegistry.create(type)
            instance.decode(JsonDataDecoder(data))
            instance
        }
    }

    override fun <C: DataCodec> objList(name: String, targets: List<C>) = safe(name) {
        objList(name, targetFactory = { index ->
            if (index >= targets.size) {
                throw MalformedDataException("Not enough target objects provided for objList with name $name", obj)
            }
            targets[index]
        })
    }

    override fun <C: DataCodec> objList(name: String, targetFactory: (Int) -> C) = safe(name) {
        obj[name]!!.jsonArray.mapIndexed { index, element ->
            val wrapper = element.jsonObject
            val data = wrapper["_data"]!!.jsonObject

            val target = targetFactory(index)
            target.decode(JsonDataDecoder(data))
            target
        }
    }

    // ---- Entry readers ----

    override fun intEntries() =
        obj.entries.filter { it.value is JsonPrimitive && it.value.jsonPrimitive.intOrNull != null }
            .associate { it.key to it.value.jsonPrimitive.int }

    override fun floatEntries() =
        obj.entries.filter { it.value is JsonPrimitive && it.value.jsonPrimitive.floatOrNull != null }
            .associate { it.key to it.value.jsonPrimitive.float }

    override fun doubleEntries() =
        obj.entries.filter { it.value is JsonPrimitive && it.value.jsonPrimitive.doubleOrNull != null }
            .associate { it.key to it.value.jsonPrimitive.double }

    override fun stringEntries() =
        obj.entries.filter { it.value is JsonPrimitive && it.value.jsonPrimitive.isString }
            .associate { it.key to it.value.jsonPrimitive.content }

    override fun boolEntries() =
        obj.entries.filter { it.value is JsonPrimitive && it.value.jsonPrimitive.booleanOrNull != null }
            .associate { it.key to it.value.jsonPrimitive.boolean }

    override fun objEntries() =
        obj.entries
            .filter { it.value is JsonObject && it.value.jsonObject.containsKey("_type") && it.value.jsonObject.containsKey("_data") }
            .associate { it.key to obj(it.key) }

    override fun intListEntries() =
        obj.entries
            .filter { it.value is JsonArray && it.value.jsonArray.all { e -> e is JsonPrimitive && e.jsonPrimitive.intOrNull != null } }
            .associate { it.key to it.value.jsonArray.map { e -> e.jsonPrimitive.int } }

    override fun floatListEntries() =
        obj.entries
            .filter { it.value is JsonArray && it.value.jsonArray.all { e -> e is JsonPrimitive && e.jsonPrimitive.floatOrNull != null } }
            .associate { it.key to it.value.jsonArray.map { e -> e.jsonPrimitive.float } }

    override fun doubleListEntries() =
        obj.entries
            .filter { it.value is JsonArray && it.value.jsonArray.all { e -> e is JsonPrimitive && e.jsonPrimitive.doubleOrNull != null } }
            .associate { it.key to it.value.jsonArray.map { e -> e.jsonPrimitive.double } }

    override fun stringListEntries() =
        obj.entries
            .filter { it.value is JsonArray && it.value.jsonArray.all { e -> e is JsonPrimitive && e.jsonPrimitive.isString } }
            .associate { it.key to it.value.jsonArray.map { e -> e.jsonPrimitive.content } }

    override fun boolListEntries() =
        obj.entries
            .filter { it.value is JsonArray && it.value.jsonArray.all { e -> e is JsonPrimitive && e.jsonPrimitive.booleanOrNull != null } }
            .associate { it.key to it.value.jsonArray.map { e -> e.jsonPrimitive.boolean } }

    override fun objListEntries() =
        obj.entries
            .filter { it.value is JsonArray && it.value.jsonArray.all { e -> e is JsonObject && e.jsonObject.containsKey("_type") && e.jsonObject.containsKey("_data") } }
            .associate { it.key to objList(it.key) }

    private inline fun <R> safe(name: String, crossinline callback: (Unit) -> R): R {
        if (!has(name)) {
            throw MalformedDataException("Data entry with name $name does not exist", obj)
        }

        return try {
            callback(Unit)
        } catch(e: Exception) {
            throw MalformedDataException("Error reading data entry with name $name: ${e.message}", obj, e)
        }
    }
}



