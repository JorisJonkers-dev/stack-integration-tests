package com.jorisjonkers.personalstack.systemtests

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * System tests that verify API health check endpoints are publicly
 * accessible through Traefik (no authentication required).
 *
 * These tests use virtual-host URLs to go through Traefik routing,
 * ensuring Uptime Kuma and similar monitoring tools can reach them.
 */
@Tag("system")
class TraefikHealthCheckSystemTest {
    private fun traefikRequest() = TestHelper.givenApi()

    companion object {
        @JvmStatic
        fun publicActuatorEndpoints(): Stream<Arguments> =
            Stream.of(
                Arguments.of("auth-api /actuator/health", "https://auth.jorisjonkers.test", "/api/actuator/health"),
            )

        @JvmStatic
        fun livenessEndpoints(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "auth-api /actuator/health/liveness",
                    "https://auth.jorisjonkers.test",
                    "/api/actuator/health/liveness",
                ),
            )

        @JvmStatic
        fun v1HealthEndpoints(): Stream<Arguments> =
            Stream.of(
                Arguments.of("auth-api /v1/health", "https://auth.jorisjonkers.test", "/api/v1/health", "auth-api"),
            )

        @JvmStatic
        fun allHealthEndpoints(): Stream<Arguments> =
            Stream.concat(
                Stream.concat(publicActuatorEndpoints(), livenessEndpoints()).map { args ->
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
    @MethodSource("publicActuatorEndpoints")
    fun `actuator health responds without authentication through Traefik`(
        label: String,
        baseUrl: String,
        path: String,
    ) {
        val response =
            traefikRequest()
                .baseUri(baseUrl)
                .`when`()
                .get(path)

        assertThat(response.statusCode)
            .describedAs("$label should be publicly reachable")
            .isIn(200, 503)
        response
            .then()
            .body("status", not(nullValue()))
    }

    @ParameterizedTest(name = "{0} actuator liveness is publicly accessible through Traefik")
    @MethodSource("livenessEndpoints")
    fun `actuator liveness responds 200 without authentication through Traefik`(
        label: String,
        baseUrl: String,
        path: String,
    ) {
        val response =
            traefikRequest()
                .baseUri(baseUrl)
                .`when`()
                .get(path)

        assertThat(response.statusCode)
            .describedAs("$label should be live")
            .isEqualTo(200)
        response
            .then()
            .body("status", equalTo("UP"))
    }

    @ParameterizedTest(name = "{0} is publicly accessible through Traefik")
    @MethodSource("v1HealthEndpoints")
    fun `v1 health responds 200 without authentication through Traefik`(
        label: String,
        baseUrl: String,
        path: String,
        serviceName: String,
    ) {
        val response =
            traefikRequest()
                .baseUri(baseUrl)
                .`when`()
                .get(path)

        assertThat(response.statusCode)
            .describedAs("$label should return OK")
            .isEqualTo(200)
        response
            .then()
            .body("status", equalTo("ok"))
            .body("service", equalTo(serviceName))
    }

    @ParameterizedTest(name = "{0} does NOT redirect to login")
    @MethodSource("allHealthEndpoints")
    fun `health endpoint does not trigger forward-auth redirect`(
        label: String,
        baseUrl: String,
        path: String,
    ) {
        val response =
            traefikRequest()
                .baseUri(baseUrl)
                .redirects()
                .follow(false)
                .`when`()
                .get(path)

        // Should be 200 (direct response), not 302 (redirect to login)
        assertThat(response.statusCode)
            .describedAs("$label should not redirect to login at ${response.header("Location")}")
            .isNotEqualTo(302)
    }
}
