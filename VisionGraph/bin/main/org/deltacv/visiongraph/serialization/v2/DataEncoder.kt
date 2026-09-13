package org.deltacv.visiongraph.serialization.v2

interface DataEncoder {

    val isIgnored: Boolean

    // Primitives
    fun int(key: String, value: Int)
    fun float(key: String, value: Float)
    fun double(key: String, value: Double)
    fun bool(key: String, value: Boolean)
    fun string(key: String, value: String)

    // Nested object
    fun obj(key: String, value: DataCodec, typeName: String? = null)

    // Typed lists
    fun intList(key: String, values: List<Int>)
    fun floatList(key: String, values: List<Float>)
    fun doubleList(key: String, values: List<Double>)
    fun boolList(key: String, values: List<Boolean>)
    fun stringList(key: String, values: List<String>)
    fun objList(key: String, values: List<DataCodec>)

    fun ignore()
    fun unignore()

}



