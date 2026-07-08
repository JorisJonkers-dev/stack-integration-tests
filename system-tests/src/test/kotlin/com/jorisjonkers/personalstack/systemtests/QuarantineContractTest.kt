package com.jorisjonkers.personalstack.systemtests

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Contract test for the quarantine manifest.
 * Enforces that every quarantine entry:
 *  - Has an owner-approved flag set to true
 *  - Has a valid GitHub issue URL under JorisJonkers-dev
 *  - Has not expired
 *
 * This test is tagged "system" so it runs during the normal (non-quarantined)
 * system test suite. The quarantine manifest file itself is CODEOWNERS-gated
 * to require platform-owners approval for any change.
 */
@org.junit.jupiter.api.Tag("system")
class QuarantineContractTest {

    private val manifest: QuarantineManifest =
        QuarantineManifest.load("../../deploy-harness/config/quarantined-tests.yaml")

    @Test
    fun `all quarantine entries have owner approval`() {
        for (entry in manifest.entries) {
            assertThat(entry.ownerApproved)
                .withFailMessage(
                    "Quarantine entry ${entry.testClass} is not owner-approved. " +
                        "Set ownerApproved: true after a platform-owner reviews the quarantine rationale.",
                )
                .isTrue()
        }
    }

    @Test
    fun `all quarantine entries have a valid GitHub issue URL`() {
        val issuePrefix = "https://github.com/JorisJonkers-dev/"
        for (entry in manifest.entries) {
            assertThat(entry.issueUrl)
                .withFailMessage(
                    "Quarantine entry ${entry.testClass} issue URL '${entry.issueUrl}' " +
                        "must start with $issuePrefix",
                )
                .startsWith(issuePrefix)
        }
    }

    @Test
    fun `all quarantine entries have a non-expired expiresAt date`() {
        val today = LocalDate.now()
        for (entry in manifest.entries) {
            val expires = LocalDate.parse(entry.expiresAt)
            assertThat(expires)
                .withFailMessage(
                    "Quarantine entry ${entry.testClass} expired on ${entry.expiresAt} " +
                        "(today: $today). Renew or remove the quarantine entry.",
                )
                .isAfter(today)
        }
    }
}
