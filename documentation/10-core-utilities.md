# Core Utilities, ID Management, and Event Systems

## Purpose and Scope

VisionGraph's high responsiveness, memory stability, and crash-resilience depend on low-level infrastructure utilities: scoped integer ID allocation, lifecycle event buses, coalesced change emitters, and tiered memory pools.

This document details these foundational building blocks, with special emphasis on how the **Thread-Local ID Container Architecture** solves Dear ImGui's integer-identification requirements across complex multi-window hierarchies.

---

## High-Level Architecture

The core runtime utilities operate beneath the GUI, graph model, and engine:

```
+-------------------------------------------------------------+
| Scoped ID Management Subsystem                              |
| - IdContext.local (ThreadLocal stack of container scopes)   |
| - DenseIdContainer (sequential allocation + ID recycling)   |
| - SparseIdContainer (arbitrary non-sequential integer keys) |
| - StackIdContainer (stack pointer for Undo/Redo actions)   |
| - SingleIdContainer (singleton constraint enforcement)      |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
| Event Dispatching & Notification Subsystem                  |
| - PaperEventHandler (synchronous event bus: once, batchOnce)|
| - QueuedChangeEmitter (coalesces rapid slider ticks)        |
| - DelegatedChangeEmitter (composition wrapper)              |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
| Performance & Resource Pooling Utilities                    |
| - MemoryPool (tiered buffer buckets: tierCapacity = 8)      |
| - ElapsedTime (high-resolution nanoTime stopwatch)         |
+-------------------------------------------------------------+
```

---

## The ID Management Subsystem (`org.deltacv.visiongraph.id`)

### The Dear ImGui / ImNodes ID Problem
Immediate-mode graphical frameworks like Dear ImGui and ImNodes do not retain persistent object references between frames. Instead, they require every interactive widget, window, node, socket pin, and link to be assigned a unique **32-bit integer ID**.

In an application featuring multiple concurrent windows, an interactive node canvas, and a separate node palette, naive global integer counters quickly produce catastrophic **ID Collisions**:
* If a pin in the node palette shares integer ID `105` with a pin on the main editor canvas, hovering over the palette pin causes the canvas pin to highlight or disconnect.
* If a deleted node's ID is immediately reused by a newly created link, undo operations target the wrong entity.

### The Solution: Scoped `IdContext` and `IdContainer`
VisionGraph resolves this cleanly via a scoped, thread-local stack architecture:
* **Context**: `IdContext` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/id/container/IdContext.kt`) maintains a thread-local stack: `ThreadLocal<IdContext>`.
* **Container Implementations**:

| Container Type | File Reference | Allocation Policy & Use Case |
| :--- | :--- | :--- |
| **`DenseIdContainer`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/id/container/DenseIdContainer.kt` | Sequential integer allocation starting from `1`. Maintains an internal recycled queue: when an element is deleted, its integer ID is returned to the pool and reused on the next allocation. Used for Nodes, Attributes, Links, and Windows. |
| **`SparseIdContainer`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/id/container/SparseIdContainer.kt` | Map-backed container allowing arbitrary non-sequential keys. Used for Fonts and miscellaneous elements. |
| **`StackIdContainer`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/id/container/StackIdContainer.kt` | Maintains a stack pointer and supports branching/forking. Used exclusively for the undo/redo `Action` history stack. |
| **`SingleIdContainer`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/id/container/SingleIdContainer.kt` | Enforces that exactly one active instance exists (e.g., `TextureProcessorQueue`). |

### Scoping Example: Palette Isolation
When `NodeList` renders its palette of preview nodes, it pushes a dedicated `DenseIdContainer`:
```kotlin
// Inside NodeList.draw():
IdContext.local.push(paletteNodeContainer)
try {
    // All nodes instantiated/drawn here receive isolated IDs 
    // that never conflict with canvas nodes
    renderCategoryNodes()
} finally {
    IdContext.local.pop<Node<*>>()
}
```

---

## Event Systems & Notification Architecture

### 1. Lightweight Event Dispatcher: `PaperEventHandler`
* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/util/event/PaperEventHandler.kt`
* Replaces heavy event buses (like Guava EventBus) with a direct, thread-safe, high-performance callback dispatcher.
* **Key Patterns**:
  * `add { ... }`: Long-lived subscription returning a `PaperEventListenerId` for unsubscription.
  * `once { ... }`: One-shot subscription that automatically unregisters itself immediately after first execution.
  * `batchOnce { ... }`: Registers a group of callbacks that all unregister simultaneously as soon as any one fires.
  * `run()`: Synchronously executes all active listeners.

### 2. Event Coalescing: `QueuedChangeEmitter`
* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/util/ChangeEmitter.kt`

```
EVENT COALESCING SEQUENCE:
==========================
[User drags slider rapidly]
   |
   +--> emitChange(10) --\
   +--> emitChange(11) ----+--> Enqueued in ArrayDeque (no synchronous updates)
   +--> emitChange(12) --/
   |
   | (Frame Render Tick)
   v
[onUpdate / processChanges()]
   |
   +--> Triggers onChange.run() once with finalized state
   +--> Clears queue for next frame
```

In visual programming, interactive sliders emit hundreds of micro-adjustments per second. Invoking graph re-traversals or code generation on every mouse tick causes severe UI stutters. `QueuedChangeEmitter` buffers events during the frame, coalesces them, and fires subscribers exactly once during `processChanges()` on the main frame tick.

---

## Performance & Resource Pooling: `MemoryPool`

* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/util/MemoryPool.kt`

Interactive 60 FPS video streaming generates huge memory churn: a 1280x720 32-bit RGBA image occupies ~3.68 MB. Continuously allocating and discarding buffers across threads triggers aggressive JVM garbage collection stops.

`MemoryPool` organizes memory into logarithmic tier buckets (`tierCapacity = 8`):
1. **Borrowing**: Tasks call `memoryPool.getOrCreate(requiredSize)`. The pool returns an existing pre-allocated byte array from the matching tier if available; otherwise, it allocates a new one.
2. **Returning**: When the OpenGL texture upload finishes, `returnBuffer(buffer)` places the array back into the tier.
3. **Result**: Zero continuous GC allocations on steady-state video streaming loops.

---

## Timing and Debounce Utilities: `ElapsedTime`

* **File**: Used throughout input debouncing, shortcut repeats, and telemetry.
* Provides high-resolution millisecond and second deltas using `System.nanoTime()` with simple `reset()` semantics, ensuring cross-platform timing accuracy immune to system wall-clock adjustments.