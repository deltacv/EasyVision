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

package org.deltacv.visiongraph.plugin.previz

import com.github.serivesmejia.eocvsim.util.ReflectUtil
import org.codehaus.janino.JavaSourceClassLoader
import org.codehaus.janino.SimpleCompiler
import org.openftc.easyopencv.OpenCvPipeline
import java.util.*
import javax.tools.*


object SinglePipelineCompiler {

    /**
     * Takes source code and returns a compiled pipeline class
     */
    @Suppress("UNCHECKED_CAST")
    fun compilePipeline(pipelineSource: String): Class<out OpenCvPipeline> {
        // Create a SimpleCompiler instance
        val compiler = SimpleCompiler()

        // Set the source code
        compiler.cook(pipelineSource)

        if(compiler.classFiles.isEmpty()) {
            throw IllegalStateException("No class files were generated from the provided source code.")
        }

        for(classFile in compiler.classFiles) {
            val clazz = try {
                compiler.classLoader.loadClass(classFile.thisClassName)
            } catch (classErr: ClassFormatError) {
                throw IllegalStateException("A ClassFormatError was thrown during class loading (build was successful)", classErr)
            }

            if(ReflectUtil.hasSuperclass(clazz, OpenCvPipeline::class.java)) {
                return clazz as Class<out OpenCvPipeline>
            }
        }

        throw IllegalStateException("No OpenCvPipeline subclass found in the provided source code.")
    }
}



