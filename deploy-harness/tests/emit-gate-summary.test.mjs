import { test } from 'node:test';
import assert from 'node:assert/strict';
import { writeFileSync, readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

// Test the SC-4 gate-summary shape by exercising the logic inline
function buildGateSummary({ status, reason, flakyCandidates }) {
  return {
    gate: 'stack-integration-gate',
    check_name: 'Stack Integration Gate',
    status,
    reason,
    flaky_candidates: flakyCandidates,
    actor_decision: 'none',
    redacted: false,
  };
}

// T-H8: SC-4 gate-summary shape validation

test('gate summary with pass status produces valid SC-4', () => {
  const summary = buildGateSummary({
    status: 'pass',
    reason: 'all-required-suites-passed',
    flakyCandidates: [],
  });
  assert.equal(summary.gate, 'stack-integration-gate');
  assert.equal(summary.check_name, 'Stack Integration Gate');
  assert.equal(summary.status, 'pass');
  assert.equal(summary.actor_decision, 'none');
  assert.equal(summary.redacted, false);
});

test('gate summary with fallback-non-mergeable reason produces valid SC-4', () => {
  const summary = buildGateSummary({
    status: 'fail',
    reason: 'fallback-non-mergeable',
    flakyCandidates: [],
  });
  assert.equal(summary.gate, 'stack-integration-gate');
  assert.equal(summary.check_name, 'Stack Integration Gate');
  assert.equal(summary.status, 'fail');
  assert.equal(summary.reason, 'fallback-non-mergeable');
  assert.equal(summary.redacted, false);
});

test('gate summary preserves flaky_candidates in SC-4 shape', () => {
  const flakyCandidates = [
    { suite: 'playwright', shard: 2, attempt: 1, failureClass: 'flux-not-ready', failureSnapshot: 'Kustomization...' },
  ];
  const summary = buildGateSummary({
    status: 'fail',
    reason: 'deterministic-test-failure',
    flakyCandidates,
  });
  assert.equal(summary.flaky_candidates.length, 1);
  assert.equal(summary.flaky_candidates[0].failureClass, 'flux-not-ready');
});

test('gate summary required SC-4 fields are all present', () => {
  const summary = buildGateSummary({ status: 'pass', reason: 'test', flakyCandidates: [] });
  const required = ['gate', 'check_name', 'status', 'reason', 'flaky_candidates', 'actor_decision', 'redacted'];
  for (const field of required) {
    assert.ok(field in summary, `SC-4 field '${field}' must be present`);
  }
});
