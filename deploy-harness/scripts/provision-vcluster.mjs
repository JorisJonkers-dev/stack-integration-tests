#!/usr/bin/env node
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { env } from 'node:process';
import { spawnSync } from 'node:child_process';
import { requireEnv, setOutput, warn } from '../lib/github-run.mjs';
import { createNamespace } from '../lib/kubectl.mjs';

class HarnessError extends Error {
  constructor(code, details) {
    super(`${code}: ${JSON.stringify(details)}`);
    this.code = code;
    this.details = details;
  }
}

async function main() {
  const runId = requireEnv('RUN_ID');
  const attempt = requireEnv('ATTEMPT');

  const namespace = `t2-${runId}-${attempt}`;
  const vclusterName = `t2-vcluster-${runId}-${attempt}`;

  // (1) Create namespace on host cluster
  createNamespace(namespace, {
    labels: { 't2-run-id': runId, 't2-attempt': attempt },
  });
  console.log(`Namespace ${namespace} ready`);

  // (2) Install vcluster via helm
  const helmResult = spawnSync('helm', [
    'upgrade', '--install', vclusterName,
    'vcluster',
    '--repo', 'https://charts.loft.sh',
    '-n', namespace,
    '--create-namespace',
    '--set', 'sync.ingresses.enabled=true',
    '--set', `syncer.extraArgs[0]=--tls-san=*.jorisjonkers.test`,
    '--wait',
    '--timeout', '120s',
  ], { encoding: 'utf8', timeout: 150_000 });

  if (helmResult.status !== 0) {
    throw new HarnessError('E_VCLUSTER_PROVISION_TIMEOUT', {
      namespace,
      vclusterName,
      stderr: helmResult.stderr?.slice(0, 500),
    });
  }
  console.log(`vcluster ${vclusterName} installed in ${namespace}`);

  // (3) Write cleanup state
  const runnerTemp = env.RUNNER_TEMP ?? '/tmp';
  const cleanupStatePath = join(runnerTemp, 'cleanup-state.json');
  const cleanupState = { namespace, vclusterName, corednsWildcard: '*.jorisjonkers.test', vault: null };
  writeFileSync(cleanupStatePath, JSON.stringify(cleanupState, null, 2), 'utf8');

  setOutput('namespace', namespace);
  setOutput('vcluster-name', vclusterName);
  setOutput('cleanup-state', cleanupStatePath);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
