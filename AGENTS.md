# VisionGraph - AI Agent Guidelines

## Project Overview

**VisionGraph** is a visual node-based algorithm design environment and multi-pass polyglot compiler for computer vision pipelines. It translates interactive node graphs into production-grade source code across multiple languages (Java, Kotlin, Python) and robotics frameworks (OpenFTC/EasyOpenCV, WPILib, Limelight, standard OpenCV).

The application operates in two distinct modes:
1. **Standalone Desktop Mode**: A self-contained GLFW/OpenGL desktop application (`LwjglPlatform` / `AppMain`).
2. **VisionBench / EOCV-Sim Plugin Mode**: An isolated child JVM process (`VisionGraphIpcMain`) communicating with the host simulator via a local WebSocket IPC bridge, supporting Janino in-memory dynamic compilation, live tuner reflection, and SIMD TurboJPEG video streaming.

---

## Module Structure

* **`VisionGraph`**: Core visual node editor (Dear ImGui / ImNodes), multi-pass polyglot compiler (`codegen`), AST model (`Value`, `Type`, `Scope`), intermediate values (`GenValue`), v2 typed envelope serialization, and graph model (`Node`, `Attribute`, `AttributeDecomposer`).
* **`Shared`**: Pure engine abstraction layer (`PaperVisionEngine`, `PaperVisionEngineBridge`), binary wire framing (`ByteMessages`), and client coordinator (`PaperVisionEngineClient`).
* **`LwjglPlatform`**: Desktop platform implementation using LWJGL3 / GLFW / OpenGL, thread-safe texture upload pipeline (`TextureProcessorQueue`), STB image decoders, and keyboard manager.
* **`VisionBenchPlugin`**: Integration plugin for the VisionBench / EOCV-Sim simulator; manages child process lifecycle, Janino in-memory compilation (`SinglePipelineCompiler`), SIMD TurboJPEG image streaming (`MackJPEG`), and tri-process crash recovery (`RecoveryDaemonClientMain`).
* **`AnnotationProcessor`**: Compile-time Kotlin Symbol Processing (KSP) verifying node constructor contracts and generating ahead-of-time (AOT) registration catalogs for `@PaperNode`.

---

## Architecture Documentation

For complete, architectural deep dives into every subsystem, consult the comprehensive documentation suite in **[`documentation/`](documentation/)**:

| Document | Subsystem / Focus |
| :--- | :--- |
| **[`README.md`](documentation/README.md)** | Master documentation index, reading guides, and Gradle cheat sheet. |
| **[`01-overview.md`](documentation/01-overview.md)** | System architecture, multi-module layout, and end-to-end dataflow. |
| **[`02-app-entry.md`](documentation/02-app-entry.md)** | Desktop bootstrapping (`AppMain` vs `VisionGraphIpcMain`), GLFW render loop, and thread tiers. |
| **[`03-config-and-settings.md`](documentation/03-config-and-settings.md)** | Config persistence (`~/.papervision/config.json`) and thread-local i18n (`mai18n`). |
| **[`04-gui-editor.md`](documentation/04-gui-editor.md)** | Dear ImGui / ImNodes canvas, context isolation, Compose DSL, and undo/redo command stack. |
| **[`05-engine-previz.md`](documentation/05-engine-previz.md)** | Engine abstraction, binary framing (`ByteMessages`), client coordinator, and stream demuxing. |
| **[`06-io-and-platform.md`](documentation/06-io-and-platform.md)** | Platform interfaces, GPU thread affinity, `TextureProcessorQueue`, and memory pooling. |
| **[`07-serialization.md`](documentation/07-serialization.md)** | Typed envelope v2 format, KSP registry, v1 migration shims, and clipboard codecs. |
| **[`08-codegen.md`](documentation/08-codegen.md)** | Compiler pipeline, AST primitives (`Value`, `Type`), `polyglot` DSL, `GenValue`, `JvmOpenCv`, `CPythonOpenCv`, `GenPreviz`, and `JvmTargets`. |
| **[`09-node-and-attributes.md`](documentation/09-node-and-attributes.md)** | DAG model, typed sockets, `AttributeType` companion contract, `AttributeDecomposer`, DFS cycle check, and dead-end propagation. |
| **[`10-core-utilities.md`](documentation/10-core-utilities.md)** | Thread-local `IdContext` stack, event dispatchers (`PaperEventHandler`), and `MemoryPool`. |
| **[`11-visionbench-plugin.md`](documentation/11-visionbench-plugin.md)** | VisionBench plugin integration, tri-process isolation, Janino dynamic compilation, TurboJPEG streaming, and crash recovery. |

---

## Common Gradle Commands

```bash
# Run standalone desktop node editor
./gradlew run

# Build all modules
./gradlew build

# Run unit tests across all modules
./gradlew test

# Build fat JAR plugin for VisionBench / EOCV-Sim
./gradlew :VisionBenchPlugin:shadowJar
```

---

## Development Guidelines for AI Agents

1. **Standard Markdown**: Maintain all documentation in standard GitHub-compatible Markdown. Never use `file:///` URLs (use repository-relative paths instead), do not use Mermaid code blocks (use clean ASCII diagrams), and do not use non-standard GitHub alert tags like `[!NOTE]` (use standard blockquotes `> **Note:** ...`).
2. **Engine Decoupling**: Preserve the pure engine abstraction in `Shared` (`PaperVisionEngine`, `PaperVisionEngineBridge`). Do not introduce EOCV-Sim / VisionBench dependencies into `Shared` or `VisionGraph`; simulator-specific code belongs strictly inside `VisionBenchPlugin`.
3. **Thread Safety**: OpenGL and GLFW contexts belong strictly to the main render thread. Off-thread tasks must upload textures through `TextureProcessorQueue` or dispatch via `onUpdate.once { ... }`.
4. **Code Generation DSL**: When modifying or adding graph nodes, declare generators using the `polyglot { generatorFor(...) { current { ... } } }` DSL and store intermediate outputs in a `CodeGenSession`.

