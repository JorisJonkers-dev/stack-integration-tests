package com.jorisjonkers.personalstack.systemtests.playwright

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

/**
 * The `/apps` route has existed since the MyApps feature landed, but
 * is only reachable through the UI via the profile dropdown (the
 * standalone navbar link was folded into it). These tests pin the
 * contract: the entry is reachable when signed in, absent otherwise,
 * and clicking it lands on the grid.
 */
@Tag("system")
class MyAppsNavTest : PlaywrightTestBase() {
    @Test
    fun `authenticated user sees My Apps entry in the profile dropdown`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        page.locator("[data-testid='nav-profile']").click()
        assertThat(page.locator("[data-testid='dropdown-my-apps']")).isVisible()
    }

    @Test
    fun `unauthenticated user does not see the profile dropdown`() {
        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        assertThat(page.locator("[data-testid='nav-profile']")).not().isVisible()
        assertThat(page.locator("[data-testid='dropdown-my-apps']")).not().isVisible()
    }

    @Test
    fun `clicking My Apps navigates to the apps grid`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        page.locator("[data-testid='nav-profile']").click()
        page.locator("[data-testid='dropdown-my-apps']").click()

        page.waitForURL(
            { it.endsWith("/apps") },
            Page.WaitForURLOptions().setTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS),
        )
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        // AppsGrid renders the section title "My Apps". A user with no
        // permissions sees the fallback copy instead, but the container
        // is still there and the URL is the authoritative check.
        assertThat(page).hasURL(Pattern.compile(".*/apps$"))
    }
}
