package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.stream.Stream

@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DownstreamOidcAuthorizationSystemTest {
    companion object {
        @JvmStatic
        fun oidcClients(): Stream<Arguments> =
            Stream.of(
                Arguments.of("grafana", "https://grafana.jorisjonkers.test/login/generic_oauth", "openid profile email", false),
                Arguments.of("vault", "https://vault.jorisjonkers.test/ui/vault/auth/oidc/oidc/callback", "openid profile email", false),
                Arguments.of("n8n", "https://n8n.jorisjonkers.test/auth/oidc/callback", "openid profile email", false),
                Arguments.of(
                    "rabbitmq",
                    "https://rabbitmq.jorisjonkers.test/js/oidc-oauth/login-callback.html",
                    "openid profile email rabbitmq.tag:administrator",
                    true,
                ),
            )
    }

    private fun authorize(
        sessionCookie: String,
        clientId: String,
        redirectUri: String,
        scope: String,
        requiresPkce: Boolean,
    ) = given()
        .relaxedHTTPSValidation()
        .baseUri("https://auth.jorisjonkers.test")
        .cookie("SESSION", sessionCookie)
        .accept("text/html")
        .redirects()
        .follow(false)
        .queryParam("response_type", "code")
        .queryParam("client_id", clientId)
        .queryParam("redirect_uri", redirectUri)
        .queryParam("scope", scope)
        .queryParam("state", "system-$clientId")
        .apply {
            if (requiresPkce) {
                val codeVerifier = generateCodeVerifier()
                queryParam("code_challenge", generateCodeChallenge(codeVerifier))
                queryParam("code_challenge_method", "S256")
            }
        }.`when`()
        .get("/api/oauth2/authorize")

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    @ParameterizedTest(name = "{0}: ADMIN can authorize the downstream client")
    @MethodSource("oidcClients")
    fun `admin sessions can authorize downstream oidc clients`(
        clientId: String,
        redirectUri: String,
        scope: String,
        requiresPkce: Boolean,
    ) {
        val adminSession = TestHelper.registerConfirmAndGetAdminSession()

        val response = authorize(adminSession.sessionCookie, clientId, redirectUri, scope, requiresPkce)

        assertThat(response.statusCode).isEqualTo(302)
        assertThat(response.header("Location"))
            .startsWith(redirectUri)
            .contains("code=")
    }

    @ParameterizedTest(name = "{0}: USER without permission is rejected by OAuth authorization")
    @MethodSource("oidcClients")
    fun `users without service permission are rejected by downstream oidc authorization`(
        clientId: String,
        redirectUri: String,
        scope: String,
        requiresPkce: Boolean,
    ) {
        val userSession = TestHelper.registerConfirmAndGetSession()

        val response = authorize(userSession.sessionCookie, clientId, redirectUri, scope, requiresPkce)

        assertThat(response.statusCode).isIn(401, 403)
        assertThat(response.header("Location").orEmpty()).doesNotContain("code=")
    }
}
