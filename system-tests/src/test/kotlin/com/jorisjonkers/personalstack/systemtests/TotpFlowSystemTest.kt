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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * System test: full TOTP two-factor authentication flow.
 *
 * Covers: register -> login (no TOTP) -> enroll TOTP -> verify TOTP ->
 *         re-login (TOTP challenge) -> submit challenge -> obtain tokens.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TotpFlowSystemTest {
    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")

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

    private fun registerAndConfirm(username: String): TestHelper.RegisteredUser =
        TestHelper.registerAndConfirm(username = username, password = "TotpTest1!")

    private fun login(username: String): io.restassured.path.json.JsonPath =
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","password":"TotpTest1!"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()

    @Test
    fun `login without TOTP returns tokens directly`() {
        val username = "totp_no_${UUID.randomUUID().toString().take(8)}"
        registerAndConfirm(username)

        val json = login(username)

        assertThat(json.getBoolean("totpRequired")).isFalse()
        assertThat(json.getString("accessToken")).isNotBlank()
        assertThat(json.getString("refreshToken")).isNotBlank()
    }

    @Suppress("LongMethod")
    @Test
    fun `full TOTP flow - enroll verify and challenge`() {
        val username = "totp_full_${UUID.randomUUID().toString().take(8)}"
        val user = registerAndConfirm(username)

        // Step 1: Login without TOTP — get full tokens
        val initialLogin = login(username)
        assertThat(initialLogin.getBoolean("totpRequired")).isFalse()
        val accessToken = initialLogin.getString("accessToken")
        assertThat(accessToken).isNotBlank()

        // Get session cookie for protected endpoint access
        val session = TestHelper.sessionLogin(user)

        // Step 2: Enroll TOTP
        val enrollJson =
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

        val secret = enrollJson.getString("secret")
        val qrUri = enrollJson.getString("qrUri")
        assertThat(secret).isNotBlank()
        assertThat(qrUri).startsWith("otpauth://totp/")

        // Step 3: Verify TOTP (enables it on the account)
        val verifyCode = generateTotpCode(secret)
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .cookie("SESSION", session.sessionCookie)
            .cookie("XSRF-TOKEN", session.csrfToken)
            .header("X-XSRF-TOKEN", session.csrfToken)
            .body("""{"code":"$verifyCode"}""")
            .`when`()
            .post("/api/v1/totp/verify")
            .then()
            .statusCode(204)

        // Step 4: Login again — should require TOTP challenge
        val secondLogin = login(username)
        assertThat(secondLogin.getBoolean("totpRequired")).isTrue()
        assertThat(secondLogin.getString("totpChallengeToken")).isNotBlank()
        assertThat(secondLogin.getString("accessToken")).isNull()

        // Step 5: Complete TOTP challenge
        val challengeToken = secondLogin.getString("totpChallengeToken")
        val challengeCode = generateTotpCode(secret)

        val challengeJson =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"totpChallengeToken":"$challengeToken","code":"$challengeCode"}""")
                .`when`()
                .post("/api/v1/auth/totp-challenge")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()

        assertThat(challengeJson.getBoolean("totpRequired")).isFalse()
        assertThat(challengeJson.getString("accessToken")).isNotBlank()
        assertThat(challengeJson.getString("refreshToken")).isNotBlank()

        // Step 6: Verify session-based access works for forward-auth
        val totpSessionCookie = TestHelper.sessionLoginAndGetCookie(user, generateTotpCode(secret))
        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", totpSessionCookie)
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(200)
    }

    @Suppress("LongMethod")
    @Test
    fun `TOTP challenge with wrong code returns 400`() {
        val username = "totp_bad_${UUID.randomUUID().toString().take(8)}"
        val user = registerAndConfirm(username)

        val session = TestHelper.sessionLogin(user)

        // Enroll + verify TOTP
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

        // Login again — get challenge
        val challengeToken = login(username).getString("totpChallengeToken")

        // Submit wrong code
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"totpChallengeToken":"$challengeToken","code":"000000"}""")
            .`when`()
            .post("/api/v1/auth/totp-challenge")
            .then()
            .statusCode(400)
    }

    @Test
    fun `TOTP enrollment requires authentication`() {
        given()
            .baseUri(authBaseUrl)
            .`when`()
            .post("/api/v1/totp/enroll")
            .then()
            .statusCode(401)
    }
}
