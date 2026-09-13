# VisionBench Plugin, Process Isolation, and Live Simulation

## Purpose and Scope

While VisionGraph can operate as a standalone algorithm design tool, its full interactive potential is realized when integrated into **VisionBench** (the official simulator runtime, formerly known as *EOCV-Sim* / *EasyOpenCV Simulator*).

Integrating a modern, high-framerate OpenGL node editor into a mature Java Swing/AWT desktop application introduces complex challenges: native graphical thread affinity conflicts, OpenGL context collisions, memory safety, dynamic code compilation, and fault isolation.

This document details the architecture of the **VisionBench Plugin** (`VisionBenchPlugin` module). It explains:
* How VisionGraph embeds into the host simulator GUI and hooks into pipeline execution.
* The multi-process isolation model separating the Swing host, the GLFW child editor, and an independent recovery daemon.
* The WebSocket IPC engine protocol bridging control messages and high-throughput video streams.
* Dynamic in-memory compilation of generated pipelines via Janino (`SinglePipelineCompiler`).
* Active session coordination (`EOCVSimPrevizSession`), pause prevention, and reflection-based tuner synchronization.
* The SIMD TurboJPEG video compression pipeline (`EOCVSimEngineImageStreamer`) and frame-differencing optimizations.
* The tri-process crash recovery architecture that protects unsaved algorithm graphs against unexpected termination.
* Embedded child process GUI controls (`InputSourceWindow`, `CloseConfirmWindow`).

---

## High-Level Architecture

The VisionBench integration divides responsibilities across three independent operating system processes communicating over local WebSocket connections:

```
+───────────────────────────────────────────────────────────────────────────+
| PROCESS 1: VisionBench Host Runtime (Java Swing / AWT)                    |
|                                                                           |
|  GUI & Workspace:                                                         |
|  - VisionGraphPlugin (EOCVSimPlugin lifecycle & hooks)                    |
|  - PaperVisionTabPanel (Sidebar tree, project CRUD, actions)              |
|  - VisionGraphProjectManager (Project workspace & file system)            |
|                                                                           |
|  Process Supervisor & Engine Server:                                      |
|  - VisionGraphProcessRunner (Spawns and monitors child JVM)               |
|  - EOCVSimIpcEngine (WebSocket server on 127.0.0.1:ephemeral)             |
|                                                                           |
|  Pipeline Execution & Streaming:                                          |
|  - SinglePipelineCompiler (Janino in-memory bytecode compiler)            |
|  - EOCVSimPrevizSession (Anonymous pipeline hot-swapper & pause guard)    |
|  - EOCVSimEngineImageStreamer (MackJPEG SIMD TurboJPEG compressor)        |
+──────────────────────────┬───────────────────────┬────────────────────────+
                           │                       │
           (Spawns JVM &   │                       │ (Spawns JVM &
            IPC WebSocket) │                       │  Recovery WebSocket)
                           v                       v
+────────────────────────────────────────+   +──────────────────────────────+
| PROCESS 2: VisionGraph Child Editor    |   | PROCESS 3: Recovery Daemon   |
| (LWJGL / GLFW / OpenGL)                |   | (Background Supervisor)      |
|                                        |   |                              |
|  - VisionGraphIpcMain (CLI parser)     |   |  - RecoveryDaemonClientMain  |
|  - EOCVSimIpcEngineBridge (WS Client)  |   |  - Receives serialized       |
|  - LWJGLPaperVisionApp & Node Canvas   |   |    graph snapshots           |
|  - InputSourceWindow (ImGui camera UI) |   |  - Persists .recoveryproj    |
|  - CloseConfirmWindow (Safety guard)   |   |    directly to disk          |
+────────────────────────────────────────+   +──────────────────────────────+
```

---

## Core Components and File References

