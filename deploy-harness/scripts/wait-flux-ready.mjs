#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { env } from 'node:process';
import { spawnSync } from 'node:child_process';
import { requireEnv } from '../lib/github-run.mjs';
import {
  waitForConditionWithKubeconfig,
  getConditionsWithKubeconfig,
  listWithKubeconfig,
} from '../lib/kubectl.mjs';

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

async function main() {
  const vclusterName = requireEnv('VCLUSTER_NAME');
  const namespace = requireEnv('NAMESPACE');
  const runnerTemp = env.RUNNER_TEMP ?? '/tmp';

  const lock = JSON.parse(readFileSync(join(runnerTemp, 'candidate-lock.json'), 'utf8'));
  const vclusterKubeconfig = await getVclusterKubeconfig(vclusterName, namespace);
  const unitNames = Object.keys(lock.spec.units);

  // (1) Wait for every OCIRepository to be Ready=True
  for (const unitName of unitNames) {
    console.log(`Waiting for OCIRepository/${unitName} to be Ready...`);
    try {
      waitForConditionWithKubeconfig(vclusterKubeconfig, {
        kind: 'OCIRepository.source.toolkit.fluxcd.io',
        name: unitName,
        namespace: 'flux-system',
        condition: 'Ready',
        timeoutSeconds: 300,
      });
    } catch {
      throw new HarnessError('E_OCIREPOSITORY_NOT_READY', { unit: unitName, timeoutSeconds: 300 });
    }
  }

  // (2) Wait for Kustomizations in DAG order
  const applyOrder = lock.spec?.dependencyGraph?.order ?? [];
  for (const layerNode of applyOrder) {
    if (layerNode.startsWith('apps-registry-')) {
      const unitName = layerNode.replace('apps-registry-', '');
      console.log(`Waiting for Kustomization/${unitName}...`);
      try {
        waitForConditionWithKubeconfig(vclusterKubeconfig, {
          kind: 'Kustomization.kustomize.toolkit.fluxcd.io',
          name: unitName,
          namespace: 'flux-system',
          condition: 'Ready',
          timeoutSeconds: 600,
        });
      } catch {
        throw new HarnessError('E_KUSTOMIZATION_NOT_READY', { unit: unitName, timeoutSeconds: 600 });
      }

      const conditions = getConditionsWithKubeconfig(vclusterKubeconfig, 'Kustomization', unitName, 'flux-system');
      const failed = Array.isArray(conditions) && conditions.find(
        c => c.type === 'ReconciliationFailed' && c.status === 'True',
      );
      if (failed) {
        throw new HarnessError('E_KUSTOMIZATION_RECONCILIATION_FAILED', {
          unit: unitName,
          message: failed.message,
          reason: failed.reason,
        });
      }
    }
  }

  // (3) Wait for HelmReleases
  const helmReleases = listWithKubeconfig(vclusterKubeconfig, 'HelmRelease', 'flux-system');
  for (const hr of helmReleases) {
    waitForConditionWithKubeconfig(vclusterKubeconfig, {
      kind: 'HelmRelease.helm.toolkit.fluxcd.io',
      name: hr.metadata.name,
      namespace: 'flux-system',
      condition: 'Ready',
      timeoutSeconds: 600,
    });
  }

  console.log('wait-flux-ready complete');
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
