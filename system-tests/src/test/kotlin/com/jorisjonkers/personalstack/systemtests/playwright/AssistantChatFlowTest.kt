package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class AssistantChatFlowTest : PlaywrightTestBase() {
    private fun conversationButtons() = page.locator("aside nav button")

    @Test
    fun `chat page shows empty state`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        page.navigate("$ASSISTANT_UI_URL/chat")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("body")).containsText("conversation")
    }

    @Test
    fun `create new conversation`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        page.navigate("$ASSISTANT_UI_URL/chat")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        page.locator("button:has-text('New')").click()
        page.waitForTimeout(2000.0)

        // New conversation should appear in the sidebar
        assertThat(conversationButtons().first()).isVisible()
    }

    @Test
    fun `send message shows in chat`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        page.navigate("$ASSISTANT_UI_URL/chat")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        // Create conversation
        page.locator("button:has-text('New')").click()
        page.waitForTimeout(2000.0)

        // Send a message
        val messageText = "Hello from Playwright test"
        page.locator("textarea").fill(messageText)
        page.locator("button:has-text('Send')").click()

        page.waitForTimeout(3000.0)
        assertThat(page.locator("body")).containsText(messageText)
    }

    @Test
    fun `switch between conversations`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        page.navigate("$ASSISTANT_UI_URL/chat")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        // Create first conversation
        page.locator("button:has-text('New')").click()
        page.waitForTimeout(2000.0)
        page.locator("textarea").fill("Message in first")
        page.locator("button:has-text('Send')").click()
        page.waitForTimeout(3000.0)

        // Create second conversation
        page.locator("button:has-text('New')").click()
        page.waitForTimeout(2000.0)
        page.locator("textarea").fill("Message in second")
        page.locator("button:has-text('Send')").click()
        page.waitForTimeout(3000.0)

        // Click first conversation in sidebar
        val conversations = conversationButtons()
        if (conversations.count() >= 2) {
            conversations.nth(1).click()
            page.waitForTimeout(2000.0)

            assertThat(page.locator("body")).containsText("Message in first")
        }
    }

    @Test
    fun `message persists after page reload`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "ASSISTANT")
        loginViaApi(user)

        page.navigate("$ASSISTANT_UI_URL/chat")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        // Create and send message
        page.locator("button:has-text('New')").click()
        page.waitForTimeout(2000.0)

        val messageText = "Persistent message ${System.currentTimeMillis()}"
        page.locator("textarea").fill(messageText)
        page.locator("button:has-text('Send')").click()
        page.waitForTimeout(3000.0)

        // Reload page
        page.reload(Page.ReloadOptions().setTimeout(15000.0))
        page.waitForTimeout(3000.0)

        // Select the conversation again
        val conversations = conversationButtons()
        if (conversations.count() > 0) {
            conversations.first().click()
            page.waitForTimeout(2000.0)
        }

        assertThat(page.locator("body")).containsText(messageText)
    }
}