| Component | File Path | Primary Responsibility |
| :--- | :--- | :--- |
| **`VisionGraphPlugin`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/VisionGraphPlugin.kt` | Plugin entry point; registers sidebar tabs, menu items, handles simulator events, and routes IPC messages. |
| **`VisionGraphProcessRunner`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/VisionGraphProcessRunner.kt` | Spawns and manages the child JVM process lifecycle, handling OS-specific flags (macOS Cocoa). |
| **`VisionGraphIpcMain`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/VisionGraphIpcMain.kt` | Main entry class of the child process; parses Picocli flags, initializes GLFW, and synchronizes projects. |
| **`EOCVSimIpcEngine`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/engine/EOCVSimIpcEngine.kt` | Host-side WebSocket server; implements `PaperVisionEngine`, broadcasts responses and binary video frames. |
| **`EOCVSimIpcEngineBridge`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/engine/EOCVSimIpcEngineBridge.kt` | Child-side WebSocket client; implements `PaperVisionEngineBridge`, manages reconnects and rate limiting. |
| **`SinglePipelineCompiler`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/previz/SinglePipelineCompiler.kt` | Compiles generated Java source code strings into JVM bytecode in memory using Janino. |
| **`EOCVSimPrevizSession`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/previz/EOCVSimPrevizSession.kt` | Hot-swaps running pipelines anonymously, overrides pauses, and manages tuner reflection. |
| **`EOCVSimEngineImageStreamer`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/previz/EOCVSimEngineImageStreamer.kt` | SIMD TurboJPEG compressor with frame-differencing, buffer pooling, and binary packet packaging. |
| **`VisionGraphProjectManager`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/project/VisionGraphProjectManager.kt` | Manages project files (`.pvproj`), workspace trees, and orchestrates crash recovery snapshots. |
| **`RecoveryDaemonProcessManager`**| `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/project/recovery/RecoveryDaemonProcessManager.kt` | Host-side manager that spawns the external recovery daemon and streams graph snapshots to it. |
| **`RecoveryDaemonClientMain`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/project/recovery/RecoveryDaemonClientMain.kt` | Independent background daemon process that persists recovery cache snapshots directly to disk. |
| **`InputSourceWindow`** | `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/gui/imgui/InputSourceWindow.kt` | Dear ImGui modal in the child process for selecting and creating simulator cameras/video sources. |

---

## Multi-Process Isolation Rationale

A fundamental architectural design decision in VisionGraph is running the visual node editor as an **isolated external JVM process** rather than embedding an OpenGL/GLFW canvas inside the Swing host window.

### Why Process Isolation is Essential

1. **Native Thread Affinity & Event Loop Collisions**:
   * Java Swing relies on the AWT Event Dispatch Thread (EDT).
   * LWJGL/GLFW requires event polling (`glfwPollEvents()`) and window management to occur on the **very first thread of the process** (`main` thread), particularly on macOS Cocoa.
   * Attempting to run GLFW and Swing within the same JVM process leads to thread deadlocks, signal crashes, or broken window focus behaviors.
2. **macOS Platform Constraints**:
   * macOS enforces strict rules regarding AWT and Cocoa event pumps. To launch a GLFW window alongside an AWT-based host, the child process must explicitly pass `-XstartOnFirstThread` and `-Djava.awt.headless=true`. These flags can only be applied at JVM startup and cannot be toggled dynamically within a running JVM.
3. **Crash & Fault Domain Isolation**:
   * Dynamic node graphs frequently execute user-defined computer vision routines, native OpenCV matrix operations, and dynamic byte manipulations. If a native graphics driver segfaults, out-of-memory errors occur, or Janino encounters an unrecoverable JVM error, only the child process terminates. The host simulator remains completely stable, and the recovery daemon guarantees no user data is lost.

### Process Launch Sequence (`VisionGraphProcessRunner`)

The host launches the child process via `VisionGraphProcessRunner.execPaperVision(classpath)`:

