#!/usr/bin/env node
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { env } from 'node:process';
import { requireEnv, setOutput } from '../lib/github-run.mjs';

async function main() {
  const fallbackMode = requireEnv('FALLBACK_MODE');
  const nonPlaywrightResult = requireEnv('NON_PLAYWRIGHT_RESULT');
  const playwrightResult = requireEnv('PLAYWRIGHT_RESULT');
  const runnerTemp = env.RUNNER_TEMP ?? '/tmp';

  // (1) Fallback-mode short-circuit FIRST (R1-7, lens C6-fidelity)
  if (fallbackMode === 'k3d-installability') {
    // k3d fallback is fail-closed; always emit non-mergeable
    emitResult({
      status: 'fail',
      reason: 'fallback-non-mergeable',
      flakyCandidates: [],
    });
    process.exit(1);
  }

  // (2) Collect all shard results and flaky candidates
  let shardFiles = [];
  try {
    shardFiles = readdirSync(runnerTemp)
      .filter((f) => /^shard-\d+-result\.json$/.test(f))
      .map((f) => join(runnerTemp, f));
  } catch {
    // runnerTemp may not exist in test context
  }

  const allFlakyCandidates = [];
  const deterministicFails = [];

  for (const file of shardFiles) {
    const r = JSON.parse(readFileSync(file, 'utf8'));
    for (const fc of (r.flakyCandidates ?? [])) {
      allFlakyCandidates.push(fc);
    }
    if (r.status === 'fail' && !(r.failureClass ?? '').includes('transient')) {
      deterministicFails.push(`${r.failureClass} (shard ${file})`);
    }
  }

  // (3) Check required suites
  let allRequired = true;
  if (nonPlaywrightResult !== 'success') {
    allRequired = false;
    deterministicFails.push('non-playwright suite failed');
  }
  if (playwrightResult !== 'success') {
    allRequired = false;
    // Note: playwright failures may include flaky candidates already captured per shard
  }

  if (deterministicFails.length > 0 || !allRequired) {
    // Preserve all flaky candidates even when a deterministic fail is present (SC-4)
    emitResult({
      status: 'fail',
      reason: deterministicFails[0] ?? 'playwright-suite-failed',
      flakyCandidates: allFlakyCandidates,
    });
    return;
  }

  // (4) All required suites passed
  emitResult({
    status: 'pass',
    reason: 'all-required-suites-passed',
    flakyCandidates: allFlakyCandidates,
  });
}

function emitResult({ status, reason, flakyCandidates }) {
  setOutput('status', status);
  setOutput('reason', reason);
  setOutput('flaky-candidates-json', JSON.stringify(flakyCandidates));
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
