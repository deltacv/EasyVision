# Polyglot Code Generation Subsystem

## Purpose and Scope

VisionGraph is more than an interactive visual editor; its primary deliverable is production-grade, human-readable, executable source code for computer vision pipelines.

Rather than relying on naive string concatenation or rigid string templates, VisionGraph implements a **Multi-Pass Polyglot Compiler Architecture**. This subsystem translates an abstract graph of nodes and attributes into concrete source code across multiple programming languages (Java, Kotlin, Python, JavaScript, Lua) and target robotics frameworks (EasyOpenCV/OpenFTC, WPILib, Limelight, standard OpenCV).

This document details:
* The compiler pipeline stages and AST builder primitives (`Scope`, `Value`, `Type`).
* The intermediate graph value model (`GenValue`) that unifies compile-time constants (`Actual`) with late-bound code symbols (`Runtime`).
* The node state lifecycle managed by `CodeGenSession`.
* The `polyglot` builder DSL and its language closeness resolution heuristic.
* The OpenCV abstraction layers (`JvmOpenCv` and `CPythonOpenCv`).
* Previsualization code injection (`GenPreviz`) that turns static constants into live tuner variables and injects video streaming hooks.
* Two-pass placeholder resolution and visual canvas error mapping.

---

## High-Level Architecture

The code generation subsystem functions as an end-to-end domain-specific compiler:

```
+-----------------------------------------------------------------------------------+
| 1. INITIATION & ROOT PIN VALIDATION (CodeGenManager)                              |
| - Validates OutputMatNode has an attached upstream attribute                      |
| - Pushes DenseIdContainer<Resolvable.Placeholder<*>> to IdContext.local           |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 2. BACKWARD GRAPH TRAVERSAL & AST EMISSION (GenNode & PolyglotGenerator)          |
| - OutputMatNode.genCodeIfNecessary() pulls upstream GenValues                     |
| - PolyglotGenerator evaluates inheritance closeness (exact=0, superclass=1+, any) |
| - CodeGenSession stores emitted variables per node                                |
| - Emits into hierarchical scopes:                                                 |
|     * importScope          * classStartScope     * initScope                      |
|     * processFrameScope    * classEndScope       * onViewportTappedScope          |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 3. END PHASE & DYNAMIC INITIALIZATION                                             |
| - Evaluates endingNodes (side-effect nodes not connected to OutputMatNode)        |
| - Type.initialize() triggers AST synthesizers (e.g. JvmOpenCv.Circle helper)      |
| - JvmTargets double-buffered target swapping logic injected if enabled            |
| - importScope collects, deduplicates, and optimizes imports                       |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 4. LANGUAGE SYNTAX RENDERING (Language.build)                                     |
| - JavaLanguage:    class extends OpenCvPipeline / StreamableOpenCvPipeline        |
| - KotlinLanguage:  class with val/var properties, override fun, @Synchronized     |
| - CPythonLanguage: def runPipeline(input, llrobot) with tuple destructuring       |
| - Embeds unresolved placeholder tokens: <mack!14>                                  |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 5. TWO-PASS PLACEHOLDER RESOLUTION (PlaceholderResolver)                          |
| - Pass 1: Resolves local variable names, dependent expressions, and math values   |
| - Pass 2: Resolves resolveLast = true placeholders (import header insertion)      |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
                              [Executable Source Code]
```

---

## Core Components and File References

| Component | File Path | Role |
| :--- | :--- | :--- |
| **`CodeGenManager`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/CodeGenManager.kt` | Orchestrates generation, error interception, visual pan-to-node, and notification toasts. |
| **`CodeGen`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/CodeGen.kt` | Holds named AST scopes, node sessions, busy re-entrancy guards, and global flags. |
| **`GenNode<S>`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/GenNode.kt` | Node interface driving `genCodeIfNecessary()`, session caching, and downstream propagation. |
| **`PolyglotGenerator<I, S>`**| `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/PolyglotGenerator.kt` | Dispatches node generation to the most specific generator based on language inheritance distance. |
| **`polyglot` DSL** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/dsl/` | Fluent builder DSL (`PolyglotGeneratorCtx`, `CodeGenCtx`, `LanguageCtx`, `ScopeCtx`). |
| **`GenValue`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/GenValue.kt` | Strongly-typed intermediate value representation bridging `Actual` literals and `Runtime` code expressions. |
| **`CodeGenSession`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/CodeGen.kt` | Per-node state container storing generated outputs and variables for consumption by downstream nodes. |
| **`Value` & `Type`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/` | Abstract Syntax Tree primitives modeling expressions, operations, declarable variables, and types. |
| **`JvmOpenCv`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/language/jvm/JvmOpenCv.kt` | JVM OpenCV types, `Imgproc` constants, line parameter converters, and dynamic AST class initializers. |
| **`CPythonOpenCv`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/language/cpython/CPythonOpenCv.kt` | Python OpenCV (`cv2`) constants, `numpy` array imports, and tuple conversion formatters. |
| **`GenPreviz`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/language/GenPreviz.kt` | Converts editor constants into public `@Label` tuner variables and injects `streamFrame()` hooks. |
| **`JvmTargets`** | `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/language/jvm/JvmTargets.kt` | Generates thread-safe double-buffered target swapping logic for FTC OpMode queries. |
| **`PlaceholderResolver`**| `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/resolve/PlaceholderResolver.kt` | Two-pass string replacement engine resolving forward symbol references and late-bound imports. |

