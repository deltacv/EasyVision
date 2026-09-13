# Serialization, Project Formats, and Extensible Codecs

## Purpose and Scope

Serialization enables VisionGraph to persist user-authored node graphs to disk, exchange pipeline states between separate processes via IPC, and facilitate clipboard copy/paste operations.

This document details the serialization architecture, comparing the legacy **v1** Gson format with the modern **v2** typed envelope architecture (`DataCodec` in `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/serialization/v2/DataCodec.kt`), explaining compile-time codec registration via KSP, backward compatibility shims, and the multi-step graph rehydration lifecycle.

---

## High-Level Architecture

VisionGraph's serialization architecture centers on explicit typing, format migration safety, and reflectionless polymorphic decoding:

```
COMPILE-TIME REGISTRATION (KSP)
===============================
[@CodecType Annotation on Nodes/Structs]
             |
             v
[CodecTypeAnnotationProcessor]
             |
             v (generates)
[CodecTypeMetadata.registerAll()]
             |
             v (populates)
[CodecTypeRegistry: Map<String, () -> DataCodec>]


RUNTIME SERIALIZATION & REHYDRATION (v2)
========================================
[PaperVisionProject (nodes, links)]
             ^
             | encode / decode
             v
[JsonCodec / JsonDataEncoder / JsonDataDecoder]
             |
             v
[JSON Payload with Typed Envelopes: {"_type": "...", "_data": {...}}]
             |
             v (rehydration)
[PaperVisionProject.apply()]
  |
  +--> 1. Purge existing canvas (forceDelete)
  +--> 2. Instantiate and decode nodes
  +--> 3. Validate singletons (InputMatNode, OutputMatNode, FlagsNode)
  +--> 4. Enable nodes and reserve IDs in IdContainer
  +--> 5. Reconnect links (deferred to frame boundary)
```

---

## The v2 Typed Envelope Architecture (`DataCodec`)

In version 1, graphs were serialized via Gson reflection. While convenient initially, raw reflection coupled serialized JSON files directly to JVM package names and internal field layouts. Refactoring a class name or renaming a package broke existing saved project files.

Version 2 introduces an explicit, reflectionless abstraction:
* **Interface**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/serialization/v2/DataCodec.kt`
  ```kotlin
  interface DataCodec {
      fun encode(encoder: DataEncoder)
      fun decode(decoder: DataDecoder)
  }
  ```

### The Envelope Schema: `_type` and `_data`
Every polymorphic object (nodes, attributes, links) is wrapped in a standardized JSON envelope:
```json
{
  "_type": "CvtColorNode",
  "_data": {
    "id": 14,
    "position": { "x": 340.0, "y": 180.0 },
    "colorSpace": "COLOR_BGR2HSV"
  }
}
```

* `_type`: A stable, human-readable string identifier assigned to the class. It is completely decoupled from the JVM package name.
* `_data`: The key-value payload managed explicitly by the class's `encode()` and `decode()` implementations.

---

## Compile-Time Codec Discovery: `CodecTypeRegistry`

* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/serialization/v2/CodecTypeRegistry.kt`

To decode an object from `"_type": "CvtColorNode"`, the decoder requires a factory function to instantiate a fresh instance without runtime reflection.

1. **Annotation**: Classes annotate themselves with `@CodecType` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/serialization/v2/CodecType.kt`):
   ```kotlin
   @CodecType(name = "CvtColorNode")
   class CvtColorNode : DrawNode<...>() { ... }
   ```
2. **KSP Code Generation**: During build time, `CodecTypeAnnotationProcessor` (`AnnotationProcessor/src/main/kotlin/org/deltacv/visiongraph/annotation/codectype/CodecTypeAnnotationProcessor.kt`) detects all `@CodecType` symbols, verifies that they provide no-argument or default constructors, and generates `CodecTypeMetadata.registerAll()`.
3. **Static Registration**: On startup, factories are registered directly into `CodecTypeRegistry`:
   ```kotlin
   CodecTypeRegistry.register("CvtColorNode", CvtColorNode::class) { CvtColorNode() }
   ```
4. **Decoding**: `JsonDataDecoder` reads `"_type"`, calls `CodecTypeRegistry.create(typeName)`, and immediately invokes `instance.decode(this)`.

---

## Top-Level Project Persistence: `PaperVisionProject`

* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/serialization/v2/PaperVisionProject.kt`

