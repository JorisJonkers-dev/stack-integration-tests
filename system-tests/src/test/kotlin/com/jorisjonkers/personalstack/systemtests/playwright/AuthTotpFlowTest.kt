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
        page.waitForSelector("canvas", Page.WaitForSelectorOptions().setTimeout(15000.0))

        assertThat(page.locator("canvas")).isVisible()
        assertThat(page.locator("body")).containsText("two-factor")
    }

    @Test
    fun `totp verification with valid code succeeds`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$AUTH_UI_URL/totp-setup")
        page.waitForSelector("canvas", Page.WaitForSelectorOptions().setTimeout(15000.0))

        // Extract secret from the page (shown in <code> or <details>)
        page.locator("details summary").click()
        val secret = page.locator("code").textContent().trim()

        val code = generateTotpCode(secret)
        page.locator("#totp-code").fill(code)
        page.locator("button[type='submit']").click()

        page.waitForURL(
            { !it.contains("/totp-setup") },
            Page.WaitForURLOptions().setTimeout(15000.0),
        )
    }

    @Test
    fun `totp verification with invalid code shows error`() {
        val user = registerAndConfirm()
        loginViaApi(user)

        page.navigate("$AUTH_UI_URL/totp-setup")
        page.waitForSelector("canvas", Page.WaitForSelectorOptions().setTimeout(15000.0))

        page.locator("#totp-code").fill("000000")
        page.locator("button[type='submit']").click()

        page.waitForSelector(
            ".text-red-400, .text-red-500",
            Page.WaitForSelectorOptions().setTimeout(10000.0),
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

        page.waitForSelector("#totp-code", Page.WaitForSelectorOptions().setTimeout(10000.0))
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

        page.waitForSelector("#totp-code", Page.WaitForSelectorOptions().setTimeout(10000.0))
        page.locator("#totp-code").fill(generateTotpCode(secret))
        page.locator("button[type='submit']").click()

        page.waitForURL(
            { !it.contains("/login") },
            Page.WaitForURLOptions().setTimeout(15000.0),
        )
    }

    @Test
    fun `totp setup page requires authentication`() {
        page.navigate("$AUTH_UI_URL/totp-setup")

        page.waitForURL(
            { it.contains("/login") },
            Page.WaitForURLOptions().setTimeout(10000.0),
        )
    }

    private fun enrollTotpViaApi(user: com.jorisjonkers.personalstack.systemtests.TestHelper.RegisteredUser): String {
        val session =
            com.jorisjonkers.personalstack.systemtests.TestHelper
                .sessionLogin(user)

        val secret =
            io.restassured.RestAssured
                .given()
                .baseUri(System.getProperty("test.auth-api.url", "http://localhost:8081"))
                .cookie("SESSION", session.sessionCookie)
                .cookie("XSRF-TOKEN", session.csrfToken)
                .header("X-XSRF-TOKEN", session.csrfToken)
                .post("/api/v1/totp/enroll")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("secret")

        io.restassured.RestAssured
            .given()
            .baseUri(System.getProperty("test.auth-api.url", "http://localhost:8081"))
            .contentType(io.restassured.http.ContentType.JSON)
            .cookie("SESSION", session.sessionCookie)
            .cookie("XSRF-TOKEN", session.csrfToken)
            .header("X-XSRF-TOKEN", session.csrfToken)
            .body("""{"code":"${generateTotpCode(secret)}"}""")
            .post("/api/v1/totp/verify")
            .then()
            .statusCode(204)

        return secret
    }
}
