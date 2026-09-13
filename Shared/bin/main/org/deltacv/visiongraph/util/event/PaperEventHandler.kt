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

package org.deltacv.visiongraph.util.event

import org.deltacv.visiongraph.util.loggerOf
import java.util.concurrent.atomic.AtomicInteger

/**
 * Event handler with:
 * - Persistent listeners (ID-based, queue-based adding)
 * - Once listeners (double buffered queue-based, deferred)
 */
class PaperEventHandler(val name: String, val catchExceptions: Boolean = true) : Runnable {

    companion object {
        fun batchOnce(vararg eventHandler: PaperEventHandler, listener: PaperOnceEventListener): List<PaperEventListenerId> {
            return eventHandler.map { it.once(listener) }
        }

        fun batchAttach(vararg eventHandler: PaperEventHandler, listener: PaperEventListener): List<PaperEventListenerId> {
            return eventHandler.map { it.attach(listener) }
        }
    }

    // ------------------------------------------------------------
    // config
    // ------------------------------------------------------------

    private val logger by loggerOf("PaperEventHandler-$name")

    var callRightAway = false

    // ------------------------------------------------------------
    // ids
    // ------------------------------------------------------------

    private val idCounter = AtomicInteger(Int.MIN_VALUE)

    // ------------------------------------------------------------
    // persistent listeners (stable + queues)
    // ------------------------------------------------------------

    private val persistentListeners = HashMap<Int, PaperEventListener>()

    private val persistentAddQueue = ArrayDeque<Pair<Int, PaperEventListener>>()
    private val persistentRemoveQueue = ArrayDeque<Int>()

    private val persistentQueueLock = Any()

    private val persistentContextCache = HashMap<Int, PaperEventListenerContext>()

    // ------------------------------------------------------------
    // once listeners (swappable double buffer)
    // ------------------------------------------------------------

    private var onceListenersCurrent = ArrayDeque<PaperOnceEventListener>()
    private var onceIdsCurrent = ArrayDeque<Int>()

    private var onceListenersQueue = ArrayDeque<PaperOnceEventListener>()
    private var onceIdsQueue = ArrayDeque<Int>()

    private val onceLock = Any()

    // ------------------------------------------------------------
    // run
    // ------------------------------------------------------------

    override fun run() {
        runPersistentListeners()
        runOnceListeners()
    }

    // ------------------------------------------------------------
    // persistent execution
    // ------------------------------------------------------------

    fun runPersistentListeners() {
        // apply pending adds/removes
        synchronized(persistentQueueLock) {
            while (persistentAddQueue.isNotEmpty()) {
                val (id, listener) = persistentAddQueue.removeFirst()
                persistentListeners[id] = listener
            }
            while (persistentRemoveQueue.isNotEmpty()) {
                val id = persistentRemoveQueue.removeFirst()
                persistentListeners.remove(id)
                persistentContextCache.remove(id)
            }
        }

        // execute
        for ((id, listener) in persistentListeners) {
            if (catchExceptions) {
                try {
                    val remover = persistentContextCache.getOrPut(id) { PaperEventListenerContext(this, PaperEventListenerId(id)) }
                    listener(remover)
                } catch (e: Exception) {
                    if (e is InterruptedException) throw e
                    logger.error("Exception in listener", e)
                }
            } else {
                val remover = persistentContextCache.getOrPut(id) { PaperEventListenerContext(this, PaperEventListenerId(id)) }
                listener(remover)
            }
        }
    }

    // ------------------------------------------------------------
    // once execution
    // ------------------------------------------------------------

    fun runOnceListeners() {
        val toRunListeners: ArrayDeque<PaperOnceEventListener>
        val toRunIds: ArrayDeque<Int>

        synchronized(onceLock) {
            // swap
            toRunListeners = onceListenersQueue
            toRunIds = onceIdsQueue

            onceListenersQueue = onceListenersCurrent
            onceIdsQueue = onceIdsCurrent

            onceListenersCurrent = toRunListeners
            onceIdsCurrent = toRunIds
        }

        while (toRunListeners.isNotEmpty()) {
            val listener = toRunListeners.removeFirst()
            toRunIds.removeFirst() // keep in sync

            if (catchExceptions) {
                try {
                    listener()
                } catch (e: Exception) {
                    if (e is InterruptedException) throw e
                    logger.error("Exception in once listener", e)
                }
            } else {
                listener()
            }
        }

        toRunListeners.clear()
        toRunIds.clear()
    }

    // ------------------------------------------------------------
    // attach
    // ------------------------------------------------------------

    operator fun invoke(listener: PaperEventListener): PaperEventListenerId = attach(listener)

    fun attach(listener: PaperEventListener): PaperEventListenerId {
        val id = PaperEventListenerId(idCounter.getAndIncrement())

        synchronized(persistentQueueLock) {
            persistentAddQueue.addLast(id.value to listener)
        }

        if (callRightAway) {
            listener(PaperEventListenerContext(this, id))
        }

        return id
    }

    fun once(listener: PaperOnceEventListener): PaperEventListenerId {
        val id = PaperEventListenerId(idCounter.getAndIncrement())

        if (callRightAway) {
            listener()
        } else {
            synchronized(onceLock) {
                onceListenersQueue.addLast(listener)
                onceIdsQueue.addLast(id.value)
            }
        }

        return id
    }

    @JvmName("attach")
    fun attach(runnable: Runnable): PaperEventListenerId = attach{ runnable.run() }

    @JvmName("once")
    fun once(runnable: Runnable): PaperEventListenerId = once { runnable.run() }

    // ------------------------------------------------------------
    // remove
    // ------------------------------------------------------------

    @JvmName("removeListener")
    fun removeListener(id: PaperEventListenerId) {
        synchronized(onceLock) {
            val index = onceIdsQueue.indexOf(id.value)
            if (index >= 0) {
                onceIdsQueue.removeAt(index)
                onceListenersQueue.removeAt(index)

                return@removeListener // removed from once queue, no need to check persistent
            }
        }

        synchronized(persistentQueueLock) {
            persistentRemoveQueue.addLast(id.value)
        }
    }

    fun removeAllListeners() {
        synchronized(persistentQueueLock) {
            persistentListeners.clear()
            persistentAddQueue.clear()
            persistentRemoveQueue.clear()
            persistentContextCache.clear()
        }
        synchronized(onceLock) {
            onceListenersCurrent.clear()
            onceIdsCurrent.clear()
            onceListenersQueue.clear()
            onceIdsQueue.clear()
        }
    }
}



