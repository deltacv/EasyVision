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

package org.deltacv.visiongraph.io

import org.deltacv.visiongraph.platform.PlatformKeys
import org.deltacv.visiongraph.util.ElapsedTime

data class KeyShortcut(
    val modifiers: List<Int>,
    val key: Int,
    val oneshot: Boolean = false,
    val action: () -> Unit
)

private data class ShortcutTriggerData(
    val timer: ElapsedTime,
    var count: Int
)

class KeyManager(val keys: PlatformKeys) {

    companion object {
        const val SHORTCUT_INITIAL_TRIGGER_RATE_SECS = 0.5
        const val SHORTCUT_TRIGGER_RATE_SECS = 0.06
    }

    private val pressedKeys = mutableMapOf<Int, Boolean>()
    private val pressingKeys = mutableMapOf<Int, Boolean>()
    private val releasedKeys = mutableMapOf<Int, Boolean>()

    private val shortcuts = mutableListOf<KeyShortcut>()
    private val shortcutTriggerData = mutableMapOf<KeyShortcut, ShortcutTriggerData>()

    fun update() {
        if(pressedKeys.isNotEmpty()) {
            for (key in pressedKeys.keys.toTypedArray()) {
                pressedKeys[key] = false
            }
        }

        if(releasedKeys.isNotEmpty()) {
            for (key in releasedKeys.keys.toTypedArray()) {
                releasedKeys[key] = false
            }
        }

        for(shortcut in shortcuts) {
            if(shortcut.modifiers.all { pressing(it) } && pressing(shortcut.key)) {
                val data = shortcutTriggerData.getOrPut(shortcut) {
                    ShortcutTriggerData(ElapsedTime(), 0)
                }

                val timer = data.timer.seconds

                if(shortcut.oneshot && data.count == 0) {
                    shortcut.action() // trigger once
                    data.count++
                } else {
                    if (data.count == 0 ||
                        (data.count == 1 && timer > SHORTCUT_INITIAL_TRIGGER_RATE_SECS) ||
                        (data.count > 1 && timer > SHORTCUT_TRIGGER_RATE_SECS)
                    ) {
                        shortcut.action()
                        data.timer.reset()
                        data.count++
                    }
                }
            } else {
                shortcutTriggerData.remove(shortcut)
            }
        }
    }

    fun updateKey(scancode: Int, action: KeyAction) {
        when (action) {
            KeyAction.PRESS -> {
                pressedKeys[scancode] = true
                pressingKeys[scancode] = false
                pressingKeys[scancode] = true
            }
            KeyAction.PRESSING -> {
                pressedKeys[scancode] = false
                pressingKeys[scancode] = true
                releasedKeys[scancode] = false
            }
            KeyAction.RELEASE -> {
                pressingKeys[scancode] = false
                pressingKeys[scancode] = false
                releasedKeys[scancode] = true
            }
            else -> {
                pressedKeys[scancode] = false
                pressingKeys[scancode] = false
                releasedKeys[scancode] = false
            }
        }
    }

    fun addShortcut(modifiers: List<Int>, key: Int, oneshot: Boolean, action: () -> Unit) {
        shortcuts.add(KeyShortcut(modifiers, key, oneshot, action))
    }

    fun addShortcut(modifier: Int, key: Int, oneshot: Boolean, action: () -> Unit) {
        addShortcut(listOf(modifier), key, oneshot, action)
    }

    // we have to manually do overloads...
    fun addShortcut(modifiers: List<Int>, key: Int, action: () -> Unit) {
        addShortcut(modifiers, key, false, action)
    }
    fun addShortcut(modifier: Int, key: Int, action: () -> Unit) {
        addShortcut(listOf(modifier), key, false, action)
    }

    fun addShortcut(key: Int, action: () -> Unit) {
        addShortcut(emptyList(), key, false, action)
    }

    fun pressed(scancode: Int)  = pressedKeys[scancode] == true
    fun pressing(scancode: Int) = pressingKeys[scancode] == true
    fun released(scancode: Int) = releasedKeys[scancode] == true

}

enum class KeyAction { PRESS, PRESSING, RELEASE, UNKNOWN }