```kotlin
// VisionGraphProcessRunner.kt
val programParams = listOf("-q", "-i=${paperVisionEngine.server.port}")

val exitCode = if(SysUtil.OS == SysUtil.OperatingSystem.MACOS) {
    val jvmArgs = listOf("-XstartOnFirstThread", "-Djava.awt.headless=true")
    JavaProcess.execClasspath(
        VisionGraphIpcMain::class.java,
        SLF4JIOReceiver(logger),
        classpath,
        jvmArgs,
        programParams
    )
} else {
    JavaProcess.execClasspath(
        VisionGraphIpcMain::class.java,
        SLF4JIOReceiver(logger),
        classpath,
        listOf(),
        programParams
    )
}
```

* `-q` (`--queryproject`): Signals the child to request the active project graph from the host immediately upon startup.
* `-i` (`--ipcport`): Passes the ephemeral WebSocket port where `EOCVSimIpcEngine` is listening.
* `onPaperVisionExitError`: If the child process exits with a non-zero exit code, the host catches the event and immediately checks for crash recovery snapshots.

---

## Host Integration and Lifecycle (`VisionGraphPlugin`)

The plugin inherits from `EOCVSimPlugin` (`VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/VisionGraphPlugin.kt`) and registers into the simulator's extension hooks:

### 1. Workspace and Classpath Resolution
The plugin determines its runtime classpath via `fullClasspath`:
* **Fat JAR Mode**: If loaded from an external `.jar` file by `FilePluginLoader`, it resolves the single fat JAR path.
* **Maven / IDE Mode**: If running in a development workspace, it concatenates all transitive runtime dependency paths using `File.pathSeparator`.

### 2. GUI Integration Hooks
During `onLoad()`:
* **Sidebar Tab**: Instantiates `PaperVisionTabPanel` and registers it with `eocvSimApi.visualizerApi.sidebarApi.addTab()`.
* **Menu Bar**: Injects "VisionGraph -> New Project" and "VisionGraph -> Import..." into the simulator's "File -> New" menu bar.
* **Viewport Deactivation**: When a live previz session begins, `eocvSimApi.visualizerApi.viewportApi.deactivate()` is called. Because frames are being streamed directly into the node editor canvas, deactivating the host Swing viewport prevents redundant OpenGL rendering and saves GPU cycles.
* **Tab Switch Protection**: When the user switches away from the VisionGraph sidebar tab in the simulator, the plugin switches the active pipeline to `VisionGraphDefaultPipeline` to suspend algorithm processing.

---

## The IPC Engine Protocol (`EOCVSimIpcEngine` & `EOCVSimIpcEngineBridge`)

Communication between host and child operates over an asynchronous WebSocket link powered by `Java-WebSocket` and `kotlinx.serialization`.

```
CHILD PROCESS (Client)                             HOST PROCESS (Server)
══════════════════════                             ═════════════════════
EOCVSimIpcEngineBridge                             EOCVSimIpcEngine
       │                                                  │
       │─── JSON: GetCurrentProjectMessage ──────────────>│ (ProjectManager loads)
       │<── JSON: JsonElementResponse(graphJson) ─────────│
       │                                                  │
       │─── JSON: PrevizStartMessage(sourceCode) ────────>│ (Janino compiles)
       │<── JSON: OkResponse ─────────────────────────────│
       │                                                  │
       │─── JSON: TunerChangeValueMessage(label, val) ───>│ (Updates @Label field)
       │<── JSON: OkResponse ─────────────────────────────│
       │                                                  │
       │<── BINARY: ByteMessages [Tag, ID, JPEG Data] ────│ (MackJPEG streams frame)
       │                                                  │
       │─── JSON: EditorChangeMessage(graphJson) ────────>│ (Forwarded to Daemon)
       │                                                  │
```