`PaperVisionProject` encapsulates the complete state of a pipeline:
```kotlin
class PaperVisionProject(
    val nodes: MutableList<Node<*>> = mutableListOf(),
    val links: MutableList<Link> = mutableListOf()
) : DataCodec
```

### The Safe Rehydration Lifecycle (`apply`)
Loading a serialized graph is not simply a matter of adding objects to a list. Dangling references, missing nodes, or invalid link IDs could crash the editor. `PaperVisionProject.apply(visionGraph)` executes a disciplined 7-stage rehydration protocol:

```
PaperVisionProject.apply(visionGraph)
 │
 ├── 1. Purge Canvas
 │     ├── All existing nodes are destroyed via node.forceDelete()
 │     └── All existing links are deleted via link.delete()
 │
 ├── 2. Validate Singleton Constraints
 │     └── Traverses decoded nodes. Validates that no more than ONE instance of 
 │         InputMatNode, OutputMatNode, or FlagsNode exists (throws IllegalStateException otherwise)
 │
 ├── 3. Bind Singletons to NodeEditor
 │     ├── visionGraph.nodeEditor.inputNode = decodedInputNode
 │     ├── visionGraph.nodeEditor.outputNode = decodedOutputNode
 │     └── visionGraph.nodeEditor.flagsNode = decodedFlagsNode
 │
 ├── 4. Node Activation & ID Reservation
 │     └── Calls node.enable() on each decoded node.
 │         Node registers its serializedId in DenseIdContainer, preventing ID collisions.
 │         Node attributes allocate socket IDs.
 │
 ├── 5. Fallback Default Ingestion
 │     └── If the project lacked an InputMatNode or OutputMatNode, default instances 
 │         are created and enabled automatically to guarantee a valid editor state.
 │
 ├── 6. Deferred Link Reconnection (Frame Boundary)
 │     └── Runs on visionGraph.onUpdate.once (next frame tick):
 │         ├── Evaluates each Link. If both link.aAttrib and link.bAttrib exist in the ID registry,
 │         │   the link is enabled: link.enable()
 │         └── If an attribute is missing (e.g., deleted node), the link is discarded:
 │             logger.debug("Cleaning up orphaned link during project application: {}", link)
 │
 └── 7. Trigger Lifecycle Notification
       └── visionGraph.onDeserialization.run() notifies UI windows that deserialization is complete.
```

---

## Backward Compatibility and v1 Auto-Migration

VisionGraph maintains support for opening older project files created during earlier development versions.

* **Legacy Deserializer**: `PaperVisionSerializer` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/serialization/v1/PaperVisionSerializer.kt`)
* **Package Relocation Shims**: Older v1 files contain hardcoded package paths referencing `org.deltacv.papervision.*` instead of `org.deltacv.visiongraph.*`. Before feeding raw JSON to Gson, `PaperVisionSerializer` applies string migration shims, remapping legacy package names to modern classes.
* **Automatic Upgrade**: When `VisionGraphIpcMain` (`VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/VisionGraphIpcMain.kt`) opens a project:
  1. It attempts to parse with `JsonCodec` (v2) first.
  2. If decoding fails, it falls back to `PaperVisionSerializer.deserializeAndApply(...)` (v1).
  3. When the user next saves the project (or on auto-save), the graph is written out exclusively in the modern v2 format, upgrading legacy projects seamlessly without user intervention.

---

## Clipboard Copy/Paste Architecture

Copy and paste operations leverage the v2 serialization engine:
1. **Copy (`Ctrl+C`)**:
   * Collects all currently selected nodes in `NodeEditor`.
   * Identifies all links that connect exclusively *between* the selected nodes.
   * Serializes this subgraph into a JSON string via `JsonCodec().encode(...)`.
   * Sets the clipboard text using native GLFW clipboard bindings (`ImGui.setClipboardText(...)`).
2. **Paste (`Ctrl+V`)**:
   * Reads JSON text from the system clipboard.
   * Decodes the payload into a temporary `PaperVisionProject` fragment.
   * Flags all decoded nodes with `forgetSerializedId = true` so they receive fresh, non-colliding integer IDs upon addition.
   * Offsets node positions relative to the current mouse cursor location on the canvas.
   * Wraps the pasted nodes and links in a `CreateNodesAction` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/action/editor/NodeActions.kt`) and pushes it to the undo stack.