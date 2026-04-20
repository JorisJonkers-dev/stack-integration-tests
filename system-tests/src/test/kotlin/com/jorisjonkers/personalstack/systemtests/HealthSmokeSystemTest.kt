package com.jorisjonkers.personalstack.systemtests

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * End-to-end smoke on the composite `/actuator/health` endpoint of
 * each Spring Boot backend. Catches the class of regression where a
 * dependency misconfiguration (e.g. Vault role mismatch) makes a
 * contributor DOWN while the kubelet probes — scoped to `liveness`
 * (`ping`) and `readiness` (`readinessState`) groups — stay UP.
 *
 * Runs in the `System Tests (api)` shard of the Full Pipeline, so it
 * blocks merge the same way the downstream OAuth2 flow tests do.
 */
@Tag("system")
class HealthSmokeSystemTest {
    companion object {
        @JvmStatic
        fun services(): Stream<Arguments> =
            Stream.of(
                Arguments.of("auth-api", "https://auth.jorisjonkers.test"),
                Arguments.of("assistant-api", "https://assistant.jorisjonkers.test"),
            )
    }

    @ParameterizedTest(name = "{0} /api/actuator/health returns 200 with every contributor UP")
    @MethodSource("services")
    fun actuatorHealthComposite(
        service: String,
        baseUrl: String,
    ) {
        val body =
            TestHelper
                .givenApi()
                .baseUri(baseUrl)
                .`when`()
                .get("/api/actuator/health")
                .then()
                .extract()

        assertThat(body.statusCode())
            .describedAs("$service /api/actuator/health body=%s", body.body().asString())
            .isEqualTo(200)

        val payload = body.body().jsonPath()
        assertThat(payload.getString("status"))
            .describedAs("$service overall status")
            .isEqualTo("UP")

        // Every named contributor must be UP. The keys under `components`
        // (Spring Boot 2.7+) or `details` (older shape) enumerate every
        // auto-configured health indicator. We fail loudly only on DOWN
        // (or OUT_OF_SERVICE). UNKNOWN is fine — e.g. the auto-registered
        // discoveryComposite is intentionally uninitialised in this stack;
        // that should not break the smoke test.
        val components: Map<String, Any?> =
            payload.getMap<String, Any?>("components")
                ?: payload.getMap("details")
                ?: error("$service /health missing components + details — show-details may be wrong: ${body.body().asString()}")

        val down =
            components.entries
                .mapNotNull { (name, value) ->
                    @Suppress("UNCHECKED_CAST")
                    val status = (value as? Map<String, Any?>)?.get("status") as? String
                    if (status == "DOWN" || status == "OUT_OF_SERVICE") "$name=$status" else null
                }

        assertThat(down)
            .describedAs("$service: one or more contributors not UP: $down\nfull body: ${body.body().asString()}")
            .isEmpty()
    }
}