---

## The Abstract Syntax Tree: `Type`, `Value`, and `Scope`

### The `Type` Abstraction

`Type` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/Type.kt`) represents a type symbol in the target language. It encapsulates package paths, generic arguments, array representations, import policies, and dynamic initializers:

* **Identity & Import Policy**:
  * `className`: Simple class name (e.g. `"Mat"`, `"Rect"`).
  * `packagePath`: Import package (e.g. `"org.opencv.core"`).
  * `shouldImport`: Boolean property. Returns `true` only if `className != packagePath` and the package is non-empty. Primitives like `int` have `packagePath == "int"` and are never imported.
  * `overridenImport`: Allows aliasing (e.g. in Python, `np.ndarray` specifies `overridenImport = np` so that `import numpy as np` is generated instead of importing the type directly).
* **Active Type Initializers (`initializer`)**:
  `Type` supports an optional lambda `initializer: (InitializerCtx.() -> Unit)?`. If a type requires supporting infrastructure in the generated source file, referencing that type marks it in `typesToInitialize`. During compilation, `type.initialize(current)` executes once, writing helper classes or methods into `classEndScope`.
  * *Example*: `JvmOpenCv.Circle` defines an initializer that synthesizes an immutable Java data class `static class Circle { ... }` in `classEndScope` if any node in the graph manipulates circles.
* **Predefined Type Registries**:
  * `StandardTypes`: Primitive C/Java types (`cint`, `clong`, `cfloat`, `cdouble`, `cboolean`, `cvoid`).
  * `JavaTypes`: Java platform types (`String`, `List`, `ArrayList`, `Map`, `HashMap`, `Set`, `LabelAnnotation`).
  * `CPythonType`: Specialized Python type supporting module names, explicit member names, and `as` aliases (e.g. `CPythonType("numpy", null, "np")` generates `import numpy as np`).

### The `Value` Abstraction

`Value` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/Value.kt`) is the base representation for expressions, literals, and variables:

* **Automatic Import Collection**: Every `Value` exposes `imports: List<Type>`. When a `Value` is created, it automatically extracts `type` and any generic types, registering them for import inclusion unless explicitly excluded.
* **Subtypes**:
  * `ConValue(type, value)`: Concrete value string with an associated `Type`.
  * `EmptyConValue(type)`: Typified value without an initial value string.
  * `Condition(booleanType, condition)`: Expression guaranteed to evaluate to a boolean condition in `if`, `while`, or ternary statements.
  * `Operation(numberType, operation)`: Arithmetic expression. Automatically tracks precedence and wraps operands in parentheses (`toStringAndWrapIfOp()`) if an operand is itself an operation. It also computes type promotion via `determineRelevantNumberType(a, b)`.
  * `DeclarableVariable(name, variableValue, isNullable)`: Represents a declared variable symbol. Inherits the type of `variableValue` and forwards its imports.
  * `AccessorVariable(type, name)`: Variable symbol used to access object properties or method return values.

### The `Scope` Hierarchy

A `Scope` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/Scope.kt`) represents an indented lexical block of source code. A `CodeGen` compilation manages several distinct scopes:

```
Top-Level Source File
  │
  ├── importScopePlaceholder ──────────> (Resolved in Pass 2 with deduplicated imports)
  │
  └── class Start/Body Scope
        │
        ├── classStartScope ────────────> (Public tuners, private Mat instance fields)
        │
        ├── initScope ──────────────────> (init(Mat input) / setup logic)
        │
        ├── processFrameScope ──────────> (processFrame(Mat input) / per-frame pipeline)
        │
        ├── viewportTappedScope ────────> (onViewportTapped() interaction handler)
        │
        └── classEndScope ──────────────> (Helper methods, target double-buffer, nested classes)
```

#### Automatic Import Optimization
The `importScope` delegates import formatting to `Language.ImportBuilder`. In `BaseLanguage`:
* If more than 2 classes are imported from the same package (e.g. `org.opencv.core.Mat`, `org.opencv.core.Point`, `org.opencv.core.Scalar`), `BaseImportBuilder` automatically collapses them into a wildcard import (`import org.opencv.core.*;`).
* In `CPythonLanguage`, `PythonImportBuilder` groups multiple imports from the same module into `from module import A, B as C`.

---

## Intermediate Graph Values: `GenValue`

In a visual DAG, outputs from one node connect to inputs of another. In VisionGraph, a value flowing across a link may be a **compile-time constant known in the editor** (e.g. an integer slider set to `5`), or it may be a **runtime symbol** (e.g. an OpenCV `Mat` returned by a filter).

`GenValue` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/GenValue.kt`) solves this by providing a unified, strongly-typed sum type with two distinct branches: `Actual` and `Runtime`.

