package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * System tests that go through Traefik (port 80) using virtual-host URLs.
 * Each service URL resolves to 127.0.0.1 (Traefik), which routes by Host header.
 * Verifies UI routes are accessible and that forward-auth protects
 * vault, rabbitmq, n8n, and grafana — redirecting unauthenticated requests to the
 * auth login page and passing authenticated requests through.
 */
@Tag("system")
class TraefikSystemTest {
    private fun traefikRequest() = given().relaxedHTTPSValidation()

    // UI services
    private val appUiUrl = "https://jorisjonkers.test"
    private val authUiUrl = "https://auth.jorisjonkers.test"
    private val assistantUiUrl = "https://assistant.jorisjonkers.test"

    // Forward-auth protected services
    private val vaultUrl = "https://vault.jorisjonkers.test"
    private val rabbitMqUrl = "https://rabbitmq.jorisjonkers.test"
    private val mailUrl = "https://mail.jorisjonkers.test"
    private val n8nUrl = "https://n8n.jorisjonkers.test"
    private val grafanaUrl = "https://grafana.jorisjonkers.test"
    private val stalwartUrl = "https://stalwart.jorisjonkers.test"

    private fun obtainAdminSession(): TestHelper.SessionUser = TestHelper.registerConfirmAndGetAdminSession()

    // ── UI routes (no authentication required) ───────────────────────────────

    @Test
    fun `app-ui responds at localhost`() {
        traefikRequest()
            .baseUri(appUiUrl)
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
    }

    @Test
    fun `auth-ui responds at auth dot localhost`() {
        traefikRequest()
            .baseUri(authUiUrl)
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
    }

    @Test
    fun `assistant-ui responds at app dot localhost`() {
        traefikRequest()
            .baseUri(assistantUiUrl)
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
    }

    // ── Unauthenticated: protected services redirect to auth login ────────────

    @Test
    fun `vault unauthenticated redirects to auth login`() {
        traefikRequest()
            .baseUri(vaultUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.jorisjonkers.test/login"))
    }

    @Test
    fun `mail unauthenticated redirects to auth login`() {
        traefikRequest()
            .baseUri(mailUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.jorisjonkers.test/login"))
    }

    @Test
    fun `rabbitmq unauthenticated redirects to auth login`() {
        traefikRequest()
            .baseUri(rabbitMqUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.jorisjonkers.test/login"))
    }

    @Test
    fun `n8n unauthenticated redirects to auth login`() {
        traefikRequest()
            .baseUri(n8nUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.jorisjonkers.test/login"))
    }

    @Test
    fun `grafana unauthenticated redirects to auth login`() {
        traefikRequest()
            .baseUri(grafanaUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.jorisjonkers.test/login"))
    }

    @Test
    fun `stalwart unauthenticated redirects to auth login`() {
        traefikRequest()
            .baseUri(stalwartUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(302)
            .header("Location", containsString("auth.jorisjonkers.test/login"))
    }

    // ── Authenticated: forward-auth passes, downstream service responds ───────

    @Test
    fun `vault authenticated passes forward-auth`() {
        val session = obtainAdminSession()
        val response =
            traefikRequest()
                .baseUri(vaultUrl)
                .cookie("SESSION", session.sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode).isNotIn(401, 403)
        assertThat(response.header("Location").orEmpty()).doesNotContain("auth.jorisjonkers.test/login")
    }

    @Test
    fun `mail authenticated passes forward-auth`() {
        val session = obtainAdminSession()
        val response =
            traefikRequest()
                .baseUri(mailUrl)
                .cookie("SESSION", session.sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode).isNotIn(401, 403)
        assertThat(response.header("Location").orEmpty()).doesNotContain("auth.jorisjonkers.test/login")
    }

    @Test
    fun `rabbitmq authenticated passes forward-auth`() {
        val session = obtainAdminSession()
        traefikRequest()
            .baseUri(rabbitMqUrl)
            .cookie("SESSION", session.sessionCookie)
            .redirects()
            .follow(false)
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
    }

    @Test
    fun `n8n authenticated passes forward-auth`() {
        val session = obtainAdminSession()
        val response =
            traefikRequest()
                .baseUri(n8nUrl)
                .cookie("SESSION", session.sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode).isNotIn(401, 403)
        assertThat(response.header("Location").orEmpty()).doesNotContain("auth.jorisjonkers.test/login")
    }

    @Test
    fun `grafana authenticated passes forward-auth`() {
        val session = obtainAdminSession()
        val response =
            traefikRequest()
                .baseUri(grafanaUrl)
                .cookie("SESSION", session.sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode).isNotIn(401, 403)
        assertThat(response.header("Location").orEmpty()).doesNotContain("auth.jorisjonkers.test/login")
    }

    @Test
    fun `stalwart authenticated passes forward-auth`() {
        val session = obtainAdminSession()
        val response =
            traefikRequest()
                .baseUri(stalwartUrl)
                .cookie("SESSION", session.sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode).isNotIn(401, 403)
        assertThat(response.header("Location").orEmpty()).doesNotContain("auth.jorisjonkers.test/login")
    }

}
