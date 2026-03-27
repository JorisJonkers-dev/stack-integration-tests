package com.jorisjonkers.privatestack.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

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
    private val appUiUrl = "http://localhost"
    private val authUiUrl = "http://auth.localhost"
    private val assistantUiUrl = "http://app.localhost"

    // Forward-auth protected services
    private val vaultUrl = "http://vault.localhost"
    private val n8nUrl = "http://n8n.localhost"
    private val grafanaUrl = "http://grafana.localhost"

    private fun obtainToken(): String {
        val username = "traefik_${UUID.randomUUID().toString().take(8)}"

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","email":"$username@test.com","password":"Test1234!"}""")
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        return given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","password":"Test1234!"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("accessToken")
    }

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
            .redirects().follow(false)
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
            .redirects().follow(false)
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
            .redirects().follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.localhost/login"))
    }

    // ── Authenticated: forward-auth passes, downstream service responds ───────

    @Test
    fun `vault authenticated passes forward-auth`() {
        val token = obtainToken()
        given()
            .baseUri(vaultUrl)
            .header("Authorization", "Bearer $token")
            .redirects().follow(false)
            .`when`()
            .get("/")
            .then()
            .header("Location", not(containsString("auth.localhost/login")))
    }

    @Test
    fun `n8n authenticated passes forward-auth`() {
        val token = obtainToken()
        given()
            .baseUri(n8nUrl)
            .header("Authorization", "Bearer $token")
            .redirects().follow(false)
            .`when`()
            .get("/")
            .then()
            .header("Location", not(containsString("auth.localhost/login")))
    }

    @Test
    fun `grafana authenticated passes forward-auth`() {
        val token = obtainToken()
        given()
            .baseUri(grafanaUrl)
            .header("Authorization", "Bearer $token")
            .redirects().follow(false)
            .`when`()
            .get("/")
            .then()
            .header("Location", not(containsString("auth.localhost/login")))
    }
}
