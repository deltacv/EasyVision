# Engine Abstraction, Client Coordinator, and Live Previz Pipeline

## Purpose and Scope

VisionGraph is intentionally designed as an execution-agnostic visual editor. The core editor canvas does not directly execute computer vision algorithms, instantiate native cameras, or bind directly to simulator runtimes. Instead, all communication between the visual graph and an execution runtime is decoupled behind a unified **Engine Abstraction Layer**.

This document details the generic engine abstraction: its message passing semantics, high-throughput binary framing protocol, client coordinator, response tracking, and live preview stream multiplexing.

> **Note:** This chapter covers the abstract engine interfaces, client-side coordination, and reference implementations (`LocalPaperVisionEngine` and `NoOpPaperVisionEngineBridge`). For the concrete **VisionBench / EOCV-Sim** simulator implementation (Janino compilation, TurboJPEG streaming, and process isolation), see [11-visionbench-plugin.md](11-visionbench-plugin.md).

---

## High-Level Architecture

The engine subsystem is structured around a bidirectional message-passing architecture:

```
+----------------------------------------------------------------+
| UI & Previz Consumers                                          |
| - ClientPrevizManager                                          |
| - ImageDisplayWindow                                           |
+-------------------------------+--------------------------------+
                                |
                                v
+----------------------------------------------------------------+
| Client Engine Coordinator (PaperVisionEngineClient)            |
| - ClientByteMessageReceiver (tag-based binary demuxing)        |
| - messagesAwaitingResponse (map tracking pending replies)      |
| - 5-second TTL orphan purging                                  |
| - bytesChannel (Channel<ByteArray>(capacity=10, DROP_OLDEST))  |
+-------------------------------+--------------------------------+
                                |
                                v
+----------------------------------------------------------------+
| Transport Bridge Abstraction (PaperVisionEngineBridge)         |
+-------------------------------+--------------------------------+
                                |
      +-------------------------+-------------------------+
      |                                                   |
      v                                                   v
+-----------------------------+             +----------------------------+
| NoOpPaperVisionEngineBridge |             | LocalPaperVisionEngine     |
| (Standalone Dev: stubs      |             | (In-Memory Tests: mock     |
|  responses, drops frames)   |             |  engine handlers & queues) |
+-----------------------------+             +----------------------------+
                                                          |
                                                          v
                                            +----------------------------+
                                            | Remote / Plugin Bridges    |
                                            | (e.g. VisionBench/EOCV-Sim |
                                            |  WebSocket IPC Bridge)     |
                                            +----------------------------+
```

### Key Architectural Principle
The visual editor communicates with an engine strictly using two primitives:
1. **Typed Asynchronous Messages / Responses**: For control-plane actions (starting previz, sending generated source code, changing tunable parameters, querying active projects).
2. **Framed Binary Packets (`ByteMessages`)**: For data-plane high-throughput streaming (JPEG camera frames, raw telemetry, compressed debug streams).

---

## Core Engine Interfaces

### 1. The Engine Contract: `PaperVisionEngine`
* **File**: `Shared/src/main/kotlin/org/deltacv/visiongraph/engine/PaperVisionEngine.kt`
* **Contract**:
  ```kotlin
  interface PaperVisionEngine {
      fun sendBytes(bytes: ByteArray)
      fun sendBytes(tag: ByteMessageTag, id: Int, bytes: ByteArray)
      fun acceptMessage(message: PaperVisionEngineMessage)
      fun sendResponse(response: PaperVisionEngineMessageResponse)
  }
  ```
An engine implementation receives control messages via `acceptMessage` and transmits binary streams or typed responses back to the client via `sendBytes` and `sendResponse`.

