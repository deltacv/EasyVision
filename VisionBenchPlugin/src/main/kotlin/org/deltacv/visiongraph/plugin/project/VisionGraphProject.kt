package org.deltacv.visiongraph.plugin.project

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class VisionGraphProject(
    val timestamp: Long,
    val path: String,
    val name: String,
    @JsonNames("json") val data: JsonElement
)



