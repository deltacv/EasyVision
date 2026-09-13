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
  When a node asks for an input's value during code generation, `readGenValue` executes:
  ```kotlin
  protected inline fun <reified R : GenValue> readGenValue(
      current: CodeGen.Current,
      inputFieldValue: R? = null
  ): R {
      if (isInput) {
          if (hasLink || inputFieldValue == null) {
              val linkedAttrib = availableLinkedAttribute
              raiseAssert(linkedAttrib != null, tr("err_musthave_attachedattrib"))
              if (linkedAttrib === this) raise("err_cannotlink_toself")

              // Recursion guard
              if (current.codeGen.isBusy(this)) raise("err_recursiondetected")
              current.codeGen.markBusy(this)

              try {
                  val value = linkedAttrib.genValue(current)
                  raiseAssert(value is R, tr("err_attachedattrib_isnot", R::class.simpleName ?: "unknown"))
                  value
              } finally {
                  current.codeGen.unmarkBusy(this)
              }
          } else {
              inputFieldValue // Use editor constant if unlinked
          }
      } else {
          val value = getGenValueFromNode(current)
          raiseAssert(value is R, tr("err_valreturned_isnot", R::class.simpleName ?: "unknown"))
          value
      }
  }
  ```
  This handles backward graph traversal, detects cycles with `isBusy(this)`, pulls upstream code generation, and asserts that the returned value matches the expected `GenValue` type.
* **Layout and ImGui 1.92+ Width Protection**:
  * Input sockets render left-aligned with their icon and label.
  * Output sockets render right-aligned against the node boundary. In ImGui 1.92+, calculating node dimensions during indenting can trigger an unbounded layout feedback loop. `TypedAttribute` enforces a `maxIndent = 200.0f` cap to guarantee visual stability.

---

## The `AttributeType<A>` Companion Contract

