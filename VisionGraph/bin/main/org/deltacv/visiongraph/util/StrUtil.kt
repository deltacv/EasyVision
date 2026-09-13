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

package org.deltacv.visiongraph.util

fun String.replaceLast(oldValue: String, newValue: String): String {
    val lastIndex = lastIndexOf(oldValue)
    if (lastIndex == -1) {
        return this
    }
    val prefix = substring(0, lastIndex)
    val suffix = substring(lastIndex + oldValue.length)
    return "$prefix$newValue$suffix"
}

fun String.toValidIdentifier(
    invalidCharReplacement: String = "_"
): String {
    if(invalidCharReplacement.isNotBlank()) {
        for(c in invalidCharReplacement) {
            if(!Character.isJavaIdentifierPart(c) || !Character.isJavaIdentifierStart(c)) {
                throw IllegalArgumentException("Invalid replacement character '$c'")
            }
        }
    }

    val sb = StringBuilder()
    if(!Character.isJavaIdentifierStart(this[0])) {
        sb.append(invalidCharReplacement);
    }

    for (c in toCharArray()) {
        if(!Character.isJavaIdentifierPart(c)) {
            sb.append(invalidCharReplacement);
        } else {
            sb.append(c);
        }
    }

    return sb.toString()
}

val Any.hashCodeString get() = Integer.toHexString(hashCode())!!



