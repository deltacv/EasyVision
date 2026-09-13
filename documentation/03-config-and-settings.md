# Configuration, Runtime Settings, and Internationalization

## Purpose and Scope

This document details how VisionGraph persists user preferences across sessions, manages platform-specific environment settings, tracks runtime UI flags, and coordinates dynamic multi-language localization (i18n).

---

## High-Level Architecture

VisionGraph bifurcates configuration into two domains:
1. **Persistent Application Configuration**: Global settings (language choice, initial tour prompts, recent paths) stored as formatted JSON in the user's home directory.
2. **Transient Runtime / Project Flags**: Dynamic state bags (such as canvas panning coordinates, zoom levels, or project-specific hints) carried in-memory or persisted within project serialization envelopes.

```
+-------------------------------------------------------------+
| Persistent Storage (~/.papervision/config.json)             |
+------------------------------+------------------------------+
                               ^
                               | (JSON serialization)
                               v
+-------------------------------------------------------------+
| Platform Configuration Layer                                |
| - PlatformConfigManager (abstract contract)                 |
| - FilePlatformConfigManager (disk-backed implementation)    |
| - DefaultFilePlatformConfigManager (singleton provider)     |
| - PaperVisionConfig (data model: lang, shouldAskForLang)    |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
| Localization Subsystem (mai18n)                             |
| - lang_pv.csv (key, en, es dictionary)                      |
| - Language engine (makeThreadTr() thread-local resolver)    |
| - tr("token") global string localization                    |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
| UI & Runtime Consumers                                      |
| - NodeEditor / OptionsWindow (language pickers)             |
| - IntroModalWindow (welcome & onboarding prompts)           |
| - NodeCategory & Socket Labels                              |
+-------------------------------------------------------------+
```

---

## Core Components and File References

### 1. Platform Configuration Abstraction
* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/platform/PlatformConfigManager.kt`
* **Data Model**:
  ```kotlin
  @Serializable
  data class PaperVisionConfig(
      val lang: String = "en",
      val shouldAskForLang: Boolean = true
  )
  ```
* **Contract**:
  * `load()`: Deserializes stored preferences into the in-memory `data` property.
  * `save(data)`: Serializes preferences to persistent storage.

### 2. File-Backed Implementation: `FilePlatformConfigManager`
* **File**: `VisionGraph/src/main/kotlin/org/deltacv/visiongraph/platform/FilePlatformConfigManager.kt`
* **Default Location**: `System.getProperty("user.home") + "/.papervision/config.json"`.
* **JSON Serializer**: Configured with `prettyPrint = true` and `encodeDefaults = true` for human readability.
* **Shutdown Safety**: Registers a JVM shutdown hook (`PlatformConfig-ShutdownHook`) during instantiation to guarantee that modified preferences are flushed to disk even on abrupt application exit.

### 3. Build Metadata Injection: `BuildInfo.kt`
* **File**: Generated during Gradle build via task `writeBuildInfo` in `VisionGraph/build.gradle`.
* **Location**: Output to `PaperVisionBuildInfo.json` in classpath resources.
* **Exposed Properties**: `VERSION_STRING`, `STANDARD_VERSION_STRING`, `BUILD_DATE`, `IS_DEV`. Used by `AboutModalWindow` to display system diagnostics.

---

## Configuration Lifecycle and Execution Flow

### 1. Application Startup
```
PlatformSetup.setup()
 │
 ├── 1. DefaultFilePlatformConfigManager ensures ~/.papervision/ exists
 ├── 2. config.load() reads config.json
 │     ├── If file does not exist: writes default PaperVisionConfig() to disk
 │     └── If file exists: parses JSON using kotlinx.serialization
 │
 └── 3. visionGraph.initLanguage()
       ├── Reads config.data.lang (defaults to "en")
       ├── Instantiates Language("/lang_pv.csv", langCode)
       └── Calls makeThreadTr() to bind translation methods to the active thread
```

### 2. Modifying Settings at Runtime
When the user changes options in the UI (e.g., selecting Spanish in `OptionsWindow` or checking "Don't show again" on the welcome modal):
1. The UI component invokes `visionGraph.changeLanguage("es")`.
2. A new `PaperVisionConfig` instance is constructed with updated properties:
   ```kotlin
   config.save(config.data.copy(lang = newLang, shouldAskForLang = false))
   ```
3. `FilePlatformConfigManager` writes the JSON payload to `config.json` immediately and updates `this.data`.

### 3. Application Termination
During `visionGraph.destroy()`:
1. `config.save()` is explicitly called prior to closing windows or releasing graphics contexts.
2. The registered JVM shutdown hook serves as a secondary fallback in case of unhandled runtime exceptions.

---

## Internationalization (i18n) Architecture

VisionGraph implements runtime multi-language localization using the lightweight `mai18n` library.

### Translation Dictionary (`lang_pv.csv`)
* **File**: `VisionGraph/src/main/resources/lang_pv.csv`
* **Format**: Comma-Separated Values where column 0 is the semantic key, followed by supported language ISO codes (`en`, `es`, etc.):
  ```csv
  name,en,es
  lan_en,English,Ingles
  lan_es,Spanish,Español
  win_welcome,Welcome!,Bienvenid@!
  cat_image_proc,Image Processing,Procesamiento de Imagen
  mis_codegen_errortoast,Code generation failed!,Fallo en la generación de código!
  ```

### Dynamic Thread-Local Translation Binding
When `visionGraph.changeLanguage(langCode)` is called:
```kotlin
fun changeLanguage(langCode: String) {
    currentLanguage = Language("/lang_pv.csv", langCode).apply { 
        makeThreadTr() 
    }
}
```
* `makeThreadTr()` binds the selected translation dictionary to the thread-local resolver used by the global `tr(key, ...args)` helper.
* String interpolation is supported via `$[0]`, `$[1]` tokens (e.g., `tr("win_about_version", BuildInfo.VERSION_STRING, BuildInfo.BUILD_DATE)`).
* All UI strings—node category titles, socket labels, error toasts, and dialog headers—call `tr("token")`, ensuring instantaneous UI updates when the language changes without restarting the application.

---

## Error Handling and Recovery

| Failure Scenario | Resolution Mechanism |
| :--- | :--- |
| **Missing `config.json`** | `FilePlatformConfigManager.load()` detects file absence, instantiates default `PaperVisionConfig()`, and immediately persists it to disk. |
| **Corrupted / Invalid JSON** | If `decodeFromString` fails due to syntax errors or incompatible schema changes, an exception is caught, a warning is logged, and the application falls back to safe default settings. |
| **Invalid Language Code** | If a configured language code is missing from `lang_pv.csv`, `initLanguage()` catches the error, logs a warning, and falls back to `"en"`. |
| **Missing Translation Token** | If `tr("unknown_key")` is queried, `mai18n` returns the key string itself, preventing UI rendering crashes while highlighting missing localizations during development. |
| **Read-Only Home Directory** | If disk writes fail due to OS filesystem permissions, `save()` logs an error without aborting execution, allowing the user to continue working in memory. |

---

## Design Rationale

### Why JSON Over Java Properties or XML?
JSON is cross-platform, easily inspected or manually edited by end users with any text editor, and directly integrates with `kotlinx.serialization` without extra reflection overhead.

### Why File-Based Abstraction in Platform Layer?
In certain embedded environments or web/mock tests, writing to `~/.papervision/config.json` might be impermissible or undesirable. By making `PlatformConfigManager` an abstract class in `VisionGraph` and instantiating `DefaultFilePlatformConfigManager` only within `PlatformSetup`, alternative hosts can inject in-memory or database-backed configuration managers seamlessly.
