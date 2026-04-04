package com.jorisjonkers.personalstack.systemtests

import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * System test: session security properties.
 *
 * Validates that session cookies have correct security attributes (HttpOnly, path)
 * and that sessions are not created on failed login attempts.
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionSecuritySystemTest {
    private val authBaseUrl = TestHelper.authBaseUrl
    private val appUiUrl = System.getProperty("test.app-ui.url", "https://jorisjonkers.test")

    @Test
    fun `session cookie is HttpOnly`() {
        val user = TestHelper.registerAndConfirm()

        val response =
            TestHelper.givenApi()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"${user.username}","password":"${user.password}"}""")
                .`when`()
                .post("/api/v1/auth/session-login")

        assertThat(response.statusCode).isEqualTo(200)

        val setCookieHeader = response.header("Set-Cookie")
        assertThat(setCookieHeader)
            .describedAs("Set-Cookie header should be present")
            .isNotNull()
        assertThat(setCookieHeader)
            .describedAs("Session cookie should be HttpOnly")
            .containsIgnoringCase("HttpOnly")
    }

    @Test
    fun `session cookie has correct path`() {
        val user = TestHelper.registerAndConfirm()

        val response =
            TestHelper.givenApi()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"${user.username}","password":"${user.password}"}""")
                .`when`()
                .post("/api/v1/auth/session-login")

        assertThat(response.statusCode).isEqualTo(200)

        val setCookieHeader = response.header("Set-Cookie")
        assertThat(setCookieHeader)
            .describedAs("Set-Cookie header should be present")
            .isNotNull()
        assertThat(setCookieHeader)
            .describedAs("Session cookie should have a Path attribute")
            .containsIgnoringCase("Path=")
    }

    @Test
    fun `session login without credentials returns 400`() {
        TestHelper.givenApi()
            .baseUri(authBaseUrl)
            .contentType(ContentType.JSON)
            .body("""{}""")
            .`when`()
            .post("/api/v1/auth/session-login")
            .then()
            .statusCode(400)
    }

    @Test
    fun `session is not created on failed login`() {
        val response =
            TestHelper.givenApi()
                .baseUri(authBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"nonexistent_user","password":"WrongPass1!"}""")
                .`when`()
                .post("/api/v1/auth/session-login")

        assertThat(response.statusCode).isEqualTo(400)

        val sessionCookie = response.cookie("JSESSIONID")
        assertThat(sessionCookie)
            .describedAs("No session cookie should be created on failed login")
            .isNull()
    }

    @Test
    fun `CORS preflight for session-login returns correct headers`() {
        val response =
            TestHelper.givenApi()
                .baseUri(authBaseUrl)
                .header("Origin", appUiUrl)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type")
                .`when`()
                .options("/api/v1/auth/session-login")

        assertThat(response.statusCode).isIn(200, 204)

        val allowOrigin = response.header("Access-Control-Allow-Origin")
        assertThat(allowOrigin)
            .describedAs("CORS should allow the requesting origin")
            .isEqualTo(appUiUrl)

        val allowCredentials = response.header("Access-Control-Allow-Credentials")
        assertThat(allowCredentials)
            .describedAs("CORS should allow credentials for session cookies")
            .isEqualTo("true")
    }
}
