package com.jorisjonkers.personalstack.systemtests

import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

/**
 * System test: verifies that an account with TOTP already enabled can
 * sign in repeatedly using the same TOTP secret, and that re-enrollment
 * is rejected once TOTP is active.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TotpReLoginSystemTest {
    private val authBaseUrl = TestHelper.authBaseUrl

    private fun generateTotpCode(secret: String): String = TestHelper.generateFreshTotpCode(secret)

    private fun registerAndConfirm(username: String): TestHelper.RegisteredUser =
        TestHelper.registerAndConfirm(username = username, password = "ReLogin1!")

    private fun login(username: String): io.restassured.path.json.JsonPath =
        TestHelper
            .givenApi()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","password":"ReLogin1!"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()

    private fun completeTotpChallenge(
        challengeToken: String,
        secret: String,
    ): io.restassured.path.json.JsonPath {
        val code = generateTotpCode(secret)
        return TestHelper
            .givenApi()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"totpChallengeToken":"$challengeToken","code":"$code"}""")
            .`when`()
            .post("/api/v1/auth/totp-challenge")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
    }

    private fun enrollAndVerifyTotp(session: TestHelper.SessionInfo): String {
        val secret =
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
                .getString("secret")

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

        return secret
    }

    private fun assertTotpLoginSucceeds(
        username: String,
        secret: String,
    ) {
        val login = login(username)
        assertThat(login.getBoolean("totpRequired")).isTrue()
        val tokens = completeTotpChallenge(login.getString("totpChallengeToken"), secret)
        assertThat(tokens.getString("accessToken")).isNotBlank()
    }

    private fun assertForwardAuthSessionWorks(
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
    fun `account with TOTP can sign in multiple times with same secret`() {
        val username = "relogin_${UUID.randomUUID().toString().take(8)}"
        val user = registerAndConfirm(username)

        val session = TestHelper.sessionLogin(user)
        val secret = enrollAndVerifyTotp(session)

        assertTotpLoginSucceeds(username, secret)
        assertTotpLoginSucceeds(username, secret)
        assertForwardAuthSessionWorks(user, secret)
    }

    @Test
    fun `re-enrollment is rejected when TOTP is already enabled`() {
        val username = "reenroll_${UUID.randomUUID().toString().take(8)}"
        val user = registerAndConfirm(username)

        // Session login, enroll, and verify TOTP
        val session = TestHelper.sessionLogin(user)
        enrollAndVerifyTotp(session)

        // Attempt to enroll again with same session — should be rejected
        TestHelper
            .givenApi()
            .baseUri(authBaseUrl)
            .cookie("SESSION", session.sessionCookie)
            .cookie("XSRF-TOKEN", session.csrfToken)
            .header("X-XSRF-TOKEN", session.csrfToken)
            .`when`()
            .post("/api/v1/totp/enroll")
            .then()
            .statusCode(400)
    }
}
