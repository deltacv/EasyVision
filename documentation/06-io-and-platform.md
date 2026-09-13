# Platform Abstraction, Input, and Thread-Safe GPU IO

## Purpose and Scope

VisionGraph is engineered to be portable and resilient against GPU threading deadlocks. This document details the **Platform Abstraction Layer** (which decouples windowing, textures, and key input from any concrete operating system or graphics API) and the **Thread-Safe GPU IO Pipeline** implemented by `TextureProcessorQueue` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/io/TextureProcessorQueue.kt`).

---

## High-Level Architecture

The platform layer separates hardware/OS dependencies in `LwjglPlatform` from the business logic in `VisionGraph`:

```
+-------------------------------------------------------------+
| Platform Abstraction Layer (VisionGraph Module)             |
| - PlatformSetup (builder and service container)             |
| - PlatformWindow (window sizing, title, icons)              |
| - PlatformTextureFactory & PlatformTexture (GPU textures)   |
| - PlatformKeys (key codes and modifiers)                    |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
| LWJGL Concrete Implementation (LwjglPlatform Module)        |
| - GlfwWindow (GLFW native handle wrapper)                   |
| - OpenGLTextureFactory & OpenGLTexture (OpenGL 2D textures) |
| - GlfwKeys (GLFW scancode mappings)                         |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
| Thread-Safe IO Synchronization                              |
| - TextureProcessorQueue (async decompress + render uploads) |
| - KeyManager (input debounce, typematic repeat, shortcuts)  |
+-------------------------------------------------------------+
```

---

## The GPU Thread Affinity Constraint

In modern graphics architectures (specifically OpenGL and GLFW), OpenGL context handles are bound to a single thread at a time (the thread that called `glfwMakeContextCurrent`). 

Executing OpenGL driver calls—such as `glGenTextures()`, `glTexImage2D()`, `glTexSubImage2D()`, or `glDeleteTextures()`—from arbitrary background threads causes immediate crashes, memory corruption, or driver panics.

However, video frames produced by live simulation engines arrive unpredictably on background networking or IPC daemon threads. VisionGraph solves this fundamental tension through `TextureProcessorQueue`.

---

## The Thread-Safe Video Pipeline: `TextureProcessorQueue`

* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/io/TextureProcessorQueue.kt`

```
FRAME PROCESSING PIPELINE:
==========================
[Background Network Thread]
   |
   | offerJpegAsync(id, width, height, bytes)
   v
[Coroutine Worker (Dispatchers.Default)]
   |
   +--> Borrow pre-allocated ByteArray from MemoryPool
   +--> MackJPEG.decompress(bytes, pooledBuffer)  (SIMD TurboJPEG)
   +--> Enqueue FutureTexture(id, pooledBuffer)
   |
   v
[queuedTextures Channel (capacity = 15, BufferOverflow.DROP_OLDEST)]
   |
   | (Drained on Render Thread)
   v
[TextureProcessorQueue.draw() - Main Render Thread]
   |
   +--> If texture exists & size matches:
   |      glTexSubImage2D(pooledBuffer)  (fast in-place upload)
   +--> If new texture or resized:
   |      glGenTextures() + glTexImage2D(pooledBuffer)
   |
   +--> Return pooledBuffer back to MemoryPool
```

### Architectural Stages of Frame Processing

#### 1. Ingestion (`offerJpegAsync`)
When a frame arrives from `ClientPrevizStream`, it is submitted to `offerJpegAsync`:
```kotlin
fun offerJpegAsync(
    id: Int,
    width: Int,
    height: Int,
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size
)
```
Rather than decompressing synchronously on the network thread, work is offloaded to `workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())`.

