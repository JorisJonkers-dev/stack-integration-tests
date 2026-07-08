#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { env } from 'node:process';
import { spawnSync } from 'node:child_process';
import { requireEnv } from '../lib/github-run.mjs';
import { listPodsInNamespaceWithKubeconfig, listWithKubeconfig } from '../lib/kubectl.mjs';

class HarnessError extends Error {
  constructor(code, details) {
    super(`${code}: ${JSON.stringify(details)}`);
    this.code = code;
    this.details = details;
  }
}

async function getVclusterKubeconfig(vclusterName, namespace) {
  const result = spawnSync('vcluster', ['connect', vclusterName, '-n', namespace, '--print-token'], {
    encoding: 'utf8',
    timeout: 30_000,
  });
  if (result.status !== 0) {
    throw new Error(`vcluster connect failed: ${result.stderr}`);
  }
  return result.stdout;
}

async function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function main() {
  const vclusterName = requireEnv('VCLUSTER_NAME');
  const namespace = requireEnv('NAMESPACE');
  const runnerTemp = env.RUNNER_TEMP ?? '/tmp';

  const lock = JSON.parse(readFileSync(join(runnerTemp, 'candidate-lock.json'), 'utf8'));
  const vclusterKubeconfig = await getVclusterKubeconfig(vclusterName, namespace);

  // (1) Continuous CrashLoop watch for 5 minutes
  const endTime = Date.now() + 5 * 60 * 1000;
  while (Date.now() < endTime) {
    for (const [unitName, unit] of Object.entries(lock.spec.units)) {
      const pods = listPodsInNamespaceWithKubeconfig(vclusterKubeconfig, unit.namespace);
      const crashStates = ['CrashLoopBackOff', 'OOMKilled', 'Error'];
      const crashingPods = pods.filter(p =>
        p.status?.containerStatuses?.some(cs => crashStates.includes(cs.state?.waiting?.reason)),
      );
      if (crashingPods.length > 0) {
        throw new HarnessError('E_RUNTIME_CRASH_LOOP', {
          unit: unitName,
          pods: crashingPods.map(p => p.metadata.name),
          reason: crashingPods[0].status.containerStatuses[0].state.waiting.reason,
        });
      }
    }
    await sleep(10_000);
  }

  // (2) Verify VSO is ready
  const vsoObjects = listWithKubeconfig(vclusterKubeconfig, 'VaultStaticSecret', '--all-namespaces');
  for (const vso of vsoObjects) {
    const synced = vso.status?.conditions?.find(c => c.type === 'SecretSynced' && c.status === 'True');
    if (!synced) {
      throw new HarnessError('E_VSO_NOT_SYNCED', {
        name: vso.metadata.name,
        namespace: vso.metadata.namespace,
      });
    }
  }

  console.log('wait-runtime-healthy complete');
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
