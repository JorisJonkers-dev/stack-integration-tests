#!/usr/bin/env node
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { env } from 'node:process';
import { spawnSync } from 'node:child_process';
import { requireEnv, warn } from '../lib/github-run.mjs';
import { loadQuarantineManifest, validateQuarantineManifest } from '../lib/quarantine.mjs';
import { classifyFailure } from '../lib/failure-classification.mjs';

class HarnessError extends Error {
  constructor(code, details) {
    super(`${code}: ${JSON.stringify(details)}`);
    this.code = code;
    this.details = details;
  }
}

async function main() {
  const suite = requireEnv('SUITE');         // "non-playwright" | "playwright"
  const shard = requireEnv('SHARD');         // "1/1" or "N/4"
  const vclusterName = requireEnv('VCLUSTER_NAME');
  const namespace = requireEnv('NAMESPACE');
  const runnerTemp = env.RUNNER_TEMP ?? '/tmp';

  const lock = JSON.parse(readFileSync(join(runnerTemp, 'candidate-lock.json'), 'utf8'));

  const quarantine = loadQuarantineManifest('deploy-harness/config/quarantined-tests.yaml');
  validateQuarantineManifest(quarantine);

  // Correction #21: ONE consolidated -Dtest.image-tags= string
  // Format: space-separated "unit.alias=ghcr.io/...@sha256:..." pairs
  const imageTagPairs = [];
  for (const [unit, u] of Object.entries(lock.spec.units)) {
    for (const [alias, ref] of Object.entries(u.imageDigests ?? {})) {
      imageTagPairs.push(`${unit}.${alias}=${ref}`);
    }
  }
  const imageTagsValue = imageTagPairs.join(' ');

  const shardBudgetMs = 45 * 60 * 1000;
  const warnAtMs = 35 * 60 * 1000;
  const shardStartTime = Date.now();
  const flakyCandidates = [];
  const maxAttempts = 3;

  const [shardIndexStr, shardTotalStr] = shard.split('/');
  const shardIndex = parseInt(shardIndexStr, 10);
  const shardTotal = parseInt(shardTotalStr, 10);

  const gradleTask = suite === 'playwright'
    ? ':system-tests:testPlaywright'
    : ':system-tests:testNonPlaywright';

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const elapsedMs = Date.now() - shardStartTime;

    if (elapsedMs > shardBudgetMs) {
      writeShardResult(runnerTemp, shardIndex, {
        status: 'fail', failureClass: 'shard-timeout', attempt, flakyCandidates,
      });
      throw new HarnessError('E_SHARD_TIMEOUT', {
        suite, shard, elapsedMs,
        reason: `shard-timeout: exceeded ${shardBudgetMs}ms budget`,
      });
    }

    if (elapsedMs > warnAtMs) {
      warn(`Shard ${shard} approaching time budget: ${elapsedMs}ms elapsed`);
    }

    const remainingMs = shardBudgetMs - elapsedMs;
    const gradleArgs = [
      gradleTask,
      '--no-daemon',
      `-Dtest.image-tags=${imageTagsValue}`,
      `-Dtest.shard.index=${shardIndex}`,
      `-Dtest.shard.total=${shardTotal}`,
      `-Dtest.vcluster.name=${vclusterName}`,
      `-Dtest.t2.namespace=${namespace}`,
    ];

    const result = spawnSync('./gradlew', gradleArgs, {
      encoding: 'utf8',
      timeout: remainingMs,
    });

    const output = (result.stdout ?? '') + (result.stderr ?? '');

    if (result.status === 0) {
      console.log(`Shard ${shard} passed on attempt ${attempt}`);
      writeShardResult(runnerTemp, shardIndex, { status: 'pass', attempt, flakyCandidates });
      return;
    }

    const classification = classifyFailure(output, suite, shard, attempt);

    if (classification.type === 'transient') {
      flakyCandidates.push({
        suite,
        shard: shardIndex,
        attempt,
        failureClass: classification.pattern,
        failureSnapshot: classification.snippet,
      });
      console.log(`Transient failure on attempt ${attempt}; retrying. Pattern: ${classification.pattern}`);
      continue;
    }

    // Deterministic failure
    console.error(`Deterministic failure on attempt ${attempt}: ${classification.pattern}`);
    writeShardResult(runnerTemp, shardIndex, {
      status: 'fail', failureClass: classification.pattern, attempt, flakyCandidates,
    });
    throw new HarnessError('E_SHARD_DETERMINISTIC_FAIL', {
      suite, shard, attempt,
      failureClass: classification.pattern,
      flakyCandidates,
    });
  }

  // All attempts exhausted (only transient failures)
  writeShardResult(runnerTemp, shardIndex, {
    status: 'fail', failureClass: 'transient-exhausted', attempt: maxAttempts, flakyCandidates,
  });
  throw new HarnessError('E_SHARD_TRANSIENT_EXHAUSTED', { suite, shard, flakyCandidates });
}

function writeShardResult(runnerTemp, shardIndex, data) {
  writeFileSync(
    join(runnerTemp, `shard-${shardIndex}-result.json`),
    JSON.stringify(data, null, 2),
    'utf8',
  );
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
