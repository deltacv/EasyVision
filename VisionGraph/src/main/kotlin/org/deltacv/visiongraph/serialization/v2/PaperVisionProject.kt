package org.deltacv.visiongraph.serialization.v2

import org.deltacv.visiongraph.VisionGraph
import org.deltacv.visiongraph.node.FlagsNode
import org.deltacv.visiongraph.node.Link
import org.deltacv.visiongraph.node.Node
import org.deltacv.visiongraph.node.vision.InputMatNode
import org.deltacv.visiongraph.node.vision.OutputMatNode
import org.deltacv.visiongraph.util.loggerForThis

class PaperVisionProject(
    val nodes: MutableList<Node<*>> = mutableListOf(),
    val links: MutableList<Link> = mutableListOf()
) : DataCodec {

    companion object {
        fun from(visionGraph: VisionGraph): PaperVisionProject {
            val project = PaperVisionProject()

            project.nodes.addAll(visionGraph.nodes.inmutable)
            project.links.addAll(visionGraph.links.inmutable)

            return project
        }
    }

    val logger by loggerForThis()

    override fun encode(encoder: DataEncoder) {
        encoder.objList("nodes", nodes)
        encoder.objList("links", links)
    }

    override fun decode(decoder: DataDecoder) {
        val decodedNodes = decoder.objList("nodes").map {
            it as? Node<*> ?: throw MalformedDataException("Decoded object is not a VisionGraph Node", it)
        }

        nodes.clear()
        nodes.addAll(decodedNodes)

        val decodedLinks = decoder.objList("links").map {
            it as? Link ?: throw MalformedDataException("Decoded object is not a VisionGraph Link", it)
        }

        links.clear()
        links.addAll(decodedLinks)
    }

    fun apply(visionGraph: VisionGraph) {
        logger.info("Loading project with ${nodes.size} nodes and ${links.size} links")

        // clear existing state
        for (node in visionGraph.nodes.inmutable) {
            node.forceDelete()
        }
        for (link in visionGraph.links.inmutable) {
            link.delete()
        }

        var createdInputNode = false
        var createdOutputNode = false
        var hasAddedFlags = false

        for (node in nodes) {
            when (node) {
                is InputMatNode -> {
                    if(createdInputNode) {
                        throw IllegalStateException("Only one InputMatNode can be present in the node editor.")
                    }
                    visionGraph.nodeEditor.inputNode = node
                    createdInputNode = true
                }

                is OutputMatNode -> {
                    if(createdOutputNode) {
                        throw IllegalStateException("Only one OutputMatNode can be present in the node editor.")
                    }
                    visionGraph.nodeEditor.outputNode = node
                    createdOutputNode = true
                }

                is FlagsNode -> {
                    if (hasAddedFlags) {
                        throw IllegalStateException("Only one FlagsNode can be present in the node editor.")
                    }
                    visionGraph.nodeEditor.flagsNode = node
                    hasAddedFlags = true
                }
            }
            node.enable()
        }

        if (!createdInputNode) {
            // If the project doesn't have an input node, create a default one to ensure the node editor is in a valid state
            visionGraph.nodeEditor.inputNode = InputMatNode().apply { enable() }
        }
        if (!createdOutputNode) {
            // If the project doesn't have an output node, create a default one to ensure the node editor is in a valid state
            visionGraph.nodeEditor.outputNode = OutputMatNode().apply { enable() }
        }

        visionGraph.nodeEditor.inputNode.ensureAttributeExists()
        visionGraph.nodeEditor.outputNode.ensureAttributeExists()

        visionGraph.onUpdate.once {
            for (link in links) {
                if (link.aAttrib != null && link.bAttrib != null) {
                    link.enable()
                } else {
                    logger.debug("Cleaning up orphaned link during project application: {}", link)
                    link.delete()
                }
            }
            visionGraph.onDeserialization.run()
        }
    }
}