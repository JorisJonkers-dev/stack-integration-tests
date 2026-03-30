package com.jorisjonkers.personalstack.systemtests.playwright

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class ProtectedPageRedirectTest : PlaywrightTestBase() {
    @Test
    fun `auth-ui totp-setup redirects unauthenticated to login`() {
        page.navigate("$AUTH_UI_URL/totp-setup")

        page.waitForTimeout(3000.0)
        assertThat(page.locator("body")).containsText("Sign in")
    }

    @Test
    fun `app-ui apps page redirects unauthenticated to home`() {
        page.navigate("$APP_UI_URL/apps")
        page.waitForTimeout(3000.0)

        val url = page.url()
        org.assertj.core.api.Assertions
            .assertThat(url)
            .doesNotEndWith("/apps")
    }

    @Test
    fun `app-ui admin page redirects unauthenticated to home`() {
        page.navigate("$APP_UI_URL/admin")
        page.waitForTimeout(3000.0)

        val url = page.url()
        org.assertj.core.api.Assertions
            .assertThat(url)
            .doesNotEndWith("/admin")
    }

    @Test
    fun `assistant-ui chat redirects to auth-ui login`() {
        page.navigate("$ASSISTANT_UI_URL/chat")

        page.waitForURL(
            { it.contains("login") },
            Page.WaitForURLOptions().setTimeout(10000.0),
        )
    }

    @Test
    fun `mail protected service redirects to login`() {
        page.navigate("https://stalwart.jorisjonkers.test")

        page.waitForURL(
            { it.contains("login") },
            Page.WaitForURLOptions().setTimeout(10000.0),
        )
    }

    @Test
    fun `mail protected service accessible after login as admin`() {
        loginAsAdmin()

        page.navigate("https://stalwart.jorisjonkers.test")
        page.waitForLoadState()
        page.waitForTimeout(5000.0)

        // Should NOT be redirected to login
        val url = page.url()
        org.assertj.core.api.Assertions
            .assertThat(url)
            .doesNotContain("auth.jorisjonkers.test/login")
    }
}
