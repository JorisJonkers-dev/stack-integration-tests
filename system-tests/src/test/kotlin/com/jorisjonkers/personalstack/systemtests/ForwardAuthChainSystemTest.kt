package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
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

        @JvmStatic
        fun allServiceHosts(): Stream<Arguments> =
            Stream.of(
                Arguments.of("vault", "http://vault.localhost:80"),
                Arguments.of("n8n", "http://n8n.localhost:80"),
                Arguments.of("grafana", "http://grafana.localhost:80"),
                Arguments.of("traefik", "http://traefik.localhost:80"),
                Arguments.of("stalwart", "http://stalwart.localhost:80"),
                Arguments.of("assistant", "http://assistant.localhost:80"),
                Arguments.of("auth", "http://auth.localhost:80"),
            )
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
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
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()

        val response =
            given()
                .baseUri(baseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
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
        val userSession = TestHelper.registerConfirmAndGetSession()

        val response =
            given()
                .baseUri(baseUrl)
                .cookie("SESSION", userSession.sessionCookie)
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
        val session = TestHelper.registerConfirmAndGetSession()

        val userId =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", session.sessionCookie)
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
        val sessionCookie = TestHelper.sessionLoginAndGetCookie(user)

        val response =
            given()
                .baseUri("http://grafana.localhost:80")
                .cookie("SESSION", sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode)
            .describedAs("USER with GRAFANA permission should pass grafana forward-auth")
            .isNotEqualTo(403)
        assertThat(response.statusCode).isNotEqualTo(401)
    }

    @Test
    fun `forward-auth with OAuth2 session token passes`() {
        val user = TestHelper.registerAndConfirm()
        TestHelper.makeUserAdmin(user.username)

        // Session login to get session cookie
        val sessionCookie = TestHelper.sessionLoginAndGetCookie(user)

        // OAuth2 authorize with PKCE to get an access token
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        val authorizeResponse =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", sessionCookie)
                .redirects()
                .follow(false)
                .queryParam("response_type", "code")
                .queryParam("client_id", "auth-ui")
                .queryParam("redirect_uri", "http://localhost:5174/callback")
                .queryParam("scope", "openid profile email")
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", "test-state")
                .`when`()
                .get("/api/oauth2/authorize")

        assertThat(authorizeResponse.statusCode).isIn(302, 303)
        val location = authorizeResponse.header("Location")
        assertThat(location).contains("code=")

        val code =
            java.net
                .URI(location)
                .query
                .split("&")
                .associate { it.split("=", limit = 2).let { kv -> kv[0] to kv[1] } }["code"]

        val oauth2Token =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "authorization_code")
                .formParam("code", code)
                .formParam("redirect_uri", "http://localhost:5174/callback")
                .formParam("client_id", "auth-ui")
                .formParam("code_verifier", codeVerifier)
                .`when`()
                .post("/api/oauth2/token")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("access_token")

        // Use OAuth2 token with forward-auth verify endpoint
        given()
            .baseUri(authBaseUrl)
            .header("Authorization", "Bearer $oauth2Token")
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(200)
    }

    @Test
    fun `forward-auth without service permission returns 403`() {
        val username = "noperm_${java.util.UUID.randomUUID().toString().take(8)}"
        val user = TestHelper.registerAndConfirm(username)
        val sessionCookie = TestHelper.sessionLoginAndGetCookie(user)

        val response =
            given()
                .baseUri("http://grafana.localhost:80")
                .cookie("SESSION", sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        assertThat(response.statusCode)
            .describedAs("USER without any service permission should be denied")
            .isEqualTo(403)
    }

    @Test
    fun `expired token triggers redirect to login`() {
        // Build an expired token dynamically to avoid gitleaks false positive
        val header =
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{"alg":"RS256"}""".toByteArray(),
            )
        val payload =
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{"sub":"test","exp":1}""".toByteArray(),
            )
        val expiredToken = "$header.$payload.invalid-sig"

        val response =
            given()
                .baseUri(authBaseUrl)
                .header("Authorization", "Bearer $expiredToken")
                .redirects()
                .follow(false)
                .`when`()
                .get("/api/v1/auth/verify")

        assertThat(response.statusCode).isEqualTo(302)

        val location = response.header("Location")
        assertThat(location)
            .describedAs("Expired token should redirect to login")
            .contains("login")
    }

    @ParameterizedTest(name = "{0}: forward-auth works for registered service host")
    @MethodSource("allServiceHosts")
    fun `forward-auth works for all registered service hosts`(
        serviceName: String,
        baseUrl: String,
    ) {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()

        val response =
            given()
                .baseUri(baseUrl)
                .cookie("SESSION", adminSession.sessionCookie)
                .redirects()
                .follow(false)
                .`when`()
                .get("/")

        // Admin should not get 403 or 401 on any service
        assertThat(response.statusCode)
            .describedAs("$serviceName should not reject ADMIN with 403")
            .isNotEqualTo(403)
        assertThat(response.statusCode)
            .describedAs("$serviceName should not reject ADMIN with 401")
            .isNotEqualTo(401)
    }

    @Test
    fun `forward-auth returns correct redirect URL with original path`() {
        val response =
            given()
                .baseUri(authBaseUrl)
                .redirects()
                .follow(false)
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "grafana.jorisjonkers.dev")
                .header("X-Forwarded-Uri", "/d/some-dashboard?orgId=1")
                .`when`()
                .get("/api/v1/auth/verify")

        assertThat(response.statusCode).isEqualTo(302)

        val location = response.header("Location")
        assertThat(location)
            .describedAs("Redirect should contain the original path")
            .contains("grafana.jorisjonkers.dev")
        assertThat(location)
            .describedAs("Redirect should preserve the original path in the redirect param")
            .contains("redirect=")
        assertThat(location)
            .describedAs("Redirect should encode the original URL including the path")
            .contains("some-dashboard")
    }
}