### Server Architecture: `EOCVSimIpcEngine`
* Binds to `127.0.0.1` on port `0` (an OS-assigned ephemeral port).
* **Localhost Security Guard**: `WsServer.onOpen()` verifies that incoming connection addresses match `127.0.0.1`, `localhost`, or `0.0.0.0`. Remote network connections are immediately rejected with status `1013`.
* Sends control responses via `sendResponse(response)` (`server.broadcast(json)`).
* Sends binary camera frames via `sendBytes(bytes)` (`server.broadcast(byteArray)`).

### Client Architecture: `EOCVSimIpcEngineBridge`
* Connects to `ws://127.0.0.1:<port>`.
* **Resilient Connection**: Uses `wsClient.connectBlocking(5, TimeUnit.SECONDS)` to ensure connectivity before transmitting.
* **High-Frequency Spam Monitor**: Tracks message frequency across a 20-message moving window. If messages are dispatched faster than 100ms on average (excluding `EditorChangeMessage` and `TunerChangeValueMessage`), it logs an alert with the calling class and line number to flag runaway UI loops.
* Implements `PaperVisionEngineBridge` to route responses back to awaiting promises in `PaperVisionEngineClient`.

---

## Dynamic Compilation via Janino (`SinglePipelineCompiler`)

When an algorithm changes, VisionGraph does not invoke `javac` or spawn an external build tool. Instead, it compiles generated Java source code directly into JVM bytecode in memory using **Janino** (`VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/previz/SinglePipelineCompiler.kt`):

```kotlin
object SinglePipelineCompiler {
    fun compilePipeline(pipelineSource: String): Class<out OpenCvPipeline> {
        val compiler = SimpleCompiler()

        // Compiles source code string directly in memory
        compiler.cook(pipelineSource)

        if (compiler.classFiles.isEmpty()) {
            throw IllegalStateException("No class files generated.")
        }

        // Search through compiled classes for the OpenCvPipeline subclass
        for (classFile in compiler.classFiles) {
            val clazz = compiler.classLoader.loadClass(classFile.thisClassName)

            if (ReflectUtil.hasSuperclass(clazz, OpenCvPipeline::class.java)) {
                return clazz as Class<out OpenCvPipeline>
            }
        }

        throw IllegalStateException("No OpenCvPipeline subclass found in source.")
    }
}
```

### Why Janino?
* **Sub-50ms Compilation**: Janino compiles Java source code directly to bytecode without disk I/O or inter-process communication.
* **No External JDK Requirement**: Janino runs entirely within the host JVM runtime without requiring `tools.jar` or a full Java Development Kit on the end-user's computer.
* **Clean ClassLoader Isolation**: Each compilation creates a fresh `JavaSourceClassLoader`, ensuring previously compiled classes and static buffers can be garbage-collected.

---

## Live Previsualization Session (`EOCVSimPrevizSession`)

The execution of a running pipeline inside the simulator is governed by `EOCVSimPrevizSession` (`VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/previz/EOCVSimPrevizSession.kt`):

```
+─────────────────────────────────────────────────────────────+
| EOCVSimPrevizSession Lifecycle                              |
+─────────────────────────────────────────────────────────────+
  1. refreshPreviz(sourceCode)
        │
        ├── SinglePipelineCompiler.compilePipeline(sourceCode)
        │
        ├── addPipelineInstantiator(StreamableNoReflectPipelineInstantiator)
        │
        ├── eocvSimApi.pipelineManagerApi.changePipelineAnonymous(newClass, force=true)
        │
        └── JvmVirtualReflection.contextOf(pipeline) extracts @Label fields
  2. Runtime Event Hooks
        │
        ├── onPauseHook: Automatically resumes if simulator attempts to pause
        │
        ├── TunerChangeValueMessage: Updates field live via TunableFieldApi
        │
        └── PrevizPingMessage: Polls average FPS and pipeline execution ms
```

### Key Coordination Mechanics

1. **Anonymous Pipeline Hot-Swapping**:
   Pipelines are switched via `changePipelineAnonymous(newClass, force = true)`. This replaces the active pipeline instance immediately on the simulator's processing thread without resetting the underlying camera hardware or video stream.
