package com.jorisjonkers.personalstack.systemtests

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
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
@Tag("system")
class QuarantineContractTest {
    private val manifest: QuarantineManifest =
        QuarantineManifest.load("../../deploy-harness/config/quarantined-tests.yaml")

    @Test
    fun `all quarantine entries have owner approval`() {
        for (entry in manifest.entries) {
            assertThat(entry.ownerApproved)
                .describedAs("Quarantine entry ${entry.testClass} must be owner-approved (ownerApproved: true)")
                .isTrue()
        }
    }

    @Test
    fun `all quarantine entries have a valid GitHub issue URL`() {
        val issuePrefix = "https://github.com/JorisJonkers-dev/"
        for (entry in manifest.entries) {
            assertThat(entry.issueUrl)
                .describedAs("Quarantine entry ${entry.testClass} issue URL must start with $issuePrefix")
                .startsWith(issuePrefix)
        }
    }

    @Test
    fun `all quarantine entries have a non-expired expiresAt date`() {
        val today = LocalDate.now()
        for (entry in manifest.entries) {
            val expires = LocalDate.parse(entry.expiresAt)
            assertThat(expires)
                .describedAs("Quarantine entry ${entry.testClass} expired on ${entry.expiresAt}; renew or remove")
                .isAfter(today)
        }
    }
}
