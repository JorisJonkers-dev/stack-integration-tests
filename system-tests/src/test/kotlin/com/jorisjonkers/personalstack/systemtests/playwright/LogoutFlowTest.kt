package com.jorisjonkers.personalstack.systemtests.playwright

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class LogoutFlowTest : PlaywrightTestBase() {
    private fun logoutFromAppUi(username: String) {
        page.locator("button:has-text('$username')").first().click()
        assertThat(page.locator("button:has-text('Logout')").first()).isVisible()
        page.locator("button:has-text('Logout')").first().click()
        page.waitForURL(
            { it.startsWith(APP_UI_URL) },
            Page.WaitForURLOptions().setTimeout(10000.0),
        )
        page.waitForLoadState()
    }

    @Test
    fun `logout from app-ui clears session`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        // Verify logged in
        assertThat(page.locator("body")).containsText(user.username)

        logoutFromAppUi(user.username)

        // Navigate back and verify logged out
        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        assertThat(page.locator("body")).not().containsText(user.username)
    }

    @Test
    fun `after logout app-ui shows unauthenticated state`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        logoutFromAppUi(user.username)
        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        // Apps grid should not be visible
        assertThat(page.locator("text=My Apps")).not().isVisible()
    }

    @Test
    fun `after logout assistant-ui redirects to login`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        // Verify we can access assistant-ui
        page.navigate("$ASSISTANT_UI_URL/chat")
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        // Clear cookies to simulate logout
        context.clearCookies()

        // Navigate to assistant-ui again
        page.navigate("$ASSISTANT_UI_URL/chat")

        page.waitForURL(
            { it.contains("login") },
            Page.WaitForURLOptions().setTimeout(10000.0),
        )
    }

    @Test
    fun `after logout protected pages require re-login`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$APP_UI_URL/apps")
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        // Clear cookies to simulate logout
        context.clearCookies()

        page.navigate("$APP_UI_URL/apps")
        page.waitForTimeout(3000.0)

        // Should be redirected away from /apps
        val url = page.url()
        org.assertj.core.api.Assertions
            .assertThat(url)
            .doesNotEndWith("/apps")
    }
}
