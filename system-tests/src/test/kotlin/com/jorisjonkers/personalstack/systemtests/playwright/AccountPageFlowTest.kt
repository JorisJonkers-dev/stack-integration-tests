package com.jorisjonkers.personalstack.systemtests.playwright

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * End-to-end coverage for the app-ui `/account` page. Previously the
 * page 500'd on load because `GET /api/v1/users/me` was never wired
 * on the server side — which meant no regression test could even
 * reach the form. With the handler added, the happy paths (load,
 * update profile, change password) + the two meaningful error cases
 * (wrong current password, re-login with rotated password) each get
 * a test.
 */
@Tag("system")
class AccountPageFlowTest : PlaywrightTestBase() {
    @Test
    fun `account page loads with the signed-in user's profile`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$APP_UI_URL/account")
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        // Username + email panels are uneditable — they prove the GET came
        // back with real data rather than the skeleton placeholder.
        assertThat(page.locator("[data-testid='account-username']")).containsText(user.username)
        assertThat(page.locator("[data-testid='account-email']")).containsText("${user.username}@")
        assertThat(page.locator("#firstName")).isVisible()
        assertThat(page.locator("#lastName")).isVisible()
    }

    @Test
    fun `updating first and last name persists after reload`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$APP_UI_URL/account")
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        val newFirst = "Renamed${user.username.takeLast(4)}"
        val newLast = "Afterwards"
        page.locator("#firstName").fill(newFirst)
        page.locator("#lastName").fill(newLast)

        page.locator("[data-testid='account-save-profile']").click()

        // Inline toast confirms the PATCH returned 200. Auto-dismisses
        // after 3s — we assert visibility before the timer fires.
        assertThat(page.locator("[data-testid='account-profile-success']"))
            .containsText("Profile updated")

        // Full reload proves the write actually persisted; otherwise the
        // assertion could pass on a stale in-memory Vue ref.
        page.navigate("$APP_UI_URL/account")
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        assertThat(page.locator("#firstName")).hasValue(newFirst)
        assertThat(page.locator("#lastName")).hasValue(newLast)
    }

    @Test
    fun `save button stays disabled until a profile field changes`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$APP_UI_URL/account")
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        assertThat(page.locator("[data-testid='account-save-profile']")).isDisabled()

        page.locator("#firstName").fill("DirtyValue")
        assertThat(page.locator("[data-testid='account-save-profile']")).isEnabled()
    }

    @Test
    fun `changing password with wrong current password shows an error`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$APP_UI_URL/account")
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        page.locator("#currentPassword").fill("definitely-wrong-password")
        page.locator("#newPassword").fill("NewPassword123!")
        page.locator("#confirmPassword").fill("NewPassword123!")

        page.locator("[data-testid='account-change-password']").click()

        assertThat(page.locator("[data-testid='account-password-error']"))
            .containsText("current password")
    }

    @Test
    fun `changing password succeeds and re-login works with the new password`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$APP_UI_URL/account")
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        val newPassword = "RotatedPassword9!"
        page.locator("#currentPassword").fill(user.password)
        page.locator("#newPassword").fill(newPassword)
        page.locator("#confirmPassword").fill(newPassword)

        page.locator("[data-testid='account-change-password']").click()

        assertThat(page.locator("[data-testid='account-password-success']"))
            .containsText("Password changed")

        // Drop the session so the login form actually authenticates anew.
        context.clearCookies()

        page.navigate("$AUTH_UI_URL/login")
        page.locator("#username").fill(user.username)
        page.locator("#password").fill(newPassword)
        page.locator("button[type='submit']").click()

        // Success == router leaves the /login URL.
        page.waitForURL(
            { !it.contains("/login") },
            Page.WaitForURLOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS),
        )
    }
}
