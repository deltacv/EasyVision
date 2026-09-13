package org.deltacv.visiongraph.engine.client.response

import kotlinx.serialization.Serializable

@Serializable
abstract class PaperVisionEngineMessageResponse {
    var id = 0

    abstract val status: Boolean

    override fun toString(): String {
        return "IpcMessageResponse(type=\"${this::class.java.typeName}\", status=\"${if(status) "OK" else "ERROR"}\")"
    }
}



