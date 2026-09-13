@file:Suppress("UNUSED")

package org.deltacv.visiongraph.serialization.v2

inline fun <reified T: DataCodec> DataDecoder.objTyped(name: String): T {
    val obj = obj(name)
    if(obj !is T) {
        throw MalformedDataException("Expected object of type ${T::class.qualifiedName}, but got ${obj::class.qualifiedName}", obj)
    }

    return obj
}

private inline fun <R> tryOrNull(crossinline block: () -> R): R? = try {
    block()
} catch (e: MalformedDataException) {
    null
}

fun DataDecoder.intOrNull(name: String): Int? = tryOrNull { if (has(name)) int(name) else null }
fun DataDecoder.floatOrNull(name: String): Float? = tryOrNull { if (has(name)) float(name) else null }
fun DataDecoder.doubleOrNull(name: String): Double? = tryOrNull { if (has(name)) double(name) else null }
fun DataDecoder.stringOrNull(name: String): String? = tryOrNull { if (has(name)) string(name) else null }
fun DataDecoder.boolOrNull(name: String): Boolean? = tryOrNull { if (has(name)) bool(name) else null }

fun DataDecoder.objOrNull(name: String): DataCodec? = tryOrNull { if (has(name)) obj(name) else null }
fun DataDecoder.objOrSkip(name: String, target: DataCodec): Boolean = tryOrNull {  if (has(name)) { obj(name, target); true } else false } ?: false

fun DataDecoder.intListOrNull(name: String): List<Int>? = tryOrNull { if (has(name)) intList(name) else null }
fun DataDecoder.floatListOrNull(name: String): List<Float>? = tryOrNull { if (has(name)) floatList(name) else null }
fun DataDecoder.doubleListOrNull(name: String): List<Double>? = tryOrNull { if (has(name)) doubleList(name) else null }
fun DataDecoder.stringListOrNull(name: String): List<String>? = tryOrNull { if (has(name)) stringList(name) else null }
fun DataDecoder.boolListOrNull(name: String): List<Boolean>? = tryOrNull { if (has(name)) boolList(name) else null }
fun DataDecoder.objListOrNull(name: String): List<DataCodec>? = tryOrNull { if (has(name)) objList(name) else null }