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
    fun `login on auth-ui creates session visible on assistant-ui`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$ASSISTANT_UI_URL/chat")
        page.waitForLoadState()

        // Should NOT be redirected to login — chat page should load
        page.waitForTimeout(3000.0)
        assertThat(page.locator("body")).not().containsText("Sign in")
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
                Page.WaitForURLOptions().setTimeout(10000.0),
            )
        }
    }

    @Test
    fun `assistant-ui redirects to auth-ui when unauthenticated`() {
        page.navigate("$ASSISTANT_UI_URL/chat")

        page.waitForURL(
            { it.contains("auth") && it.contains("login") },
            Page.WaitForURLOptions().setTimeout(10000.0),
        )
    }

    @Test
    fun `after login redirect returns to original app`() {
        // Navigate to assistant-ui while unauthenticated — should redirect to auth login
        page.navigate("$ASSISTANT_UI_URL/chat")
        page.waitForURL(
            { it.contains("login") },
            Page.WaitForURLOptions().setTimeout(10000.0),
        )

        // Login on auth-ui
        val user = registerAndConfirm()
        page.locator("#username").fill(user.username)
        page.locator("#password").fill(user.password)
        page.locator("button[type='submit']").click()

        // Should eventually end up back at assistant-ui
        page.waitForURL(
            { it.contains("assistant") || it.contains("chat") },
            Page.WaitForURLOptions().setTimeout(20000.0),
        )
    }
}
