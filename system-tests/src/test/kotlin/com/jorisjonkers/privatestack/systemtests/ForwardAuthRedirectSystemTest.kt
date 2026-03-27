package com.jorisjonkers.privatestack.systemtests

import io.restassured.RestAssured.given
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * System test: verifies that the forward-auth verify endpoint redirects
 * unauthenticated requests to the login page with the correct redirect URL.
 */
@Tag("system")
class ForwardAuthRedirectSystemTest {
    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")

    @Test
    fun `verify endpoint returns 302 redirect to login when unauthenticated`() {
        given()
            .baseUri(authBaseUrl)
            .redirects()
            .follow(false)
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(302)
            .header("Location", containsString("/login?redirect="))
    }

    @Test
    fun `verify endpoint includes original URL from forwarded headers in redirect`() {
        given()
            .baseUri(authBaseUrl)
            .redirects()
            .follow(false)
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "grafana.jorisjonkers.dev")
            .header("X-Forwarded-Uri", "/d/dashboard")
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(302)
            .header("Location", containsString("redirect=https%3A%2F%2Fgrafana.jorisjonkers.dev%2Fd%2Fdashboard"))
    }

    @Test
    fun `verify endpoint does not redirect with valid token`() {
        val token = registerAndLogin()

        given()
            .baseUri(authBaseUrl)
            .header("Authorization", "Bearer $token")
            .`when`()
            .get("/api/v1/auth/verify")
            .then()
            .statusCode(200)
    }

    private fun registerAndLogin(): String {
        val username = "fwdauth_${java.util.UUID.randomUUID().toString().take(8)}"

        given()
            .baseUri(authBaseUrl)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("""{"username":"$username","email":"$username@test.com","password":"Test1234!"}""")
            .`when`()
            .post("/api/v1/users/register")
            .then()
            .statusCode(201)

        return given()
            .baseUri(authBaseUrl)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("""{"username":"$username","password":"Test1234!"}""")
            .`when`()
            .post("/api/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("accessToken")
    }
}
