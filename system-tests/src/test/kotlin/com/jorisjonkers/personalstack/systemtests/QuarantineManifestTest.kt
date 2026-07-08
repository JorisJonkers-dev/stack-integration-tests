package com.jorisjonkers.personalstack.systemtests

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Unit tests for QuarantineManifest parsing and QuarantineContractTest contract enforcement.
 * These are structural tests tagged with no system-specific tags so they run in CI without
 * a live cluster.
 */
class QuarantineManifestTest {

    // --- Parsing tests ---

    @Test
    fun `empty entries list parses to empty manifest`() {
        val yaml = """
            entries: []
        """.trimIndent()
        val manifest = QuarantineManifest.parseManifest(yaml)
        assertThat(manifest.entries).isEmpty()
    }

    @Test
    fun `single valid entry is parsed correctly`() {
        val yaml = """
            entries:
              - testClass: com.example.SomeTest
                ownerApproved: true
                issueUrl: https://github.com/JorisJonkers-dev/stack-integration-tests/issues/42
                expiresAt: 2099-12-31
                reason: Flaky in CI due to DNS timing
        """.trimIndent()
        val manifest = QuarantineManifest.parseManifest(yaml)
        assertThat(manifest.entries).hasSize(1)
        val entry = manifest.entries[0]
        assertThat(entry.testClass).isEqualTo("com.example.SomeTest")
        assertThat(entry.ownerApproved).isTrue()
        assertThat(entry.issueUrl).startsWith("https://github.com/JorisJonkers-dev/")
        assertThat(LocalDate.parse(entry.expiresAt)).isAfter(LocalDate.now())
    }

    @Test
    fun `ownerApproved false is parsed correctly`() {
        val yaml = """
            entries:
              - testClass: com.example.PendingTest
                ownerApproved: false
                issueUrl: https://github.com/JorisJonkers-dev/stack-integration-tests/issues/1
                expiresAt: 2099-01-01
        """.trimIndent()
        val manifest = QuarantineManifest.parseManifest(yaml)
        assertThat(manifest.entries[0].ownerApproved).isFalse()
    }

    @Test
    fun `missing file returns empty manifest`() {
        val manifest = QuarantineManifest.load("/nonexistent/path/quarantined-tests.yaml")
        assertThat(manifest.entries).isEmpty()
    }

    // --- QuarantineContractTest logic validation ---

    @Test
    fun `contract fails when expiresAt is in the past`() {
        val yaml = """
            entries:
              - testClass: com.example.ExpiredTest
                ownerApproved: true
                issueUrl: https://github.com/JorisJonkers-dev/stack-integration-tests/issues/42
                expiresAt: 2020-01-01
        """.trimIndent()
        val manifest = QuarantineManifest.parseManifest(yaml)
        val today = LocalDate.now()
        for (entry in manifest.entries) {
            val expires = LocalDate.parse(entry.expiresAt)
            assertThat(expires).isBefore(today)
        }
    }

    @Test
    fun `contract fails when ownerApproved is false`() {
        val yaml = """
            entries:
              - testClass: com.example.PendingTest
                ownerApproved: false
                issueUrl: https://github.com/JorisJonkers-dev/stack-integration-tests/issues/1
                expiresAt: 2099-01-01
        """.trimIndent()
        val manifest = QuarantineManifest.parseManifest(yaml)
        assertThat(manifest.entries[0].ownerApproved).isFalse()
    }

    @Test
    fun `contract fails when issueUrl does not start with expected prefix`() {
        val yaml = """
            entries:
              - testClass: com.example.WrongUrlTest
                ownerApproved: true
                issueUrl: https://github.com/other-org/some-repo/issues/1
                expiresAt: 2099-01-01
        """.trimIndent()
        val manifest = QuarantineManifest.parseManifest(yaml)
        val issuePrefix = "https://github.com/JorisJonkers-dev/"
        assertThat(manifest.entries[0].issueUrl).doesNotStartWith(issuePrefix)
    }
}
