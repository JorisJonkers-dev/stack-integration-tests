package com.jorisjonkers.personalstack.systemtests.playwright

import com.jorisjonkers.personalstack.systemtests.TestHelper
import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.assertj.core.api.Assertions.assertThat as assertThatValue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class MainSiteServiceLaunchTest : PlaywrightTestBase() {
    @Test
    fun `main site launches Vault and completes downstream oidc login`() {
        prepareAdminSessionOnMainSite()

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        val card = page.locator("a[href='https://vault.jorisjonkers.test/']").first()
        assertThat(card).isVisible()

        val seenUrls = mutableListOf<String>()
        context.onRequest { request ->
            val url = request.url()
            if (url.contains("vault.jorisjonkers.test") || url.contains("auth.jorisjonkers.test")) {
                seenUrls += url
            }
        }

        val servicePage = context.waitForPage(card::click)
        servicePage.waitForLoadState()
        servicePage.waitForTimeout(5000.0)
        try {
            waitForServicePageToSettle(servicePage)
            servicePage.locator("select").selectOption("oidc")
            servicePage.locator("button:has-text('Sign in with OIDC Provider')").click()
            servicePage.waitForTimeout(8000.0)
            waitForServicePageToSettle(servicePage)

            val currentUrl = servicePage.url()
            assertThatValue(currentUrl)
                .contains("vault.jorisjonkers.test")
                .doesNotContain("/ui/vault/auth")
                .doesNotContain("error=")

            val pageText = buildString {
                append(servicePage.title())
                append('\n')
                append(servicePage.locator("body").textContent().orEmpty())
            }.lowercase()
            assertThatValue(seenUrls.any { it.contains("/v1/auth/oidc/oidc/auth_url") }).isTrue()
            assertThatValue(
                seenUrls.any { it.contains("auth.jorisjonkers.test/api/oauth2/authorize") && it.contains("client_id=vault") },
            ).isTrue()
            assertThatValue(pageText).doesNotContain("sign in to vault")
            assertThatValue(pageText).doesNotContain("error fetching role")
        } finally {
            servicePage.close()
        }
    }

    @Test
    fun `main site launches Stalwart through forward auth to web admin`() {
        prepareAdminSessionOnMainSite()

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        val card = page.locator("a[href='https://stalwart.jorisjonkers.test/']").first()
        assertThat(card).isVisible()

        val seenUrls = mutableListOf<String>()
        context.onRequest { request ->
            val url = request.url()
            if (url.contains("stalwart.jorisjonkers.test") || url.contains("auth.jorisjonkers.test")) {
                seenUrls += url
            }
        }

        val servicePage = context.waitForPage(card::click)
        servicePage.waitForLoadState()
        servicePage.waitForTimeout(5000.0)
        try {
            waitForServicePageToSettle(servicePage)

            val currentUrl = servicePage.url()
            assertThatValue(currentUrl)
                .contains("stalwart.jorisjonkers.test")
                .doesNotContain("auth.jorisjonkers.test/login")
                .doesNotContain("error=")

            val pageText = buildString {
                append(servicePage.title())
                append('\n')
                append(servicePage.locator("body").textContent().orEmpty())
            }.lowercase()
            assertThatValue(
                seenUrls.none { it.contains("auth.jorisjonkers.test/api/oauth2/authorize") },
            ).isTrue()
            assertThatValue(pageText).contains("stalwart management")
        } finally {
            servicePage.close()
        }
    }

    @Test
    fun `main site launches n8n and completes downstream oidc login once`() {
        prepareAdminSessionOnMainSite()

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        val card = page.locator("a[href='https://n8n.jorisjonkers.test/']").first()
        assertThat(card).isVisible()

        val seenUrls = mutableListOf<String>()
        context.onRequest { request ->
            val url = request.url()
            if (url.contains("n8n.jorisjonkers.test") || url.contains("auth.jorisjonkers.test")) {
                seenUrls += url
            }
        }

        val servicePage = context.waitForPage(card::click)
        servicePage.waitForLoadState()
        servicePage.waitForTimeout(10000.0)
        try {
            waitForServicePageToSettle(servicePage)

            servicePage.waitForFunction(
                "() => !!document.querySelector('#oidc-sso-button') || !window.location.pathname.startsWith('/signin')",
                null,
                Page.WaitForFunctionOptions().setTimeout(15000.0),
            )

            if (servicePage.url().contains("/signin")) {
                val signInPageText = buildString {
                    append(servicePage.title())
                    append('\n')
                    append(servicePage.locator("body").textContent().orEmpty())
                }.lowercase()
                assertThatValue(signInPageText).contains("sign in with sso")
                servicePage.locator("#oidc-sso-button").click()
                servicePage.waitForTimeout(10000.0)
                waitForServicePageToSettle(servicePage)
            }

            val currentUrl = servicePage.url()
            assertThatValue(currentUrl)
                .contains("n8n.jorisjonkers.test")
                .doesNotContain("/signin")
                .doesNotContain("auth.jorisjonkers.test/login")
                .doesNotContain("error=")

            val pageText = buildString {
                append(servicePage.title())
                append('\n')
                append(servicePage.locator("body").textContent().orEmpty())
            }.lowercase()
            val authorizeRequests =
                seenUrls.count {
                    it.contains("auth.jorisjonkers.test/api/oauth2/authorize") &&
                        it.contains("client_id=n8n")
                }
            val callbackRequests = seenUrls.count { it.contains("n8n.jorisjonkers.test/auth/oidc/callback?code=") }
            assertThatValue(authorizeRequests).isEqualTo(1)
            assertThatValue(callbackRequests).isEqualTo(1)
            assertThatValue(pageText).doesNotContain("owner account")
            assertThatValue(pageText).doesNotContain("sign in with email")
            val landedInN8nWorkspace =
                pageText.contains("workflow credential project overview") ||
                    (pageText.contains("workflows - n8n") && pageText.contains("start from scratch"))
            assertThatValue(landedInN8nWorkspace).isTrue()
        } finally {
            servicePage.close()
        }
    }

    @Test
    fun `main site launches Grafana and completes downstream oidc login`() {
        prepareAdminSessionOnMainSite()

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        val card = page.locator("a[href='https://grafana.jorisjonkers.test/']").first()
        assertThat(card).isVisible()

        val seenUrls = mutableListOf<String>()
        context.onRequest { request ->
            val url = request.url()
            if (url.contains("grafana.jorisjonkers.test") || url.contains("auth.jorisjonkers.test")) {
                seenUrls += url
            }
        }

        val servicePage = context.waitForPage(card::click)
        servicePage.waitForLoadState()
        servicePage.waitForTimeout(5000.0)
        try {
            waitForServicePageToSettle(servicePage)
            servicePage.waitForFunction(
                "() => Boolean(window.grafanaBootData && window.grafanaBootData.user)",
                null,
                Page.WaitForFunctionOptions().setTimeout(15000.0),
            )

            val currentUrl = servicePage.url()
            assertThatValue(currentUrl)
                .contains("grafana.jorisjonkers.test")
                .doesNotContain("auth.jorisjonkers.test/login")
                .doesNotContain("error=")

            val grafanaSignedIn =
                servicePage.evaluate("() => window.grafanaBootData?.user?.isSignedIn === true") as Boolean
            val grafanaAuthMethod =
                servicePage.evaluate("() => window.grafanaBootData?.user?.authenticatedBy || null") as String?
            val grafanaLogin =
                servicePage.evaluate("() => window.grafanaBootData?.user?.login || null") as String?
            val pageText = buildString {
                append(servicePage.title())
                append('\n')
                append(servicePage.locator("body").textContent().orEmpty())
            }.lowercase()
            assertThatValue(
                seenUrls.any { it.contains("auth.jorisjonkers.test/api/oauth2/authorize") && it.contains("client_id=grafana") },
            ).isTrue()
            assertThatValue(
                seenUrls.any { it.contains("grafana.jorisjonkers.test/login/generic_oauth?code=") },
            ).isTrue()
            assertThatValue(grafanaSignedIn).isTrue()
            assertThatValue(grafanaAuthMethod).isEqualTo("oauth_generic_oauth")
            assertThatValue(grafanaLogin).isNotBlank()
            assertThatValue(pageText).doesNotContain("failed to get token from provider")
            assertThatValue(pageText).doesNotContain("login failed")
        } finally {
            servicePage.close()
        }
    }

    @Test
    fun `main site launches Assistant directly into chat ui`() {
        prepareAdminSessionOnMainSite()

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        val card = page.locator("a[href='https://assistant.jorisjonkers.test/']").first()
        assertThat(card).isVisible()

        val seenUrls = mutableListOf<String>()
        context.onRequest { request ->
            val url = request.url()
            if (url.contains("assistant.jorisjonkers.test") || url.contains("auth.jorisjonkers.test")) {
                seenUrls += url
            }
        }

        val servicePage = context.waitForPage(card::click)
        servicePage.waitForLoadState()
        servicePage.waitForTimeout(5000.0)
        try {
            waitForServicePageToSettle(servicePage)

            val currentUrl = servicePage.url()
            assertThatValue(currentUrl)
                .contains("assistant.jorisjonkers.test")
                .doesNotContain("auth.jorisjonkers.test/login")
                .doesNotContain("error=")

            val pageText = buildString {
                append(servicePage.title())
                append('\n')
                append(servicePage.locator("body").textContent().orEmpty())
            }.lowercase()
            assertThatValue(
                seenUrls.none { it.contains("auth.jorisjonkers.test/api/oauth2/authorize") },
            ).isTrue()
            assertThatValue(pageText).contains("select a conversation")
        } finally {
            servicePage.close()
        }
    }

    @Test
    fun `main site launches Traefik through forward auth to dashboard`() {
        prepareAdminSessionOnMainSite()

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        val card = page.locator("a[href='https://traefik.jorisjonkers.test/']").first()
        assertThat(card).isVisible()

        val seenUrls = mutableListOf<String>()
        context.onRequest { request ->
            val url = request.url()
            if (url.contains("traefik.jorisjonkers.test") || url.contains("auth.jorisjonkers.test")) {
                seenUrls += url
            }
        }

        val servicePage = context.waitForPage(card::click)
        servicePage.waitForLoadState()
        servicePage.waitForTimeout(5000.0)
        try {
            waitForServicePageToSettle(servicePage)

            val currentUrl = servicePage.url()
            assertThatValue(currentUrl)
                .contains("traefik.jorisjonkers.test")
                .doesNotContain("auth.jorisjonkers.test/login")
                .doesNotContain("error=")

            val pageText = buildString {
                append(servicePage.title())
                append('\n')
                append(servicePage.locator("body").textContent().orEmpty())
            }.lowercase()
            assertThatValue(
                seenUrls.none { it.contains("auth.jorisjonkers.test/api/oauth2/authorize") },
            ).isTrue()
            assertThatValue(pageText).contains("dashboard - traefik proxy")
        } finally {
            servicePage.close()
        }
    }

    @Test
    fun `main site launches Status into a configured uptime page`() {
        prepareAdminSessionOnMainSite()

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(2000.0)

        val card = page.locator("a[href='https://status.jorisjonkers.test/']").first()
        assertThat(card).isVisible()

        val seenUrls = mutableListOf<String>()
        context.onRequest { request ->
            val url = request.url()
            if (url.contains("status.jorisjonkers.test") || url.contains("auth.jorisjonkers.test")) {
                seenUrls += url
            }
        }

        val servicePage = context.waitForPage(card::click)
        servicePage.waitForLoadState()
        servicePage.waitForTimeout(5000.0)
        try {
            waitForServicePageToSettle(servicePage)

            val currentUrl = servicePage.url()
            assertThatValue(currentUrl)
                .contains("status.jorisjonkers.test")
                .doesNotContain("/setup-database")
                .doesNotContain("error=")

            val pageText = buildString {
                append(servicePage.title())
                append('\n')
                append(servicePage.locator("body").textContent().orEmpty())
            }.lowercase()
            assertThatValue(
                seenUrls.none { it.contains("auth.jorisjonkers.test/api/oauth2/authorize") },
            ).isTrue()
            assertThatValue(pageText).doesNotContain("which database would you like to use")
        } finally {
            servicePage.close()
        }
    }

    private fun prepareAdminSessionOnMainSite() {
        val user = registerAndConfirm()
        val totpSecret = loginFromMainSiteAndEnrollTotp(user)

        assertThat(page.locator("body")).containsText(user.username)

        TestHelper.makeUserAdmin(user.username)

        // The authenticated principal is stored in the session, so re-login is required
        // after changing the user's role directly in the database.
        context.clearCookies()
        loginFromMainSiteWithTotp(user, totpSecret)

        page.navigate(APP_UI_URL)
        page.waitForLoadState()
        page.waitForTimeout(3000.0)

        assertThat(page.locator("text=My Apps")).isVisible()
        assertThat(page.locator("a[href='/admin']")).isVisible()
    }

    private fun loginFromMainSiteAndEnrollTotp(user: TestHelper.RegisteredUser): String {
        page.navigate(APP_UI_URL)
        page.waitForLoadState()

        page.locator("button:has-text('Login')").click()
        page.waitForURL(
            { it.contains("auth.jorisjonkers.test/login") },
            Page.WaitForURLOptions().setTimeout(15000.0),
        )

        page.locator("#username").fill(user.username)
        page.locator("#password").fill(user.password)
        page.locator("button[type='submit']").click()

        page.waitForURL(
            { it.contains("/totp-setup") },
            Page.WaitForURLOptions().setTimeout(20000.0),
        )
        page.waitForSelector("canvas", Page.WaitForSelectorOptions().setTimeout(15000.0))
        page.locator("details summary").click()
        val secret = page.locator("code").textContent().trim()
        page.locator("#totp-code").fill(generateTotpCode(secret))
        page.locator("button[type='submit']").click()

        waitForMainSite()
        return secret
    }

    private fun loginFromMainSiteWithTotp(
        user: TestHelper.RegisteredUser,
        totpSecret: String,
    ) {
        page.navigate(APP_UI_URL)
        page.waitForLoadState()

        page.locator("button:has-text('Login')").click()
        page.waitForURL(
            { it.contains("auth.jorisjonkers.test/login") },
            Page.WaitForURLOptions().setTimeout(15000.0),
        )

        page.locator("#username").fill(user.username)
        page.locator("#password").fill(user.password)
        page.locator("button[type='submit']").click()

        page.waitForSelector("#totp-code", Page.WaitForSelectorOptions().setTimeout(10000.0))
        page.locator("#totp-code").fill(generateTotpCode(totpSecret))
        page.locator("button[type='submit']").click()

        waitForMainSite()
    }

    private fun waitForMainSite() {
        page.waitForURL(
            { it.startsWith(APP_UI_URL) && !it.contains("/login") },
            Page.WaitForURLOptions().setTimeout(20000.0),
        )
        page.waitForLoadState()
        page.waitForTimeout(2000.0)
    }

    private fun waitForServicePageToSettle(servicePage: Page) {
        repeat(5) {
            servicePage.waitForLoadState()
            servicePage.waitForTimeout(1500.0)

            try {
                servicePage.title()
                servicePage.locator("body").textContent()
                return
            } catch (error: PlaywrightException) {
                if (!isNavigationInFlight(error)) {
                    throw error
                }
            }
        }
    }

    private fun isNavigationInFlight(error: PlaywrightException): Boolean =
        error.message.orEmpty().contains("Execution context was destroyed")
}
