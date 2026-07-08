import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { writeFileSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { loadCandidateLock, sha256TreeDigest } from '../lib/candidate-lock.mjs';

// Minimal valid SC-5 lock fixture
function validLock(overrides = {}) {
  return {
    apiVersion: 'deployment.jorisjonkers.dev/cluster-composition-lock/v1',
    kind: 'ClusterCompositionLock',
    metadata: { environment: 'production', generatedAt: '2026-07-08T00:00:00Z' },
    spec: {
      schemaVersion: '0.16.0',
      composedRootDigest: 'sha256:abc123',
      generatedFromCommit: 'deadbeef',
      previousLockDigest: null,
      lockChain: [],
      publicContext: { ref: 'ghcr.io/test@sha256:aaa', digest: 'sha256:aaa', inventorySourceSha: 'bbb' },
      internalContext: { ref: 'ghcr.io/test-int@sha256:ccc', digest: 'sha256:ccc', inventorySourceSha: 'bbb' },
      units: {
        'test-unit': {
          artifactRef: 'ghcr.io/test/test-unit@sha256:ddd',
          artifactDigest: 'sha256:ddd',
          namespace: 'test-unit',
          layer: 'apps-core',
          imageDigests: { 'test-unit': 'ghcr.io/test/test-unit@sha256:ddd' },
        },
      },
      aggregates: {},
      dependencyGraph: { order: ['apps-core', 'apps-registry-test-unit'] },
      ...overrides,
    },
  };
}

function writeLockYaml(dir, lock) {
  // Build YAML string manually (avoiding js-yaml dependency in tests)
  const content = `apiVersion: ${lock.apiVersion}
kind: ${lock.kind}
metadata:
  environment: ${lock.metadata.environment}
  generatedAt: ${lock.metadata.generatedAt}
spec:
  schemaVersion: "${lock.spec.schemaVersion}"
  composedRootDigest: "${lock.spec.composedRootDigest}"
  generatedFromCommit: "${lock.spec.generatedFromCommit}"
  previousLockDigest: ${lock.spec.previousLockDigest === null ? 'null' : `"${lock.spec.previousLockDigest}"`}
  lockChain: []
  publicContext:
    ref: "${lock.spec.publicContext.ref}"
    digest: "${lock.spec.publicContext.digest}"
    inventorySourceSha: "${lock.spec.publicContext.inventorySourceSha}"
  internalContext:
    ref: "${lock.spec.internalContext.ref}"
    digest: "${lock.spec.internalContext.digest}"
    inventorySourceSha: "${lock.spec.internalContext.inventorySourceSha}"
  units: {}
  aggregates: {}
  dependencyGraph:
    order: []
`;
  const lockPath = join(dir, 'cluster-composition.lock.yml');
  writeFileSync(lockPath, content, 'utf8');
  return lockPath;
}

// --- loadCandidateLock tests ---

test('loadCandidateLock throws E_CANDIDATE_LOCK_MISSING when file not found', () => {
  assert.throws(
    () => loadCandidateLock('/nonexistent/path/lock.yml'),
    (err) => {
      assert.equal(err.code, 'E_CANDIDATE_LOCK_MISSING');
      return true;
    },
  );
});

test('loadCandidateLock throws E_LOCK_MISSING_FIELD when composedRootDigest absent', () => {
  const dir = mkdtempSync(join(tmpdir(), 'lock-test-'));
  try {
    // Write lock without composedRootDigest — we write custom YAML
    const content = `apiVersion: deployment.jorisjonkers.dev/cluster-composition-lock/v1
kind: ClusterCompositionLock
metadata:
  environment: production
  generatedAt: 2026-07-08T00:00:00Z
spec:
  schemaVersion: "0.16.0"
  generatedFromCommit: "deadbeef"
  previousLockDigest: null
  lockChain: []
  publicContext:
    ref: "ghcr.io/test@sha256:aaa"
    digest: "sha256:aaa"
    inventorySourceSha: "bbb"
  internalContext:
    ref: "ghcr.io/test-int@sha256:ccc"
    digest: "sha256:ccc"
    inventorySourceSha: "bbb"
  units: {}
  aggregates: {}
  dependencyGraph:
    order: []
`;
    const lockPath = join(dir, 'lock.yml');
    writeFileSync(lockPath, content, 'utf8');

    assert.throws(
      () => loadCandidateLock(lockPath),
      (err) => {
        assert.equal(err.code, 'E_LOCK_MISSING_FIELD');
        assert.equal(err.details.field, 'composedRootDigest');
        return true;
      },
    );
  } finally {
    rmSync(dir, { recursive: true });
  }
});

test('loadCandidateLock throws E_CANDIDATE_LOCK_MISSING for wrong apiVersion', () => {
  const dir = mkdtempSync(join(tmpdir(), 'lock-test-'));
  try {
    const content = `apiVersion: wrong/v1\nkind: ClusterCompositionLock\nspec: {}\n`;
    const lockPath = join(dir, 'lock.yml');
    writeFileSync(lockPath, content, 'utf8');

    assert.throws(
      () => loadCandidateLock(lockPath),
      (err) => {
        assert.equal(err.code, 'E_CANDIDATE_LOCK_MISSING');
        return true;
      },
    );
  } finally {
    rmSync(dir, { recursive: true });
  }
});

// --- sha256TreeDigest tests ---

test('sha256TreeDigest returns consistent digest for same files', () => {
  const dir = mkdtempSync(join(tmpdir(), 'tree-test-'));
  try {
    mkdirSync(join(dir, 'apps', 'edge'), { recursive: true });
    writeFileSync(join(dir, 'apps', 'edge', 'traefik.yaml'), 'content: a\n', 'utf8');
    writeFileSync(join(dir, 'apps', 'edge', 'routes.yaml'), 'content: b\n', 'utf8');

    const d1 = sha256TreeDigest(dir);
    const d2 = sha256TreeDigest(dir);
    assert.equal(d1, d2, 'digest should be deterministic');
    assert.match(d1, /^[0-9a-f]{64}$/);
  } finally {
    rmSync(dir, { recursive: true });
  }
});

test('sha256TreeDigest changes when file content changes', () => {
  const dir = mkdtempSync(join(tmpdir(), 'tree-test-'));
  try {
    writeFileSync(join(dir, 'file.yaml'), 'original\n', 'utf8');
    const d1 = sha256TreeDigest(dir);
    writeFileSync(join(dir, 'file.yaml'), 'modified\n', 'utf8');
    const d2 = sha256TreeDigest(dir);
    assert.notEqual(d1, d2);
  } finally {
    rmSync(dir, { recursive: true });
  }
});
