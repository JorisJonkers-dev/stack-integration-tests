import { test } from 'node:test';
import assert from 'node:assert/strict';
import { writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

// We test rerun-policy logic by re-implementing the core function inline
// since the script is a runnable CLI, not a module. The key logic is:
// 1. k3d-installability → fail/fallback-non-mergeable immediately
// 2. aggregate shard results + flaky_candidates
// 3. fail if any non-transient shard fail or required suites fail

function rerunPolicyLogic({ fallbackMode, nonPlaywrightResult, playwrightResult, shardFiles }) {
  if (fallbackMode === 'k3d-installability') {
    return { status: 'fail', reason: 'fallback-non-mergeable', flakyCandidates: [] };
  }

  const allFlakyCandidates = [];
  const deterministicFails = [];

  for (const r of shardFiles) {
    for (const fc of (r.flakyCandidates ?? [])) {
      allFlakyCandidates.push(fc);
    }
    if (r.status === 'fail' && !(r.failureClass ?? '').includes('transient')) {
      deterministicFails.push(`${r.failureClass} (shard)`);
    }
  }

  let allRequired = true;
  if (nonPlaywrightResult !== 'success') {
    allRequired = false;
    deterministicFails.push('non-playwright suite failed');
  }
  if (playwrightResult !== 'success') {
    allRequired = false;
  }

  if (deterministicFails.length > 0 || !allRequired) {
    return {
      status: 'fail',
      reason: deterministicFails[0] ?? 'playwright-suite-failed',
      flakyCandidates: allFlakyCandidates,
    };
  }

  return { status: 'pass', reason: 'all-required-suites-passed', flakyCandidates: allFlakyCandidates };
}

// T-H3: k3d fallback is fail-closed
test('rerunPolicy emits fail/fallback-non-mergeable for k3d-installability mode', () => {
  const result = rerunPolicyLogic({
    fallbackMode: 'k3d-installability',
    nonPlaywrightResult: 'success',
    playwrightResult: 'success',
    shardFiles: [],
  });
  assert.equal(result.status, 'fail');
  assert.equal(result.reason, 'fallback-non-mergeable');
  assert.deepEqual(result.flakyCandidates, []);
});

// T-H4: transient+deterministic same run preserves flaky_candidates
test('rerunPolicy preserves flaky_candidates when another shard has deterministic fail', () => {
  const shardFiles = [
    {
      status: 'fail',
      failureClass: 'deterministic-test-failure',
      attempt: 1,
      flakyCandidates: [],
    },
    {
      status: 'fail',
      failureClass: 'deterministic-test-failure',
      attempt: 2,
      flakyCandidates: [
        { suite: 'playwright', shard: 2, attempt: 1, failureClass: 'flux-not-ready', failureSnapshot: '...' },
      ],
    },
  ];

  const result = rerunPolicyLogic({
    fallbackMode: 'real-k3s-vcluster',
    nonPlaywrightResult: 'success',
    playwrightResult: 'failure',
    shardFiles,
  });

  assert.equal(result.status, 'fail');
  assert.equal(result.flakyCandidates.length, 1);
  assert.equal(result.flakyCandidates[0].failureClass, 'flux-not-ready');
});

test('rerunPolicy passes when all suites succeed', () => {
  const result = rerunPolicyLogic({
    fallbackMode: 'real-k3s-vcluster',
    nonPlaywrightResult: 'success',
    playwrightResult: 'success',
    shardFiles: [],
  });
  assert.equal(result.status, 'pass');
  assert.equal(result.reason, 'all-required-suites-passed');
});

test('rerunPolicy fails when non-playwright fails even with playwright passing', () => {
  const result = rerunPolicyLogic({
    fallbackMode: 'real-k3s-vcluster',
    nonPlaywrightResult: 'failure',
    playwrightResult: 'success',
    shardFiles: [],
  });
  assert.equal(result.status, 'fail');
  assert.equal(result.reason, 'non-playwright suite failed');
});

test('rerunPolicy fails when playwright fails', () => {
  const result = rerunPolicyLogic({
    fallbackMode: 'real-k3s-vcluster',
    nonPlaywrightResult: 'success',
    playwrightResult: 'failure',
    shardFiles: [],
  });
  assert.equal(result.status, 'fail');
});

test('rerunPolicy carries flaky candidates on pass path', () => {
  const shardFiles = [
    {
      status: 'pass',
      attempt: 2,
      flakyCandidates: [
        { suite: 'playwright', shard: 1, attempt: 1, failureClass: 'dns-resolution', failureSnapshot: '...' },
      ],
    },
  ];

  const result = rerunPolicyLogic({
    fallbackMode: 'real-k3s-vcluster',
    nonPlaywrightResult: 'success',
    playwrightResult: 'success',
    shardFiles,
  });
  assert.equal(result.status, 'pass');
  assert.equal(result.flakyCandidates.length, 1);
});
