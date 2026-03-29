package com.jorisjonkers.personalstack.systemtests.playwright

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class LogoutFlowTest : PlaywrightTestBase() {
    @Test
    fun `logout from app-ui clears session`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        // Verify logged in
        assertThat(page.locator("body")).containsText(user.username)

        // Click username/logout button
        page.locator("button:has-text('${user.username}'), a:has-text('${user.username}')").first().click()
        page.waitForTimeout(3000.0)

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

        // Logout
        page.locator("button:has-text('${user.username}'), a:has-text('${user.username}')").first().click()
        page.waitForTimeout(3000.0)
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
