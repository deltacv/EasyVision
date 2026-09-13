# Graph Model, Nodes, Sockets, and Annotation Processing

## Purpose and Scope

The Node and Attribute subsystem forms the canonical in-memory representation of a computer vision algorithm in VisionGraph. It defines the Directed Acyclic Graph (DAG) topology, enforces socket type safety, prevents infinite recursion cycles, drives code generation propagation, powers polymorphic socket decomposition, and provides compile-time metadata discovery via Kotlin Symbol Processing (KSP).

This document details:
* The class hierarchy from `DrawableIdElementBase` through `Node<S>` and `DrawNode<S>`.
* Sockets (`Attribute` and `TypedAttribute<T>`) and their visual rendering.
* The `AttributeType<A>` companion object contract and its metadata registry.
* Socket link compatibility rules and topological cycle prevention (`DirectedNodeGraph`).
* Polymorphic socket decomposition (`AttributeDecomposer` and `DecomposerNode`).
* Code generation propagation mechanics, including backward-pull traversal and forward dead-end resolution.
* Compile-time discovery and registration via `@PaperNode` and KSP.

---

## High-Level Architecture

The graph model connects the visual UI canvas, the serialization codecs, and the code generation pipeline:

```
CLASS HIERARCHY:
================
                  IdElement
                      │
            DrawableIdElementBase
                      │
       ┌──────────────┴──────────────┐
       ▼                             ▼
    Node<S>                      Attribute
 (DAG Entity)                 (Socket Mode & Links)
       │                             │
    DrawNode<S>                TypedAttribute<R>
 (ImNodes Visuals)            (Strongly-Typed Socket)
                                     │
                        ┌────────────┴────────────┐
                        ▼                         ▼
                 Primitives / Structs      ListAttribute<E, ER>
                 (Mat, Rect, Int, Double)  (Composite Sockets)

OWNERSHIP & GRAPH RELATIONSHIPS:
================================
Node (1) ────────── owns ─────────> (*) Attribute
Attribute (1) ──── connects ──────> (*) Link
Link ──────── connects A to B ────> (Attribute A, Attribute B)
Node ──────── propagates to ──────> (Downstream Nodes via Links)
```

---

## Core Components and File References

| Component | File Path | Primary Responsibility |
| :--- | :--- | :--- |
| **`Node<S>`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/Node.kt` | Base entity for all graph nodes; manages attribute lists, serialization, and code generation propagation. |
| **`DrawNode<S>`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/DrawNode.kt` | Visual specialization of `Node`; handles ImNodes title bar styling, pin placement, and canvas drawing. |
| **`Attribute`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/Attribute.kt` | Base socket abstraction; manages input/output pin modes, link acceptance policies, and change events. |
| **`TypedAttribute<R>`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/TypedAttribute.kt` | Strongly-typed socket carrying concrete `GenValue` types; handles upstream link pulling and visual layout. |
| **`AttributeType<A>`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/TypedAttribute.kt` | Metadata and factory interface implemented by companion objects of all typed attribute classes. |
| **`AttributeDecomposer<S>`**| `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/decomp/AttributeDecomposer.kt` | Base class for decomposers that unpack structured sockets (e.g. `Rect`, `Mat`, `Vec2`) into primitive outputs. |
| **`DecomposerNode`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/transform/DecomposerNode.kt` | Polymorphic canvas node that dynamically injects output sockets according to the linked decomposer. |
| **`DirectedNodeGraph`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/DirectedNodeGraph.kt` | Topological DAG representation; verifies acyclicity via recursive Depth-First Search (DFS). |
| **`PaperNodeRegistry`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/PaperNodeRegistry.kt` | Central discovery catalog providing categorized node lists and zero-reflection instantiation factories. |
| **`PaperNodeAnnotationProcessor`** | `AnnotationProcessor/src/main/kotlin/org/deltacv/visiongraph/annotation/papernode/PaperNodeAnnotationProcessor.kt` | KSP compiler plugin validating constructors and generating AOT registration metadata. |

---

## Node Hierarchy and Lifecycle

