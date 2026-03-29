package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

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
        loginViaApi(user)

        page.navigate("$APP_UI_URL/apps")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("body")).containsText("Grafana")
        assertThat(page.locator("body")).containsText("Vault")
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
    fun `apps grid only visible when authenticated`() {
        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        assertThat(page.locator("text=My Apps")).not().isVisible()
    }

    @Test
    fun `authenticated user sees apps grid on home page`() {
        val user = registerAndConfirm()
        TestHelper.grantServicePermission(user.username, "GRAFANA")
        loginViaApi(user)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("text=My Apps")).isVisible()
    }
}
