package com.jorisjonkers.personalstack.systemtests.playwright

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class AuthTotpFlowTest : PlaywrightTestBase() {
    @Test
    fun `totp setup page shows QR code and secret`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$AUTH_UI_URL/totp-setup")
        page.waitForSelector("canvas", Page.WaitForSelectorOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS))

        assertThat(page.locator("canvas")).isVisible()
        assertThat(page.locator("body")).containsText("two-factor")
    }

    @Test
    fun `totp verification with valid code succeeds`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$AUTH_UI_URL/totp-setup")
        page.waitForSelector("canvas", Page.WaitForSelectorOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS))

        // Extract secret from the page (shown in <code> or <details>)
        page.locator("details summary").click()
        val secret = page.locator("code").textContent().trim()

        val code = generateTotpCode(secret)
        page.locator("#totp-code").fill(code)
        page.locator("button[type='submit']").click()

        page.waitForURL(
            { !it.contains("/totp-setup") },
            Page.WaitForURLOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS),
        )
    }

    @Test
    fun `totp verification with invalid code shows error`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$AUTH_UI_URL/totp-setup")
        page.waitForSelector("canvas", Page.WaitForSelectorOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS))

        page.locator("#totp-code").fill("000000")
        page.locator("button[type='submit']").click()

        page.waitForSelector(
            ".text-red-400, .text-red-500",
            Page.WaitForSelectorOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS),
        )
        assertThat(page.locator(".text-red-400, .text-red-500").first()).isVisible()
    }

    @Test
    fun `login with TOTP shows code input after credentials`() {
        val user = registerAndConfirm()
        enrollTotpViaApi(user)

        page.navigate("$AUTH_UI_URL/login")
        page.locator("#username").fill(user.username)
        page.locator("#password").fill(user.password)
        page.locator("button[type='submit']").click()

        page.waitForSelector("#totp-code", Page.WaitForSelectorOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS))
        assertThat(page.locator("#totp-code")).isVisible()
        assertThat(page.locator("body")).containsText("Two-factor")
    }

    @Test
    fun `login with valid TOTP code succeeds`() {
        val user = registerAndConfirm()
        val secret = enrollTotpViaApi(user)

        page.navigate("$AUTH_UI_URL/login")
        page.locator("#username").fill(user.username)
        page.locator("#password").fill(user.password)
        page.locator("button[type='submit']").click()

        page.waitForSelector("#totp-code", Page.WaitForSelectorOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS))
        page.locator("#totp-code").fill(generateTotpCode(secret))
        page.locator("button[type='submit']").click()

        page.waitForURL(
            { !it.contains("/login") },
            Page.WaitForURLOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS),
        )
    }

    @Test
    fun `totp setup page requires authentication`() {
        page.navigate("$AUTH_UI_URL/totp-setup")

        page.waitForURL(
            { it.contains("/login") },
            Page.WaitForURLOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS),
        )
    }
}