All nodes inherit from `Node<S: CodeGenSession>` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/Node.kt`):

```
+─────────────────────────────────────────────────────────────+
| Node<S: CodeGenSession> Lifecycle                           |
+─────────────────────────────────────────────────────────────+
  1. Instantiation: Primary constructor invoked (AOT lambda or reflection)
  2. Attachment: parentNode assigned, IdContext allocates scoped ID
  3. Attribute Registration: attributes registered via +attribute
  4. Enable: onEnable() initializes internal state & decomposers
  5. Per-Frame Loop: draw() -> drawNode() / drawAttributes()
  6. Propagation: codeGenPropagate() pushes to dead-end side effects
  7. Destruction: delete() removes links and releases ID
```

### Core Node Responsibilities

1. **Attribute Management**:
   Nodes declare sockets as member properties and register them using the unary plus operator (`+attribute`):
   ```kotlin
   class BlurNode : DrawNode<BlurNode.Session>() {
       val input = MatAttribute(INPUT, "$[att_input]")
       val output = MatAttribute(OUTPUT, "$[att_output]").enablePrevizButton()
       val blurType = EnumAttribute(INPUT, "$[att_blurtype]", BlurType.options)

       override fun onEnable() {
           + input
           + output
           + blurType
       }
   ...
   ```
   `addAttribute(attribute)` attaches the socket to the node, sets `attribute.parentNode = this`, wires change listeners (`attribOnChangeListenerIds`), and automatically requests a previz rebuild when links change (`rebuildOnLink()`).

2. **Visual Specialization: `DrawNode<S>`**:
   `DrawNode` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/DrawNode.kt`) integrates `Node` with the ImNodes canvas:
   * Pushes header styling: `ImNodesCol.NodeBackground`, `ImNodesCol.TitleBar`, and `ImNodesCol.TitleBarHovered`.
   * Title bar rendering: Displays localized title text with category icons.
   * Renders sockets: Calls `attribute.draw()` within `ImNodes.beginInputAttribute()` or `ImNodes.beginOutputAttribute()`.
   * Panning: Centers the viewport on the node when compilation errors occur.

3. **Deletion, Restoration, and the Action Stack**:
   * `delete()`: Deletes all attached sockets, removes all incident links, unregisters from `idContainer`, and fires `onDelete`.
   * `restore()`: Restores the node with its original ID, restores attributes, and restores previous links if their counterparty attributes are enabled. This powers the undo/redo stack (`ActionStack`).

---

## Sockets: `Attribute` and `TypedAttribute<R>`

Sockets represent the input and output connection pins on a node.

### 1. Base Class: `Attribute`
Defined in `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/Attribute.kt`:
* **Pin Modes**: `AttributeMode.INPUT` vs `AttributeMode.OUTPUT`.
  * `INPUT`: Receives data from exactly one incoming link.
  * `OUTPUT`: Broadcasts data to zero, one, or many outgoing links.
* **Link State**:
  * `links`: All incident `Link` objects.
  * `hasLink`: Returns `true` if any enabled links are attached.
  * `availableLinkedAttribute`: Convenience accessor for input attributes returning the single connected upstream socket. Throws an error if called on an output attribute.
  * `availableLinkedAttributes`: Returns all connected sockets.
* **Change Notification & Event Coalescing**:
  Inherits `DelegatedChangeEmitter<Attribute.ChangeType>`. Emits `ChangeType.LinkChange` or `ChangeType.ValueChange`. Changes are queued via `QueuedChangeEmitter` and coalesced during the frame draw pass to avoid UI event spam.
* **Tuner Integration**:
  Provides `editorValue` and `tunerValue`. Sockets generate unique tuner identifiers via `tunerLabel()`. When user values change, `broadcastTunerMessageFor()` dispatches a `TunerChangeValueMessage` over IPC to the simulator without restarting the pipeline.

