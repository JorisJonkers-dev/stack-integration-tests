package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * System tests that go through Traefik (port 80) using virtual-host URLs.
 * Each service URL resolves to 127.0.0.1 (Traefik), which routes by Host header.
 * Verifies UI routes are accessible and that forward-auth protects
 * vault, n8n, and grafana — redirecting unauthenticated requests to the
 * auth login page and passing authenticated requests through.
 */
@Tag("system")
class TraefikSystemTest {
    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")

    // UI services
    private val appUiUrl = "http://localhost:80"
    private val authUiUrl = "http://auth.localhost:80"
    private val assistantUiUrl = "http://assistant.localhost:80"

    // Forward-auth protected services
    private val vaultUrl = "http://vault.localhost:80"
    private val n8nUrl = "http://n8n.localhost:80"
    private val grafanaUrl = "http://grafana.localhost:80"
    private val stalwartUrl = "http://stalwart.localhost:80"

    private fun obtainSession(): TestHelper.SessionUser = TestHelper.registerConfirmAndGetSession()

    // ── UI routes (no authentication required) ───────────────────────────────

    @Test
    fun `app-ui responds at localhost`() {
        given()
            .baseUri(appUiUrl)
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
    }

    @Test
    fun `auth-ui responds at auth dot localhost`() {
        given()
            .baseUri(authUiUrl)
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
    }

    @Test
    fun `assistant-ui responds at app dot localhost`() {
        given()
            .baseUri(assistantUiUrl)
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
    }

    // ── Unauthenticated: protected services redirect to auth login ────────────

    @Test
    fun `vault unauthenticated redirects to auth login`() {
        given()
            .baseUri(vaultUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.localhost/login"))
    }

    @Test
    fun `n8n unauthenticated redirects to auth login`() {
        given()
            .baseUri(n8nUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.localhost/login"))
    }

    @Test
    fun `grafana unauthenticated redirects to auth login`() {
        given()
            .baseUri(grafanaUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.localhost/login"))
    }

    @Test
    fun `stalwart unauthenticated redirects to auth login`() {
        given()
            .baseUri(stalwartUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.localhost/login"))
    }

    // ── Authenticated: forward-auth passes, downstream service responds ───────

    @Test
    fun `vault authenticated passes forward-auth`() {
        val session = obtainSession()
        given()
            .baseUri(vaultUrl)
            .cookie("SESSION", session.sessionCookie)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .header("Location", not(containsString("auth.localhost/login")))
    }

    @Test
    fun `n8n authenticated passes forward-auth`() {
        val session = obtainSession()
        given()
            .baseUri(n8nUrl)
            .cookie("SESSION", session.sessionCookie)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .header("Location", not(containsString("auth.localhost/login")))
    }

    @Test
    fun `grafana authenticated passes forward-auth`() {
        val session = obtainSession()
        given()
            .baseUri(grafanaUrl)
            .cookie("SESSION", session.sessionCookie)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .header("Location", not(containsString("auth.localhost/login")))
    }

    @Test
    fun `stalwart authenticated passes forward-auth`() {
        val session = obtainSession()
        given()
            .baseUri(stalwartUrl)
            .cookie("SESSION", session.sessionCookie)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .header("Location", not(containsString("auth.localhost/login")))
    }
}
