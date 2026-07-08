package com.jorisjonkers.personalstack.systemtests

import java.io.File

/**
 * Data class representing a single quarantine manifest entry.
 */
data class QuarantineEntry(
    val testClass: String,
    val ownerApproved: Boolean,
    val issueUrl: String,
    val expiresAt: String,
    val reason: String = "",
)

/**
 * Quarantine manifest loaded from quarantined-tests.yaml.
 *
 * Parses the simple YAML structure:
 * ```yaml
 * entries:
 *   - testClass: com.example.SomeTest
 *     ownerApproved: true
 *     issueUrl: https://github.com/JorisJonkers-dev/...
 *     expiresAt: 2026-12-31
 *     reason: Flaky due to timing
 * ```
 *
 * Uses a minimal block-YAML parser that covers only this schema.
 * The manifest is gated by CODEOWNERS so structure is controlled.
 */
data class QuarantineManifest(val entries: List<QuarantineEntry>) {
    companion object {
        /**
         * Load a quarantine manifest from a YAML file path.
         * Returns an empty manifest if the file does not exist.
         */
        @JvmStatic
        fun load(path: String): QuarantineManifest {
            val file = File(path)
            if (!file.exists()) {
                return QuarantineManifest(emptyList())
            }
            return parseManifest(file.readText())
        }

        internal fun parseManifest(yaml: String): QuarantineManifest {
            val entries = mutableListOf<QuarantineEntry>()
            var currentEntry = mutableMapOf<String, String>()
            var inEntries = false
            var inEntry = false

            for (rawLine in yaml.lines()) {
                val line = rawLine.trimEnd()
                when {
                    // Top-level "entries:" key
                    line.trimStart() == "entries:" -> {
                        inEntries = true
                        continue
                    }
                    // New entry item (list element marker "  - ..." or "- ...")
                    inEntries && line.trimStart().startsWith("- ") -> {
                        if (inEntry && currentEntry.isNotEmpty()) {
                            entries.add(buildEntry(currentEntry))
                        }
                        currentEntry = mutableMapOf()
                        inEntry = true
                        // Parse the key-value on the same line as the dash
                        val kvPart = line.trimStart().removePrefix("- ")
                        parseKeyValue(kvPart)?.let { (k, v) -> currentEntry[k] = v }
                    }
                    // Key-value line within an entry block
                    inEntry && line.trimStart().matches(Regex("[a-zA-Z][a-zA-Z0-9]*:.*")) -> {
                        parseKeyValue(line.trimStart())?.let { (k, v) -> currentEntry[k] = v }
                    }
                    // Empty line or comment — end of entry detection handled by next "- " marker
                    line.isBlank() || line.trimStart().startsWith("#") -> continue
                    // Any other indented line within an entry (continuation) — skip
                    else -> continue
                }
            }
            // Flush last entry
            if (inEntry && currentEntry.isNotEmpty()) {
                entries.add(buildEntry(currentEntry))
            }
            return QuarantineManifest(entries)
        }

        private fun parseKeyValue(line: String): Pair<String, String>? {
            val colonIdx = line.indexOf(':')
            if (colonIdx < 0) return null
            val key = line.substring(0, colonIdx).trim()
            val value = line.substring(colonIdx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
            return key to value
        }

        private fun buildEntry(map: Map<String, String>): QuarantineEntry {
            val testClass = map["testClass"] ?: error("quarantine entry missing testClass")
            val ownerApproved = map["ownerApproved"]?.lowercase() == "true"
            val issueUrl = map["issueUrl"] ?: ""
            val expiresAt = map["expiresAt"] ?: ""
            val reason = map["reason"] ?: ""
            return QuarantineEntry(
                testClass = testClass,
                ownerApproved = ownerApproved,
                issueUrl = issueUrl,
                expiresAt = expiresAt,
                reason = reason,
            )
        }
    }
}