### 2. Strongly-Typed Sockets: `TypedAttribute<R: GenValue>`
Defined in `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/TypedAttribute.kt`:
`TypedAttribute` binds a socket to a concrete intermediate code generation value (`GenValue`):
* **Upstream Link Pulling (`readGenValue`)**:
  When a node requests an input value during code generation via `input.genValue(current)`, `readGenValue` resolves the dependency through a multi-stage process:
  1. **Mode Differentiation**: When invoked on an output socket, it triggers `parentNode.genCodeIfNecessary(current)` and returns the node's cached session value. When invoked on an input socket, it resolves the upstream data source.
  2. **Upstream Link Resolution**: If the input socket is linked (`hasLink`), it locates the connected upstream output socket (`availableLinkedAttribute`). It verifies the connection exists and asserts that the socket is not linked to itself.
  3. **Circular Recursion Guard**: Before traversing upstream, it checks `current.codeGen.isBusy(this)`. If the socket is already marked busy in the compilation session, generation halts immediately with an `err_recursiondetected` diagnostic.
  4. **Recursive Upstream Execution**: The socket marks itself as busy in `CodeGen`, recursively invokes `linkedAttrib.genValue(current)` to force the upstream node to generate its session code, asserts that the resulting value matches the expected `GenValue` subtype `R`, and unmarks itself in a `finally` block.
  5. **Editor Fallback Evaluation**: If the socket has no incoming link, it falls back to the local editor field value (`inputFieldValue`). If the socket strictly requires a connection (such as required OpenCV Mat inputs) and no default constant exists, it raises an actionable validation error on the pin.
* **Layout and ImGui 1.92+ Width Protection**:
  * Input sockets render left-aligned with their icon and label.
  * Output sockets render right-aligned against the node boundary. In ImGui 1.92+, calculating node dimensions during indenting can trigger an unbounded layout feedback loop. `TypedAttribute` enforces a `maxIndent = 200.0f` cap to guarantee visual stability.

---

## The `AttributeType<A>` Companion Contract

The contract establishes a standardized interface for metadata, visual theming, factory instantiation, and decomposer wiring:

* **Visual Identity (`icon`, `styleColor`, `styleHoveredColor`)**: Defines the FontAwesome icon glyph and the ImNodes pin color in both idle and hovered states.
* **List Palette Styling (`listStyleColor`, `listStyleHoveredColor`, `isDefaultListColor`)**: Specifies custom colors when instances of this attribute type are embedded inside composite `ListAttribute` containers.
* **Dynamic Factory (`new`, `allowsNew`)**: Instantiates new attribute instances dynamically given an `AttributeMode` (`INPUT` or `OUTPUT`) and variable name. Sockets that cannot be created on-the-fly override `allowsNew = false`.
* **Decomposition Provider (`newDecomposer`)**: Returns a dedicated `AttributeDecomposer` instance if the attribute can be unpacked into constituent primitive sockets (e.g. decomposing a `Rect` into position and size vectors), or `null` if the data type is atomic.

### Purpose of the `AttributeType` Contract

1. **Metadata Decoupling**: Sockets expose visual styling (pin colors, hover colors, FontAwesome glyphs) statically without requiring an active instance.
2. **Dynamic Instantiation (`new`)**: Allows composite attributes (like `ListAttribute`) to instantiate new element sockets dynamically at runtime.
3. **Decomposer Discovery (`newDecomposer`)**: Informs transformer nodes whether the socket supports decomposition into component parts.

### Decentralized Extensibility

VisionGraph deliberately avoids maintaining a central enum, registry, or catalog of attribute types. Sockets are designed around the **Open-Closed Principle**:
* New attribute types can be created in any package or external module without modifying core registries.
* Sockets self-describe their capabilities, visual theme, and factory construction entirely through their local `AttributeType` companion.
* Features like link type-checking, composite containers (`ListAttribute`), and dynamic decomposition (`DecomposerNode`) interact with attributes purely through the `AttributeType` interface rather than hardcoded `when` or `instanceof` branches.

### Behavioral Archetypes

Rather than rigid type hierarchies, sockets fall into distinct behavioral categories:

1. **Primitive & Scalar Sockets**:
   * Carry scalar values such as integers, floating-point numbers, booleans, ranges, or enums.
   * Typically bridge an interactive editor widget (sliders, combo boxes, text inputs) with emitted code literals or live tuner fields.
   * Do not provide decomposers as they are already atomic.

2. **Computer Vision & Geometric Sockets**:
   * Carry domain objects such as image buffers (`GenValue.Mat`), 2D vectors, bounding rectangles, rotated rectangles, or keypoints.
   * Sockets carrying structured data (such as rectangles or vectors) often provide an `AttributeDecomposer` via `newDecomposer()`, allowing `DecomposerNode` to unpack them into constituent properties.
   * Can carry domain-specific metadata (such as color space tracking in `MatAttribute`).

