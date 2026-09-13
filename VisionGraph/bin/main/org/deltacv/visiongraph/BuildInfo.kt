package org.deltacv.visiongraph

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BuildInfoData(
    val versionString: String,
    val standardVersionString: String,
    val buildDate: String,
    val isDev: Boolean
)

/**
 * Build information loaded from the generated PaperVisionBuildInfo.json resource file.
 * This object provides build-time metadata about VisionGraph, including version and build date.
 */
object BuildInfo {
    private val buildInfo: BuildInfoData by lazy {
        // Load the JSON resource file from the classpath
        val resourceUrl = BuildInfo::class.java.getResource("/PaperVisionBuildInfo.json")

        if (resourceUrl != null) {
            val jsonString = resourceUrl.readText(Charsets.UTF_8)
            Json.decodeFromString<BuildInfoData>(jsonString)
        } else {
            // Fallback if resource is not found (should not occur in normal builds)
            throw IllegalStateException("PaperVisionBuildInfo.json resource not found in classpath")
        }
    }

    /**
     * The full version string, including dev suffix if applicable
     * (e.g. "1.1.0-dev" or "1.1.0")
     */
    val VERSION_STRING: String by lazy { buildInfo.versionString }

    /**
     * The semantic version without any dev suffix or build metadata
     * (e.g. "1.1.0")
     */
    @Suppress("unused")
    val STANDARD_VERSION_STRING: String by lazy { buildInfo.standardVersionString }

    /**
     * Human-readable build date/time
     * (formatted timestamp for all builds)
     */
    val BUILD_DATE: String by lazy { buildInfo.buildDate }

    /**
     * Whether this is a development build (contains "dev" suffix)
     */
    val IS_DEV: Boolean by lazy { buildInfo.isDev }
}