2. **Zero-Reflection Instantiation (`StreamableNoReflectPipelineInstantiator`)**:
   Instead of using standard Java reflection to instantiate the pipeline, `StreamableNoReflectPipelineInstantiator` passes the active `EOCVSimEngineImageStreamer` directly to the compiled pipeline instance, wiring frame streaming immediately upon construction.
3. **Live Tuner Synchronization (`VirtualReflectContext`)**:
   When the pipeline is instantiated, `JvmVirtualReflection.contextOf(pipeline)` scans for fields annotated with `@Label`. When the user adjusts a slider in VisionGraph:
   * Child sends `TunerChangeValueMessage(label, value)`.
   * Host resolves the `VirtualField` from the cache.
   * `TunableFieldApi.setFieldValue()` sets the value directly on the running pipeline instance.
   * The pipeline reflects the new threshold or parameter on the very next frame without restarting or recompiling!
4. **Pause Interception**:
   If user interaction or simulator events trigger an `onPauseHook`, the session intercepts the event and calls `resume()` immediately, guaranteeing continuous live feedback.

---

## High-Throughput SIMD Image Streaming (`EOCVSimEngineImageStreamer`)

Live frame previsualization requires encoding and transmitting multiple 1080p/720p OpenCV image buffers per second across IPC with minimal latency and zero memory leaks.

This is executed by `EOCVSimEngineImageStreamer` (`VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/previz/EOCVSimEngineImageStreamer.kt`):

### 1. Zero-Allocation Object & Buffer Pooling
Steady-state frame streaming must avoid triggering JVM Garbage Collection pauses:
* **`MatRecycler`**: Pools reusable OpenCV `Mat` buffers across worker coroutines (`JPEG_WORKER_THREADS * 3`).
* **`MemoryPool`**: Pools reusable direct byte buffers for native SIMD operations.

### 2. Native TurboJPEG Compression (`MackJPEG`)
* Uses native SIMD-accelerated libjpeg-turbo through the `MackJPEG` library.
* Compresses raw OpenCV `CV_8UC3` RGB buffers into JPEG format using hardware acceleration.
* **Graceful OpenCV Fallback**: If `MackJPEG` encounters a native exception (`JPEGException`), the streamer catches the error, logs a warning once, and falls back to OpenCV's built-in encoder:
  ```kotlin
  Imgproc.cvtColor(targetImage, targetImage, Imgproc.COLOR_RGB2BGR)
  Imgcodecs.imencode(".jpg", targetImage, bytes)
  ```

### 3. Dynamic Frame Differencing Optimization (`hasChanged`)
Transmitting identical frames over WebSocket wastes CPU and network bandwidth. Before compressing a frame, `hasChanged(id, image)` checks for image divergence:
* Computes absolute pixel differences using `Core.absdiff(latestMat, image, maskMat)`.
* If pixel divergence falls below the motion threshold, compression and transmission are dropped entirely.
* **Forced Keep-Alive**: If a stream remains unchanged for 5.0 seconds (`forceSend`), a refresh frame is automatically sent to keep the client texture active.

### 4. Binary Wire Packaging
Frames are prefixed with binary metadata via `ByteMessages`:
```kotlin
val headerSize = ByteMessages.headerSize(tag)
// ... compress JPEG into jpegBuffer after header offset ...
ByteMessages.writeHeader(tag, id, jpegSize, byteMessageBuffer)
ipcEngine.sendBytes(jpegBuffer)
```
The packet is transmitted over the WebSocket as raw binary (`ByteArray`), bypassing JSON serialization completely.

---

## Tri-Process Fault Tolerance & Crash Recovery

Visual algorithm development is vulnerable to crashes caused by invalid OpenCV matrix math, native memory exhaustion, or GPU driver resets. VisionGraph deploys a **Tri-Process Crash Recovery Architecture** to ensure work is never lost.

