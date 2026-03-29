package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

/**
 * System test: user registration, email confirmation, and login flow.
 *
 * Covers: full registration flow, duplicate username/email handling,
 * login without confirmation, and confirmation token resend.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistrationFlowSystemTest {
    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")

    @Test
    fun `full registration confirm and login flow`() {
        val username = "reg_full_${UUID.randomUUID().toString().take(8)}"
        val email = "$username@systemtest.example.com"
        val password = "Test1234!"

        // Step 1: Register
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","email":"$email","password":"$password"}""")
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        // Step 2: Confirm email
        val confirmToken = TestHelper.getConfirmationTokenFromDb(username)
        assertThat(confirmToken).isNotBlank()

        given()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/api/v1/auth/confirm-email?token=$confirmToken")
            .then()
            .statusCode(200)

        // Step 3: Session login
        val sessionResponse =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"$username","password":"$password"}""")
                .`when`()
                .post("/api/v1/auth/session-login")

        assertThat(sessionResponse.statusCode).isEqualTo(200)
        val sessionCookie = sessionResponse.cookie("SESSION")
        assertThat(sessionCookie).isNotBlank()

        // Step 4: Verify session works
        given()
            .baseUri(authBaseUrl)
            .cookie("SESSION", sessionCookie)
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(200)
    }

    @Test
    fun `registration with duplicate username returns 400`() {
        val username = "dup_user_${UUID.randomUUID().toString().take(8)}"
        val body1 = """{"username":"$username","email":"$username@test.com","password":"Test1234!"}"""
        val body2 = """{"username":"$username","email":"${username}_2@test.com","password":"Test1234!"}"""

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body(body1)
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body(body2)
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(400)
    }

    @Test
    fun `registration with duplicate email returns 400`() {
        val email = "dup_email_${UUID.randomUUID().toString().take(8)}@test.com"
        val body1 = """{"username":"dup_e1_${UUID.randomUUID().toString().take(
            8,
        )}","email":"$email","password":"Test1234!"}"""
        val body2 = """{"username":"dup_e2_${UUID.randomUUID().toString().take(
            8,
        )}","email":"$email","password":"Test1234!"}"""

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body(body1)
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body(body2)
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(400)
    }

    @Test
    fun `login without email confirmation returns 400`() {
        val username = "unconf_reg_${UUID.randomUUID().toString().take(8)}"

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
            .post("/api/v1/auth/login")
            .then()
            .statusCode(400)
    }

    @Test
    fun `resend confirmation generates new token`() {
        val username = "resend_${UUID.randomUUID().toString().take(8)}"
        val email = "$username@systemtest.example.com"

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","email":"$email","password":"Test1234!"}""")
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        val firstToken = TestHelper.getConfirmationTokenFromDb(username)
        assertThat(firstToken).isNotBlank()

        // Resend confirmation
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"email":"$email"}""")
            .`when`()
            .post("/api/v1/auth/resend-confirmation")
            .then()
            .statusCode(200)

        val secondToken = TestHelper.getConfirmationTokenFromDb(username)
        assertThat(secondToken).isNotBlank()
        assertThat(secondToken)
            .describedAs("Resend should generate a new confirmation token")
            .isNotEqualTo(firstToken)

        // Confirm with new token and login
        given()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/api/v1/auth/confirm-email?token=$secondToken")
            .then()
            .statusCode(200)

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"$username","password":"Test1234!"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
    }
}
