package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.options.Cookie
import com.microsoft.playwright.options.SameSiteAttribute
import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import io.restassured.http.ContentType
import org.apache.commons.codec.binary.Base32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import java.util.concurrent.TimeUnit

internal const val MAX_PLAYWRIGHT_TIMEOUT_MS = 5_000.0

@ExtendWith(PlaywrightShardCondition::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PlaywrightTestBase {
    companion object {
        val AUTH_UI_URL: String =
            System.getProperty("test.auth-ui.url", "https://auth.jorisjonkers.test")
        val APP_UI_URL: String =
            System.getProperty("test.app-ui.url", "https://jorisjonkers.test")
        val ASSISTANT_UI_URL: String =
            System.getProperty("test.assistant-ui.url", "https://assistant.jorisjonkers.test")
    }

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    protected lateinit var context: BrowserContext
    protected lateinit var page: Page

    @BeforeAll
    fun launchBrowser() {
        playwright = Playwright.create()
        browser =
            playwright.chromium().launch(
                BrowserType.LaunchOptions().setHeadless(true),
            )
    }

    @AfterAll
    fun closeBrowser() {
        browser.close()
        playwright.close()
    }

    @BeforeEach
    fun createContext() {
        context =
            browser.newContext(
                Browser.NewContextOptions().setIgnoreHTTPSErrors(true),
            )
        context.setDefaultTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS)
        context.setDefaultNavigationTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS)
        page = context.newPage()
        page.setDefaultTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS)
        page.setDefaultNavigationTimeout(MAX_PLAYWRIGHT_TIMEOUT_MS)
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    protected fun navigateWithRetry(
        url: String,
        attempts: Int = 3,
    ) {
        var lastException: PlaywrightException? = null
        repeat(attempts) { attempt ->
            try {
                page.navigate(url)
                return
            } catch (e: PlaywrightException) {
                if (e.message?.contains("ERR_CONNECTION_REFUSED") == true) {
                    lastException = e
                    if (attempt < attempts - 1) {
                        Thread.sleep(2000L * (attempt + 1))
                    }
                } else {
                    throw e
                }
            }
        }
        throw lastException!!
    }

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

    protected fun generateTotpCode(secret: String): String {
        val padded = secret.padEnd((secret.length + 7) / 8 * 8, '=')
        val secretBytes = Base32().decode(padded)
        val config =
            TimeBasedOneTimePasswordConfig(
                codeDigits = 6,
                hmacAlgorithm = HmacAlgorithm.SHA1,
                timeStep = 30,
                timeStepUnit = TimeUnit.SECONDS,
            )
        return TimeBasedOneTimePasswordGenerator(secretBytes, config).generate()
    }
}