3. **Composite & Container Sockets**:
   * Higher-order sockets (such as `ListAttribute`) that manage a dynamic collection of child sockets.
   * Decoupled from concrete child types: they operate on any `AttributeType`, querying `new()` to spawn child elements on demand and inheriting the child's theme colors.

4. **Polymorphic & Wildcard Sockets**:
   * Sockets like `AnyAttribute` that dynamically accept connections from diverse attribute types.
   * Utilize functional predicates (`linkAcceptor`) to accept, reject, or morph socket behavior based on the connected counterparty at runtime.

### Implementing a New Socket Type

To introduce a new attribute to VisionGraph, a developer only needs to implement two components:

1. **Subclass `TypedAttribute<R>`**: Parameterize with an intermediate compiler value (`GenValue` subtype `R`), pass the companion object into `super(Companion)`, and implement UI rendering if the socket displays widgets.
2. **Implement `AttributeType<Self>` on the Companion Object**: Declare visual icons/colors, implement `new(mode, variableName)` for dynamic list embedding, and optionally return an `AttributeDecomposer` from `newDecomposer()`.

Once declared, the socket is immediately fully functional across the node editor, link validation, composite lists, decomposers, and code generation without altering any central registration files:

```kotlin
// Canonical Pattern: Subclass TypedAttribute with a Companion implementing AttributeType
class FloatAttribute(
    mode: AttributeMode,
    variableName: String = "$[att_value]"
) : TypedAttribute<GenValue.Float>(Companion) {

    override var attributeName: String? = variableName

    // Companion object serves as the decentralized AttributeType descriptor
    companion object : AttributeType<FloatAttribute> {
        override val icon = FontAwesomeIcons.Hashtag
        override fun new(mode: AttributeMode, variableName: String) = FloatAttribute(mode, variableName)
    }
}
```

---

## Composite Sockets: `ListAttribute<E, ER>`

`ListAttribute` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/misc/ListAttribute.kt`) represents a list of homogeneous attributes. It is a composite attribute that manages a collection of child sockets:

```
+─────────────────────────────────────────────────────────────+
| ListAttribute: <[#]> Points List                            |
+─────────────────────────────────────────────────────────────+
  │
  ├── Child Socket 0: IntAttribute (Value = 10)
  ├── Child Socket 1: IntAttribute (Value = 20)
  ├── Child Socket 2: IntAttribute (Value = 30)
  └── [+] Add Element Button (if allowModification == true)
```

### Key Architectural Behaviors

1. **Composite Visual Icon & Styling**:
   * The icon is dynamically assembled by enclosing the child element's icon in bracket glyphs (`<[elementIcon]>`).
   * Pin color inherits from `elementAttributeType.listStyleColor`.
2. **Fixed vs Dynamic Lists**:
   * If `fixedLength` is set, the socket maintains exactly that number of child sockets and disallows manual deletion.
   * If `fixedLength == null`, user controls allow adding new elements (`createElement()`) or deleting existing elements interactively.
3. **Link Redirection**:
   If an incoming link carries an output matching `elementAttributeType`, `ListAttribute.acceptLink` intercepts the connection and appends the linked item into the list collection.

---

## Link Validation and DAG Cycle Prevention

Connections between sockets are governed by strict type matching and topological validation.

### 1. Link Acceptance Rules (`acceptLink`)
When a user drags a link between sockets, the target attribute's `acceptLink(other)` method evaluates the proposed connection against several criteria:
1. **Direction Invariant**: Links must always connect an `OUTPUT` socket to an `INPUT` socket. Connecting two inputs or two outputs is rejected immediately.
2. **Matching Attribute Type**: The connection is accepted if both sockets share the same `AttributeType` companion identity (e.g. connecting a `MatAttribute` output to a `MatAttribute` input).
3. **Exact Class Equality**: Sockets sharing the exact same concrete Kotlin class are accepted.
4. **Polymorphic Wildcards**: An output pin can connect to an `AnyAttribute` if the target's `linkAcceptor` predicate approves the source.
5. **List Element Embedding**: An output socket can link directly to an input `ListAttribute` if the output's `attributeType` matches the list's `elementAttributeType`. The list automatically wraps and appends the connection as a new element.

If none of these criteria match, the connection is rejected with an explanatory failure message (`err_couldntlink_didntmatch`).

### 2. Topological Cycle Prevention (`DirectedNodeGraph`)
Even if socket types match, a link is rejected if it would create an algorithmic cycle.

`DirectedNodeGraph` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/DirectedNodeGraph.kt`) tracks the DAG structure in an internal adjacency map (`fromNodeId -> Set<toNodeId>`). Before `CreateLinkAction` creates a link:
1. **Immediate Self-Loop Check**: Rejects any link where `fromNodeId == toNodeId`.
2. **Recursive DFS Traversal**: Runs a Depth-First Search starting at `toNodeId` to explore all downstream paths.
3. **Cycle Rejection**: If any traversal branch reaches `fromNodeId`, a path already exists from target to source. Adding the proposed link would close an infinite loop, so the connection is rejected immediately. This prevents visual graphs from ever entering an infinite recursion state.

