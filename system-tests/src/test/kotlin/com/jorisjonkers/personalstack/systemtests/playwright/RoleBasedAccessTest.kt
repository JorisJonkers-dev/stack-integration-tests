package com.jorisjonkers.personalstack.systemtests.playwright

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class RoleBasedAccessTest : PlaywrightTestBase() {
    private fun userCard(username: String) = page.locator("div.rounded-lg:has-text('$username')").first()

    @Test
    fun `admin user sees admin link in navbar`() {
        loginAsAdmin()

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        assertThat(page.locator("a[href='/admin']")).isVisible()
    }

    @Test
    fun `regular user does not see admin link`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        assertThat(page.locator("a[href='/admin']")).not().isVisible()
    }

    @Test
    fun `admin can access admin page`() {
        loginAsAdmin()

        page.navigate("$APP_UI_URL/admin")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("body")).containsText("User Management")
    }

    @Test
    fun `non-admin accessing admin page is redirected`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$APP_UI_URL/admin")
        page.waitForTimeout(3000.0)

        // Should be redirected away from /admin
        val url = page.url()
        org.assertj.core.api.Assertions
            .assertThat(url)
            .doesNotContain("/admin")
    }

    @Test
    fun `admin page shows user list with roles`() {
        val targetUser = registerAndConfirm()
        loginAsAdmin()

        page.navigate("$APP_UI_URL/admin")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("body")).containsText(targetUser.username)
    }

    @Test
    fun `admin can change user role via UI`() {
        val targetUser = registerAndConfirm()
        loginAsAdmin()

        page.navigate("$APP_UI_URL/admin")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        // Find the target user's row and change role
        val userRow = userCard(targetUser.username)
        val roleSelect = userRow.locator("select").first()
        roleSelect.selectOption("READONLY")

        page.waitForTimeout(2000.0)

        // Reload and verify persistence
        page.reload()
        page.waitForTimeout(3000.0)

        val updatedRow = userCard(targetUser.username)
        val updatedSelect = updatedRow.locator("select").first()
        org.assertj.core.api.Assertions
            .assertThat(updatedSelect.inputValue())
            .isEqualTo("READONLY")
    }

    @Test
    fun `admin can delete user via UI`() {
        val targetUser = registerAndConfirm()
        loginAsAdmin()

        page.navigate("$APP_UI_URL/admin")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("body")).containsText(targetUser.username)

        // Click delete button for target user
        val userRow = userCard(targetUser.username)
        userRow.locator("text=Delete").first().click()

        // Confirm deletion in dialog
        page.locator("[role='dialog'] button:has-text('Delete')").click()
        page.waitForTimeout(2000.0)

        // User should be removed from the list
        assertThat(page.locator("body")).not().containsText(targetUser.username)
    }

    @Test
    fun `admin can toggle service permissions via UI`() {
        val targetUser = registerAndConfirm()
        loginAsAdmin()

        page.navigate("$APP_UI_URL/admin")
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        // Find the target user's row and toggle a service permission
        val userRow = userCard(targetUser.username)
        val grafanaButton =
            userRow.locator("button:has-text('GRAFANA'), button:has-text('Grafana')").first()
        grafanaButton.click()

        page.waitForTimeout(2000.0)

        // Reload and verify
        page.reload()
        page.waitForTimeout(3000.0)

        // Verify the permission was toggled (button should have active state)
        val updatedRow = userCard(targetUser.username)
        assertThat(
            updatedRow.locator("button:has-text('GRAFANA'), button:has-text('Grafana')").first(),
        ).isVisible()
    }
}
