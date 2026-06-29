package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.common.test.system.PlaywrightStackTestBase
import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.options.Cookie
import com.microsoft.playwright.options.SameSiteAttribute
import io.restassured.http.ContentType
import java.util.UUID

internal const val MAX_PLAYWRIGHT_TIMEOUT_MS = 5_000.0

abstract class PlaywrightTestBase : PlaywrightStackTestBase() {
    companion object {
        val AUTH_UI_URL: String =
            System.getProperty("test.auth-ui.url", "https://auth.jorisjonkers.test")
        val APP_UI_URL: String =
            System.getProperty("test.home-portal.url")
                ?: System.getProperty("test.app-ui.url", "https://jorisjonkers.test")
    }

    override val defaultTimeoutMillis: Double = MAX_PLAYWRIGHT_TIMEOUT_MS

    protected fun uniqueUsername(prefix: String = "pw"): String = "${prefix}_${UUID.randomUUID().toString().take(8)}"

    protected fun registerAndConfirm(
        username: String = uniqueUsername(),
        password: String = "Test1234!",
    ): TestHelper.RegisteredUser = TestHelper.registerAndConfirm(username, password)

    protected fun loginViaUi(
        username: String,
        password: String,
    ) {
        page.navigate("$AUTH_UI_URL/login")
        page.locator("#username").fill(username)
        page.locator("#password").fill(password)
        page.locator("button[type='submit']").click()
        page.waitForURL { !it.contains("/login") }
    }

    protected fun loginViaApi(
        user: TestHelper.RegisteredUser,
        totpCode: String? = null,
    ) {
        val session = TestHelper.sessionLogin(user, totpCode)
        context.addCookies(
            listOf(
                Cookie("SESSION", session.sessionCookie)
                    .setDomain(".jorisjonkers.test")
                    .setPath("/")
                    .setHttpOnly(true)
                    .setSecure(true)
                    .setSameSite(SameSiteAttribute.LAX),
                Cookie("XSRF-TOKEN", session.csrfToken)
                    .setDomain(".jorisjonkers.test")
                    .setPath("/")
                    .setSecure(true)
                    .setSameSite(SameSiteAttribute.LAX),
            ),
        )
    }

    protected fun loginAsAdmin(): TestHelper.RegisteredUser {
        val user = registerAndConfirm(uniqueUsername("adm"))
        TestHelper.makeUserAdmin(user.username)
        loginViaApi(user)
        return user
    }

    protected fun enrollTotpViaApi(user: TestHelper.RegisteredUser): String {
        val session = TestHelper.sessionLogin(user)
        val authApiUrl = TestHelper.authBaseUrl

        val secret =
            TestHelper
                .givenApi()
                .baseUri(authApiUrl)
                .cookie("SESSION", session.sessionCookie)
                .cookie("XSRF-TOKEN", session.csrfToken)
                .header("X-XSRF-TOKEN", session.csrfToken)
                .post("/api/v1/totp/enroll")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("secret")

        TestHelper
            .givenApi()
            .baseUri(authApiUrl)
            .contentType(ContentType.JSON)
            .cookie("SESSION", session.sessionCookie)
            .cookie("XSRF-TOKEN", session.csrfToken)
            .header("X-XSRF-TOKEN", session.csrfToken)
            .body("""{"code":"${generateTotpCode(secret)}"}""")
            .post("/api/v1/totp/verify")
            .then()
            .statusCode(204)

        return secret
    }

    protected fun generateTotpCode(secret: String): String = TestHelper.generateFreshTotpCode(secret)
}