---

## Dynamic Socket Decomposition: `AttributeDecomposer` & `DecomposerNode`

A key usability feature of VisionGraph is the **Decomposer** (`attribute/decomp/` and `node/transform/DecomposerNode.kt`).

Rather than creating dozens of static nodes (e.g. `DecomposeRect`, `DecomposeMat`, `DecomposeVector2`), VisionGraph implements a single, polymorphic **`DecomposerNode`** whose output sockets morph dynamically depending on what is plugged into its input pin.

```
                  ┌────────────────────────┐
                  │     DecomposerNode     │
                  │                        │
  RectAttribute ──► Input [AnyAttribute]   │
                  │                        │
                  │   (Morphs Out Pins)    │
                  │   Position [Vector2] ──►
                  │   Size     [Vector2] ──►
                  └────────────────────────┘
```

### How Decomposition Works

1. **Link Verification**:
   `DecomposerNode.input` is an `AnyAttribute`. Its `linkAcceptor` dynamically checks if the incoming candidate socket's `AttributeType` companion provides a decomposer via `newDecomposer()`. If a decomposer exists, the link is accepted; otherwise, the connection is rejected.
2. **Socket Injection**:
   When a valid link is established:
   * `DecomposerNode` detects the connection and attaches the new decomposer instance via `enable(node, input)`.
   * The active `AttributeDecomposer` runs its `onEnable()` lifecycle, registering specialized output sockets directly onto the `DecomposerNode`.
3. **Cleanup**:
   If the link is disconnected, `decomposer.disable()` removes the dynamically injected output sockets and tears them down, returning the node to its dormant empty state.
4. **Code Generation Delegation**:
   `DecomposerNode` declares a universal polyglot generator that delegates execution to the active decomposer. During code generation, the decomposer extracts the incoming `GenValue` from the input socket, generates the component property extractions for the target language, and stores its session in `current.codeGen.sessions`. Output sockets defer their values directly to this session.

### Concrete Decomposers

#### 1. `RectAttributeDecomposer`
* **Inputs**: Accepts `GenValue.Rect`.
* **Injected Outputs**:
  * `position`: `Vector2Attribute` (X, Y).
  * `size`: `Vector2Attribute` (Width, Height).
* **Language Logic**:
  * **Java**: Emits property accesses on `org.opencv.core.Rect`: `rect.x`, `rect.y`, `rect.width`, `rect.height`.
  * **Python**: Emits tuple indexing on OpenCV bounding box: `rect[0]`, `rect[1]`, `rect[2]`, `rect[3]`.

#### 2. `Vector2AttributeDecomposer`
* **Inputs**: Accepts `GenValue.Vec2`.
* **Injected Outputs**:
  * `x`: `IntAttribute`.
  * `y`: `IntAttribute`.
* **Dynamic Naming**: If the connected `Vector2Attribute` has `useSizeNaming = true`, the decomposer automatically labels the sockets **Width** and **Height** instead of **X** and **Y**.

#### 3. `MatAttributeDecomposer`
* **Inputs**: Accepts `GenValue.Mat`.
* **Injected Outputs**:
  * `size`: `Vector2Attribute` (Width, Height).
  * `channels`: `IntAttribute`.
* **Language Logic**:
  * **Java**: Calls `mat.cols()`, `mat.rows()`, and `mat.channels()`.
  * **Python**: Reads `shape[1]` (width), `shape[0]` (height), and channels (`shape[2]` or `1` for grayscale images).

---

## Code Generation Propagation: Backward Pull vs Forward Push

