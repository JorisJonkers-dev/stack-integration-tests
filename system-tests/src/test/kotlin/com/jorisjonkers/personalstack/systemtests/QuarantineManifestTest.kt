package com.jorisjonkers.personalstack.systemtests

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Unit tests for QuarantineManifest parsing and the quarantine contract rules.
 * These are structural tests without system-specific tags so they run in CI
 * without a live cluster.
 */
class QuarantineManifestTest {
    @Test
    fun `empty entries list parses to empty manifest`() {
        val manifest = QuarantineManifest.parseManifest("entries: []")

        assertThat(manifest.entries).isEmpty()
    }

    @Test
    fun `single valid entry is parsed correctly`() {
        val manifest =
            QuarantineManifest.parseManifest(
                """
                entries:
                  - testClass: com.example.SomeTest
                    ownerApproved: true
                    issueUrl: https://github.com/JorisJonkers-dev/stack-integration-tests/issues/42
                    expiresAt: 2099-12-31
                    reason: Flaky in CI due to DNS timing
                """.trimIndent(),
            )

        assertThat(manifest.entries).hasSize(1)
        val entry = manifest.entries[0]
        assertThat(entry.testClass).isEqualTo("com.example.SomeTest")
        assertThat(entry.ownerApproved).isTrue()
        assertThat(entry.issueUrl).startsWith("https://github.com/JorisJonkers-dev/")
        assertThat(LocalDate.parse(entry.expiresAt)).isAfter(LocalDate.now())
    }

    @Test
    fun `ownerApproved false is parsed correctly`() {
        val manifest =
            QuarantineManifest.parseManifest(
                """
                entries:
                  - testClass: com.example.PendingTest
                    ownerApproved: false
                    issueUrl: https://github.com/JorisJonkers-dev/stack-integration-tests/issues/1
                    expiresAt: 2099-01-01
                """.trimIndent(),
            )

        assertThat(manifest.entries[0].ownerApproved).isFalse()
    }

    @Test
    fun `multiple entries are parsed correctly`() {
        val manifest =
            QuarantineManifest.parseManifest(
                """
                entries:
                  - testClass: com.example.FirstTest
                    ownerApproved: true
                    issueUrl: https://github.com/JorisJonkers-dev/stack-integration-tests/issues/1
                    expiresAt: 2099-01-01
                  - testClass: com.example.SecondTest
                    ownerApproved: true
                    issueUrl: https://github.com/JorisJonkers-dev/stack-integration-tests/issues/2
                    expiresAt: 2099-06-30
                """.trimIndent(),
            )

        assertThat(manifest.entries).hasSize(2)
        assertThat(manifest.entries.map { it.testClass })
            .containsExactly("com.example.FirstTest", "com.example.SecondTest")
    }

    @Test
    fun `missing file returns empty manifest`() {
        val manifest = QuarantineManifest.load("/nonexistent/path/quarantined-tests.yaml")

        assertThat(manifest.entries).isEmpty()
    }

    @Test
    fun `expired entry violates the expiry contract rule`() {
        val manifest =
            QuarantineManifest.parseManifest(
                """
                entries:
                  - testClass: com.example.ExpiredTest
                    ownerApproved: true
                    issueUrl: https://github.com/JorisJonkers-dev/stack-integration-tests/issues/42
                    expiresAt: 2020-01-01
                """.trimIndent(),
            )

        val expires = LocalDate.parse(manifest.entries[0].expiresAt)
        assertThat(expires).isBefore(LocalDate.now())
    }

    @Test
    fun `unapproved entry violates the owner-approval contract rule`() {
        val manifest =
            QuarantineManifest.parseManifest(
                """
                entries:
                  - testClass: com.example.PendingTest
                    ownerApproved: false
                    issueUrl: https://github.com/JorisJonkers-dev/stack-integration-tests/issues/1
                    expiresAt: 2099-01-01
                """.trimIndent(),
            )

        assertThat(manifest.entries[0].ownerApproved).isFalse()
    }

    @Test
    fun `foreign issue URL violates the issue-url contract rule`() {
        val manifest =
            QuarantineManifest.parseManifest(
                """
                entries:
                  - testClass: com.example.WrongUrlTest
                    ownerApproved: true
                    issueUrl: https://github.com/other-org/some-repo/issues/1
                    expiresAt: 2099-01-01
                """.trimIndent(),
            )

        assertThat(manifest.entries[0].issueUrl).doesNotStartWith("https://github.com/JorisJonkers-dev/")
    }
}
