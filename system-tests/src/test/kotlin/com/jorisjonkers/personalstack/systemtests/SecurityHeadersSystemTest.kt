package com.jorisjonkers.personalstack.systemtests

import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * System tests that verify security headers are present on all UI routes
 * served through Traefik, matching production middleware configuration.
 */
@Tag("system")
class SecurityHeadersSystemTest {
    companion object {
        @JvmStatic
        fun uiEndpoints(): Stream<Arguments> =
            Stream.of(
                Arguments.of("app-ui", "http://localhost:80", "/"),
                Arguments.of("auth-ui", "http://auth.localhost:80", "/"),
                Arguments.of("assistant-ui", "http://assistant.localhost:80", "/"),
            )
    }

    @ParameterizedTest(name = "{0} returns Content-Security-Policy header")
    @MethodSource("uiEndpoints")
    fun `UI route includes Content-Security-Policy header`(
        @Suppress("UNUSED_PARAMETER") label: String,
        baseUrl: String,
        path: String,
    ) {
        val csp =
            given()
                .baseUri(baseUrl)
                .`when`()
                .get(path)
                .then()
                .statusCode(200)
                .extract()
                .header("Content-Security-Policy")

        assertThat(csp)
            .describedAs("Content-Security-Policy header should be present")
            .isNotNull()
            .isNotBlank()

        val scriptSrc =
            csp
                .split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("script-src") } ?: ""

        assertThat(scriptSrc)
            .describedAs("CSP script-src must not allow unsafe-inline")
            .doesNotContain("'unsafe-inline'")

        assertThat(csp)
            .describedAs("CSP img-src must not reference external QR API")
            .doesNotContain("api.qrserver.com")
    }

    @ParameterizedTest(name = "{0} returns X-Content-Type-Options header")
    @MethodSource("uiEndpoints")
    fun `UI route includes X-Content-Type-Options nosniff`(
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
            .header("X-Content-Type-Options", "nosniff")
    }

    @ParameterizedTest(name = "{0} returns X-Frame-Options header")
    @MethodSource("uiEndpoints")
    fun `UI route includes X-Frame-Options DENY`(
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
            .header("X-Frame-Options", "DENY")
    }

    @ParameterizedTest(name = "{0} returns Referrer-Policy header")
    @MethodSource("uiEndpoints")
    fun `UI route includes Referrer-Policy header`(
        @Suppress("UNUSED_PARAMETER") label: String,
        baseUrl: String,
        path: String,
    ) {
        val referrerPolicy =
            given()
                .baseUri(baseUrl)
                .`when`()
                .get(path)
                .then()
                .statusCode(200)
                .extract()
                .header("Referrer-Policy")

        assertThat(referrerPolicy)
            .describedAs("Referrer-Policy header should be present")
            .isNotNull()
            .isNotBlank()
    }
}