```
                    GenValue
                       │
       ┌───────────────┴───────────────┐
       ▼                               ▼
     Actual                         Runtime
 (Compile-Time)                  (Late-Bound)
 Known constant in editor        Resolvable<Value>
 Can be inlined or folded        Variable symbol in code
 E.g. Double.Actual(5.0)         E.g. Mat(variable, RGB)
```

### Key `GenValue` Specializations

#### 1. `GenValue.Mat`
Encapsulates an OpenCV image buffer with three core properties:
* `value: Resolvable<Value>`: The variable identifier representing the OpenCV matrix in the emitted code.
* `color: Resolvable<ColorSpace>`: Tracks whether the image is currently in `RGB`, `BGR`, `HSV`, `LAB`, `YCrCb`, or `GRAY` format. Downstream nodes inspect this property to skip redundant conversions or raise compilation errors.
* `isBinary: Boolean`: Flags whether the matrix is a thresholded single-channel binary mask. Nodes that require binary inputs (e.g. contour detectors) call `requireBinary(attribute)` to validate incoming matrices, raising immediate compile errors directly on socket pins if color matrices are linked.

#### 2. `GenValue.Number` (`Int`, `Float`, `Double`)
Every number has an `Actual` representation (wrapping a compile-time Kotlin primitive) and a `Runtime` representation (wrapping a `Resolvable<Value>` variable reference).
* Provides polymorphic conversions: `toInt(langHolder)`, `toFloat(langHolder)`, `toDouble(langHolder)`, and `toRuntime(langHolder)`.
* When an `Actual` is converted via `toRuntime()`, it requests the active language backend to render the literal into a typified code `Value`.

#### 3. `GenValue.Scalar`
Represents 4-channel color/threshold bounds:
* `Components(a, b, c, d)`: Compile-time constant channel values.
* `Inst(value)`: Late-bound OpenCV `Scalar` instance variable.

#### 4. `GenValue.Rect` & `GenValue.RotatedRect`
* `Components(position, size)`: Geometry known at edit time.
* `Inst(value)`: Runtime `org.opencv.core.Rect` or Python tuple.

#### 5. `GenValue.LineParameters`
Encapsulates line drawing styling: wraps a `Scalar` color and an `Int` thickness. Exposes `LineParameters.wrap(color, thickness, langHolder)` which converts components to runtime representations if either attribute is dynamic.

#### 6. `GenValue.List<E>`
Heterogeneous collection support:
* `Actual<E>(elements)`: List of compile-time `GenValue` items.
* `Runtime<E>(value, typeClass)`: List variable in code.
* `Either<E>(actual, runtime)`: Container providing a `.match(ifActual, ifRuntime)` pattern-matching helper.

### Deferred Evaluation (`defer`)
Because upstream nodes might not have finished emitting their code when a downstream attribute references an input socket, every `GenValue` specialization provides a companion `defer` factory. This wraps the underlying variable, color space, and metadata in `Resolvable.from { ... }` provider lambdas. Property evaluation is deferred until the final placeholder resolution pass, eliminating null pointer exceptions regardless of graph traversal order.

---

## State Management: `CodeGenSession`

To decouple node code emission from attribute retrieval, every node defines an internal **Session** class implementing `CodeGenSession` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/CodeGen.kt`). Nodes that produce no outputs use the singleton `NoSession`.

### The Session Lifecycle

1. **Declaration**: A node declares an inner session class holding its output variable references (e.g. `lateinit var outputMatValue: GenValue.Mat`).
2. **Instantiation & Storage**: When `genCodeIfNecessary(current)` executes:
   * The node creates an instance of its session class.
   * It emits local variables or instance fields into the current scopes.
   * It stores references to those emitted variables inside its session instance.
   * The generator returns the session, which `CodeGen` caches in `codeGen.sessions[node]`.
3. **Retrieval**: Downstream nodes query upstream outputs via `sourceNode.getGenValueOf(current, sourceAttribute)`. The source node returns a deferred `GenValue` whose resolution lambda reads `current.sessionOf(this)?.outputProperty`.
4. **On-Demand Forcing**: If a downstream node requests a session that has not yet been computed, `current.nonNullSessionOf(node)` automatically triggers `node.genCodeIfNecessary(current)`, forcing the upstream dependency to compile first.
5. **Re-entrancy Protection**: `codeGen.markBusy(node)` and `codeGen.unmarkBusy(node)` prevent recursive loops if circular connections exist.

---

## The Polyglot DSL Architecture

Nodes declare their generators using the `polyglot { ... }` builder DSL (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/dsl/`).

### Context Hierarchy

The DSL is stratified into nested execution contexts:

```
PolyglotGeneratorCtx<I, S>
  │
  └── GeneratorCtx<I, S> (input, current)
        │
        └── CodeGenCtx (codeGen, language)
              │
              ├── LanguageCtx (operators: +, -, *, /, ==, instanceof, callValue, new)
              │
              └── ScopeCtx (scope-specific: local, public, private, ifCondition, forLoop, streamMat)
```

