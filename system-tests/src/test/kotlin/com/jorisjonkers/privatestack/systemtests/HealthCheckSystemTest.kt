package com.jorisjonkers.privatestack.systemtests

import io.restassured.RestAssured.given
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class HealthCheckSystemTest {

    private val authBaseUrl = System.getProperty("test.auth-api.url", "http://localhost:8081")
    private val assistantBaseUrl = System.getProperty("test.assistant-api.url", "http://localhost:8082")

    @Test
    fun `auth-api health endpoint responds`() {
        given()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/actuator/health")
            .then()
            .statusCode(200)
    }

    @Test
    fun `assistant-api health endpoint responds`() {
        given()
            .baseUri(assistantBaseUrl)
            .`when`()
            .get("/actuator/health")
            .then()
            .statusCode(200)
    }

    @Test
    fun `auth-api oidc discovery is accessible`() {
        given()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/.well-known/openid-configuration")
            .then()
            .statusCode(200)
    }
}
