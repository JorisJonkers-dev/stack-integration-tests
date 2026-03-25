package com.jorisjonkers.privatestack.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

/**
 * System test: full user registration and token flow against running services.
 * Run with Docker Compose up or against a real environment.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthFlowSystemTest {

    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")
    private val username = "systemtest_${UUID.randomUUID().toString().take(8)}"
    private val email = "$username@systemtest.example.com"
    private val password = "SystemTest1!"

    @Test
    fun `register user and receive token successfully`() {
        // Step 1: Register
        val registerBody = """{"username":"$username","email":"$email","password":"$password"}"""
        given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body(registerBody)
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        // Step 2: Login — should get access token
        val loginBody = """{"username":"$username","password":"$password"}"""
        val tokenResponse = given()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body(loginBody)
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()

        val accessToken = tokenResponse.getString("accessToken")
        assertThat(accessToken).isNotBlank()

        // Step 3: Verify token via forward-auth endpoint
        given()
            .baseUri(authBaseUrl)
            .header("Authorization", "Bearer $accessToken")
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(200)

        // Step 4: Refresh token
        val refreshToken = tokenResponse.getString("refreshToken")
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
        val fixedUsername = "duptest_${UUID.randomUUID().toString().take(8)}"
        val body = """{"username":"$fixedUsername","email":"$fixedUsername@test.com","password":"Test1234!"}"""

        given().baseUri(authBaseUrl).contentType(ContentType.JSON).body(body).`when`()
            .post("/api/v1/users/register").then().statusCode(201)

        given().baseUri(authBaseUrl).contentType(ContentType.JSON).body(body).`when`()
            .post("/api/v1/users/register").then().statusCode(400)
    }

    @Test
    fun `protected endpoint rejects request without token`() {
        given()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(401)
    }
}
