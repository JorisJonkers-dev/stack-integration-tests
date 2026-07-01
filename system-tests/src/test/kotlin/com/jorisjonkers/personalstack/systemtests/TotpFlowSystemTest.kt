package com.jorisjonkers.personalstack.systemtests

import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

/**
 * System test: full TOTP two-factor authentication flow.
 *
 * Covers: register -> login (no TOTP) -> enroll TOTP -> verify TOTP ->
 *         re-login (TOTP challenge) -> submit challenge -> obtain tokens.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TotpFlowSystemTest {
    private val authBaseUrl = TestHelper.authBaseUrl

    private data class EnrolledTotp(
        val secret: String,
        val qrUri: String,
    )

    private fun generateTotpCode(secret: String): String = TestHelper.generateFreshTotpCode(secret)

    private fun registerAndConfirm(username: String): TestHelper.RegisteredUser =
        TestHelper.registerAndConfirm(username = username, password = "TotpTest1!")

    private fun login(username: String): io.restassured.path.json.JsonPath =
        TestHelper
            .givenApi()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","password":"TotpTest1!"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()

    private fun enrollAndVerifyTotp(session: TestHelper.SessionInfo): EnrolledTotp {
        val enrollJson =
            TestHelper
                .givenApi()
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

        TestHelper
            .givenApi()
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

        return EnrolledTotp(secret, enrollJson.getString("qrUri"))
    }

    private fun completeTotpChallenge(
        challengeToken: String,
        secret: String,
    ): io.restassured.path.json.JsonPath =
        TestHelper
            .givenApi()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"totpChallengeToken":"$challengeToken","code":"${generateTotpCode(secret)}"}""")
            .`when`()
            .post("/api/v1/auth/totp-challenge")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()

    private fun verifyForwardAuthSession(
        user: TestHelper.RegisteredUser,
        secret: String,
    ) {
        val totpSessionCookie = TestHelper.sessionLoginAndGetCookie(user, TestHelper.generateFreshTotpCode(secret))
        TestHelper
            .givenApi()
            .baseUri(authBaseUrl)
            .cookie("SESSION", totpSessionCookie)
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(200)
    }

    @Test
    fun `login without TOTP returns tokens directly`() {
        val username = "totp_no_${UUID.randomUUID().toString().take(8)}"
        registerAndConfirm(username)

        val json = login(username)

        assertThat(json.getBoolean("totpRequired")).isFalse()
        assertThat(json.getString("accessToken")).isNotBlank()
        assertThat(json.getString("refreshToken")).isNotBlank()
    }

    @Test
    fun `full TOTP flow - enroll verify and challenge`() {
        val username = "totp_full_${UUID.randomUUID().toString().take(8)}"
        val user = registerAndConfirm(username)

        val initialLogin = login(username)
        assertThat(initialLogin.getBoolean("totpRequired")).isFalse()
        val accessToken = initialLogin.getString("accessToken")
        assertThat(accessToken).isNotBlank()

        val session = TestHelper.sessionLogin(user)
        val enrolledTotp = enrollAndVerifyTotp(session)
        assertThat(enrolledTotp.secret).isNotBlank()
        assertThat(enrolledTotp.qrUri).startsWith("otpauth://totp/")

        val secondLogin = login(username)
        assertThat(secondLogin.getBoolean("totpRequired")).isTrue()
        assertThat(secondLogin.getString("totpChallengeToken")).isNotBlank()
        assertThat(secondLogin.getString("accessToken")).isNull()

        val challengeToken = secondLogin.getString("totpChallengeToken")
        val challengeJson = completeTotpChallenge(challengeToken, enrolledTotp.secret)

        assertThat(challengeJson.getBoolean("totpRequired")).isFalse()
        assertThat(challengeJson.getString("accessToken")).isNotBlank()
        assertThat(challengeJson.getString("refreshToken")).isNotBlank()

        verifyForwardAuthSession(user, enrolledTotp.secret)
    }

    @Test
    fun `TOTP challenge with wrong code returns 400`() {
        val username = "totp_bad_${UUID.randomUUID().toString().take(8)}"
        val user = registerAndConfirm(username)

        val session = TestHelper.sessionLogin(user)
        enrollAndVerifyTotp(session)

        val challengeToken = login(username).getString("totpChallengeToken")

        TestHelper
            .givenApi()
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
        TestHelper
            .givenApi()
            .baseUri(authBaseUrl)
            .`when`()
            .post("/api/v1/totp/enroll")
            .then()
            .statusCode(401)
    }
}
