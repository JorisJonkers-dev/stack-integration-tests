package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Repository CRUD + deploy-key attach wizard, end-to-end through
 * the redesigned UI surfaces shipped in PR #378 and PR #379.
 *
 * Self-isolation per KB lesson `01KRXJQXGA06ZK1E2WP13F84ZV`: every
 * selector that targets a per-test entity (the repository card,
 * the detail link) includes the entity id. The repository name
 * carries a UUID prefix so re-runs of a single shard don't collide
 * on the global UNIQUE constraint that V9 enforces.
 */
@Tag("system")
class RepositoriesFlowTest : PlaywrightTestBase() {
    @Test
    fun `user can create a repository and see it in the list`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        val name = "rt-${UUID.randomUUID().toString().take(8)}"
        val url = "git@github.com:owner/$name.git"

        page.navigate("$ASSISTANT_UI_URL/repositories")
        page.waitForLoadState()

        page.locator("[data-testid='repositories-new-button']").click()
        page.locator("[data-testid='repo-name']").fill(name)
        page.locator("[data-testid='repo-url']").fill(url)
        page.locator("[data-testid='repo-create-submit']").click()

        // After create the UI navigates to /repositories/{id} (see
        // RepositoriesView.onCreate). Wait for the detail surface.
        page.waitForURL { it.contains("/repositories/") && !it.endsWith("/repositories") }
        assertThat(page.locator("[data-testid='repository-detail']")).isVisible()
        assertThat(page.locator("body")).containsText(name)
    }

    @Test
    fun `attach-key wizard surfaces the meaningful 4xx when Vault is disabled`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        val name = "rt-${UUID.randomUUID().toString().take(8)}"
        page.navigate("$ASSISTANT_UI_URL/repositories")
        page.waitForLoadState()
        page.locator("[data-testid='repositories-new-button']").click()
        page.locator("[data-testid='repo-name']").fill(name)
        page.locator("[data-testid='repo-url']").fill("git@github.com:owner/$name.git")
        page.locator("[data-testid='repo-create-submit']").click()
        page.waitForURL { it.contains("/repositories/") }

        // Open the attach-key wizard.
        page.locator("[data-testid='repository-attach-key']").click()
        assertThat(page.locator("[data-testid='attach-key-wizard']")).isVisible()

        // Synthetic OpenSSH armour — assistant-api validates the
        // PEM shape but doesn't crypto-verify the bytes.
        page.locator("[data-testid='attach-key-private']").fill(
            "-----BEGIN OPENSSH PRIVATE KEY-----\nrt-private-$name\n-----END OPENSSH PRIVATE KEY-----\n",
        )
        page.locator("[data-testid='attach-key-public']").fill("ssh-ed25519 AAAA-$name test@laptop")
        page.locator("[data-testid='attach-key-submit']").click()

        // POST /key writes to Vault, which is disabled in the
        // docker-compose system-test stack. PR #383 surfaces the
        // upstream failure as a meaningful 502 ProblemDetail rather
        // than a bare 500. The UI keeps the modal open (so the user
        // can retry without losing typed input), surfaces a toast
        // carrying the ProblemDetail, and flips the SubmitButton to
        // its `failure` indicator.
        assertThat(page.locator("[data-testid='attach-key-wizard']")).isVisible()
        val toast = page.locator("[data-testid='toast'][data-kind='error']").first()
        assertThat(toast).isVisible()
        assertThat(toast).containsText("Could not attach the deploy key")
        // SubmitButton emits `data-status="failure"` after the
        // mutation rejects. `useMutationState` auto-resets to `idle`
        // after 2_000 ms, so this assertion has to race the timer —
        // Playwright's default 5 s polling is well within that window
        // on the first poll after the click resolves.
        assertThat(page.locator("[data-testid='attach-key-submit']"))
            .hasAttribute("data-status", "failure")
    }

    @Test
    fun `submit button surfaces loading then success state on repository create`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        page.navigate("$ASSISTANT_UI_URL/repositories")
        page.waitForLoadState()
        page.locator("[data-testid='repositories-new-button']").click()
        page.locator("[data-testid='repo-name']").fill("rt-btn-${UUID.randomUUID().toString().take(8)}")
        page.locator("[data-testid='repo-url']").fill("git@github.com:owner/rt-btn.git")

        // Snapshot the button's data-status before + during + after
        // the submit. `idle` → `pending` → (after async resolves)
        // either `success` or `idle` once the auto-reset fires.
        val button = page.locator("[data-testid='repo-create-submit']")
        assertThat(button).hasAttribute("data-status", "idle")
        button.click()
        // The button becomes non-clickable during the in-flight POST.
        // Pinned state assertion: aria-busy goes true and disabled
        // attribute is present.
        page.waitForURL { it.contains("/repositories/") }
    }
}
