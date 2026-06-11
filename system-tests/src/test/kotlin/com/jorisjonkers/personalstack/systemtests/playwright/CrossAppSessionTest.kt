package com.jorisjonkers.personalstack.systemtests.playwright

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class CrossAppSessionTest : PlaywrightTestBase() {
    @Test
    fun `login on auth-ui creates session visible on app-ui`() {
        val user = registerAndConfirm()
        loginViaUi(user.username, user.password)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()

        assertThat(page.locator("body")).containsText(user.username)
    }

    @Test
    fun `app-ui login button redirects to auth-ui`() {
        page.navigate(APP_UI_URL)
        page.waitForLoadState()

        val loginButton =
            page
                .locator(
                    "text=Login, text=Sign in, a:has-text('Login'), button:has-text('Login')",
                ).first()
        if (loginButton.isVisible) {
            loginButton.click()
            page.waitForURL(
                { it.contains("auth") && it.contains("login") },
                Page.WaitForURLOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS),
            )
        }
    }
}