* **`PolyglotGeneratorCtx`**: Registers generators against specific target languages using `generatorFor(...)`.
* **`GeneratorCtx`**: Wraps the generation invocation, providing access to `current: CodeGen.Current`.
* **`CodeGenCtx`**: Provides high-level structure methods:
  * `group { ... }`: Emits an instance variable block into `classStartScope`.
  * `deferredGroup(resolvable) { ... }`: Emits an instance variable block conditionally once a dependency resolves.
  * `uniqueVariable(name, value)`: Allocates a collision-free variable name in `classStartScope`.
  * `initScope { ... }`, `processFrameScope { ... }`, `onViewportTappedScope { ... }`.
* **`LanguageCtx`**: Provides operator overloading and factory methods:
  * Number arithmetic: `val sum = a + b`, `val diff = a - b`.
  * Conditions: `val cond = a lessThan b`, `val combined = cond1 and cond2`.
  * Instantiations: `val mat = Mat.new()`, `val arr = Type.newArray(size)`.
  * Method calls: `"Imgproc".callValue(returnType, *parameters)`.
  * Extension conversions: `5.v` (converts Kotlin Int to `ConValue`), `Resolvable.v`.
* **`ScopeCtx`**: Controls local statements inside a code block:
  * Variable declarations: `"myVar" local value`, `public(variable, label)`, `private(variable)`.
  * Control flow: `ifCondition(cond) { ... }.elseIf(cond) { ... }.elseCondition { ... }`.
  * Loops: `forLoop(i, start, max) { ... }`, `foreach(item, list) { ... }`.
  * Frame streaming: `streamMat(id, mat, color)`, `output.streamIfEnabled(mat, color)`.

### Polyglot DSL Code Examples

#### 1. Declaring Generators Across Languages
```kotlin
override val generators = polyglot {
    // Generator for Java / JVM robotics runtimes
    generatorFor(JavaLanguage) {
        current {
            val session = Session()
            // Pull upstream inputs
            val inputMat = input.genValue(current)

            // Emit frame processing logic
            current.scope {
                nameComment() // // "Node Name"
                // statements...
            }
            session
        }
    }

    // Generator for Python runtimes (e.g. Limelight)
    generatorFor(CPythonLanguage) {
        current {
            val session = Session()
            // Python generation...
            session
        }
    }
}
```

#### 2. Persistent Allocation & Memory Reuse (JVM)
To prevent native memory exhaustion and per-frame GC pauses, matrices should be declared at class scope and reused:
```kotlin
// Allocate a persistent instance field in classStartScope:
// private Mat blurredMat = new Mat();
val outputMat = uniqueVariable("blurredMat", Mat.new())
group {
    private(outputMat)
}

// Emit OpenCV call in the active frame processing method:
current.scope {
    nameComment()
    Imgproc("blur", inputMat.value.v, outputMat, Size.new(5.v, 5.v))

    // Automatically emit live preview stream hook if user enabled the socket eye icon
    output.streamIfEnabled(outputMat, inputMat.color)
}
```

#### 3. Emitting Conditional Branches
```kotlin
current.scope {
    val thresholdValue = thresholdAttr.genValue(current).value
    val condition = thresholdValue greaterThan 100.v

    ifCondition(condition) {
        Imgproc("threshold", inputMat.value.v, outputMat, thresholdValue, 255.v, Imgproc.THRESH_BINARY)
    }.elseCondition {
        Imgproc("copyTo", inputMat.value.v, outputMat)
    }
}
```

#### 4. Live Tuner Parameter Promotion (`GenPreviz`)
```kotlin
// In previz/simulation mode: emits a public @Label instance variable
// In robot production mode: inlines the literal constant
val ksize = kernelSizeAttr.toPrevizInt(current)
```

---

## Language Resolution: The Closeness Heuristic

When compiling, `CodeGen` dispatches generation through `PolyglotGenerator<I, S>` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/PolyglotGenerator.kt`).

A node does not need to provide a bespoke generator for every dialect. It can declare generators for abstract language families, and the compiler selects the closest match using an **inheritance distance algorithm**:
* The compiler measures the distance between the target compilation language and candidate generators by crawling the target language's Kotlin superclass hierarchy (`KClass.superclasses`).
* It assigns a numerical score representing how many inheritance steps separate the candidate from the target.

### Match Priority Rules

1. **Distance = 0 (Exact Match)**: The generator is declared specifically for the target language (e.g. `generatorFor(JavaLanguage)` when compiling for Java). Execution picks this immediately.
2. **Distance > 0 (Superclass / Dialect Match)**: If compiling for `KotlinLanguage`, but the node only declares a generator for `BaseLanguage`, the inheritance distance is `1`. If no exact Kotlin generator exists, the `BaseLanguage` generator is used as fallback.
3. **Distance = `Int.MAX_VALUE` (`AnyLanguage`)**: A catch-all generator declared via `generatorForAny { ... }`. Used only if no specific or superclass generator matches.
4. **Distance < 0 (Discard)**: If the language is unrelated (e.g. matching a `CPythonLanguage` generator when targeting Java), the generator is discarded.

If no generator matches, `NoSuchElementException` is thrown, alerting the user that the node does not support the chosen language.

---

## OpenCV Abstraction Layers: `JvmOpenCv` and `CPythonOpenCv`

Computer vision pipelines require OpenCV primitives. Because OpenCV method signatures, memory semantics, and data types differ between Java/C++ and Python, VisionGraph provides dedicated OpenCV bridges.

### JVM OpenCV: `JvmOpenCv`

`JvmOpenCv` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/language/jvm/JvmOpenCv.kt`) targets Java and Kotlin runtimes (EasyOpenCV, WPILib, standard OpenCV):