#### 2. High-Performance Memory Pooling (`MemoryPool`)
Allocating multi-megabyte `ByteArray` instances 60 times per second triggers severe garbage collection pauses. VisionGraph utilizes `MemoryPool` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/util/MemoryPool.kt`) with tiered bucket capacities (`tierCapacity = 8`). Worker tasks borrow an appropriately sized byte array from the pool, fill it with raw RGBA pixel data, and return it once the GPU upload finishes.

#### 3. Parallel SIMD Decompression (`MackJPEG`)
Decompression is accelerated via [MackJPEG](https://github.com/deltacv/MackJPEG), which binds to native [libjpeg-turbo](https://libjpeg-turbo.org/) SIMD assembly routines (ARM NEON, x86 AVX2/SSE2), converting compressed JPEG streams to uncompressed RGBA pixel buffers in sub-millisecond times.

#### 4. Bounded Buffer with Backpressure (`DROP_OLDEST`)
Decompressed frames are pushed to a channel:
```kotlin
private val queuedTextures = Channel<FutureTexture>(
    capacity = 15,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
```
If the rendering loop slows down, the queue automatically discards stale frames. The UI always displays the most recent video frame without accumulation lag.

#### 5. Render-Thread Consumption (`draw()`)
During the main render pass, `TextureProcessorQueue.draw()` runs directly on the OpenGL render thread:
* It drains all `FutureTexture` objects from `queuedTextures`.
* If a texture with that `id` already exists and its dimensions match, it calls `texture.set(buffer)` (using `glTexSubImage2D` for maximum upload efficiency).
* If new or resized, it invokes `textureFactory.create(...)` to allocate a fresh OpenGL texture handle.
* Finally, the pixel buffer is returned to `MemoryPool`.

---

## Input Architecture: `KeyManager`

* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/io/KeyManager.kt`

Handling keyboard input in an immediate-mode application requires bridging discrete hardware interrupt events from GLFW with single-frame polled queries.

### State Tracking and Transitions
`KeyManager` tracks three state categories per scancode:
* `pressedKeys`: `true` only on the exact frame the key was depressed (one-shot).
* `pressingKeys`: `true` continuously as long as the physical key remains down.
* `releasedKeys`: `true` only on the exact frame the key was released (one-shot).

At the end of every frame during `keyManager.update()`, one-shot `pressed` and `released` flags are automatically reset to `false`.

### Shortcut Dispatch and Typematic Repeat
Shortcuts registered via `keyManager.addShortcut(...)` support configurable typematic repeat behaviors:
* `SHORTCUT_INITIAL_TRIGGER_RATE_SECS = 0.5`: Delay before continuous triggering begins.
* `SHORTCUT_TRIGGER_RATE_SECS = 0.06`: Firing cadence (~16 Hz) while holding keys down (e.g., continuous undo when holding `Ctrl+Z`).

---

## Window Operations Queue: `GlfwWindow`

* **File**: `LwjglPlatform/src/main/kotlin/org/deltacv/visiongraph/platform/lwjgl/glfw/GlfwWindow.kt`

Modifying GLFW window properties (changing window title, resizing, centering, or requesting focus) must also happen on the thread owning the GLFW event loop.

To allow background tasks (like engine connection status callbacks) to update the window title safely:
1. Operations are wrapped in lambdas and placed on `windowOpsQueue` (a thread-safe queue).
2. `LWJGLPaperVisionApp.process()` calls `glfwWindow.processWindowOps()` at the start of every frame, draining and executing all queued window operations on the main GLFW thread.

---

## Native Dialogs and Asset IO

VisionGraph uses lightweight native bindings from the LWJGL ecosystem:
* **Native File Dialogs (`lwjgl-nfd`)**: Used for Open/Save project operations. Invoking `NFD_OpenDialog` opens the native OS file picker (Explorer on Windows, Finder on macOS, GTK/Zenity on Linux) without freezing the GLFW thread.
* **STB Image (`lwjgl-stb`)**: Decodes static application assets (window icons like `/ico/ico_ezv.png` and font atlas textures) directly into native memory buffers (`ByteBuffer`) for OpenGL ingestion.