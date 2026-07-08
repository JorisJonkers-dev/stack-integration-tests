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
data class QuarantineManifest(
    val entries: List<QuarantineEntry>,
) {
    companion object {
        private val KEY_PATTERN = Regex("[a-zA-Z][a-zA-Z0-9]*")

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

        internal fun parseManifest(yaml: String) = QuarantineManifest(splitEntryBlocks(yaml).map(::buildEntry))

        private fun splitEntryBlocks(yaml: String): List<Map<String, String>> {
            val blocks = mutableListOf<MutableMap<String, String>>()
            var inEntries = false
            for (line in contentLines(yaml)) {
                inEntries = inEntries || isEntriesKey(line)
                appendLineToBlocks(blocks, line, inEntries)
            }
            return blocks
        }

        private fun appendLineToBlocks(
            blocks: MutableList<MutableMap<String, String>>,
            line: String,
            inEntries: Boolean,
        ) {
            when {
                !inEntries -> Unit
                isListItem(line) -> {
                    blocks.add(mutableMapOf())
                    addKeyValue(blocks.last(), line.trimStart().removePrefix("- "))
                }
                blocks.isNotEmpty() -> addKeyValue(blocks.last(), line.trimStart())
            }
        }

        private fun contentLines(yaml: String): List<String> =
            yaml
                .lines()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }

        private fun isEntriesKey(line: String): Boolean = line.trimStart() == "entries:"

        private fun isListItem(line: String): Boolean = line.trimStart().startsWith("- ")

        private fun addKeyValue(
            target: MutableMap<String, String>,
            raw: String,
        ) {
            val colonIdx = raw.indexOf(':')
            if (colonIdx <= 0) {
                return
            }
            val key = raw.substring(0, colonIdx).trim()
            if (!key.matches(KEY_PATTERN)) {
                return
            }
            val value =
                raw
                    .substring(colonIdx + 1)
                    .trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
            target[key] = value
        }

        private fun buildEntry(map: Map<String, String>): QuarantineEntry =
            QuarantineEntry(
                testClass = map["testClass"] ?: error("quarantine entry missing testClass"),
                ownerApproved = map["ownerApproved"]?.lowercase() == "true",
                issueUrl = map["issueUrl"] ?: "",
                expiresAt = map["expiresAt"] ?: "",
                reason = map["reason"] ?: "",
            )
    }
}
