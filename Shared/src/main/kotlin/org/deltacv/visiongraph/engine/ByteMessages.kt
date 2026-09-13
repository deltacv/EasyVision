/*
 * PaperVision
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

package org.deltacv.visiongraph.engine

import java.nio.ByteBuffer

open class ByteMessageTag(val content: ByteArray) {
    companion object {
        fun fromString(tag: String) = ByteMessageTag(tag.toByteArray(Charsets.UTF_8))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ByteMessageTag

        return content.contentEquals(other.content)
    }

    override fun toString() = content.toString(Charsets.UTF_8)

    override fun hashCode() = content.contentHashCode()
}

/**
 * Structure of a byte message:
 * - 4 bytes: int n
 * - n bytes: string tag
 * - 4 bytes: int id
 * - rest: the message
 */
object ByteMessages {

    fun tagFromBytes(bytes: ByteArray): ByteArray {
        // first four bytes are the size of the tag string
        val tagSize = ByteBuffer.wrap(bytes, 0, 4).int

        // next tagSize bytes are the tag
        val tagBytes = ByteArray(tagSize)
        ByteBuffer.wrap(bytes, 4, tagSize).get(tagBytes)

        return tagBytes
    }

    fun idFromBytes(bytes: ByteArray): Int {
        // first four bytes are the size of the tag string
        val tagSize = ByteBuffer.wrap(bytes, 0, 4).getInt()

        // next four bytes after tag are the id
        return ByteBuffer.wrap(bytes, 4 + tagSize, 4).getInt()
    }

    fun messageFromBytes(bytes: ByteArray): ByteArray {
        val messageOffset = messageOffsetFromBytes(bytes)
        val messageSize = messageLengthFromBytes(bytes)

        // next messageSize bytes are the message
        return bytes.copyOfRange(messageOffset, messageOffset + messageSize)
    }

    fun messageFromBytes(bytes: ByteArray, target: ByteArray) {
        val messageOffset = messageOffsetFromBytes(bytes)
        val messageSize = messageLengthFromBytes(bytes)

        if(target.size < messageSize) {
            throw IllegalArgumentException("Target array is too small to fit the message")
        }

        // next messageSize bytes are the message
        System.arraycopy(bytes, messageOffset, target, 0, messageSize)
    }

    fun headerSize(tag: ByteArray) = 4 + tag.size + 4 + 4
    fun headerSize(tag: ByteMessageTag) = headerSize(tag.content)

    fun writeHeader(tag: ByteArray, id: Int, payloadSize: Int, target: ByteBuffer) {
        target.putInt(tag.size)
        target.put(tag)
        target.putInt(id)
        target.putInt(payloadSize)
    }

    fun writeHeader(tag: ByteMessageTag, id: Int, payloadSize: Int, target: ByteBuffer) =
        writeHeader(tag.content, id, payloadSize, target)

    fun toBytes(tag: ByteArray, id: Int, message: ByteArray): ByteArray {
        // header: tagSize(4) + tag(n) + id(4) + payloadSize(4) + payload
        val buffer = ByteBuffer.allocate(4 + tag.size + 4 + 4 + message.size)
        buffer.putInt(tag.size)
        buffer.put(tag)
        buffer.putInt(id)
        buffer.putInt(message.size)
        buffer.put(message)

        return buffer.array()
    }
    fun toBytes(tag: ByteMessageTag, id: Int, message: ByteArray) = toBytes(tag.content, id, message)

    fun messageOffsetFromBytes(it: ByteArray): Int {
        val tagSize = ByteBuffer.wrap(it, 0, 4).getInt()
        // header = [4: tagSize][tagSize: tag][4: id][4: payloadSize]
        return 4 + tagSize + 4 + 4
    }

    fun messageLengthFromBytes(bytes: ByteArray): Int {
        val tagSize = ByteBuffer.wrap(bytes, 0, 4).getInt()
        // payloadSize field sits immediately after the id field
        return ByteBuffer.wrap(bytes, 4 + tagSize + 4, 4).getInt()
    }
}