* **Class & Enum Symbols**:
  * Core: `Mat`, `MatOfInt`, `MatOfPoint`, `MatOfPoint2f`, `MatOfKeyPoint`, `Size`, `Scalar`, `Point`, `KeyPoint`, `Rect`, `RotatedRect`.
  * `Imgproc` constants: `RETR_LIST`, `RETR_EXTERNAL`, `CHAIN_APPROX_SIMPLE`, `MORPH_RECT`, `HOUGH_GRADIENT`.
* **Runtime Conversion Helpers**:
  * `Scalar(genValue, langHolder)`: Converts `Scalar.Components` into `new Scalar(a, b, c, d)` or wraps `Scalar.Inst`.
  * `toRectInst(rect, langHolder)`: Instantiates `new Rect(x, y, w, h)`.
  * `toRotatedRectInst(rect, langHolder)`: Instantiates `new RotatedRect(new Point(x, y), new Size(w, h), angle)`.
  * `syncScalarVariable(scalarVariable, scalar, current)`: Dynamically generates code to synchronize scalar channel arrays (`scalarVariable.val[i] = ...`) when individual channels change.
* **Dynamic Type Synthesis (`JvmOpenCv.Circle`)**:
  OpenCV Java does not provide a native `Circle` class (circles are conventionally represented as raw float arrays `[x, y, radius]`). `JvmOpenCv.Circle` defines an active type initializer that injects a synthetic `Circle` helper class (with `Point center` and `double radius` members and constructor) into `classEndScope` the first time any node in the graph references a circle.

### Python OpenCV: `CPythonOpenCv`

`CPythonOpenCv` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/language/cpython/CPythonOpenCv.kt`) targets Python runtimes (Limelight, OpenCV Python):

* **Tuple-Based Data Representations**:
  Unlike Java, Python OpenCV does not use classes for rectangles or scalars; it uses native Python tuples:
  * `toRectTuple(rect, langHolder)`: Emits `(x, y, w, h)` tuple.
  * `toRotatedRectTuple(rect, langHolder)`: Emits nested tuple `((x, y), (w, h), angle)`.
  * `scalarTuple(scalar, langHolder)`: Emits `(b, g, r, a)` tuple.
* **Module Symbols**:
  * `cv2`: `CPythonType("cv2")` with constants like `cv2.RETR_EXTERNAL`, `cv2.CHAIN_APPROX_SIMPLE`, `cv2.contourArea`.
  * `np`: `CPythonType("numpy", null, "np")` (emits `import numpy as np`).
  * `npArray`: `np.ndarray` (with `overridenImport = np`).
* **Pipeline Structure**:
  Instead of generating a class extending `OpenCvPipeline`, `CPythonLanguage` formats the pipeline as a Limelight entry point:
  ```python
  import cv2
  import numpy as np

  # Tuner variables and constants
  blur_ksize = 5

  def runPipeline(input, llrobot):
      # per-frame pipeline logic
      return contours, input
  ```

---

## Previsualization & Live Tuning: `GenPreviz`

A critical feature of VisionGraph is **live interactive simulation**. When running inside the simulator, users can tweak node parameters in real-time without triggering a full recompile.

This behavior is governed by `GenPreviz` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/language/GenPreviz.kt`):

```
                       User Modifies Parameter
                                  │
                  Is codeGen.isForPreviz active?
                                  │
                 ┌────────────────┴────────────────┐
                 ▼                                 ▼
               [YES]                              [NO]
        (Previsualization)                    (Production)
                 │                                 │
     GenPreviz intercepts value        Inline constant directly:
                 │                     Imgproc.blur(..., new Size(5, 5))
                 ▼
 Promotes to public instance field
 decorated with @Label("Blur Size"):
 @Label(name = "Blur Size")
 public int blur_ksize = 5;
                 │
                 ▼
 EOCV-Sim reflection detects @Label
 and binds GUI slider dynamically!
```

### 1. Tuner Variable Promotion
Methods `toPrevizInt()`, `toPrevizFloat()`, `toPrevizDouble()`, and `toPrevizVec2()` check `codeGen.isForPreviz`:
* **If `isForPreviz == true`**:
  If the value is an `Actual` compile-time constant, `GenPreviz` declares a **public instance variable** in `classStartScope` annotated with `@Label(name = "...")`. In `processFrameScope`, it emits a reference to that field instead of inlining the constant:
  ```java
  @Label(name = "Threshold Low")
  public int thresholdLow = 100;
  ```
  In VisionBench / EOCV-Sim, the simulator's reflection subsystem detects public `@Label` fields on the running pipeline and automatically attaches UI sliders to them. As the user moves the sliders, the JVM pipeline updates live!
