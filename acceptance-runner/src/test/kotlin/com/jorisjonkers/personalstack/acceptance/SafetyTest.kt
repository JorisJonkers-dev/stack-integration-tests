package com.jorisjonkers.personalstack.acceptance

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SafetyTest {
    @Test
    fun `refuses a bank without the required prefix`() {
        assertThatThrownBy { assertSeedableIdentifier("hindsight bank", "production", "eval-") }
            .isInstanceOf(ProductionTargetGuardError::class.java)
            .hasMessageContaining("eval-")
    }

    @Test
    fun `allows a bank with the required prefix`() {
        assertSeedableIdentifier("hindsight bank", "eval-acceptance-2026", "eval-")
    }

    @Test
    fun `assertSafeToSeed refuses when only the enabled target is unsafe`() {
        val config = testConfig(bank = "production-bank", project = "eval-project")
        assertThatThrownBy { assertSafeToSeed(config) }.isInstanceOf(ProductionTargetGuardError::class.java)
    }

    @Test
    fun `assertSafeToSeed ignores a disabled target's name`() {
        val config = testConfig(bank = "production-bank", project = "eval-project", hindsightEnabled = false)
        assertSafeToSeed(config)
    }

    @Test
    fun `assertSafeToSeed passes when every enabled target is prefixed`() {
        assertSafeToSeed(testConfig(bank = "eval-bank", project = "eval-project"))
    }

    @Test
    fun `assertOfflineSafe refuses a known production host`() {
        val config =
            testConfig(
                bank = "eval-bank",
                project = "eval-project",
                hindsightBaseUrl = "https://memory-api.jorisjonkers.dev",
            )
        assertThatThrownBy { assertOfflineSafe(config) }
            .isInstanceOf(ProductionTargetGuardError::class.java)
            .hasMessageContaining("memory-api.jorisjonkers.dev")
    }

    @Test
    fun `assertOfflineSafe allows a local stub host`() {
        val config = testConfig(bank = "eval-bank", project = "eval-project")
        assertOfflineSafe(config)
    }

    private fun testConfig(
        bank: String,
        project: String,
        hindsightEnabled: Boolean = true,
        hindsightBaseUrl: String = "http://localhost:8888",
    ): AcceptanceConfig =
        AcceptanceConfig(
            evaluationSetPath = null,
            resultsDir = "./results",
            safety = SafetyConfig(),
            availabilityPoll = AvailabilityPollConfig(),
            hindsight =
                HindsightTargetConfig(
                    enabled = hindsightEnabled,
                    baseUrl = hindsightBaseUrl,
                    bank = bank,
                    apiKeyEnv = null,
                    endpoints = HindsightEndpoints(),
                    timeoutMs = 1_000,
                ),
            basicMemory =
                BasicMemoryTargetConfig(
                    enabled = true,
                    baseUrl = "http://localhost:8000",
                    mcpPath = "/mcp",
                    project = project,
                    apiKeyEnv = null,
                    timeoutMs = 1_000,
                ),
            knowledgeApi = null,
        )
}