```
+───────────────────────+        +───────────────────────+
| VisionGraph Child     |        | VisionBench Host      |
| Node Canvas           |        | ProjectManager        |
+───────────┬───────────+        +───────────┬───────────+
            │                                │
            │ EditorChangeMessage            │ RecoveryData JSON
            │ (Continuous Graph Snapshots)   │ (Project Data + Path)
            ▼                                ▼
+────────────────────────────────────────────────────────+
| RecoveryDaemonProcessManager (Host WebSocket Server)   |
+───────────────────────────┬────────────────────────────+
                            │
                            │ Local WebSocket
                            ▼
+────────────────────────────────────────────────────────+
| PROCESS 3: RecoveryDaemonClientMain                    |
| - Independent background daemon process                |
| - Writes to ~/.papervision/papervision_recovery/       |
| - Persists <name>.recoverypaperproj directly to disk   |
+────────────────────────────────────────────────────────+
```

### 1. Continuous Snapshot Streaming
* Whenever the user modifies the node canvas (moving a node, editing an attribute, adding a link), `VisionGraphIpcMain` catches `nodeEditor.onEditorChange`.
* It serializes the current graph to JSON via `JsonCodec().encodeToJsonElement(project)`.
* It dispatches an `EditorChangeMessage` over IPC to the host.
* The host forwards the snapshot to `RecoveryDaemonProcessManager`, which broadcasts it over a dedicated internal WebSocket to `RecoveryDaemonClientMain`.

### 2. Process Survival Guarantees
* **If Child Process Crashes** (GLFW/OpenGL crash, native driver crash): The host JVM and the recovery daemon remain unaffected.
* **If Host Process Crashes** (OutOfMemory, Janino error, native OpenCV crash): The recovery daemon runs in a separate, isolated JVM process (`RecoveryDaemonClientMain`). It flushes the latest snapshot to disk even as the parent terminates.

### 3. Phased Recovery UI
Upon launching VisionBench or detecting a child exit error:
1. `VisionGraphProjectManager` crawls `~/.papervision/papervision_recovery/`.
2. It parses any `.recoverypaperproj` files and compares their timestamps against the project files on disk:
   ```kotlin
   if (recoveredProject.date > project.timestamp) {
       add(recoveredProject)
   }
   ```
3. If an unsaved snapshot is newer than the saved project, `VisionGraphDialogFactory.displayProjectRecoveryDialog()` prompts the user with a visual recovery list, allowing one-click restoration of their unsaved pipeline.

---

## Child Process GUI Controls

To prevent users from having to switch between windows during development, the child process embeds native controls that interact with the host simulator:

### 1. Input Source Window (`InputSourceWindow`)
An ImGui window docked in the child editor canvas:
* Lists all available simulator input sources (webcams, test images, video recordings, HTTP streams) decorated with FontAwesome icons.
* Subscribes to `InputSourceListChangeListenerMessage`. If the user adds a new webcam in the simulator, the ImGui list updates immediately.
* Clicking an input source sends `SetInputSourceMessage(name)`, switching the camera feed in real time.

### 2. Create Input Source Modal (`CreateInputSourceWindow`)
* Clicking "Create new input source" displays an ImGui modal with Camera, Image, and Video options.
* Selecting an option sends `OpenCreateInputSourceMessage(type)`. The host simulator brings up the native OS file picker or webcam selection dialog on the main desktop frame.

### 3. Close Confirmation Safety Guard (`CloseConfirmWindow`)
* When the user clicks the window close button (`X`), `paperVisionUserCloseListener` intercepts the event and opens `CloseConfirmWindow`.
* Users can choose **Save and Exit** (dispatches `SaveCurrentProjectMessage`), **Discard and Exit** (dispatches `DiscardCurrentRecoveryMessage`), or **Cancel**.
* As a fail-safe against stuck windows, requesting close 3 times forces an immediate exit.