Every typed attribute class in VisionGraph pairs with a companion object implementing the `AttributeType<A>` interface (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/attribute/TypedAttribute.kt`):

```kotlin
interface AttributeType<A: TypedAttribute<*>> {
    val icon: String
    val allowsNew: Boolean get() = true

    val styleColor: Int get() = VisionGraph.imnodesStyle.pin
    val styleHoveredColor: Int get() = VisionGraph.imnodesStyle.pinHovered

    val listStyleColor: Int get() = VisionGraph.imnodesStyle.pin
    val listStyleHoveredColor: Int get() = VisionGraph.imnodesStyle.pinHovered

    val isDefaultListColor: Boolean get() =
        listStyleColor == VisionGraph.imnodesStyle.pin
            && listStyleHoveredColor == VisionGraph.imnodesStyle.pinHovered

    fun new(mode: AttributeMode, variableName: String): A {
        throw UnsupportedOperationException("Cannot instantiate this attribute with new")
    }

    fun newDecomposer(): AttributeDecomposer<*>? = null
}
```

### Purpose of the `AttributeType` Contract

1. **Metadata Decoupling**: Sockets expose visual styling (pin colors, hover colors, FontAwesome glyphs) statically without requiring an active instance.
2. **Dynamic Instantiation (`new`)**: Allows composite attributes (like `ListAttribute`) to instantiate new element sockets dynamically at runtime.
3. **Decomposer Discovery (`newDecomposer`)**: Informs transformer nodes whether the socket supports decomposition into component parts.

### Complete Attribute Type Catalog

| Attribute Class | Companion Interface | Pin Icon | Decomposable? | Primary Value Carried |
| :--- | :--- | :--- | :--- | :--- |
| **`BooleanAttribute`** | `AttributeType<BooleanAttribute>` | `FontAwesomeIcons.ToggleOn` | No | `GenValue.Boolean` |
| **`IntAttribute`** | `AttributeType<IntAttribute>` | `FontAwesomeIcons.Hashtag` | No | `GenValue.Int` |
| **`DoubleAttribute`** | `AttributeType<DoubleAttribute>` | `FontAwesomeIcons.Hashtag` | No | `GenValue.Double` |
| **`RangeAttribute`** | `AttributeType<RangeAttribute>` | `FontAwesomeIcons.ArrowsAltH` | No | `GenValue.Range` |
| **`StringAttribute`** | `AttributeType<StringAttribute>` | `FontAwesomeIcons.Font` | No | `GenValue.String` |
| **`EnumAttribute<E>`** | `AttributeType<EnumAttribute<*>>` | `FontAwesomeIcons.List` | No | `GenValue.Enum<E>` |
| **`MatAttribute`** | `AttributeType<MatAttribute>` | `FontAwesomeIcons.Image` | **Yes** (`MatAttributeDecomposer`) | `GenValue.Mat` (with ColorSpace) |
| **`Vector2Attribute`**| `AttributeType<Vector2Attribute>` | `FontAwesomeIcons.ArrowsAlt` | **Yes** (`Vector2AttributeDecomposer`)| `GenValue.Vec2` |
| **`RectAttribute`** | `AttributeType<RectAttribute>` | `FontAwesomeIcons.VectorSquare` | **Yes** (`RectAttributeDecomposer`)| `GenValue.Rect` |
| **`RotatedRectAttribute`**| `AttributeType<RotatedRectAttribute>`| `FontAwesomeIcons.SyncAlt` | No | `GenValue.RotatedRect` |
| **`CircleAttribute`** | `AttributeType<CircleAttribute>` | `FontAwesomeIcons.Circle` | No | `GenValue.Circle` |
| **`KeyPointAttribute`**| `AttributeType<KeyPointAttribute>`| `FontAwesomeIcons.DotCircle` | No | `GenValue.KeyPoint` |
| **`PointsAttribute`** | `AttributeType<PointsAttribute>` | `FontAwesomeIcons.EllipsisH` | No | `GenValue.Points` |
| **`LineParametersAttribute`**| `AttributeType<LineParametersAttribute>`| `FontAwesomeIcons.PaintBrush` | No | `GenValue.LineParameters` |
| **`ListAttribute<E, ER>`**| `AttributeType<ListAttribute<*, *>>`| `FontAwesomeIcons.List` | No | `GenValue.List<ER>` |
| **`AnyAttribute`** | `AttributeType<AnyAttribute>` | `FontAwesomeIcons.Asterisk` | Dynamic | Wildcard `GenValue` |

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
   * Icon is dynamically assembled from brackets and the child element's icon:
     ```kotlin
     override var icon = "${FontAwesomeIcons.ChevronLeft}[${elementAttributeType.icon}${FontAwesomeIcons.ChevronRight}"
     ```
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
When a user drags a link between sockets, `acceptLink(other)` executes:

```kotlin
// TypedAttribute.kt
override fun acceptLink(other: Attribute): LinkAcceptance {
    val sameTypedAttribute = other is TypedAttribute<*> && other.attributeType == attributeType
    val sameClass = this::class == other::class
    val outputToAny = mode == AttributeMode.OUTPUT && other is AnyAttribute

    // Allow output of matching type to link into an input ListAttribute
    val outputToMatchingList = mode == AttributeMode.OUTPUT &&
            other is ListAttribute<*, *> &&
            other.elementAttributeType == attributeType

    return if (sameTypedAttribute || sameClass || outputToAny || outputToMatchingList) {
        LinkAcceptance.Accept
    } else {
        LinkAcceptance.Reject()
    }
}
```

* **Direction Invariant**: Links must always connect an `OUTPUT` socket to an `INPUT` socket.
* **Type Invariant**: Sockets must share the same `AttributeType` or match the `ListAttribute` element type.
* **Wildcards**: `AnyAttribute` uses a lambda predicate `linkAcceptor: (Attribute) -> LinkAcceptance` to accept or reject links dynamically.

### 2. Topological Cycle Prevention (`DirectedNodeGraph`)
Even if socket types match, a link is rejected if it would create an algorithmic cycle.

`DirectedNodeGraph` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/node/DirectedNodeGraph.kt`) tracks the DAG structure in an adjacency list `Map<Int, Set<Int>>`:

