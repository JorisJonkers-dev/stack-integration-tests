package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

/**
 * System test: full user registration and token flow against running services.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthFlowSystemTest {
    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")

    @Test
    fun `register confirm and login successfully`() {
        val user = TestHelper.registerAndConfirm()
        val token = TestHelper.loginAndGetToken(user)

        assertThat(token).isNotBlank()

        given()
            .baseUri(authBaseUrl)
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(200)
    }

    @Test
    fun `login without email confirmation returns 400`() {
        val username = "unconf_${UUID.randomUUID().toString().take(8)}"

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
    fun `token refresh works after confirm and login`() {
        val user = TestHelper.registerAndConfirm()

        val loginJson =
            given()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"${user.username}","password":"${user.password}"}""")
                .`when`()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()

        val refreshToken = loginJson.getString("refreshToken")
        assertThat(refreshToken).isNotBlank()

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{"refreshToken":"$refreshToken"}""")
            .`when`()
            .post("/api/v1/auth/refresh")
            .then()
            .statusCode(200)
    }

    @Test
    fun `duplicate registration returns 400`() {
        val user = "duptest_${UUID.randomUUID().toString().take(8)}"
        val body = """{"username":"$user","email":"$user@test.com","password":"Test1234!"}"""

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(400)
    }

    @Test
    fun `verify endpoint redirects to login without token`() {
        given()
            .baseUri(authBaseUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(302)
            .header("Location", org.hamcrest.Matchers.containsString("/login?redirect="))
    }

    @Test
    fun `other protected endpoints reject request without token`() {
        given()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/api/v1/users/me")
            .then()
            .statusCode(401)
    }
}
