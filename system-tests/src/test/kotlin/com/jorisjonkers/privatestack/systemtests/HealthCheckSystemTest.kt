package com.jorisjonkers.privatestack.systemtests

import io.restassured.RestAssured
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
@Disabled("Enable when services are deployed")
class HealthCheckSystemTest {

    @Test
    fun `auth-api health endpoint responds`() {
        RestAssured.given()
            .baseUri("http://localhost:8081")
            .`when`()
            .get("/api/v1/health")
            .then()
            .statusCode(200)
    }

    @Test
    fun `assistant-api health endpoint responds`() {
        RestAssured.given()
            .baseUri("http://localhost:8082")
            .`when`()
            .get("/api/v1/health")
            .then()
            .statusCode(200)
    }
}
