# Application Entry Points and Runtime Lifecycle

## Purpose and Scope

This document details the startup mechanics, bootstrap sequence, frame execution loop, and shutdown lifecycle of VisionGraph. It explains how platform abstractions are wired into concrete runtime contexts and contrasts the two entry paths: the **Standalone Launcher** and the **IPC Child Process Launcher**.

---

## High-Level Architecture

VisionGraph is architected around an inversion-of-control lifecycle: the core application coordinator (`VisionGraph` in `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/VisionGraph.kt`) does not own the main loop or graphics device context directly. Instead, it is hosted by a platform application wrapper (`LWJGLPaperVisionApp` in `LwjglPlatform/src/main/kotlin/org/deltacv/visiongraph/platform/lwjgl/LWJGLPaperVisionApp.kt`), which implements Dear ImGui's native application harness (`imgui.app.Application`).

```
LIFECYCLE FLOW:
===============
[Launcher: AppMain / VisionGraphIpcMain]
   |
   | Application.launch(app)
   v
[LWJGLPaperVisionApp (imgui.app.Application)]
   |
   +--> 1. Initialize GLFW window, OpenGL context, & Dear ImGui
   |
   +--> 2. visionGraph.init()
   |         |
   |         +--> initPlatform()  (resolve GLFW window, keys, textures, config)
   |         +--> initLanguage()  (bind i18n CSV)
   |         +--> initEngine()    (connect PaperVisionEngineClient to bridge)
   |         +--> initFonts()     (build glyph ranges, load Calcutta & JetBrains Mono)
   |         +--> initUI()        (enable NodeEditor canvas)
   |         +--> RootAction().enable()
   |
   +--> 3. Frame Render Loop (Every Frame)
   |         |
   |         +--> Poll GLFW events & dispatch key callbacks
   |         +--> visionGraph.firstProcess()  [Frame 1 only: maximize, show intro]
   |         +--> visionGraph.process()       [Tick: onUpdate -> engineClient -> ImGui draw -> keyManager -> previz]
   |         +--> Swap OpenGL buffers
   |
   +--> 4. Shutdown / Teardown
             |
             +--> visionGraph.destroy()
             |      |
             |      +--> config.save()
             |      +--> engineClient.disconnect()
             |      +--> textureProcessorQueue.delete()
             |      +--> windows.reversed().forEach { it.delete() }
             |
             +--> Terminate GLFW & destroy OpenGL context
```

---

## Main Components and Entry Points

### 1. Standalone Entry: `AppMain.kt`
* **File**: `LwjglPlatform/Standalone/src/main/kotlin/org/deltacv/visiongraph/platform/lwjgl/AppMain.kt`
* **Execution**: Run via `./gradlew runEv` or executing `org.deltacv.visiongraph.platform.lwjgl.AppMain`.
* **Behavior**: Instantiates `LWJGLPaperVisionApp(bridge = null, showWelcomeWindow = true)` and hands control to `Application.launch(...)`. When `bridge` is null, the platform defaults to `NoOpPaperVisionEngineBridge`.

### 2. IPC Subprocess Entry: `VisionGraphIpcMain.kt`
* **File**: `VisionBenchPlugin/src/main/kotlin/org/deltacv/visiongraph/plugin/VisionGraphIpcMain.kt`
* **Execution**: Spawned as an isolated JVM subprocess by `VisionGraphProcessRunner` inside VisionBench.
* **CLI Arguments**: Uses Picocli to parse:
  * `-i, --ipcport <port>`: The TCP port of the local WebSocket engine server hosted by VisionBench.
  * `-q, --queryproject`: Instructs the client to request the currently active project from the host on startup.
* **Behavior**: Instantiates `EOCVSimIpcEngineBridge` connecting to the specified port, binds it to `LWJGLPaperVisionApp`, registers change listeners for project synchronization, and starts the render loop.

