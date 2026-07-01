package com.jorisjonkers.personalstack.systemtests

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
    private fun traefikRequest() = TestHelper.givenApi()

    companion object {
        @JvmStatic
        fun uiEndpoints(): Stream<Arguments> =
            Stream.of(
                Arguments.of("app-ui", "https://jorisjonkers.test", "/"),
                Arguments.of("auth-ui", "https://auth.jorisjonkers.test", "/"),
            )
    }

    @ParameterizedTest(name = "{0} returns Content-Security-Policy header")
    @MethodSource("uiEndpoints")
    fun `UI route includes Content-Security-Policy header`(
        label: String,
        baseUrl: String,
        path: String,
    ) {
        val csp =
            traefikRequest()
                .baseUri(baseUrl)
                .`when`()
                .get(path)
                .then()
                .statusCode(200)
                .extract()
                .header("Content-Security-Policy")

        assertThat(csp)
            .describedAs("$label Content-Security-Policy header should be present")
            .isNotNull()
            .isNotBlank()

        val scriptSrc =
            csp
                .split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("script-src") }
                .orEmpty()

        assertThat(scriptSrc)
            .describedAs("$label CSP script-src must include 'self'")
            .contains("'self'")

        assertThat(csp)
            .describedAs("$label CSP img-src must not reference external QR API")
            .doesNotContain("api.qrserver.com")
    }

    @ParameterizedTest(name = "{0} returns X-Content-Type-Options header")
    @MethodSource("uiEndpoints")
    fun `UI route includes X-Content-Type-Options nosniff`(
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
            .describedAs("$label should return OK")
            .isEqualTo(200)
        assertThat(response.header("X-Content-Type-Options"))
            .describedAs("$label should return nosniff")
            .isEqualTo("nosniff")
    }

    @ParameterizedTest(name = "{0} returns X-Frame-Options header")
    @MethodSource("uiEndpoints")
    fun `UI route includes X-Frame-Options DENY`(
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
            .describedAs("$label should return OK")
            .isEqualTo(200)
        assertThat(response.header("X-Frame-Options"))
            .describedAs("$label should deny framing")
            .isEqualTo("DENY")
    }

    @ParameterizedTest(name = "{0} returns Referrer-Policy header")
    @MethodSource("uiEndpoints")
    fun `UI route includes Referrer-Policy header`(
        label: String,
        baseUrl: String,
        path: String,
    ) {
        val referrerPolicy =
            traefikRequest()
                .baseUri(baseUrl)
                .`when`()
                .get(path)
                .then()
                .statusCode(200)
                .extract()
                .header("Referrer-Policy")

        assertThat(referrerPolicy)
            .describedAs("$label Referrer-Policy header should be present")
            .isNotNull()
            .isNotBlank()
    }
}
