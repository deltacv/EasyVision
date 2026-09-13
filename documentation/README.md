# VisionGraph Technical Architecture Documentation

Welcome to the software architecture documentation for **VisionGraph** (formerly *PaperVision* / *EasyVision*, maintained by [deltacv](https://github.com/deltacv/VisionGraph)).

This documentation suite serves as an architectural and design reference for developers and contributors working on the VisionGraph codebase. Written from an engineering perspective, each chapter explains the **what**, **how**, and **why** of a specific subsystem, moving from high-level architectural patterns to concrete implementation details, thread models, and lifecycle sequences.

---

## High-Level System Architecture

VisionGraph is organized as a multi-module Gradle project designed around strict decoupling between presentation, graph data modeling, code synthesis, engine execution, and hardware platform bindings:

```
                  +-----------------------------------+
                  |        AnnotationProcessor        |
                  |     (Compile-Time KSP Discovery)  |
                  +-----------------+-----------------+
                                    | (metadata)
          +-------------------------+-------------------------+
          |                                                   |
          v                                                   v
+-------------------+                               +-------------------+
|      Shared       |                               | VisionBenchPlugin |
| (Wire Protocols,  |---+                           | (EOCV-Sim Host    |
| Byte Framing,     |   |                           |  Integration)     |
| Common Models)    |   |                           +---------+---------+
+---------+---------+   |                                     ^
          |             v                                     |
          |   +-------------------+                           |
          +-->|    VisionGraph    |                           |
              | (Core UI, Engine  |                           |
              |   Model, Codegen) |                           |
              +---------+---------+                           |
                        |                                     |
                        v                                     |
              +-------------------+                           |
              |   LwjglPlatform   |---------------------------+
              | (GLFW, OpenGL,    |
              |  ImGui Hardware)  |
              +---------+---------+
                        |
                        v
              +-------------------+
              |    Standalone     |
              | (Desktop Executable|
              |    Launcher)      |
              +-------------------+
```

---

## Architectural Chapter Index

| Chapter | Title | Focus & Core Responsibilities |
| :--- | :--- | :--- |
| **[01-overview.md](01-overview.md)** | **System Architecture & Overview** | Core mission, multi-module layout, architectural principles (zero-reflection AOT discovery, platform decoupling, fault isolation), dual run modes (standalone vs integrated), and end-to-end dataflow. |
| **[02-app-entry.md](02-app-entry.md)** | **Application Entry Points & Runtime Lifecycle** | Bootstrapping sequence in `VisionGraph.kt`, `LWJGLPaperVisionApp`, entry points (`AppMain` vs `VisionGraphIpcMain`), the per-frame render loop, input polling, and graceful teardown. |
| **[03-config-and-settings.md](03-config-and-settings.md)** | **Configuration, Settings & i18n** | Persistent JSON storage (`PlatformConfigManager` / `~/.papervision/config.json`), runtime flags, build metadata (`BuildInfo`), and thread-local multi-language localization via `mai18n`. |
| **[04-gui-editor.md](04-gui-editor.md)** | **GUI, Visual Canvas & Node Editor** | Dear ImGui + ImNodes rendering loop, dual-context isolation (canvas vs palette `NodeList`), declarative Compose DSL, link validation, and the forkable undo/redo `Action` command stack. |
| **[05-engine-previz.md](05-engine-previz.md)** | **Engine Abstraction & Live Previz Pipeline** | Generic execution engine abstraction (`PaperVisionEngine`, `PaperVisionEngineBridge`), binary wire framing (`ByteMessages`), client coordinator (`PaperVisionEngineClient`), request TTLs, frame backpressure, and in-memory mock engines. |
| **[06-io-and-platform.md](06-io-and-platform.md)** | **Platform Abstraction & Thread-Safe GPU IO** | OS/hardware decoupling (`PlatformSetup`, `PlatformWindow`, `PlatformTextureFactory`), GLFW input handling (`KeyManager`), and thread-affine GPU texture uploads via `TextureProcessorQueue`. |
| **[07-serialization.md](07-serialization.md)** | **Serialization, Project Formats & Codecs** | v1 legacy format vs modern v2 typed envelope architecture (`DataCodec`), compile-time codec registration via KSP (`CodecTypeRegistry`), clipboard copy/paste, and safe multi-step graph rehydration. |
| **[08-codegen.md](08-codegen.md)** | **Polyglot Code Generation Subsystem** | Multi-pass compiler pipeline, AST builders (`Scope`, `Value`, `Type`), polyglot generator closeness heuristics, language backends (Java, Kotlin, Python, JS, Lua), two-pass placeholder resolution, and visual canvas error mapping. |
| **[09-node-and-attributes.md](09-node-and-attributes.md)** | **Graph Model, Sockets & Annotation Processing** | Node and socket class hierarchy (`Node`, `DrawNode`, `TypedAttribute`), recursive DFS cycle prevention (`DirectedNodeGraph`), decomposers, and compile-time discovery via KSP (`@PaperNode`). |
| **[10-core-utilities.md](10-core-utilities.md)** | **Core Utilities, ID Containers & Event Systems** | Thread-local ID management (`IdContext`, `DenseIdContainer`), event buses (`PaperEventHandler`), change event batching and coalescing (`QueuedChangeEmitter`), and tiered memory pools (`MemoryPool`). |
| **[11-visionbench-plugin.md](11-visionbench-plugin.md)** | **VisionBench Plugin & Live Simulation** | Integration into VisionBench / EOCV-Sim, external JVM process isolation runner, sub-50ms dynamic Java compilation with Janino, SIMD TurboJPEG video streaming (`MackJPEG`), zero-latency parameter tuning, and the crash recovery daemon. |

---

## Suggested Reading Paths

Depending on your engineering focus, we recommend the following sequences:

### For Node and Algorithm Developers
1. [01-overview.md](01-overview.md) — Architectural overview.
2. [09-node-and-attributes.md](09-node-and-attributes.md) — Learn how to define nodes, typed sockets, and annotations.
3. [08-codegen.md](08-codegen.md) — Learn how to write polyglot generators and emit into AST scopes.
4. [04-gui-editor.md](04-gui-editor.md) — Learn how to compose custom property inspectors using Compose DSL.

### For Engine and Backend Integrators
1. [05-engine-previz.md](05-engine-previz.md) — Master the generic engine abstraction and wire protocols.
2. [06-io-and-platform.md](06-io-and-platform.md) — Understand how binary frames become GPU textures.
3. [11-visionbench-plugin.md](11-visionbench-plugin.md) — Study the canonical VisionBench/EOCV-Sim simulator implementation.

### For UI and Platform Engineers
1. [02-app-entry.md](02-app-entry.md) — Frame loops and lifecycle management.
2. [04-gui-editor.md](04-gui-editor.md) — ImGui/ImNodes canvas rendering and action stacks.
3. [10-core-utilities.md](10-core-utilities.md) — Scoped ID containers and event coalescing.

---

## Developer Quick Reference

### Running and Building
* **Run in Standalone Mode (Dev)**:
  ```bash
  ./gradlew runEv
  # Or: ./gradlew :LwjglPlatform:Standalone:runPv
  ```
* **Clean and Build All Modules**:
  ```bash
  ./gradlew clean build
  ```
* **Build VisionBench Plugin Jar**:
  ```bash
  ./gradlew :VisionBenchPlugin:shadowJar
  ```