### 3. Application Wrapper: `LWJGLPaperVisionApp`
* **File**: `LwjglPlatform/src/main/kotlin/org/deltacv/visiongraph/platform/lwjgl/LWJGLPaperVisionApp.kt`
* **Role**: Subclasses `imgui.app.Application`. Implements:
  * `configure(config)`: Sets default window dimensions and title flags.
  * `initImGui(config)`: Intercepts ImGui initialization and invokes `visionGraph.init()`.
  * `process()`: Intercepts the per-frame rendering callback to drive `visionGraph.process()`.
  * `postRun()`: Intercepts process termination to invoke `visionGraph.destroy()`.

---

## Detailed Initialization Sequence

When `visionGraph.init()` is invoked, it wraps execution inside `containers.withContext { ... }` ensuring all ID allocations occur within the correct thread-local context, executing the following chronological steps:

```
visionGraph.init()
 │
 ├── 1. initPlatform()
 │     ├── Executes PlatformSetupCallback (resolves GLFW window, OpenGL textures, key bindings)
 │     ├── Loads persistent settings from PlatformConfigManager (config.load())
 │     ├── Registers native global keyboard shortcuts (Ctrl+Spacebar debug dump, Esc modal dismiss)
 │
 ├── 2. initLanguage()
 │     └── Loads i18n CSV dictionary (/lang_pv.csv) for the configured language code
 │
 ├── 3. initEngine()
 │     ├── Instantiates and enables TextureProcessorQueue
 │     ├── Constructs PaperVisionEngineClient(bridge) and ClientPrevizManager
 │     ├── Establishes client engine connection (engineClient.connect())
 │     └── Disables ImGui .ini and log disk output (iniFilename = null)
 │
 ├── 4. initFonts()
 │     ├── Builds GlyphRanges including FontAwesome icon codepoints
 │     ├── Loads Calcutta (Bold, SemiBold, Regular) and JetBrains Mono fonts
 │     └── Registers icon font sheets (FontAwesome6 Free Solid & Brands)
 │
 ├── 5. initUI()
 │     └── Enables the NodeEditor canvas
 │
 ├── 6. RootAction & Lifecycle Hooks
 │     ├── Instantiates RootAction().enable() to baseline the undo/redo stack
 │     ├── Fires onInit.run() event handlers
 │     └── Outputs JVM environment diagnostics to debug logs
```

---

## The Per-Frame Execution Loop

The frame loop runs continuously on the main thread (typically synchronized to display VSync). Each invocation of `LWJGLPaperVisionApp.process()` drives the entire application tick:

### 1. First-Frame Initialization (`firstProcess()`)
On the very first frame after context creation:
* The GLFW window is maximized (`window.maximized = true`).
* An `onUpdate.once` callback evaluates whether to display `IntroModalWindow` (welcome / quick tour screen) based on platform configuration (`setup.showWelcomeWindow`) or serialized project flags.
* Window focus is requested.

### 2. Standard Frame Tick (`process()`)
On every frame:
```kotlin
fun process() = containers.withContext {
    onUpdate.run()
    engineClient.process()

    ImGui.setNextWindowPos(0f, 0f, ImGuiCond.Always)
    val size = window.size
    ImGui.setNextWindowSize(size.x, size.y, ImGuiCond.Always)

    defaultFont.push()

    windows.forEach { it.draw() }
    popups.forEach { it.draw() }
    textureProcessorQueues.forEach { it.draw() }

    ImGui.popFont()

    keyManager.update()
    previzManager.update()
}
```

* **`onUpdate.run()`**: Dispatches scheduled frame events and runs deferred callbacks registered via `PaperEventHandler`.
* **`engineClient.process()`**: Drains incoming binary/JSON packet queues received over the bridge, processes message responses, handles request timeouts (TTL purge), and triggers stream callbacks.
* **Viewport Geometry Setup**: Fixes the root window boundary to fill the exact GLFW viewport.
* **UI Draw Pass**:
  * Loops through all registered `Window` instances (including `NodeEditor`, `NodeList`, and tool windows) calling `draw()`.
  * Loops through active popups and modal dialogs.
  * Calls `textureProcessorQueues.forEach { it.draw() }`, which consumes queued JPEG frame bytes and executes thread-affine OpenGL texture creation.