* **If `isForPreviz == false` (Production Mode)**:
  `GenPreviz` returns the literal constant unmodified. The final generated code contains no `@Label` annotations and no redundant public fields, ensuring maximum execution performance on the target robot.

### 2. Live Frame Streaming Hooks (`streamMat`)
When inspecting nodes visually, users can toggle the **Preview Eye Icon** on output sockets:
* **Conditional Activation**: During code generation, `streamIfEnabled(mat, matColor)` checks whether a live preview window is currently attached to that socket pin.
* **Automatic RGB Conversion**: If the stream is active and compiling for simulation (`isForPreviz == true`), the generator inspects the matrix color space. If the matrix is not already formatted as RGB, it synthesizes an in-place color conversion to RGB.
* **Simulator Wire Hook**: It emits a call to `streamFrame(streamId, mat, code)`. The `StreamableOpenCvPipeline` base class in the simulator intercepts these frames, compresses them via SIMD TurboJPEG, and transmits them over WebSocket IPC back to the VisionGraph editor canvas in real time.

---

## Double-Buffered Target Swapping: `JvmTargets`

When computer vision pipelines detect game elements (bounding boxes, rotated rectangles, AprilTags), robot control threads (such as FTC OpModes) must read those results concurrently without encountering race conditions or tearing.

`JvmTargets` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/build/language/jvm/JvmTargets.kt`) injects a high-performance **double-buffered target swapping mechanism** into the pipeline class:

```
Vision Pipeline Thread (60 FPS)              OpMode / Control Thread (50 Hz)
───────────────────────────────              ───────────────────────────────
1. Computes contours & rects                 
2. addRectTarget("target", rect)             
   (Writes to backRectTargets map)           
3. swapTargets() [Synchronized]              
   ┌───────────────────────────┐             
   │ frontRectTargets <───────┼───────────── getRectTarget("target")
   │ backRectTargets.clear()   │             (Reads atomically from front map)
   └───────────────────────────┘             
```

When a node calls `current.enableJavaTargets()`:
1. **Fields Injected** into `classStartScope`:
   * `private Map<String, Rect> frontRectTargets = new HashMap<>();`
   * `private Map<String, Rect> backRectTargets = new HashMap<>();`
   * (And corresponding maps for `RotatedRect`).
2. **Methods Injected** into `classEndScope`:
   * `swapTargets()`: Synchronized method called at the beginning/end of `processFrame()` to swap front and back references and clear the back buffer.
   * `addRectTarget(label, rect)` / `addRotRectTarget(label, rotRect)`: Internal writers called by detection nodes.
   * `getRectTarget(label)`: Thread-safe public getter called by user robot code.
   * `getRectTargets(label)`: Returns an `ArrayList<Rect>` containing all targets whose labels start with a specified prefix.

---

## Concrete Node Implementation Walkthrough

To see how all these subsystems intersect in practice, consider the complete implementation of `CvtColorNode`:

```kotlin
@PaperNode(
    name = "Convert Color",
    category = "Color Spaces",
    description = "Converts an OpenCV Mat from one color space to another."
)
class CvtColorNode : DrawNode<CvtColorNode.Session>() {
    val input     = MatAttribute(INPUT, "$[att_input]")
    val output    = MatAttribute(OUTPUT, "$[att_output]").enablePrevizButton()
    val convertTo = EnumAttribute(INPUT, "$[att_convertto]", ColorSpace.options)

    class Session : CodeGenSession {
        lateinit var outputMatValue: GenValue.Mat
    }

    override val generators = polyglot {
        // 1. Java / EasyOpenCV Generator
        generatorFor(JavaLanguage) {
            current {
                val session = Session()

                // Pull upstream GenValue (forces upstream generation if not yet run)
                val inputMat = input.genValue(current)
                val targetColor = convertTo.genValue(current).value
                val matColor = inputMat.color
                val matColorResolved = matColor.resolve()

                // Optimize: Skip conversion if image is already in target color space
                if (matColorResolved == null || matColorResolved != targetColor) {
                    // Declare an instance variable in classStartScope
                    val mat = uniqueVariable("${inputMat.value.v}${targetColor.name}", Mat.new())
                    group {
                        private(mat)
                    }

                    // Emit OpenCV processing statements in processFrameScope
                    current.scope {
                        nameComment() // Emits: // "Convert Color"

                        deferredBlock(Resolvable.DependentPlaceholder(matColor) {
                            {
                                if (it != targetColor) {
                                    Imgproc("cvtColor", inputMat.value.v, mat, cvtColorValue(it, targetColor))
                                } else {
                                    Imgproc("copyTo", inputMat.value.v, mat)
                                }
                            }
                        })

                        // Wire live video preview stream if user enabled the eye icon
                        output.streamIfEnabled(mat, targetColor.resolved())
                    }

                    session.outputMatValue = GenValue.Mat(mat.resolved(), targetColor.resolved())
                } else {
                    session.outputMatValue = inputMat
                }

                session // Stored in codeGen.sessions[this]
            }
        }

        // 2. Python / OpenCV Generator
        generatorFor(CPythonLanguage) {
            current {
                val session = Session()
                val inputMat = input.genValue(current)
                val targetColor = convertTo.genValue(current).value

                current.scope {
                    nameComment() // Emits: # "Convert Color"

                    val cvtValue = cv2.callValue("cvtColor", CPythonLanguage.NoType, inputMat.value.v, cvtColorValue(inputMat.color.v, targetColor))
                    val mat = uniqueVariable("${inputMat.value.v}_${targetColor.name.lowercase()}", cvtValue)
                    local(mat)

                    session.outputMatValue = GenValue.Mat(mat.resolved(), targetColor.resolved())
                }

                session
            }
        }
    }

