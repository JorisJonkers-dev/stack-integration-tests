package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat as assertThatValue

@Tag("system")
class ServicePermissionsUiTest : PlaywrightTestBase() {
    @Test
    fun `user with no permissions sees no apps on apps page`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$APP_UI_URL/apps")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("body")).containsText("No services")
    }

    @Test
    fun `user with GRAFANA permission sees grafana in apps`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "GRAFANA")
        loginViaApi(user)

        page.navigate("$APP_UI_URL/apps")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("body")).containsText("Grafana")
    }

    @Test
    fun `user with multiple permissions sees all granted services`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "GRAFANA")
        TestHelper.grantServicePermission(user.username, "VAULT")
        TestHelper.grantServicePermission(user.username, "DASHBOARD")
        loginViaApi(user)

        page.navigate("$APP_UI_URL/apps")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("body")).containsText("Grafana")
        assertThat(page.locator("body")).containsText("Vault")
        assertThat(page.locator("body")).containsText("Cluster Dashboard")
    }

    @Test
    fun `admin sees all services`() {
        loginAsAdmin()

        page.navigate("$APP_UI_URL/apps")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("body")).containsText("Grafana")
        assertThat(page.locator("body")).containsText("Vault")
    }

    @Test
    fun `apps page uses canonical service hosts and loads remote favicons`() {
        loginAsAdmin()

        page.navigate("$APP_UI_URL/apps")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        val mailCard = page.locator("a[href='https://stalwart.jorisjonkers.test/']")
        val vaultCard = page.locator("a[href='https://vault.jorisjonkers.test/']")
        val n8nCard = page.locator("a[href='https://n8n.jorisjonkers.test/']")
        val dashboardCard = page.locator("a[href='https://dashboard.jorisjonkers.test/']")

        assertThat(mailCard).isVisible()
        assertThat(vaultCard).isVisible()
        assertThat(n8nCard).isVisible()
        assertThat(dashboardCard).isVisible()
        assertThat(n8nCard.locator("img")).isVisible()

        val mailUsesFallback = mailCard.locator("span").count() > 0
        val vaultUsesFallback = vaultCard.locator("span").count() > 0
        val mailIconLoaded =
            if (mailUsesFallback) {
                false
            } else {
                mailCard.locator("img").evaluate("img => img.complete && img.naturalWidth > 0") as Boolean
            }
        val vaultIconLoaded =
            if (vaultUsesFallback) {
                false
            } else {
                vaultCard.locator("img").evaluate("img => img.complete && img.naturalWidth > 0") as Boolean
            }
        val n8nIconLoaded = n8nCard.locator("img").evaluate("img => img.complete && img.naturalWidth > 0") as Boolean

        assertThatValue(mailUsesFallback || mailIconLoaded).isTrue()
        assertThatValue(vaultUsesFallback || vaultIconLoaded).isTrue()
        assertThatValue(n8nIconLoaded).isTrue()
    }

    @Test
    fun `apps grid only visible when authenticated`() {
        navigateWithRetry(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        assertThat(page.locator("h2:has-text('My Apps')")).not().isVisible()
    }

    @Test
    fun `authenticated user reaches apps grid via the My Apps nav link`() {
        // The home page used to render `<AppsGrid>` inline for any
        // authenticated user. PR #387 dropped that — the dedicated
        // `/apps` route is the canonical surface and the home view
        // stays focused on the portfolio. This test was retargeted
        // to verify the nav link reaches the apps grid instead.
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "GRAFANA")
        loginViaApi(user)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.locator("[data-testid='nav-my-apps']").first().click()
        page.waitForURL { it.contains("/apps") }

        assertThat(page.locator("h2:has-text('My Apps')")).isVisible()
    }
}
