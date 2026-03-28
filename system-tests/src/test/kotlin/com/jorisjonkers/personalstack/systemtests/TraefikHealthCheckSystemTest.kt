package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * System tests that verify health check endpoints are publicly accessible
 * through Traefik (no authentication required).
 *
 * These tests use virtual-host URLs to go through Traefik routing,
 * ensuring Uptime Kuma and similar monitoring tools can reach them.
 */
@Tag("system")
class TraefikHealthCheckSystemTest {
    companion object {
        @JvmStatic
        fun actuatorEndpoints(): Stream<Arguments> =
            Stream.of(
                Arguments.of("auth-api /actuator/health", "http://auth.localhost:80", "/api/actuator/health"),
                Arguments.of("assistant-api /actuator/health", "http://app.localhost:80", "/api/actuator/health"),
            )

        @JvmStatic
        fun v1HealthEndpoints(): Stream<Arguments> =
            Stream.of(
                Arguments.of("auth-api /v1/health", "http://auth.localhost:80", "/api/v1/health", "auth-api"),
                Arguments.of("assistant-api /v1/health", "http://app.localhost:80", "/api/v1/health", "assistant-api"),
            )

        @JvmStatic
        fun allHealthEndpoints(): Stream<Arguments> =
            Stream.concat(
                actuatorEndpoints().map { args ->
                    val arr = args.get()
                    Arguments.of(arr[0], arr[1], arr[2])
                },
                v1HealthEndpoints().map { args ->
                    val arr = args.get()
                    Arguments.of(arr[0], arr[1], arr[2])
                },
            )
    }

    @ParameterizedTest(name = "{0} actuator is publicly accessible through Traefik")
    @MethodSource("actuatorEndpoints")
    fun `actuator health responds 200 without authentication through Traefik`(
        @Suppress("UNUSED_PARAMETER") label: String,
        baseUrl: String,
        path: String,
    ) {
        given()
            .baseUri(baseUrl)
            .`when`()
            .get(path)
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }

    @ParameterizedTest(name = "{0} is publicly accessible through Traefik")
    @MethodSource("v1HealthEndpoints")
    fun `v1 health responds 200 without authentication through Traefik`(
        @Suppress("UNUSED_PARAMETER") label: String,
        baseUrl: String,
        path: String,
        serviceName: String,
    ) {
        given()
            .baseUri(baseUrl)
            .`when`()
            .get(path)
            .then()
            .statusCode(200)
            .body("status", equalTo("ok"))
            .body("service", equalTo(serviceName))
    }

    @ParameterizedTest(name = "{0} does NOT redirect to login")
    @MethodSource("allHealthEndpoints")
    fun `health endpoint does not trigger forward-auth redirect`(
        @Suppress("UNUSED_PARAMETER") label: String,
        baseUrl: String,
        path: String,
    ) {
        val response =
            given()
                .baseUri(baseUrl)
                .redirects()
                .follow(false)
                .`when`()
                .get(path)

        // Should be 200 (direct response), not 302 (redirect to login)
        assert(response.statusCode != 302) {
            "Expected health endpoint to NOT redirect, but got 302 to: ${response.header("Location")}"
        }
    }
}