```kotlin
fun hasCycleIfAdded(fromNodeId: Int, toNodeId: Int): Boolean {
    if (fromNodeId == toNodeId) return true // Immediate self-loop

    val visited = mutableSetOf<Int>()

    fun dfs(current: Int): Boolean {
        if (current == fromNodeId) return true // Traced back to start = cycle!
        if (!visited.add(current)) return false

        adjacencyList[current]?.forEach { next ->
            if (dfs(next)) return true
        }
        return false
    }

    return dfs(toNodeId)
}
```

Before `CreateLinkAction` creates a link, `nodeEditor.hasCycleIfAdded(fromNode, toNode)` runs this recursive Depth-First Search (DFS). If `toNode` can reach `fromNode`, the connection is rejected immediately, preventing visual graphs from ever entering an infinite recursion state.

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
   `DecomposerNode.input` is an `AnyAttribute`. Its `linkAcceptor` tests whether the candidate socket provides a decomposer:
   ```kotlin
   val decomposer = (candidate as? TypedAttribute<*>)?.attributeType?.newDecomposer()
   if (decomposer != null) LinkAcceptance.Accept else LinkAcceptance.Reject(...)
   ```
2. **Socket Injection**:
   When a link is created:
   * `DecomposerNode.drawNode()` detects the new link and calls `newDecomposer.enable(this, input)`.
   * `AttributeDecomposer.onEnable()` executes, using `+attribute` to add output sockets to the `DecomposerNode`.
3. **Cleanup**:
   If the link is disconnected, `decomposer.disable()` removes the injected output sockets and deletes them, returning the node to its empty state.
4. **Code Generation Delegation**:
   `DecomposerNode` delegates code generation and output resolution to the active decomposer:
   ```kotlin
   override val generators = polyglot {
       generatorForAny { _, current ->
           decomposer?.let {
               current.codeGen.sessions[it] = it.genCode(input.genValue(current), current)
           }
           NoSession
       }
   }
   ```

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

VisionGraph solves this with **Dead-End Propagation** in `Node.kt`:

```kotlin
// Node.kt
fun hasDeadEnd(initialNode: Node<*> = this): Boolean {
    for (attribute in nodeAttributes) {
        if (attribute.mode == AttributeMode.INPUT) continue

        for (linkedAttribute in attribute.availableLinkedAttributes) {
            if (linkedAttribute != null) {
                if (linkedAttribute.mode == AttributeMode.OUTPUT || linkedAttribute.parentNode == initialNode) {
                    continue
                }

                // If any link reaches OutputMatNode, this path is NOT a dead end
                if (linkedAttribute.parentNode is OutputMatNode || !linkedAttribute.parentNode.hasDeadEnd(initialNode)) {
                    return false
                }
            }
        }
    }
    return true
}

override fun codeGenPropagate(current: CodeGen.Current) {
    val linkedNodes = mutableListOf<Node<*>>()

    // Discover all direct downstream nodes
    for (attribute in _nodeAttributes) {
        if (attribute.mode == AttributeMode.OUTPUT) {
            for (linkedAttribute in attribute.availableLinkedAttributes) {
                if (linkedAttribute != null && !linkedNodes.contains(linkedAttribute.parentNode)) {
                    linkedNodes.add(linkedAttribute.parentNode)
                }
            }
        }
    }

    // Identify which downstream nodes are dead ends (do not reach OutputMatNode)
    val deadEndNodes = linkedNodes.filter { it.hasDeadEnd() }

    // Explicitly force code generation for dead ends so side effects are emitted
    deadEndNodes.forEach { it.genCodeIfNecessary(current) }
}
```

Whenever a node completes code generation, `codeGenPropagate(current)` crawls downstream connections. If it detects a downstream branch that terminates in a dead end, it explicitly invokes `genCodeIfNecessary(current)` on that branch, guaranteeing side-effect nodes are fully compiled.

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