    // Downstream nodes retrieve our output value through this method
    override fun getGenValueOf(current: CodeGen.Current, attrib: Attribute): GenValue {
        if (attrib == output) {
            return GenValue.Mat.defer { current.sessionOf(this)?.outputMatValue }
        }
        return GenValue.None
    }
}
```

### Architectural Breakdown of `CvtColorNode`

### 1. Sockets and Visual Properties
* **Input Sockets**:
  * `input`: A `MatAttribute` configured in `INPUT` mode that accepts an upstream OpenCV image matrix.
  * `convertTo`: An `EnumAttribute` in `INPUT` mode presenting a dropdown of valid OpenCV target color spaces (e.g. `RGB`, `BGR`, `HSV`, `GRAY`, `YCrCb`, `Lab`).
* **Output Sockets**:
  * `output`: A `MatAttribute` configured in `OUTPUT` mode with `.enablePrevizButton()`, adding a toggleable eye icon in the UI that lets users preview the transformed matrix in real time.

### 2. Session Encapsulation (`Session`)
`CvtColorNode` defines an inner `Session` class implementing `CodeGenSession`. The session preserves intermediate artifacts produced during compilation:
* `outputMatValue`: Holds the resulting `GenValue.Mat` containing the variable name and resolved color space of the transformed image.
* When code generation executes, an instance of `Session` is attached to `current.codeGen.sessions[this]`, where downstream nodes can retrieve it.

### 3. Java / EasyOpenCV Generator
Under the Java target, the generator implements multiple optimizations and scope divisions:
1. **Upstream Data Pull**:
   Invoking `input.genValue(current)` recursively pulls the input `GenValue.Mat` from whatever upstream node is linked to `input`. It also pulls the target color enum from `convertTo.genValue(current)`.
2. **No-Op Elimination**:
   The generator compares the input matrix's color space (`inputMat.color.resolve()`) with the requested target color. If the image is already in the target color space, the node bypasses conversion entirely and passes `inputMat` directly to `session.outputMatValue`.
3. **Class-Level Persistent Allocation (`classStartScope`)**:
   In high-throughput robotics pipelines, allocating OpenCV `Mat` instances inside the frame processing loop triggers severe GC stutter and native memory churn. The generator allocates a unique variable in class scope (e.g. `private Mat inputMatHSV = new Mat();`) so that memory is reused across frames.
4. **Frame Processing Body (`processFrameScope`)**:
   Within the active frame loop:
   * Emits a section header comment (`nameComment()`).
   * Emits a deferred `Imgproc.cvtColor(...)` call linking the input matrix, output matrix, and the calculated color conversion code.
   * Calls `output.streamIfEnabled(mat, targetColor)`: if the user activated the eye icon on the output socket, this automatically generates the preview streaming call for the simulator.

### 4. Python / OpenCV Generator
In Python pipelines, where memory semantics differ from JVM OpenCV bindings:
* Matrix instances are dynamically allocated NumPy arrays.
* The generator emits a direct local call to `cv2.cvtColor(inputMat, code)` inside the current frame function.
* The result is stored in a locally scoped variable and published to `session.outputMatValue`.

### 5. Downstream Value Publication (`getGenValueOf`)
When a downstream node requests the value of `output`:
* `getGenValueOf(current, attrib)` returns a deferred `GenValue.Mat` resolving to `current.sessionOf(this)?.outputMatValue`.
* If downstream nodes evaluate this socket before `CvtColorNode` finishes generating, the deferred reference safely delays resolution until the session is populated.

---

## Two-Pass Placeholder Resolution: `PlaceholderResolver`

Forward references—such as determining the complete import header after all method bodies have been crawled, or resolving variable names across nested dependent blocks—are handled by `PlaceholderResolver` (`VisionGraph/src/main/kotlin/org/deltacv/visiongraph/codegen/resolve/PlaceholderResolver.kt`) and the `Resolvable` hierarchy (`Resolvable.kt`).

### The Token Wire Format (`<mack!%d>`)

Placeholders embed delimited token keys into the raw source code stream during initial code emission:
* **Prefix & Suffix**: `PLACEHOLDER_PREFIX = "<mack!"` and `PLACEHOLDER_SUFFIX = ">"`.
* **Token Template**: `PLACEHOLDER_TEMPLATE = "<mack!%d>"` where `%d` is an integer identifier lazily allocated from `IdContext.local.peekNonNull<Placeholder<*>>()`.
* **String Representation**: Calling `placeholder.toString()` or `placeholder.key` outputs its delimited token (e.g. `<mack!0>`, `<mack!1>`, `<mack!2>`).

```java
Raw Pre-Resolution Stream:
--------------------------
<mack!0>

