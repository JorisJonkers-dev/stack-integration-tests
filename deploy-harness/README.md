# Deploy Harness (T2 Gate)

This directory contains the T2 full-system integration test harness, called by `homelab-deploy`'s
`stack-integration-gate` job via the `full-system.yml` reusable workflow.

## Overview

The harness provisions an ephemeral vcluster on a `self-hosted-k3s-t2` runner, applies the
candidate composed cluster state (downloaded as a GitHub Actions artifact from the compose gate),
runs the Gradle system test suites, and emits an SC-4 `gate-summary.json`.

## Directory layout

```
deploy-harness/
  config/
    full-system.yaml           # Harness configuration (CODEOWNERS-gated)
    quarantined-tests.yaml     # Quarantine registry (CODEOWNERS-gated, owner-approved per entry)
    k3d-installability.yaml    # k3d fallback config (non-mergeable)
  scripts/
    prepare-candidate.mjs      # SC-6 triple-digest assertion
    provision-vcluster.mjs     # Namespace + vcluster creation
    seed-vault-approle.mjs     # Scoped Vault AppRole per test namespace
    apply-candidate.mjs        # Rewrite routes + server-side apply
    wait-flux-ready.mjs        # Wait for OCIRepository/Kustomization/HelmRelease Ready
    wait-runtime-healthy.mjs   # CrashLoop watch + VSO sync verification
    run-gradle-suite.mjs       # Per-shard Gradle execution with retry + failure classification
    rerun-policy.mjs           # Aggregate shard results; fallback-mode short-circuit
    emit-gate-summary.mjs      # Emit SC-4 gate-summary.json
    cleanup-target.mjs         # Guaranteed teardown (always runs)
  lib/
    candidate-lock.mjs         # SC-5 lock parser + sha256TreeDigest
    failure-classification.mjs # TRANSIENT_PATTERNS + sanitizeSnippet
    github-run.mjs             # setOutput, warn, requireEnv helpers
    kubectl.mjs                # kubectl wrapper (kubeconfig via temp file)
    quarantine.mjs             # Quarantine manifest loader + validator
    vault.mjs                  # Vault CLI wrapper
```

## SC-6 fidelity

Before any cluster apply, `prepare-candidate.mjs` asserts the triple-digest invariant:

```
sha256Tree(candidate/cluster/flux) == lock.spec.composedRootDigest == inputs.composed-root-digest
```

This prevents a tampered or mismatched candidate from being tested.

## Correction notes

- **#21**: `run-gradle-suite.mjs` passes ONE consolidated `-Dtest.image-tags=` string
  with space-separated `unit.alias=ghcr.io/...@sha256:...` pairs, matching what
  `system-tests/build.gradle.kts` reads via `project.findProperty("test.image-tags")`.
- **#22**: `candidate-lock.json` is uploaded as a GitHub Actions artifact in the
  `deploy-candidate` job and downloaded in each `non-playwright`/`playwright` job,
  because `RUNNER_TEMP` does not persist across jobs.

## Quarantine policy

Any test class in `quarantined-tests.yaml` requires:
- `ownerApproved: true` (set by a platform-owner reviewer)
- `issueUrl` starting with `https://github.com/JorisJonkers-dev/`
- `expiresAt` set to a future date

The `QuarantineContractTest` enforces these constraints as a `@Tag("system")` test
that runs in every non-quarantined test suite execution.

## k3d fallback

If `fallback-mode: k3d-installability` is passed, the harness emits
`status: fail, reason: fallback-non-mergeable` and exits 1. This mode is structurally
non-mergeable and treated as a gate failure by the Pipeline Complete job.
