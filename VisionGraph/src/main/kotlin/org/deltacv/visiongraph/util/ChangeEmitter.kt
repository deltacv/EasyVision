package org.deltacv.visiongraph.util

import org.deltacv.visiongraph.util.event.PaperEventHandler

interface ChangeEmitter<C> {
    /**
     * Event handler that is invoked when a change is emitted.
     * The current change can be accessed via [currentChange] during the event.
     */
    val onChange: PaperEventHandler

    /**
     * The most recently processed change, or null if no change is currently being processed.
     * This is set during the execution of [onChange] and cleared afterward.
     */
    val currentChange: C?

    val defaultPeekChange: C

    fun emitChange(change: C)
    fun processChanges()

    fun peekChange(): C = currentChange ?: defaultPeekChange
    fun nullablePeekChange(): C? = currentChange

    fun hasChanged(): Boolean = currentChange != null
}

interface DelegatedChangeEmitter<C> : ChangeEmitter<C> {
    val changeEmitterDelegate: ChangeEmitter<C>

    override val onChange: PaperEventHandler
        get() = changeEmitterDelegate.onChange

    override val currentChange: C?
        get() = changeEmitterDelegate.currentChange
    override val defaultPeekChange: C
        get() = changeEmitterDelegate.defaultPeekChange

    override fun emitChange(change: C) = changeEmitterDelegate.emitChange(change)
    override fun processChanges() = changeEmitterDelegate.processChanges()
}

open class QueuedChangeEmitter<C>(
    override val defaultPeekChange: C,
    queueSize: Int = 8,
    private val logging: Boolean = false
) : ChangeEmitter<C> {

    val logger by loggerOf("QueuedChangeEmitter-${this::class.simpleName}")

    private val queue = ArrayDeque<C>(queueSize)

    final override val onChange = PaperEventHandler("OnChange-${this::class.simpleName}")

    private var _current: C? = null
    final override val currentChange get() = _current

    final override fun emitChange(change: C) {
        if (queue.size == queueSizeLimit) {
            queue.removeFirst() // evict oldest
        }

        queue.addLast(change)

        if (logging) {
            logger.debug("Emitted change: {} (queue size: {})", change, queue.size)
        }
    }

    private val queueSizeLimit = queueSize

    final override fun processChanges() {
        while (queue.isNotEmpty()) {
            val change = queue.removeFirst()

            _current = change
            try {
                if(logging) {
                    logger.debug("Processing change: {} (remaining queue size: {}, peek: {})", change, queue.size, peekChange())
                }
                onChange.run()
            } finally {
                _current = null
            }
        }
    }
}



