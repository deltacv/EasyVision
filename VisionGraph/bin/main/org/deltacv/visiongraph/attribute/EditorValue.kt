package org.deltacv.visiongraph.attribute

import org.deltacv.visiongraph.node.vision.ColorSpace

sealed interface EditorValue {
    data class Image(val colorSpace: ColorSpace, val isBinary: kotlin.Boolean): EditorValue

    data class Boolean(val value: kotlin.Boolean): EditorValue
    data class Int(val value: kotlin.Int): EditorValue
    data class Double(val value: kotlin.Double): EditorValue
    data class Range(val min: kotlin.Double, val max: kotlin.Double): EditorValue

    data class String(val value: kotlin.String): EditorValue
    data class Enum<E: kotlin.Enum<E>>(val value: kotlin.Enum<E>): EditorValue

    data class List(val values: kotlin.collections.List<EditorValue>): EditorValue

    data class Any(val value: kotlin.Any): EditorValue
    object Null: EditorValue
}