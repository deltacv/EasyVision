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

package org.deltacv.visiongraph.util

import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

fun Any.loggerFor(clazz: KClass<*>) = lazy { LoggerFactory.getLogger(clazz.java)!! }
// Reified
inline fun <reified T> Any.loggerFor() = lazy { LoggerFactory.getLogger(T::class.java)!! }

fun Any.loggerForThis() = lazy { LoggerFactory.getLogger(this::class.java)!! }
fun Any.loggerOf(name: String) = lazy { LoggerFactory.getLogger(name)!! }



