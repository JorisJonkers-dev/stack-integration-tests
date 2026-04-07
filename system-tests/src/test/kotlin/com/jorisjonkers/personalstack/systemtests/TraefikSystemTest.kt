package com.jorisjonkers.personalstack.systemtests

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * System tests that go through Traefik (port 80) using virtual-host URLs.
 * Each service URL resolves to 127.0.0.1 (Traefik), which routes by Host header.
 * Verifies UI routes are accessible, that mail-oriented services stay behind forward-auth,
 * and that native OIDC services are reachable without Traefik redirecting them to the auth
 * login page first.
 */
@Tag("system")
class TraefikSystemTest {
    private fun traefikRequest() = TestHelper.givenApi()

    // UI services
    private val appUiUrl = "https://jorisjonkers.test"
    private val authUiUrl = "https://auth.jorisjonkers.test"
    private val assistantUiUrl = "https://assistant.jorisjonkers.test"

    // Native OIDC services
    private val vaultUrl = "https://vault.jorisjonkers.test"
    private val n8nUrl = "https://n8n.jorisjonkers.test"
    private val grafanaUrl = "https://grafana.jorisjonkers.test"

    // Forward-auth protected services
    private val rabbitMqUrl = "https://rabbitmq.jorisjonkers.test"
    private val mailUrl = "https://stalwart.jorisjonkers.test"
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

    // ── Unauthenticated: native OIDC services stay reachable ──────────────────

    @Test
    fun `vault unauthenticated does not redirect to auth login`() {
        val response =
            traefikRequest()
                .baseUri(vaultUrl)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode).isNotIn(401, 403)
        assertThat(response.header("Location").orEmpty()).doesNotContain("auth.jorisjonkers.test/login")
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
    fun `rabbitmq authenticated passes forward-auth`() {
        val session = obtainAdminSession()
        val response =
            traefikRequest()
                .baseUri(rabbitMqUrl)
                .cookie("SESSION", session.sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode).isNotIn(401, 403)
        assertThat(response.header("Location").orEmpty()).doesNotContain("auth.jorisjonkers.test/login")
    }

    @Test
    fun `n8n unauthenticated does not redirect to auth login`() {
        val response =
            traefikRequest()
                .baseUri(n8nUrl)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode).isNotIn(401, 403)
        assertThat(response.header("Location").orEmpty()).doesNotContain("auth.jorisjonkers.test/login")
    }

    @Test
    fun `grafana unauthenticated does not redirect to auth login`() {
        val response =
            traefikRequest()
                .baseUri(grafanaUrl)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode).isNotIn(401, 403)
        assertThat(response.header("Location").orEmpty()).doesNotContain("auth.jorisjonkers.test/login")
    }

    // ── Unauthenticated: forward-auth services still redirect to auth login ───

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