### 2. The Transport Decoupler: `PaperVisionEngineBridge`
* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/engine/bridge/PaperVisionEngineBridge.kt`
* **Contract**:
  ```kotlin
  interface PaperVisionEngineBridge {
      val isConnected: Boolean
      val onClientProcess: PaperEventHandler

      fun connectClient(client: PaperVisionEngineClient)
      fun terminate(client: PaperVisionEngineClient)
      fun broadcastBytes(bytes: ByteArray)
      fun sendMessage(client: PaperVisionEngineClient, message: PaperVisionEngineMessage)
      fun acceptResponse(response: PaperVisionEngineMessageResponse)
  }
  ```
The bridge isolates `PaperVisionEngineClient` from physical connection topologies: in-memory queues, inter-process pipes, WebSockets, or Unix domain sockets.

---

## Canonical Reference Implementations

### 1. `NoOpPaperVisionEngineBridge` (Standalone Mode)
* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/engine/bridge/NoOpPaperVisionEngineBridge.kt`
* Used when running the standalone editor (`AppMain.kt`) without an attached vision backend.
* Drops outgoing control messages gracefully, returns stub responses when required, and emits no video frames. This enables offline graph editing, palette exploration, and code export without throwing connection exceptions.

### 2. `LocalPaperVisionEngine` & Bridge (In-Memory Testing)
* **Files**: `LocalPaperVisionEngine.kt`, `LocalPaperVisionEngineBridge.kt` in `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/engine/`
* An in-process, synchronous mock engine operating entirely in JVM memory.
* Allows test suites to register custom typed handlers via `setMessageHandlerOf<T> { ... }`.
* **Example Usage in Unit Tests**:
  ```kotlin
  val engine = LocalPaperVisionEngine()
  val bridge = LocalPaperVisionEngineBridge(engine)
  val client = PaperVisionEngineClient(bridge)

  engine.setMessageHandlerOf<PrevizStartMessage> {
      // Simulate engine responding to previz request
      respond(OkResponse(message.id))
      // Push synthetic test frame
      sendBytes(ByteMessageTag.fromString("testStream"), 0, syntheticJpegBytes)
  }
  ```

---

## Binary Framing Protocol: `ByteMessages`

High-throughput video streaming requires framing arbitrary byte payloads over multiplexed connections without incurring JSON parsing or serialization overhead.

* **File**: `Shared/src/main/kotlin/org/deltacv/visiongraph/engine/ByteMessages.kt`
* **Packet Wire Format**:

```
+----------------+----------------+----------------+--------------------+-------------------+
| tagSize (4B)   | tagBytes (nB)  | id (4B)        | payloadSize (4B)   | payload (mB)      |
| Int32 (BigEnd) | UTF-8 String   | Int32 (BigEnd) | Int32 (BigEnd)     | Raw Payload Bytes |
+----------------+----------------+----------------+--------------------+-------------------+
```

### Protocol Mechanics
* **Zero-Copy Parsing**: `tagFromBytes`, `idFromBytes`, and `messageOffsetFromBytes` read slicing boundaries directly via `ByteBuffer.wrap()`, avoiding heap allocation when extracting metadata headers.
* **Tag Multiplexing**: Senders attach arbitrary semantic string tags (e.g., `"previz_stream_main"`, `"telemetry_fps"`). The client-side demultiplexer (`ByteMessageReceiver` in `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/engine/client/`) routes bytes to registered handlers based on matching tag prefixes.

---

## Client-Side Coordinator: `PaperVisionEngineClient`

* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/engine/client/PaperVisionEngineClient.kt`

`PaperVisionEngineClient` acts as the single point of contact between the UI thread and the bridge. It solves three critical distributed communication challenges:

### 1. Request-Response Matching
Outgoing messages subclassing `PaperVisionEngineMessage` (`Shared/src/main/kotlin/org/deltacv/visiongraph/engine/client/message/`) are assigned a unique integer `id`. When `sendMessage(msg)` is called:
1. The message and current timestamp are stored in a thread-safe map: `messagesAwaitingResponse[msg.id]`.
2. The message is transmitted via `bridge.sendMessage(this, msg)`.
3. When the engine replies with a `PaperVisionEngineMessageResponse`, the client looks up `response.id`, dispatches the typed callback (`msg.acceptResponse(response)`), and cleans up non-persistent entries.

### 2. Request Timeout & Orphan Purging (5-Second TTL)
In unstable or crashed network connections, an engine might fail to respond. Without mitigation, `messagesAwaitingResponse` would leak indefinitely.
During the per-frame `process()` tick:
```kotlin
val now = System.currentTimeMillis()
for ((id, data) in activeTrackingState) {
    val timeMillis = now - data.timestamp
    data.message.acceptElapsedTime(timeMillis)

    // Hard TTL bound to purge orphaned requests
    if (timeMillis > 5000L && !data.message.persistent) {
        droppedOrphans.add(id)
    }
}
```
If a message exceeds 5,000 ms without a response, it is purged and its timeout callback (`msg.onTimeout`) is fired, allowing UI elements to show a warning.

### 3. Asynchronous Ingestion & Backpressure (`bytesChannel`)
Raw video frames arrive asynchronously from background network threads. If the UI thread stutters or drops frames, buffering unbounded video frames in memory would trigger Out-Of-Memory (OOM) crashes.
VisionGraph uses a bounded Kotlin Coroutines Channel:
```kotlin
private val bytesChannel = Channel<ByteArray>(
    capacity = 10, 
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
```
* **Policy**: `BufferOverflow.DROP_OLDEST` ensures that if the rendering thread cannot consume frames as fast as the engine produces them, older stale frames are discarded in favor of the latest frame, maintaining minimal visual latency.
* **Drain Cycle**: During `client.process()` on the main thread, all pending byte buffers are drained from `bytesChannel` and dispatched to `ClientByteMessageReceiver`.

---

## The Live Previz Subsystem

The Previz (preview) subsystem manages the high-level lifecycle of interactive pipeline execution.

* **Files**: `ClientPrevizManager.kt`, `ClientPrevizStream.kt` in `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/engine/previz/`

```
PREVIZ LIFECYCLE SEQUENCE:
==========================
[NodeEditor / User]
   |
   | startPreviz(sessionName)
   v
[ClientPrevizManager]
   |
   +--> Calls CodeGenManager.build(sessionName, JavaLanguage, isForPreviz = true)
   |
   +--> Sends PrevizStartMessage(sessionName, sourceCode, width, height)
   |
   v
[PaperVisionEngineClient]
   |
   | Forward message over bridge
   v
[Connected Engine Runtime]
   |
   | (Compiles pipeline, starts camera feed, processes frames)
   |
   | Streams binary frames: sendBytes("previz_stream", id, jpegBytes)
   v
[PaperVisionEngineClient]
   |
   +--> bytesChannel.trySend(bytes) [drops oldest if full]
   |
   +--> (Main Thread tick): Drains channel and routes to ClientPrevizStream
   |
   v
[TextureProcessorQueue]
   |
   +--> Decompresses JPEG asynchronously via MackJPEG worker
   +--> (Render pass): Uploads pixels to OpenGL PlatformTexture
   |
   v
[ImageDisplayWindow / Node Preview]
   |
   +--> Renders live texture at 60 FPS with FPS & Latency stats
```

### Live Parameter Tuning Flow
When the user edits a threshold slider in the node graph during an active preview:
1. `ClientPrevizManager` catches the change and sends a `TunerChangeValueMessage(label, value)`.
2. The engine receives the message and updates the field directly in memory via reflection, altering live output frames without halting or recompiling the pipeline.

### Telemetry & Performance Tracking
* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/engine/previz/LivePipelineStatistics.kt`
* Collects running frame intervals, calculating real-time Frames Per Second (FPS), pipeline execution latency, and transmission jitter, displayed on the title bar of preview windows.

---

## Design Decisions and Rationale

### Why Asynchronous Message Passing Over Direct Function Calls?
Decoupling the editor from the engine via message queues allows the editor and the computer vision runtime to execute:
1. In the same JVM process on separate threads.
2. In separate JVM processes on the same machine via WebSockets (preventing graphics library crashes from crashing the host).
3. Across physical network boundaries (e.g., the editor running on a developer laptop while the pipeline runs on a robot's onboard coprocessor like a Raspberry Pi or Control Hub).
