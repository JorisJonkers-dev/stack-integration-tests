#!/usr/bin/env node
import { existsSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { env } from 'node:process';
import { loadCandidateLock, sha256TreeDigest } from '../lib/candidate-lock.mjs';
import { requireEnv } from '../lib/github-run.mjs';

class HarnessError extends Error {
  constructor(code, details) {
    super(`${code}: ${JSON.stringify(details)}`);
    this.code = code;
    this.details = details;
  }
}

async function main() {
  const candidateDir = requireEnv('CANDIDATE_DIR');
  const lockPath = requireEnv('LOCK_PATH');
  const expectedDigest = requireEnv('EXPECTED_COMPOSED_DIGEST');

  // (1) Parse SC-5 lock
  const lockFullPath = join(candidateDir, lockPath);
  const lock = loadCandidateLock(lockFullPath);

  // (2) Compute actual digest of candidate/cluster/flux tree
  const clusterFluxDir = join(candidateDir, 'cluster', 'flux');
  if (!existsSync(clusterFluxDir)) {
    throw new HarnessError('E_CANDIDATE_TREE_MISSING', { path: clusterFluxDir });
  }
  const actualDigest = 'sha256:' + sha256TreeDigest(clusterFluxDir);

  // (3) SC-6 triple assertion
  const lockDigest = lock.spec.composedRootDigest;
  if (actualDigest !== lockDigest) {
    throw new HarnessError('E_CANDIDATE_DIGEST_MISMATCH', {
      source: 'actual-tree-vs-lock',
      actual: actualDigest,
      expected: lockDigest,
      reason: 'sha256Tree(candidate/cluster/flux) does not match lock.spec.composedRootDigest',
    });
  }
  if (actualDigest !== expectedDigest) {
    throw new HarnessError('E_CANDIDATE_DIGEST_MISMATCH', {
      source: 'actual-tree-vs-workflow-input',
      actual: actualDigest,
      expected: expectedDigest,
      reason: 'sha256Tree(candidate/cluster/flux) does not match inputs.composed-root-digest (SC-6)',
    });
  }
  // lockDigest == expectedDigest follows transitively
  console.log(`SC-6 triple digest assertion passed: ${actualDigest}`);

  // (4) Require every unit's artifactDigest and imageDigest present in lock
  for (const [unitName, unit] of Object.entries(lock.spec.units)) {
    if (!unit.artifactDigest || !unit.artifactDigest.startsWith('sha256:')) {
      throw new HarnessError('E_LOCK_UNIT_MISSING_DIGEST', { unit: unitName, field: 'artifactDigest' });
    }
    for (const [alias, ref] of Object.entries(unit.imageDigests ?? {})) {
      if (!ref.includes('@sha256:')) {
        throw new HarnessError('E_LOCK_UNIT_MISSING_IMAGE_DIGEST', { unit: unitName, alias, ref });
      }
    }
  }

  // Expose parsed lock for downstream scripts/jobs (correction #22: also uploaded as artifact)
  const runnerTemp = env.RUNNER_TEMP ?? '/tmp';
  writeFileSync(join(runnerTemp, 'candidate-lock.json'), JSON.stringify(lock, null, 2), 'utf8');
  console.log(`prepare-candidate complete; ${Object.keys(lock.spec.units).length} units in lock`);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
