#!/usr/bin/env node
import { readFileSync, existsSync } from 'node:fs';
import { env } from 'node:process';
import { spawnSync } from 'node:child_process';
import { getEnv, warn } from '../lib/github-run.mjs';
import * as vault from '../lib/vault.mjs';
import { deleteNamespace, waitForNamespaceGone } from '../lib/kubectl.mjs';

async function main() {
  const namespace = getEnv('NAMESPACE');
  const vclusterName = getEnv('VCLUSTER_NAME');
  const cleanupStatePath = getEnv('CLEANUP_STATE');
  const vaultAddr = getEnv('VAULT_ADDR');

  if (!namespace) {
    console.log('No namespace provided; nothing to clean up');
    return;
  }

  // (1) Destroy Vault resources from cleanup state
  if (cleanupStatePath && existsSync(cleanupStatePath)) {
    const state = JSON.parse(readFileSync(cleanupStatePath, 'utf8'));
    if (state.vault && vaultAddr) {
      vault.setVaultAddr(vaultAddr);
      try {
        await vault.deleteApprole(state.vault.roleName);
        await vault.deletePolicy(state.vault.policyName);
        await vault.unmount(state.vault.mountPath);
      } catch (err) {
        warn(`Vault cleanup partial failure (continuing): ${err.message}`);
      }
    }
  }

  // (2) Delete vcluster helm release
  if (vclusterName) {
    const result = spawnSync(
      'helm',
      ['uninstall', vclusterName, '-n', namespace, '--ignore-not-found'],
      { encoding: 'utf8', timeout: 60_000 },
    );
    if (result.status !== 0) {
      warn(`Helm uninstall failed (continuing): ${result.stderr}`);
    }
  }

  // (3) Delete namespace on host cluster (cascades all resources)
  try {
    deleteNamespace(namespace);
    waitForNamespaceGone(namespace, 120);
  } catch (err) {
    warn(`Namespace deletion error (continuing): ${err.message}`);
  }

  console.log(`Cleanup complete for namespace: ${namespace}`);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
