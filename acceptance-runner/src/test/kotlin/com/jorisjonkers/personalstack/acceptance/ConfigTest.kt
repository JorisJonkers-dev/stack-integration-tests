package com.jorisjonkers.personalstack.acceptance

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

private const val CONFIGURED_INTERVAL_MS = 500L
private const val CONFIGURED_TIMEOUT_MS = 5_000L

private const val FULL_CONFIG_YAML = """
evaluationSetPath: ./evaluation-set.json
resultsDir: ./results
safety:
  allowedBankPrefix: eval-
availabilityPoll:
  intervalMs: 500
  timeoutMs: 5000
targets:
  hindsight:
    baseUrl: http://localhost:8888
    bank: eval-bank
    apiKeyEnv: HINDSIGHT_API_KEY
  basicMemory:
    baseUrl: http://localhost:8000
    project: eval-project
comparison:
  knowledgeApi:
    baseUrl: http://localhost:8090
"""

class ConfigTest {
    private fun parse(yaml: String) = AcceptanceConfigLoader.parse(ByteArrayInputStream(yaml.toByteArray()))

    @Test
    fun `parses every section of a full config`() {
        val config = parse(FULL_CONFIG_YAML)
        assertThat(config.resultsDir).isEqualTo("./results")
        assertThat(config.safety.allowedBankPrefix).isEqualTo("eval-")
        assertThat(config.availabilityPoll.intervalMs).isEqualTo(CONFIGURED_INTERVAL_MS)
        assertThat(config.availabilityPoll.timeoutMs).isEqualTo(CONFIGURED_TIMEOUT_MS)
        assertThat(config.hindsight?.bank).isEqualTo("eval-bank")
        assertThat(config.hindsight?.apiKeyEnv).isEqualTo("HINDSIGHT_API_KEY")
        assertThat(config.basicMemory?.project).isEqualTo("eval-project")
        assertThat(config.knowledgeApi?.baseUrl).isEqualTo("http://localhost:8090")
    }

    @Test
    fun `defaults hindsight endpoints when the config omits them`() {
        val config = parse(FULL_CONFIG_YAML)
        assertThat(config.hindsight?.endpoints?.seed).isEqualTo("/v1/banks/{bank}/memories")
        assertThat(config.hindsight?.endpoints?.query).isEqualTo("/v1/banks/{bank}/query")
    }

    @Test
    fun `defaults safety and poll settings when the config omits them`() {
        val config = parse("targets:\n  hindsight:\n    baseUrl: http://localhost:8888\n    bank: eval-bank\n")
        assertThat(config.safety.allowedBankPrefix).isEqualTo("eval-")
        assertThat(config.availabilityPoll.intervalMs).isEqualTo(AvailabilityPollConfig.DEFAULT_INTERVAL_MS)
    }

    @Test
    fun `leaves a target absent from the config as null`() {
        val config = parse("resultsDir: ./results\n")
        assertThat(config.hindsight).isNull()
        assertThat(config.basicMemory).isNull()
        assertThat(config.knowledgeApi).isNull()
    }

    @Test
    fun `rejects a hindsight target missing its bank`() {
        assertThatThrownBy {
            parse("targets:\n  hindsight:\n    baseUrl: http://localhost:8888\n")
        }.isInstanceOf(ConfigError::class.java).hasMessageContaining("bank")
    }

    @Test
    fun `rejects empty yaml`() {
        assertThatThrownBy { parse("") }.isInstanceOf(ConfigError::class.java)
    }

    @Test
    fun `missing config file is a ConfigError`() {
        assertThatThrownBy {
            AcceptanceConfigLoader.load(
                java.nio.file.Path
                    .of("/no/such/config.yaml"),
            )
        }.isInstanceOf(ConfigError::class.java)
    }
}
