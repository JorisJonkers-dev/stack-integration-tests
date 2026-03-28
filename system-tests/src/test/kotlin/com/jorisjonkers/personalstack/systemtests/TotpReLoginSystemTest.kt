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
 * System test: verifies that an account with TOTP already enabled can
 * sign in repeatedly using the same TOTP secret, and that re-enrollment
 * is rejected once TOTP is active.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TotpReLoginSystemTest {
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
        TestHelper.registerAndConfirm(username = username, password = "ReLogin1!")

    private fun login(username: String): io.restassured.path.json.JsonPath =
        given()
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
        return given()
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

    private fun enrollAndVerifyTotp(accessToken: String): String {
        val secret =
            given()
                .baseUri(authBaseUrl)
                .header("Authorization", "Bearer $accessToken")
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
            .header("Authorization", "Bearer $accessToken")
            .body("""{"code":"${generateTotpCode(secret)}"}""")
            .`when`()
            .post("/api/v1/totp/verify")
            .then()
            .statusCode(204)

        return secret
    }

    @Suppress("LongMethod")
    @Test
    fun `account with TOTP can sign in multiple times with same secret`() {
        val username = "relogin_${UUID.randomUUID().toString().take(8)}"
        registerAndConfirm(username)

        // First login — no TOTP yet, get tokens
        val initialLogin = login(username)
        assertThat(initialLogin.getBoolean("totpRequired")).isFalse()
        val accessToken = initialLogin.getString("accessToken")

        // Enroll and verify TOTP
        val secret = enrollAndVerifyTotp(accessToken)

        // Second login — TOTP required, complete challenge
        val secondLogin = login(username)
        assertThat(secondLogin.getBoolean("totpRequired")).isTrue()
        val secondTokens = completeTotpChallenge(secondLogin.getString("totpChallengeToken"), secret)
        assertThat(secondTokens.getString("accessToken")).isNotBlank()

        // Third login — same TOTP secret still works
        val thirdLogin = login(username)
        assertThat(thirdLogin.getBoolean("totpRequired")).isTrue()
        val thirdTokens = completeTotpChallenge(thirdLogin.getString("totpChallengeToken"), secret)
        assertThat(thirdTokens.getString("accessToken")).isNotBlank()

        // Verify the final token works for forward-auth
        given()
            .baseUri(authBaseUrl)
            .header("Authorization", "Bearer ${thirdTokens.getString("accessToken")}")
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(200)
    }

    @Test
    fun `re-enrollment is rejected when TOTP is already enabled`() {
        val username = "reenroll_${UUID.randomUUID().toString().take(8)}"
        registerAndConfirm(username)

        // Login, enroll, and verify TOTP
        val accessToken = login(username).getString("accessToken")
        enrollAndVerifyTotp(accessToken)

        // Attempt to enroll again — should be rejected
        given()
            .baseUri(authBaseUrl)
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .post("/api/v1/totp/enroll")
            .then()
            .statusCode(400)
    }
}
