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

package org.deltacv.visiongraph.platform

import kotlinx.serialization.json.Json
import org.deltacv.visiongraph.util.loggerForThis
import java.io.File

open class FilePlatformConfigManager(
    val path: String
) : PlatformConfigManager() {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    val file = File(path)

    val logger by loggerForThis()

    init {
        Runtime.getRuntime().addShutdownHook(Thread({
            save()
        }, "PlatformConfig-ShutdownHook"))
    }

    override fun load() {
        if(!file.exists()) {
            save()
            return
        }

        logger.info("Loading config from $path")
        data = json.decodeFromString(file.readText())
    }

    override fun save(data: PaperVisionConfig) {
        logger.info("Saving config to $path")
        file.writeText(json.encodeToString(data))
        this.data = data
    }
}

val defaultPaperVisionFolderPath get() = System.getProperty("user.home") + File.separator + ".papervision"
val defaultConfigPath get() = defaultPaperVisionFolderPath + File.separator + "config.json"

object DefaultFilePlatformConfigManager : FilePlatformConfigManager(defaultConfigPath) {
    init {
        File(defaultConfigPath).parentFile.mkdirs() // mkdir .papervision
    }
}