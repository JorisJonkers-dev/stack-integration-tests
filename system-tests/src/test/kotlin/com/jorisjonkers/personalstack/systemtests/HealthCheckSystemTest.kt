package com.jorisjonkers.personalstack.systemtests

import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class HealthCheckSystemTest {
    private val authBaseUrl = TestHelper.authBaseUrl
    private val assistantBaseUrl = TestHelper.assistantBaseUrl

    @Test
    fun `auth-api health endpoint responds`() {
        TestHelper.givenApi()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/api/actuator/health")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(503)))
    }

    @Test
    fun `assistant-api health endpoint responds`() {
        TestHelper.givenApi()
            .baseUri(assistantBaseUrl)
            .`when`()
            .get("/api/actuator/health")
            .then()
            .statusCode(200)
    }

    @Test
    fun `auth-api v1 health endpoint responds`() {
        TestHelper.givenApi()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/api/v1/health")
            .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("ok"))
            .body("service", org.hamcrest.Matchers.equalTo("auth-api"))
    }

    @Test
    fun `assistant-api v1 health endpoint responds`() {
        TestHelper.givenApi()
            .baseUri(assistantBaseUrl)
            .`when`()
            .get("/api/v1/health")
            .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("ok"))
            .body("service", org.hamcrest.Matchers.equalTo("assistant-api"))
    }

    @Test
    fun `auth-api oidc discovery is accessible`() {
        TestHelper.givenApi()
            .baseUri(authBaseUrl)
            .`when`()
            .get("/.well-known/openid-configuration")
            .then()
            .statusCode(200)
    }
}
