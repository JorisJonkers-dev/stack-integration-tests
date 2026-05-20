package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class AssistantChatFlowTest : PlaywrightTestBase() {
    private fun openChatTab() {
        page.navigate("$ASSISTANT_UI_URL/sessions")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)
        page.locator("[data-testid='sessions-tab-chat']").click()
        page.waitForTimeout(1000.0)
    }

    private fun startNewChat(title: String? = null) {
        if (title != null) {
            page.locator("[data-testid='chat-new-title']").fill(title)
        }
        page.locator("[data-testid='chat-new-submit']").click()
        page.waitForTimeout(2000.0)
    }

    @Test
    fun `chat tab shows empty state`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        openChatTab()

        assertThat(page.locator("[data-testid='chat-tab']")).isVisible()
        assertThat(page.locator("body")).containsText("No chats yet")
    }

    @Test
    fun `create new chat session`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        openChatTab()
        startNewChat("first chat")

        // The new session should be selected and the detail card visible.
        assertThat(page.locator("[data-testid='chat-sessions-list']")).isVisible()
        assertThat(page.locator("[data-testid='chat-sessions-list']")).containsText("first chat")
    }

    @Test
    fun `send message shows in chat`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        openChatTab()
        startNewChat()

        val messageText = "Hello from Playwright test"
        page.locator("[data-testid='chat-input']").fill(messageText)
        page.locator("[data-testid='chat-send-submit']").click()

        page.waitForTimeout(3000.0)
        assertThat(page.locator("[data-testid='chat-message-list']")).containsText(messageText)
    }

    @Test
    fun `switch between chat sessions`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        openChatTab()

        startNewChat("first")
        page.locator("[data-testid='chat-input']").fill("Message in first")
        page.locator("[data-testid='chat-send-submit']").click()
        page.waitForTimeout(3000.0)

        startNewChat("second")
        page.locator("[data-testid='chat-input']").fill("Message in second")
        page.locator("[data-testid='chat-send-submit']").click()
        page.waitForTimeout(3000.0)

        // Click first session card via its title text inside the sidebar list.
        val firstSession = page.locator("[data-testid='chat-sessions-list'] button:has-text('first')").first()
        if (firstSession.count() > 0) {
            firstSession.click()
            page.waitForTimeout(2000.0)

            assertThat(page.locator("[data-testid='chat-message-list']")).containsText("Message in first")
        }
    }

    @Test
    fun `chat session persists after page reload`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        openChatTab()
        startNewChat("persistent")

        val messageText = "Persistent message ${System.currentTimeMillis()}"
        page.locator("[data-testid='chat-input']").fill(messageText)
        page.locator("[data-testid='chat-send-submit']").click()
        page.waitForTimeout(3000.0)

        page.reload(Page.ReloadOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS))
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        // Re-open the Chat tab after the reload — the default tab is Chat
        // anyway, but the explicit click survives any future default flip.
        page.locator("[data-testid='sessions-tab-chat']").click()
        page.waitForTimeout(2000.0)

        // The most-recent session auto-opens. If the test created multiple,
        // the persistent one is the latest.
        assertThat(page.locator("[data-testid='chat-message-list']")).containsText(messageText)
    }
}
