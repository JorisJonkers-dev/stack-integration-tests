package com.jorisjonkers.personalstack.acceptance

import java.net.URI

/**
 * Known public hosts for the real knowledge platform (fleet-infra
 * cluster/flux/apps/knowledge-platform and cluster/flux/apps/knowledge).
 * `--dry-run` refuses to talk to any of these — it exists so a stub/staging
 * config can never be pointed at production by accident.
 */
private val KNOWN_PRODUCTION_HOSTS =
    setOf(
        "memory.jorisjonkers.dev",
        "memory-api.jorisjonkers.dev",
        "memory-mcp.jorisjonkers.dev",
        "kb.jorisjonkers.dev",
    )

/**
 * Refuses to seed a bank/project whose name does not start with the
 * configured safety prefix (default `eval-`). This is the guard fleet-infra
 * #251 requires: seeding must be impossible against a production bank.
 */
fun assertSeedableIdentifier(
    kind: String,
    value: String,
    allowedPrefix: String,
) {
    if (!value.startsWith(allowedPrefix)) {
        throw ProductionTargetGuardError(
            "refusing to seed $kind \"$value\": it must start with \"$allowedPrefix\" " +
                "(for example \"$allowedPrefix$kind-2026-09-15\"); this guard exists so the runner " +
                "can never write fixtures into a production bank or project",
        )
    }
}

fun assertSafeToSeed(config: AcceptanceConfig) {
    val prefix = config.safety.allowedBankPrefix
    config.hindsight?.takeIf { it.enabled }?.let { assertSeedableIdentifier("hindsight bank", it.bank, prefix) }
    config.basicMemory
        ?.takeIf { it.enabled }
        ?.let { assertSeedableIdentifier("basic-memory project", it.project, prefix) }
}

/** Refuses `--dry-run` against a host this estate actually serves the live platform on. */
fun assertOfflineSafe(config: AcceptanceConfig) {
    listOfNotNull(
        config.hindsight?.takeIf { it.enabled }?.baseUrl,
        config.basicMemory?.takeIf { it.enabled }?.baseUrl,
        config.knowledgeApi?.takeIf { it.enabled }?.baseUrl,
    ).forEach(::assertNotProductionHost)
}

private fun assertNotProductionHost(baseUrl: String) {
    val host = URI(baseUrl).host
    if (host in KNOWN_PRODUCTION_HOSTS) {
        throw ProductionTargetGuardError(
            "--dry-run refuses to call $baseUrl: $host is a known production host; " +
                "point the config at a stub or staging endpoint for a dry run",
        )
    }
}
