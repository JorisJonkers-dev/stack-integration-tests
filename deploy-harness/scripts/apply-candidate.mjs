#!/usr/bin/env node
import { readFileSync, writeFileSync } from 'node:fs';
import { readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { env } from 'node:process';
import { spawnSync } from 'node:child_process';
import { requireEnv } from '../lib/github-run.mjs';
import { serverSideApplyWithKubeconfig } from '../lib/kubectl.mjs';

async function getVclusterKubeconfig(vclusterName, namespace) {
  const result = spawnSync('vcluster', ['connect', vclusterName, '-n', namespace, '--print-token'], {
    encoding: 'utf8',
    timeout: 30_000,
  });
  if (result.status !== 0) {
    throw new Error(`Failed to get vcluster kubeconfig: ${result.stderr}`);
  }
  return result.stdout;
}

async function main() {
  const candidateDir = requireEnv('CANDIDATE_DIR');
  const namespace = requireEnv('NAMESPACE');
  const vclusterName = requireEnv('VCLUSTER_NAME');
  const runnerTemp = env.RUNNER_TEMP ?? '/tmp';

  const lock = JSON.parse(readFileSync(join(runnerTemp, 'candidate-lock.json'), 'utf8'));

  // (1) Rewrite Traefik IngressRoute host rules to *.jorisjonkers.test
  const edgeDir = join(candidateDir, 'cluster', 'flux', 'apps', 'edge');
  let edgeFiles = [];
  try {
    edgeFiles = (await readdir(edgeDir)).filter(f => f.startsWith('traefik-') && f.endsWith('.yaml'));
  } catch {
    // edge dir may not exist in all configs
  }

  for (const file of edgeFiles) {
    const filePath = join(edgeDir, file);
    const content = readFileSync(filePath, 'utf8');
    const rewritten = content.replace(/\.jorisjonkers\.dev\b/g, '.jorisjonkers.test');
    writeFileSync(filePath, rewritten, 'utf8');
  }

  const vclusterKubeconfig = await getVclusterKubeconfig(vclusterName, namespace);

  // (2) Apply registry resources per DAG order
  const applyOrder = lock.spec?.dependencyGraph?.order ?? [];
  for (const layerNode of applyOrder) {
    if (layerNode.startsWith('apps-registry-')) {
      const unitName = layerNode.replace('apps-registry-', '');
      const unitDir = join(candidateDir, 'cluster', 'flux', 'apps', 'registry', unitName);
      serverSideApplyWithKubeconfig(vclusterKubeconfig, unitDir, { fieldManager: 't2-harness' });
    }
  }

  // Apply edge aggregates
  if (edgeFiles.length > 0) {
    serverSideApplyWithKubeconfig(vclusterKubeconfig, edgeDir, { fieldManager: 't2-harness' });
  }

  // Apply gatus observability
  const gatusDir = join(candidateDir, 'cluster', 'flux', 'apps', 'observability', 'gatus');
  try {
    serverSideApplyWithKubeconfig(vclusterKubeconfig, gatusDir, { fieldManager: 't2-harness' });
  } catch {
    // gatus dir may not exist in all configs
  }

  console.log('apply-candidate complete');
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
