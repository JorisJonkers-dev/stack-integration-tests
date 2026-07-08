#!/usr/bin/env node
import { readFileSync, writeFileSync } from 'node:fs';
import { env } from 'node:process';
import { requireEnv, warn } from '../lib/github-run.mjs';
import * as vault from '../lib/vault.mjs';

class HarnessError extends Error {
  constructor(code, details) {
    super(`${code}: ${JSON.stringify(details)}`);
    this.code = code;
    this.details = details;
  }
}

async function main() {
  const vaultAddr = requireEnv('VAULT_ADDR');
  const namespace = requireEnv('NAMESPACE');
  const cleanupStatePath = requireEnv('CLEANUP_STATE');

  // Safety: assert test-only Vault
  if (!vaultAddr.includes('.test') && !vaultAddr.includes('t2-vault')) {
    throw new HarnessError('E_VAULT_ADDR_NOT_TEST', {
      addr: vaultAddr,
      reason: 'T2 Vault seed must target test-only Vault; production Vault address rejected',
    });
  }

  const cleanupState = JSON.parse(readFileSync(cleanupStatePath, 'utf8'));

  // OIDC login
  const token = await vault.loginWithOidc(vaultAddr, 't2-ci-runner');
  vault.setVaultAddr(vaultAddr);
  vault.setVaultToken(token);

  // Scoped mount/policy/AppRole
  const mountPath = `kv-${namespace}`;
  const policyName = `t2-policy-${namespace}`;
  const roleName = `t2-approle-${namespace}`;

  await vault.mountKv(mountPath);
  await vault.createPolicy(policyName, {
    paths: [`${mountPath}/data/*`],
    capabilities: ['read', 'list'],
  });
  await vault.enableApproleAuth();
  await vault.createApprole(roleName, {
    policies: [policyName],
    tokenTtl: '1h',
    tokenMaxTtl: '2h',
    bindSecretId: true,
  });

  const roleId = await vault.readRoleId(roleName);
  const secretId = await vault.generateSecretId(roleName);

  // Seed test secrets
  await vault.writeKv(`${mountPath}/data/test-config`, {
    'app-secret': `test-placeholder-${namespace}`,
  });

  // Record cleanup state
  cleanupState.vault = { mountPath, policyName, roleName };
  writeFileSync(cleanupStatePath, JSON.stringify(cleanupState, null, 2), 'utf8');
  console.log(`Vault AppRole seeded for namespace ${namespace}`);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