* **Input & Stream Updates**:
  * `keyManager.update()`: Cleans up transient single-frame key states (e.g., transitions `PRESS` -> `PRESSING`).
  * `previzManager.update()`: Checks stream health, evaluates frame rate statistics, and detects pipeline timeouts.

---

## Teardown and Graceful Shutdown

When the user requests window closure (via OS close button or keyboard shortcut), GLFW signals `windowShouldClose`. `LWJGLPaperVisionApp.postRun()` delegates to `visionGraph.destroy()`:

```kotlin
fun destroy() {
    logger.info("Shutting down VisionGraph...")

    config.save()                    // 1. Flush preferences to disk
    engineClient.disconnect()        // 2. Terminate engine bridge & websocket connections
    textureProcessorQueue.delete()   // 3. Free queued image buffers & GPU textures

    windows.reversed().forEach { it.delete() } // 4. Dispose windows in reverse order
    popups.reversed().forEach { it.delete() }

    nodeEditor.delete()              // 5. Release ImNodes editor context
}
```

---

## Concurrency and Threading Architecture

VisionGraph operates across three distinct execution tiers:

```
THREADING ARCHITECTURE:
=======================
1. MAIN RENDER THREAD (Thread-Affinity Enforced)
   - GLFW event loop & VSync
   - Dear ImGui / ImNodes rendering
   - OpenGL texture creation (glGenTextures, glTexImage2D)
   - Core application tick: VisionGraph.process()

2. BACKGROUND IO & NETWORKING THREADS
   - WebSocket IPC client (Java-WebSocket)
   - Binary header demuxing (ByteMessages)
   - Non-blocking packet enqueueing into bounded bytesChannel

3. COMPUTATION & COROUTINE WORKER POOL
   - Parallel AST traversal during CodeGenManager.build()
   - SIMD TurboJPEG decompression (MackJPEG)
   - Pushes prepared texture payloads to TextureProcessorQueue
```

1. **The Main Render Thread**:
   * Has exclusive ownership of the OpenGL context and the GLFW window handle.
   * **Rule**: Absolutely no OpenGL calls (`glGenTextures`, `glTexImage2D`, `glDeleteTextures`) may occur outside this thread.
   * `TextureProcessorQueue` acts as the synchronization bridge: background threads push decompressed pixel data into the queue, and the main thread drains the queue into OpenGL textures during `draw()`.
2. **Background IO & Network Threads**:
   * Managed by `Java-WebSocket` or standard thread pools.
   * Handles non-blocking socket reads and writes. Raw byte buffers are placed into bounded thread-safe channels (`bytesChannel`) to prevent blocking the network stack.
3. **Computation & Image Decompression Pool**:
   * Utilizes Kotlin Coroutines (`Dispatchers.Default`) with concurrency throttles for parallel TurboJPEG frame decompression.

---

## Design Decisions and Rationale

### Why Decouple `VisionGraph` from `LWJGLPaperVisionApp`?
By keeping `VisionGraph` purely dependent on abstract platform interfaces (`PlatformWindow`, `PlatformTextureFactory`), the entire editor engine is independent of LWJGL. It can theoretically be compiled against other rendering backends (e.g., a native Metal/Vulkan backend or an offscreen headless buffer for automated integration tests) without changing a single line of node editing or code generation logic.

### Why Isolate the Process in `VisionGraphIpcMain`?
When running inside VisionBench / EOCV-Sim, the host is a standard Java Swing/AWT application, whereas VisionGraph uses GLFW and OpenGL. On platforms like macOS, GLFW requires the graphics event loop to execute on thread `0` with `-XstartOnFirstThread`. Hosting both Swing and GLFW in the same JVM process causes severe Cocoa thread collisions and driver deadlocks. Running VisionGraph as a separate JVM child process completely isolates memory spaces and graphics contexts, ensuring a crash in an experimental OpenCV shader cannot take down the simulator host.
