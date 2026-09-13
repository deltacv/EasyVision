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

import org.deltacv.eocvsim.pipeline.StreamableOpenCvPipeline
import com.github.serivesmejia.eocvsim.pipeline.instantiator.DefaultPipelineInstantiator
import org.deltacv.eocvsim.plugin.EOCVSimPlugin
import org.deltacv.eocvsim.plugin.api.PipelineInstantiatorApi
import org.deltacv.eocvsim.stream.ImageStreamer
import org.deltacv.eocvsim.virtualreflect.jvm.JvmVirtualReflection
import org.deltacv.visiongraph.util.loggerForThis
import org.firstinspires.ftc.robotcore.external.Telemetry
import org.openftc.easyopencv.OpenCvPipeline

class StreamableNoReflectPipelineInstantiator(
    owner: EOCVSimPlugin, val imageStreamer: ImageStreamer
) : PipelineInstantiatorApi(owner) {

    val logger by loggerForThis()

    override fun instantiatePipeline(pipelineClass: Class<*>, telemetry: Telemetry) = apiImpl {
        DefaultPipelineInstantiator.instantiate(pipelineClass, telemetry).apply {
            if (this is StreamableOpenCvPipeline) {
                this.streamer = imageStreamer
            }
        }
    }

    override fun virtualReflectionFor(pipeline: OpenCvPipeline) = apiImpl { JvmVirtualReflection }

    override fun variableTunerTargetFor(pipeline: OpenCvPipeline) = apiImpl { Any() }

    override fun disableApi() { }
}



