package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * System test: full forward-auth redirect chain through Traefik.
 *
 * Validates the complete flow for every protected service:
 *   1. Unauthenticated request redirects to auth login with redirect URL
 *   2. Redirect URL encodes the original service hostname
 *   3. ADMIN authenticated request passes forward-auth and reaches the service
 *   4. USER without service permission is denied (403) by forward-auth
 *   5. X-User-Id header is propagated by the verify endpoint
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForwardAuthChainSystemTest {
    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")

    companion object {
        @JvmStatic
        fun protectedServices(): Stream<Arguments> =
            Stream.of(
                Arguments.of("vault", "http://vault.localhost:80", "/"),
                Arguments.of("n8n", "http://n8n.localhost:80", "/"),
                Arguments.of("grafana", "http://grafana.localhost:80", "/d/dashboard"),
                Arguments.of("traefik", "http://traefik.localhost:80", "/dashboard/"),
                Arguments.of("stalwart", "http://stalwart.localhost:80", "/"),
            )
    }

    @ParameterizedTest(name = "{0}: unauthenticated request redirects to login with redirect URL")
    @MethodSource("protectedServices")
    fun `unauthenticated request redirects to login with service redirect URL`(
        serviceName: String,
        baseUrl: String,
        path: String,
    ) {
        val response =
            given()
                .baseUri(baseUrl)
                .redirects()
                .follow(false)
                .`when`()
                .get(path)

        assertThat(response.statusCode).isEqualTo(302)

        val location = response.header("Location")
        assertThat(location)
            .describedAs("$serviceName redirect should point to login")
            .contains("login")
        assertThat(location)
            .describedAs("$serviceName redirect should include redirect param")
            .contains("redirect=")
        assertThat(location)
            .describedAs("$serviceName redirect should encode the original service URL")
            .containsIgnoringCase(serviceName)
    }

    @Suppress("UnusedParameter")
    @ParameterizedTest(name = "{0}: ADMIN passes forward-auth for all services")
    @MethodSource("protectedServices")
    fun `admin authenticated request passes forward-auth`(
        serviceName: String,
        baseUrl: String,
        @Suppress("UNUSED_PARAMETER") path: String,
    ) {
        val token = TestHelper.registerConfirmAndLoginAsAdmin()

        val response =
            given()
                .baseUri(baseUrl)
                .header("Authorization", "Bearer $token")
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        // Forward-auth failure for permission denial returns 403.
        // Backend services may have their own login pages (e.g. Grafana, Stalwart),
        // so we only verify the response is not a forward-auth rejection.
        assertThat(response.statusCode).isNotEqualTo(403)
        assertThat(response.statusCode).isNotEqualTo(401)
    }

    @ParameterizedTest(name = "{0}: USER without permission is denied by forward-auth")
    @MethodSource("protectedServices")
    fun `user without service permission is denied access`(
        serviceName: String,
        baseUrl: String,
        path: String,
    ) {
        val token = TestHelper.registerConfirmAndLogin()

        val response =
            given()
                .baseUri(baseUrl)
                .header("Authorization", "Bearer $token")
                .redirects()
                .follow(false)
                .`when`()
                .get(path)

        assertThat(response.statusCode)
            .describedAs("$serviceName should reject USER without service permission")
            .isEqualTo(403)
    }

    @Test
    fun `forward-auth propagates X-User-Id header`() {
        val token = TestHelper.registerConfirmAndLogin()

        val userId =
            given()
                .baseUri(authBaseUrl)
                .header("Authorization", "Bearer $token")
                .`when`()
                .get("/api/v1/auth/verify")
                .then()
                .statusCode(200)
                .extract()
                .header("X-User-Id")

        assertThat(userId).isNotBlank()
        assertThat(userId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }

    @Test
    fun `user with granted service permission passes forward-auth for that service`() {
        val username = "svc_${java.util.UUID.randomUUID().toString().take(8)}"
        val user = TestHelper.registerAndConfirm(username)
        TestHelper.grantServicePermission(username, "GRAFANA")
        val token = TestHelper.loginAndGetToken(user)

        val response =
            given()
                .baseUri("http://grafana.localhost:80")
                .header("Authorization", "Bearer $token")
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode)
            .describedAs("USER with GRAFANA permission should pass grafana forward-auth")
            .isNotEqualTo(403)
        assertThat(response.statusCode).isNotEqualTo(401)
    }
}
