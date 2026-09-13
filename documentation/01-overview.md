# System Architecture and Overview

## Purpose and Scope

**VisionGraph** (historically developed as *PaperVision* under the [deltacv](https://github.com/deltacv/VisionGraph) ecosystem) is an open-source visual node-graph editor, polyglot code generation compiler, and interactive computer vision prototyping environment. 

Developed primarily for robotics applications—specifically FIRST Tech Challenge (FTC) and FIRST Robotics Competition (FRC) teams using OpenCV and EasyOpenCV—VisionGraph enables robotics programmers and computer vision engineers to visually assemble complex image processing pipelines, inspect intermediate frames at any point in the pipeline in real-time, tune algorithm thresholds interactively, and export optimized production-ready source code into multiple target programming languages without manual reimplementation.

This document outlines the high-level system architecture, guiding principles, modular structure, and end-to-end dataflow governing the VisionGraph ecosystem.

---

## High-Level Architecture

VisionGraph is engineered around strict separation of concerns across presentation, graph data modeling, code synthesis, engine communication, and platform hardware abstraction. The repository is organized into six distinct Gradle modules:

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
              +--------------------+
              |    Standalone      |
              | (Desktop Executable|
              |    Launcher)       |
              +--------------------+
```

### Module Breakdown and Responsibilities

| Gradle Module | Primary Responsibility | Dependencies |
| :--- | :--- | :--- |
| **`AnnotationProcessor`** | Compile-time Kotlin Symbol Processing (KSP). Generates AOT registration registries for nodes, polymorphic serialization codecs, and message dispatchers using KotlinPoet, eliminating runtime reflection. | `ksp-symbol-processing-api`, `kotlinpoet` |
| **`Shared`** | Protocol definitions, domain annotations, and binary wire framing. Shared between client UI applications and backend execution runtimes without pulling in rendering or UI dependencies. | `kotlinx-serialization`, `slf4j-api` |
| **`VisionGraph`** | Core business logic, directed graph model, Dear ImGui / ImNodes visual editor canvas, polyglot code generation compiler, project serialization (v1 and v2), and client engine bridge. | `Shared`, `AnnotationProcessor`, `imgui-java-binding`, `mai18n`, `gson` |
| **`LwjglPlatform`** | Desktop platform implementation. Concrete hardware bindings for GLFW windowing, OpenGL texture lifecycle, Native File Dialogs (NFD), and STB image loading. | `VisionGraph`, `imgui-java-app`, `lwjgl-bom`, `natives-*` |
| **`LwjglPlatform:Standalone`** | Standalone desktop application entry point. Configures a `NoOpPaperVisionEngineBridge` to allow visual editing, graph authoring, and code export without requiring an external simulator backend. | `LwjglPlatform`, `logback-classic` |
| **`VisionBenchPlugin`** | The integration module for VisionBench / EOCV-Sim. Manages dynamic in-memory pipeline compilation (Janino), SIMD TurboJPEG video streaming (`MackJPEG`), external JVM process isolation, and WebSocket-driven crash recovery. | `LwjglPlatform`, `EOCV-Sim:Common`, `EOCV-Sim:Vision`, `janino`, `picocli`, `javalin` |

---

## Architectural Principles and Design Goals

### 1. Zero-Reflection Compile-Time Discovery
Reflective classpath scanning is slow, fragile across module boundaries, and problematic in modern JVM modular environments. VisionGraph offloads all node categorization, registration, and polymorphic serialization schema generation to KSP compile-time symbol processors. Annotating a node with `@PaperNode` generates static registration calls during the build phase, yielding instant application boot times and deterministic type resolution.

### 2. Pure Platform Decoupling
The core `VisionGraph` module contains zero direct calls to GLFW, OpenGL, or OS-native file pickers. All platform-specific behavior is mediated through explicit interfaces (`PlatformSetup`, `PlatformWindow`, `PlatformTextureFactory`, `PlatformKeys` in `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/platform/`). This architecture allows the editor to run on standard desktop LWJGL/OpenGL, embed inside custom hosts, or run in headless simulation environments.

### 3. Separation of Graph Semantics from Code Syntax
Nodes do not concatenate source code strings directly. Instead, nodes declare structured operations, data types, and parameters through a polyglot code generation DSL. A multi-pass compiler pipeline manages hierarchical scopes, imports, variables, and symbol resolution, allowing a single visual graph to target multiple execution languages (Java, Kotlin, CPython, JavaScript, Lua).

### 4. Asynchronous, Thread-Affine Video Streaming
Interactive previewing requires streaming live video frames at 30–60 FPS. Graphics APIs (OpenGL, DirectX) strictly require texture allocation and pixel buffer uploads to happen on the main rendering thread. VisionGraph handles high-throughput video ingestion asynchronously over binary channels, buffering payloads via a thread-safe queue (`TextureProcessorQueue` in `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/io/`) that performs GPU uploads strictly during the render pass, eliminating driver race conditions and UI lockups.

### 5. Architectural Engine Decoupling
The visual editor communicates with execution runtimes solely through an abstract message-passing engine layer (`PaperVisionEngine` in `Shared` and `PaperVisionEngineBridge` in `VisionGraph`). The editor does not know or care whether the pipeline is executing inside VisionBench/EOCV-Sim, a local mock test harness, or an embedded coprocessor over a network socket.

---

## Dual Operational Run Modes

VisionGraph operates in two primary execution modes:

```
MODE 1: STANDALONE DEVELOPMENT (./gradlew runEv)
================================================
[User] -> [AppMain.kt]
             |
             v
       [LWJGLPaperVisionApp]
             |
             +--> [NoOpPaperVisionEngineBridge] (Stubs engine responses)
             |
             +--> [VisionGraph Core Editor]
                     |
                     +--> [Export Code (Java, Python, etc.)]


MODE 2: INTEGRATED SIMULATION (VisionBench Plugin)
==================================================
[User] -> [VisionBench Host (Swing/AWT)]
             |
             v
       [VisionGraphPlugin]
             |
             v
       [VisionGraphProcessRunner]
             |
             | (Spawns isolated child JVM with -XstartOnFirstThread)
             v
       [VisionGraphIpcMain (LWJGL/OpenGL)]
             ^
             | (Local WebSocket IPC: JSON RPC + Binary ByteMessages)
             v
       [EOCVSimIpcEngine]
             |
             +--> [Janino Dynamic Compiler] (In-memory Java bytecode)
             |
             +--> [MackJPEG Video Streamer] (SIMD TurboJPEG streaming)
```

### 1. Standalone Mode (Development / Authoring)
* Launched via `./gradlew runEv` or executing `org.deltacv.visiongraph.platform.lwjgl.AppMain`.
* Uses `NoOpPaperVisionEngineBridge`.
* Provides the full interactive ImGui/ImNodes visual editor, node palette, copy/paste, serialization/deserialization, and polyglot code generation.
* Live camera preview is idle because no external video feed is attached, making this mode lightweight and fast for UI testing, node development, and offline algorithm authoring.

### 2. Integrated Mode (Live VisionBench / EOCV-Sim Simulation)
* Installed as an `EOCVSimPlugin` inside the VisionBench simulator.
* Spawns VisionGraph inside an isolated JVM child process (`VisionGraphIpcMain`) communicating with the host via local WebSockets.
* Provides full closed-loop visual preview: as the user edits nodes, Java OpenCV code is compiled on-the-fly via Janino, executed against real camera or video file streams in the simulator, and compressed into TurboJPEG frames streamed directly into the node editor canvas.

---

## End-to-End Runtime Dataflow

The primary lifecycle of a VisionGraph editing session follows a continuous feedback loop:

1. **Graph Construction**: The user places nodes from `NodeList` onto the `NodeEditor` canvas and connects sockets. Sockets validate type compatibility, and `DirectedNodeGraph` prevents algorithmic cycles using depth-first search.
2. **Model Mutation & History**: Every user action (node addition, deletion, link connection, property adjustment) is wrapped in an `Action` and pushed to the forkable undo/redo stack.
3. **Change Notification & Debounce**: Mutations trigger `QueuedChangeEmitter`, which coalesces rapid user inputs (such as dragging a slider) into a single downstream update per tick.
4. **Code Generation & Verification**: `CodeGenManager` traverses the graph backwards from `OutputMatNode`, invoking polyglot generators. If validation fails (e.g., an unplugged required pin), generation aborts gracefully, and an interactive tooltip appears over the offending socket while the canvas pans to center it.
5. **Previz Dispatch**: When running with an engine, `ClientPrevizManager` sends the generated code string to the engine inside a `PrevizSourceCodeMessage`.
6. **Dynamic Compilation & Execution**: The backend engine compiles the code in memory (using Janino in VisionBench), instantiates the pipeline, and routes camera frames through the compiled OpenCV instructions.
7. **Frame Compression & Streaming**: Output frames are compressed to JPEG using SIMD TurboJPEG and packaged into binary byte messages.
8. **Asynchronous Ingestion & Rendering**: The client receives binary packets, decodes header tags, enqueues the JPEG payload to `TextureProcessorQueue`, and uploads them into OpenGL textures on the rendering thread for immediate display within `ImageDisplayWindow` or inside node preview panels.

---

## Subsystem Navigation Guide

To explore the architecture in detail, consult the corresponding subsystem chapters:

* [02-app-entry.md](02-app-entry.md) — Application bootstrap, window lifecycle, frame loops, and shutdown.
* [03-config-and-settings.md](03-config-and-settings.md) — Persistent platform configuration, runtime flags, and localization.
* [04-gui-editor.md](04-gui-editor.md) — Dear ImGui + ImNodes rendering, dual canvas contexts, Compose DSL, and undo/redo.
* [05-engine-previz.md](05-engine-previz.md) — The pure engine abstraction, wire protocols, byte framing, client coordinator, and previz sessions.
* [06-io-and-platform.md](06-io-and-platform.md) — Platform abstractions, GLFW input handling, and thread-safe GPU texture uploads.
* [07-serialization.md](07-serialization.md) — Project file formats (v1 legacy vs v2 typed codecs), migration, and safe rehydration.
* [08-codegen.md](08-codegen.md) — Polyglot multi-pass compiler architecture, AST scopes, language backends, and placeholder resolution.
* [09-node-and-attributes.md](09-node-and-attributes.md) — Node graph model, strongly-typed sockets, cycle detection DFS, and KSP metadata discovery.
* [10-core-utilities.md](10-core-utilities.md) — Thread-local ID containers, event bus, change emitters, and memory pooling.
* [11-visionbench-plugin.md](11-visionbench-plugin.md) — VisionBench / EOCV-Sim plugin implementation, Janino dynamic compilation, TurboJPEG streaming, and crash recovery.
