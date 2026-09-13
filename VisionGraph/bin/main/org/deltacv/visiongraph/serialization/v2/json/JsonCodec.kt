package org.deltacv.visiongraph.serialization.v2.json

import org.deltacv.visiongraph.serialization.v2.Codec
import org.deltacv.visiongraph.serialization.v2.DataCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class JsonCodec(
    private val prettyPrint: Boolean = false
) : Codec<String> {

    private val json = Json { prettyPrint = this@JsonCodec.prettyPrint }

    override fun encode(root: DataCodec) = Json.encodeToString(encodeToJsonElement(root))

    fun encodeToJsonElement(root: DataCodec): JsonElement {
        val writer = JsonDataEncoder()
        root.encode(writer)
        return writer.build()
    }

    override fun <C: DataCodec> decode(data: String, root: C) = decode(json.parseToJsonElement(data), root)

    fun <C: DataCodec> decode(data: JsonElement, root: C): C {
        if(data !is JsonObject) {
            throw IllegalArgumentException("Expected a JSON object as root, but got ${data::class}")
        }

        val reader = JsonDataDecoder(data)
        root.decode(reader)
        return root
    }

}