public class Pipeline extends OpenCvPipeline {
    private Mat <mack!1> = new Mat();

    @Override
    public Mat processFrame(Mat input) {
        <mack!2>
        return <mack!3>;
    }
}
```

### The `Resolvable<T>` Hierarchy

1. **`Resolvable.Now<T>`**:
   Wraps an already-computed result. Resolves synchronously without embedding token tags into the code stream.
2. **`Resolvable.Placeholder<T>`**:
   Represents a late-bound value implementing `IdElement`.
   * Holds `val resolveLast: Boolean` (defaults to `false`).
   * Caches its resolved value on first evaluation.
   * Exposes an `onResolve: PaperEventHandler` so downstream callbacks can listen for resolution via `letOrDefer { ... }`.
3. **`Resolvable.DependentPlaceholder<P, T>`**:
   Defers evaluation of its resolver closure until its upstream `dependency: Resolvable<P>` resolves to a non-null value.
4. **`Resolvable.DoubleDependentPlaceholder<P1, P2, T>` & `ListPlaceholder<T>`**:
   Chains evaluation across multiple upstream dependencies.

---

### The Two-Pass Resolution Engine

`CodeGen.build()` first invokes `language.build(this)` to produce the raw code string, then passes it to `placeholderResolver.resolve(raw)`:

```
Raw Source with <mack!%d> Tokens
             │
             ├── Pass 1 (resolveLast = false)
             │     ├── Recursively replaces expressions, variable names, and deferred blocks
             │     ├── Cycle guard: tracks active placeholder IDs in a recursion stack
             │     └── Auto-Import: when resolving a Value, calls importScope.importType(value.type)
             │
             └── Pass 2 (resolveLast = true)
                   └── Resolves <mack!0> (CodeGen.importScopePlaceholder)
                         ├── Builds de-duplicated, sorted import list
                         ├── Applies wildcard optimization (e.g. import org.opencv.core.*)
                         └── Replaces <mack!0> at the top of the file
             │
             ▼
Finalized Executable Source Code
```

#### Pass 1: Local Expressions & Dynamic Import Collection (`resolveLast = false`)
* Iterates in a `do { val (text, changed) = replaceOnce(...) } while (changed)` loop until no more non-`resolveLast` tokens change.
* `replaceOnce` scans the string for `Resolvable.PLACEHOLDER_PREFIX` (`<mack!`) and `Resolvable.PLACEHOLDER_SUFFIX` (`>`).
* **Cycle Prevention**: Maintains an in-flight stack (`stack: MutableList<Int>`). If `pid in stack`, a recursive cycle is detected, a warning is logged, and infinite expansion is prevented.
* **Dynamic Import Discovery**: When a placeholder resolves to a `Value`, `resolvedValueToString()` automatically calls:
  ```kotlin
  importScope.importType(value.type)
  ```
  This guarantees that all types used in synthesized methods, expressions, and OpenCV operations are automatically registered for import without manual book-keeping.

#### Pass 2: Header Scope Injection (`resolveLast = true`)
* Once Pass 1 has completely stabilized and every expression in the class body has been expanded, Pass 2 runs with `resolveLastPlaceholders = true`.
* The primary `resolveLast` placeholder is `CodeGen.importScopePlaceholder` (`<mack!0>`), written by `BaseLanguage` at the very top of `mainScope`.
* Because Pass 1 has already processed every statement, `importScope` now has the complete, exhaustive catalog of all types used across the pipeline.
* `BaseImportBuilder` renders the final import header:
  * Excludes default imports (e.g. `java.lang.String`).
  * De-duplicates identical package paths.
  * **Wildcard Optimization**: If more than 2 distinct classes come from the same package (e.g. `Mat`, `Point`, `Rect` from `org.opencv.core`), and `optimizeImports` is true, they are consolidated into a single wildcard import:
    ```java
    import org.opencv.core.*;
    ```
* The finalized import block replaces `<mack!0>` at the top of the file, producing pristine, compile-ready source code.

---

## Visual Canvas Error Diagnostics

When a compilation exception occurs during graph traversal:
* `CodeGenManager` catches the exception.
* If the error is an `AttributeGenException` or `NodeGenException`, the compiler extracts the associated `Attribute` or `Node`.
* The visual editor pans and zooms the viewport to center the offending node on screen.
* The offending socket or node header pulses red, and an actionable notification toast describes the exact validation failure (e.g. *"Mat is not binary as required"* or *"Disconnected required input socket"*).