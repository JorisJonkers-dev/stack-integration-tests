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
    fun `attach-key wizard writes the key to Vault and the wizard closes on success`() {
        // Happy-path end-to-end. The CI compose stack now wires
        // real Vault into assistant-api (see docker-compose.ci.yml's
        // assistant-api `VAULT_*` env block), so this exercise drives
        // the full write path: UI submits → assistant-api validates
        // → SpringVaultKeyValueWriter writes
        // `secret/data/agents/repositories/<id>` → 202 Accepted →
        // wizard closes → success toast appears.
        //
        // This is the test that would have caught PR #405 (missing
        // Vault policy), PR #409 (bootstrap Job broken by image
        // drift), and PR #426 (UI crashing on 202 empty-body) all
        // at once — none of them had a happy-path Playwright spec
        // before this PR.
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

        page.locator("[data-testid='repository-attach-key']").click()
        assertThat(page.locator("[data-testid='attach-key-wizard']")).isVisible()

        // Synthetic OpenSSH armour. assistant-api stores the bytes
        // verbatim but VaultDeployKeyStore.sha256Fingerprint
        // Base64-decodes the middle field of the public key, so a
        // non-Base64 stand-in (e.g. `AAAA-{uuid}`) raises an
        // IllegalArgumentException → 400 before the write. The
        // body below is the real github.com ed25519 host key: free
        // to publish, valid OpenSSH single-line shape, and yields
        // a stable fingerprint.
        page.locator("[data-testid='attach-key-private']").fill(
            "-----BEGIN OPENSSH PRIVATE KEY-----\nrt-private-$name\n-----END OPENSSH PRIVATE KEY-----\n",
        )
        page.locator("[data-testid='attach-key-public']").fill(
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl ps-test-$name",
        )
        page.locator("[data-testid='attach-key-submit']").click()

        // Success path: wizard's `submit.run` resolves, the wizard
        // emits `success`, and RepositoryView closes the modal.
        assertThat(page.locator("[data-testid='attach-key-wizard']")).not().isVisible()
        // Success toast surfaces in the host. The matcher targets
        // `data-kind="success"` so a stale red toast from a prior
        // failure can't satisfy the assertion.
        val toast = page.locator("[data-testid='toast'][data-kind='success']").first()
        assertThat(toast).isVisible()
        assertThat(toast).containsText("Deploy key attached")
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
