package org.deltacv.visiongraph.serialization.v2.json

import kotlinx.serialization.json.*
import org.deltacv.visiongraph.serialization.v2.*

class JsonDataEncoder : DataEncoder {

    private val map = mutableMapOf<String, JsonElement>()

    fun build(): JsonObject = JsonObject(map)

    override var isIgnored = false
        private set

    override fun int(key: String, value: Int) = safe(key) {
        map[key] = JsonPrimitive(value)
    }

    override fun float(key: String, value: Float) = safe(key) {
        map[key] = JsonPrimitive(value)
    }

    override fun double(key: String, value: Double) = safe(key) {
        map[key] = JsonPrimitive(value)
    }

    override fun bool(key: String, value: Boolean) = safe(key) {
        map[key] = JsonPrimitive(value)
    }

    override fun string(key: String, value: String) = safe(key) {
        map[key] = JsonPrimitive(value)
    }

    override fun obj(key: String, value: DataCodec, typeName: String?) = safe(key) {
        val writer = JsonDataEncoder()
        value.encode(writer)

        if(writer.isIgnored) {
            return@safe // object requested for us to ignore it
        }

        val type = typeName // override typeName with passed parameter first
            ?: CodecTypeRegistry.nameOf(value::class) // look up in the register if no parameter was passed
            ?: throw NoSuchElementException("Unregistered codec: ${value::class}") // oops

        map[key] = buildJsonObject {
            put("_type", type)
            put("_data", writer.build())
        }
    }

    // ---- Typed lists ----

    override fun intList(key: String, values: List<Int>) = safe(key) {
        map[key] = JsonArray(values.map { JsonPrimitive(it) })
    }

    override fun floatList(key: String, values: List<Float>) = safe(key) {
        map[key] = JsonArray(values.map { JsonPrimitive(it) })
    }

    override fun doubleList(key: String, values: List<Double>) = safe(key) {
        map[key] = JsonArray(values.map { JsonPrimitive(it) })
    }

    override fun stringList(key: String, values: List<String>) = safe(key) {
        map[key] = JsonArray(values.map { JsonPrimitive(it) })
    }

    override fun boolList(key: String, values: List<Boolean>) = safe(key) {
        map[key] = JsonArray(values.map { JsonPrimitive(it) })
    }

    override fun objList(key: String, values: List<DataCodec>) = safe(key) {
        val array = JsonArray(buildList {
            for (value in values) {
                val writer = JsonDataEncoder()
                value.encode(writer)

                if (writer.isIgnored) continue

                val type = CodecTypeRegistry.nameOf(value::class)
                    ?: throw NoSuchElementException("Unregistered DataCodec: ${value::class.qualifiedName}")

                add(buildJsonObject {
                    put("_type", type)
                    put("_data", writer.build())
                })
            }
        })

        map[key] = array
    }

    override fun ignore() {
        isIgnored = true
    }

    override fun unignore() {
        isIgnored = false
    }

    private inline fun <R> safe(key: String, crossinline block: () -> R): R {
        if(map.containsKey(key)) {
            throw IllegalArgumentException("Duplicate key: $key")
        }
        return block()
    }
}



