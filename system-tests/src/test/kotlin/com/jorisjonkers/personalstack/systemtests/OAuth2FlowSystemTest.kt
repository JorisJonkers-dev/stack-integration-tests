package com.jorisjonkers.personalstack.systemtests

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.apache.commons.codec.binary.Base32
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * System test: OAuth2 session-login and PKCE authorization code flow.
 *
 * Covers: session-login (with and without TOTP), session cookie creation,
 * OAuth2 authorize redirect with PKCE, and full token exchange.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OAuth2FlowSystemTest {
    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun generateTotpCode(secret: String): String {
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

    @Test
    fun `session login returns 200 with success for valid credentials`() {
        val user = TestHelper.registerAndConfirm()

        val json =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"${user.username}","password":"${user.password}"}""")
                .`when`()
                .post("/api/v1/auth/session-login")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()

        assertThat(json.getBoolean("success")).isTrue()
        assertThat(json.getBoolean("totpRequired")).isFalse()
    }

    @Suppress("LongMethod")
    @Test
    fun `session login returns totpRequired when TOTP is enabled`() {
        val username = "oauth_totp_${UUID.randomUUID().toString().take(8)}"
        val user = TestHelper.registerAndConfirm(username = username)
        val session = TestHelper.sessionLogin(user)

        // Enroll TOTP
        val secret =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", session.sessionCookie)
                .cookie("XSRF-TOKEN", session.csrfToken)
                .header("X-XSRF-TOKEN", session.csrfToken)
                .`when`()
                .post("/api/v1/totp/enroll")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("secret")

        // Verify TOTP to enable it
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .cookie("SESSION", session.sessionCookie)
            .cookie("XSRF-TOKEN", session.csrfToken)
            .header("X-XSRF-TOKEN", session.csrfToken)
            .body("""{"code":"${generateTotpCode(secret)}"}""")
            .`when`()
            .post("/api/v1/totp/verify")
            .then()
            .statusCode(204)

        // Session login without TOTP code should return totpRequired
        val json =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"${user.username}","password":"${user.password}"}""")
                .`when`()
                .post("/api/v1/auth/session-login")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()

        assertThat(json.getBoolean("totpRequired")).isTrue()
        assertThat(json.getBoolean("success")).isFalse()
    }

    @Suppress("LongMethod")
    @Test
    fun `session login with TOTP code creates valid session`() {
        val username = "oauth_totp2_${UUID.randomUUID().toString().take(8)}"
        val user = TestHelper.registerAndConfirm(username = username)
        val session = TestHelper.sessionLogin(user)

        // Enroll and verify TOTP
        val secret =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", session.sessionCookie)
                .cookie("XSRF-TOKEN", session.csrfToken)
                .header("X-XSRF-TOKEN", session.csrfToken)
                .`when`()
                .post("/api/v1/totp/enroll")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("secret")

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .cookie("SESSION", session.sessionCookie)
            .cookie("XSRF-TOKEN", session.csrfToken)
            .header("X-XSRF-TOKEN", session.csrfToken)
            .body("""{"code":"${generateTotpCode(secret)}"}""")
            .`when`()
            .post("/api/v1/totp/verify")
            .then()
            .statusCode(204)

        // Session login with TOTP code should succeed
        val json =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body(
                    """{"username":"${user.username}","password":"${user.password}",""" +
                        """"totpCode":"${generateTotpCode(secret)}"}""",
                ).`when`()
                .post("/api/v1/auth/session-login")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()

        assertThat(json.getBoolean("success")).isTrue()
        assertThat(json.getBoolean("totpRequired")).isFalse()
    }

    @Test
    fun `session login with invalid credentials returns 400`() {
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"nonexistent_user","password":"WrongPass1!"}""")
            .`when`()
            .post("/api/v1/auth/session-login")
            .then()
            .statusCode(400)
    }

    @Test
    fun `session login with unconfirmed email returns 400`() {
        val username = "unconf_sess_${UUID.randomUUID().toString().take(8)}"

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","email":"$username@test.com","password":"Test1234!"}""")
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","password":"Test1234!"}""")
            .`when`()
            .post("/api/v1/auth/session-login")
            .then()
            .statusCode(400)
    }

    @Test
    fun `session login creates session cookie`() {
        val user = TestHelper.registerAndConfirm()

        val response =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"${user.username}","password":"${user.password}"}""")
                .`when`()
                .post("/api/v1/auth/session-login")

        assertThat(response.statusCode).isEqualTo(200)

        val sessionCookie = response.cookie("SESSION")
        assertThat(sessionCookie)
            .describedAs("Session login should create a SESSION cookie")
            .isNotNull()
            .isNotBlank()
    }

    @Test
    fun `session login with blank username returns validation error`() {
        val status =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"","password":"Test1234!"}""")
                .`when`()
                .post("/api/v1/auth/session-login")
                .statusCode()
        // 422 for blank field (Bean Validation) or 400 for malformed body
        assertThat(status).isIn(400, 422)
    }

    @Suppress("LongMethod")
    @Test
    fun `session login with TOTP enabled but wrong code returns 400`() {
        val username = "oauth_bad_${UUID.randomUUID().toString().take(8)}"
        val user = TestHelper.registerAndConfirm(username = username)
        val session = TestHelper.sessionLogin(user)

        // Enroll and verify TOTP
        val secret =
            given()
                .baseUri(authBaseUrl)
                .cookie("SESSION", session.sessionCookie)
                .cookie("XSRF-TOKEN", session.csrfToken)
                .header("X-XSRF-TOKEN", session.csrfToken)
                .`when`()
                .post("/api/v1/totp/enroll")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("secret")

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .cookie("SESSION", session.sessionCookie)
            .cookie("XSRF-TOKEN", session.csrfToken)
            .header("X-XSRF-TOKEN", session.csrfToken)
            .body("""{"code":"${generateTotpCode(secret)}"}""")
            .`when`()
            .post("/api/v1/totp/verify")
            .then()
            .statusCode(204)

        // Session login with wrong TOTP code
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"${user.username}","password":"${user.password}","totpCode":"000000"}""")
            .`when`()
            .post("/api/v1/auth/session-login")
            .then()
            .statusCode(400)
    }

    @Test
    fun `OAuth2 authorize with valid session redirects with code`() {
        val user = TestHelper.registerAndConfirm()

        // Step 1: Session login to get session cookie
        val sessionResponse =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"${user.username}","password":"${user.password}"}""")
                .`when`()
                .post("/api/v1/auth/session-login")

        assertThat(sessionResponse.statusCode).isEqualTo(200)
        val sessionCookie = sessionResponse.cookie("SESSION")
        assertThat(sessionCookie).isNotNull()

        // Step 2: OAuth2 authorize with PKCE
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

        // Should redirect with authorization code
        assertThat(authorizeResponse.statusCode).isIn(302, 303)

        val location = authorizeResponse.header("Location")
        assertThat(location)
            .describedAs("OAuth2 authorize should redirect to callback with code")
            .contains("code=")
        assertThat(location).contains("state=test-state")
    }

    @Suppress("LongMethod")
    @Test
    fun `full OAuth2 PKCE flow - session login to token exchange`() {
        val user = TestHelper.registerAndConfirm()

        // Step 1: Session login
        val sessionResponse =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"${user.username}","password":"${user.password}"}""")
                .`when`()
                .post("/api/v1/auth/session-login")

        assertThat(sessionResponse.statusCode).isEqualTo(200)
        val sessionCookie = sessionResponse.cookie("SESSION")
        assertThat(sessionCookie).isNotNull()

        // Step 2: OAuth2 authorize with PKCE
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

        // Extract authorization code from redirect URI
        val code =
            java.net
                .URI(location)
                .query
                .split("&")
                .associate { it.split("=", limit = 2).let { kv -> kv[0] to kv[1] } }["code"]
        assertThat(code).isNotNull().isNotBlank()

        // Step 3: Exchange code for tokens
        val tokenJson =
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

        assertThat(tokenJson.getString("access_token")).isNotBlank()
        assertThat(tokenJson.getString("token_type")).isEqualToIgnoringCase("Bearer")
    }
}
