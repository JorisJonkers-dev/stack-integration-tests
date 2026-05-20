package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Chat tab of the redesigned /sessions surface. Drives the new
 * chat_sessions API path (PR #377) — no Pod is provisioned, no
 * agent runtime is involved.
 *
 * The Scratch + Workspace tab specs land in a follow-up alongside
 * the stub-orchestrator wiring (this PR's `SPRING_PROFILES_ACTIVE=
 * system-test` change makes the orchestrator side a no-op, but the
 * accompanying Playwright flows need the redesigned views to render
 * cleanly in the docker-compose stack first).
 */
@Tag("system")
class SessionsChatFlowTest : PlaywrightTestBase() {
    @Test
    fun `sessions view renders the three-tab host`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        page.navigate("$ASSISTANT_UI_URL/sessions")
        page.waitForLoadState()

        assertThat(page.locator("[data-testid='sessions-tab-chat']")).isVisible()
        assertThat(page.locator("[data-testid='sessions-tab-scratch']")).isVisible()
        assertThat(page.locator("[data-testid='sessions-tab-workspace']")).isVisible()
    }

    @Test
    fun `user can start a chat session and send a message`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        val title = "ct-${UUID.randomUUID().toString().take(8)}"

        page.navigate("$ASSISTANT_UI_URL/sessions")
        page.waitForLoadState()

        // Sessions defaults to the Chat tab. Start a fresh session
        // with a unique title so re-runs of this shard pick the
        // right card in the sidebar.
        page.locator("[data-testid='chat-new-title']").fill(title)
        page.locator("[data-testid='chat-new-submit']").click()

        // The sidebar list flips to include the new session; the
        // store auto-opens it after `start()` resolves.
        assertThat(page.locator("[data-testid='chat-sessions-list']")).containsText(title)

        // Send a message; the body appears in the transcript region.
        val body = "hello-$title"
        page.locator("[data-testid='chat-input']").fill(body)
        page.locator("[data-testid='chat-send-submit']").click()

        assertThat(page.locator("[data-testid='chat-message-list']")).containsText(body)
    }

    @Test
    fun `nav-sessions points at the redesigned entry`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        page.navigate(ASSISTANT_UI_URL)
        page.waitForLoadState()
        page.locator("[data-testid='nav-sessions']").click()
        page.waitForURL { it.endsWith("/sessions") }
    }
}
