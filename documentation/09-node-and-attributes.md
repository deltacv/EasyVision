# Graph Model, Nodes, Sockets, and Annotation Processing

## Purpose and Scope

The Node and Attribute model is the canonical in-memory representation of a computer vision algorithm in VisionGraph. It defines the directed acyclic graph (DAG) structure, manages strongly-typed input and output sockets, prevents algorithmic recursion cycles, drives code generation propagation, and integrates compile-time metadata discovery via Kotlin Symbol Processing (KSP).

This document details the node class hierarchy, socket mechanics, link validation rules, cycle prevention algorithms, and compile-time node catalog generation.

---

## High-Level Architecture

The graph model sits at the intersection of the visual UI, serialization, and code generation:

```
CLASS RELATIONSHIPS:
====================
IdElement
   ^
   |
DrawableIdElementBase
   ^
   |
   +---> Node<S> (Manages attributes, DAG links, codegen propagation)
   |       ^
   |       |
   |       +---> DrawNode<S> (Visual ImNodes rendering, header colors, pin layout)
   |
   +---> Attribute (Abstract socket: INPUT or OUTPUT mode, type constraints)
   |       ^
   |       |
   |       +---> TypedAttribute<T> (Concrete typed socket: Mat, Rect, Scalar, Int, Double)
   |
   +---> Link (Visual and mathematical connection between an OUTPUT and an INPUT socket)

OWNERSHIP:
==========
Node (1) ---> owns ---> (*) Attribute
Attribute (1) <--- connects ---> (*) Link
```

---

## Core Components and File References

| Component | File Path | Primary Responsibility |
| :--- | :--- | :--- |
| **`Node<S>`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/Node.kt` | Base entity for all graph nodes; manages attribute lists, lifecycle hooks, serialization codecs, and codegen propagation. |
| **`DrawNode<S>`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/DrawNode.kt` | Visual specialization of `Node`; handles ImNodes title bar styling, header rendering, pin positioning, and canvas drawing. |
| **`Attribute`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/Attribute.kt` | Base socket abstraction; manages input/output pin modes, link acceptance policies, and change events. |
| **`TypedAttribute<T>`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/TypedAttribute.kt` | Strongly-typed socket carrying concrete primitive or OpenCV values. |
| **`DirectedNodeGraph`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/DirectedNodeGraph.kt` | Topological graph representation; detects cycles using recursive Depth-First Search (DFS). |
| **`PaperNodeRegistry`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/PaperNodeRegistry.kt` | Central discovery catalog providing categorized node lists and instantiation factories. |
| **`PaperNodeAnnotationProcessor`** | `AnnotationProcessor/src/main/kotlin/org/deltacv/visiongraph/annotation/papernode/PaperNodeAnnotationProcessor.kt` | KSP processor verifying constructor invariants and generating AOT registration code. |

---

## Strongly-Typed Sockets and Link Validation

Sockets are represented by `Attribute` and `TypedAttribute<T>`.

### Supported Data Types
* **Vision Matrices**: `MatAttribute` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/vision/MatAttribute.kt`) (OpenCV image buffers).
* **Geometric Structs**: `RectAttribute`, `RotatedRectAttribute`, `CircleAttribute`, `KeyPointAttribute`, `PointsAttribute`, `Vector2Attribute`.
* **Color / Scalar**: `ScalarAttribute` (Scalar RGB/HSV values), `ScalarRangeAttribute` (min/max bounds for thresholding).
* **Primitives**: `IntAttribute`, `DoubleAttribute`, `BooleanAttribute`, `RangeAttribute`.
* **Collections & Misc**: `EnumAttribute`, `ListAttribute`, `StringAttribute`.

### Connection Validation Rules (`Attribute.acceptLink`)
When a user attempts to connect two pins with a mouse gesture, `acceptLink(other)` enforces four strict invariants:
1. **Directionality**: A connection can only be established from an `OUTPUT` socket to an `INPUT` socket:
   ```kotlin
   if (this.mode == other.mode) return LinkAcceptance.Reject("Cannot connect two pins of the same mode")
   ```
2. **Self-Connection**: An attribute cannot connect to another attribute on the same parent node.
3. **Type Equality**: Sockets must share the exact data type unless a registered **Decomposer** applies.
4. **Decomposition Compatibility**: If a complex composite type is linked to a primitive (e.g., connecting a `RotatedRectAttribute` to a decomposer), `AttributeDecomposer` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/decomp/AttributeDecomposer.kt`) unpacks the constituent fields (center x, center y, width, height, angle) into individual scalar pins automatically.

---

## Cycle Prevention: Topological DFS Algorithm

* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/DirectedNodeGraph.kt`

Visual graph editors must prevent cyclic connections (e.g., Node A -> Node B -> Node A). Cyclic links in computer vision pipelines represent impossible infinite execution loops and would trigger stack overflows during backward code generation traversal.

`DirectedNodeGraph` maintains an adjacency set: `Map<Int, Set<Int>>` (Node ID -> Set of Downstream Node IDs). Before any link is added, `hasCycleIfAdded(fromNodeId, toNodeId)` is evaluated:

```kotlin
fun hasCycleIfAdded(fromNodeId: Int, toNodeId: Int): Boolean {
    if (fromNodeId == toNodeId) return true // Self-loops are trivially cyclic
    
    val visited = mutableSetOf<Int>()
    
    fun dfs(current: Int): Boolean {
        if (current == fromNodeId) return true // Traced back to start = cycle detected
        if (!visited.add(current)) return false // Already explored branch
        
        adjacencyList[current]?.forEach { next ->
            if (dfs(next)) return true
        }
        return false
    }
    
    return dfs(toNodeId)
}
```
* **Performance**: The DFS traverses primitive integer sets, executing in sub-microsecond time even on large graphs.
* **Result**: If `hasCycleIfAdded` returns `true`, the connection is rejected immediately, and an informative tooltip notification is displayed to the user.

---

## Node Lifecycle and State Transitions

Nodes inherit lifecycle management from `DrawableIdElementBase` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/id/DrawableIdElement.kt`):

```
NODE LIFECYCLE STATES:
======================
[Instantiated] ---> node.enable() ---> [Enabled / Active]
                                          |            ^
                         node.delete()    |            |  node.restore()
                         (User Del/Undo)  v            |  (Redo)
                                       [Deleted] ------+
                                          |
                                          | node.forceDelete()
                                          v
                                   [Purged from Memory]
```

* **ID Preservation on Undo**: When a node is deleted, its `serializedId` is retained. If the action is undone via `restore()`, the node reclaims its exact original ID, guaranteeing that previously connected links and undo history remain valid.

---

## Compile-Time Discovery via KSP

Rather than using classpath scanning libraries (like ClassGraph) which slow down startup times by hundreds of milliseconds, VisionGraph utilizes Google's Kotlin Symbol Processing (KSP).

### The `@PaperNode` Annotation
Every node declares metadata via `@PaperNode`:
```kotlin
@PaperNode(
    name = "nod_cv_threshold",
    category = NodeCategory.IMAGE_PROC,
    description = "desc_cv_threshold",
    showInList = true,
    instantiable = true
)
class ThresholdNode : DrawNode<...>() { ... }
```

### KSP Processor Verification
`PaperNodeAnnotationProcessor` (`AnnotationProcessor/src/main/kotlin/org/deltacv/visiongraph/annotation/papernode/PaperNodeAnnotationProcessor.kt`) executes during compilation:
1. Validates that all classes marked `instantiable = true` have a zero-argument constructor or default values for all parameters.
2. If invalid, the compiler halts with an explicit error explaining which constructor parameter is missing a default value.
3. Generates `PaperVisionPaperNodeMetadata.registerAll()` using KotlinPoet, generating direct registration statements:
   ```kotlin
   PaperNodeRegistry.registerNode(ThresholdNode::class, NodeCategory.IMAGE_PROC) { ThresholdNode() }
   ```
4. At runtime, application startup populates `PaperNodeRegistry` in less than a millisecond with zero runtime reflection.

---

## Built-In Node Library Overview

VisionGraph includes an extensive library of specialized computer vision nodes:

| Category | Typical Nodes | Functionality |
| :--- | :--- | :--- |
| **`FLOW`** | `InputMatNode`, `OutputMatNode`, `FlagsNode` | Pipeline anchors, frame input sources, and global execution flags. |
| **`IMAGE_PROC`** | `BlurNode`, `CannyEdgeNode`, `CvtColorNode`, `ErodeDilateNode`, `MaskNode`, `ThresholdNode`, `AverageColorNode`, Bitwise Ops | Fundamental image transformations, color space conversions (RGB, HSV, LAB, Grayscale), edge detection, and morphological filters. |
| **`FEATURE_DET`**| `BlobDetectorNode`, `FindContoursNode`, `HoughCirclesNode`, `BoundingRectsNode`, `BoundingRotatedRectsNode`, `PaperVisionMagicNode` | Contour discovery, circle detection, blob detection, oriented bounding boxes. |
| **`CLASSIFICATION`**| `FilterContoursByAreaNode`, `FilterContoursByShapeNode`, `FilterRectsByRatioNode`, `CrosshairNode`, `ExportTargetNode` | Filtering candidate detections by aspect ratio, area, or convexity; exporting target coordinates to robot telemetry. |
| **`OVERLAY`** | `DrawContoursNode`, `DrawRectanglesNode`, `DrawRotatedRectanglesNode`, `DrawCirclesNode`, `LineParametersNode` | Drawing diagnostic visual overlays on intermediate video frames. |
| **`TRANSFORM`** | `ComposeRectNode`, `ComposeRotRectNode`, `DecomposerNode` | Splitting and merging coordinate primitives and bounding boxes. |
| **`MATH`** | `IntegerMathNode`, `DecimalMathNode`, `Vector2MathNode`, Type Converters | Arithmetic, clamping, vector operations, and unit conversions. |