A challenge in visual graph compilers is reconciling **pull-based** dataflow with **push-based** side effects.

### The Backward Pull (Primary Flow)
Standard pipeline compilation begins at the root anchor `OutputMatNode`:
1. `OutputMatNode.genCodeIfNecessary(current)` requests `input.genValue(current)`.
2. This invokes `sourceNode.getGenValueOf(current, sourceAttribute)`.
3. `sourceNode` invokes its own `genCodeIfNecessary(current)` to compute its session.
4. Traversal proceeds recursively backwards to camera input nodes.

### The Forward Push: Dead-End Handling (`codeGenPropagate`)
In robotics pipelines, some nodes produce vital side-effects but do not feed back into `OutputMatNode`. Examples include:
* Target extraction nodes (`ExportTargetNode`, `ExportTargetsNode`).
* Telemetry logging nodes.
* Diagnostic Mat displays.

Because nothing pulls from these nodes, a pure backward traversal would leave them out of the generated code.

VisionGraph solves this with **Dead-End Propagation** in `Node`:

#### 1. Dead-End Identification (`hasDeadEnd`)
A node is identified as a dead end relative to the pipeline's main output if none of its downstream paths eventually connect to `OutputMatNode`:
* The algorithm traverses all output sockets of the node and inspects their connected counterparty attributes.
* Sockets connecting back to the initial traversal node or flowing backwards are ignored to prevent infinite recursion.
* If any connected downstream node is an instance of `OutputMatNode`, or recursively reports that it does *not* terminate in a dead end, the branch is proven to contribute to the primary frame pipeline, and `hasDeadEnd` returns `false`.
* If all downstream paths terminate at leaf nodes without ever reaching `OutputMatNode`, the node is marked as a dead end (`hasDeadEnd` returns `true`).

#### 2. Forward Push Propagation (`codeGenPropagate`)
Because dead-end branches cannot be reached by the standard backward pull originating at `OutputMatNode`, compilation must be pushed forward into them:
* Immediately after any node finishes generating its own code during `genCodeIfNecessary(current)`, its `codeGenPropagate(current)` method is invoked.
* The method discovers all direct downstream nodes connected to the current node's output sockets.
* It filters these downstream nodes using `hasDeadEnd()` to isolate the branches that will never be reached by the primary backward pull.
* For every dead-end downstream node discovered, it explicitly invokes `genCodeIfNecessary(current)`.
* This pushes compilation forward along the dead-end branch, guaranteeing that side-effect statements (such as target telemetry publication or data export) are fully generated and inserted into the output source code.

---

## Compile-Time Discovery: `@PaperNode` and KSP

VisionGraph avoids expensive classpath scanning at runtime. Node catalogs are generated ahead-of-time (AOT) using Kotlin Symbol Processing (KSP).

### 1. The `@PaperNode` Annotation
Every concrete node class is annotated with `@PaperNode`:
```kotlin
@PaperNode(
    name = "nod_blur",
    category = NodeCategory.IMAGEPROC,
    description = "des_blur"
)
class BlurNode : DrawNode<BlurNode.Session>() { ... }
```

### 2. KSP Processing: `PaperNodeAnnotationProcessor`
During compilation of the `VisionGraph` module, the KSP processor (`AnnotationProcessor/src/main/kotlin/org/deltacv/visiongraph/annotation/papernode/PaperNodeAnnotationProcessor.kt`):
1. Finds all classes annotated with `@PaperNode`.
2. Validates that the annotated class inherits from `Node`.
3. Verifies that the class provides a public, zero-argument primary constructor.
4. Generates a synthetic registration class `PaperVisionPaperNodeMetadata.registerAll()` that registers every node class and its category directly into `PaperNodeRegistry`.

### 3. Catalog Registry: `PaperNodeRegistry`
Defined in `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/PaperNodeRegistry.kt`:
* **Categorized Map**: `val nodes: CategorizedNodes` maps each `NodeCategory` (e.g. `IMAGEPROC`, `FEATUREDET`, `CLASSIFICATION`, `TRANSFORM`, `MATH`) to a list of node classes. The visual search popup (`NodeList`) queries this map to render the categorized palette.
* **Fast Instantiation (`instantiate`)**: Instantiates nodes using registered AOT constructor lambdas, avoiding Java reflection overhead.