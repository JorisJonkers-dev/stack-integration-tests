import { execSync, spawnSync } from 'node:child_process';

/**
 * Vault helper library for T2 harness.
 * Uses the vault CLI for all operations.
 */

let vaultToken = null;
let vaultAddr = null;

/**
 * @param {string} addr
 */
export function setVaultAddr(addr) {
  vaultAddr = addr;
}

/**
 * @param {string} token
 */
export function setVaultToken(token) {
  vaultToken = token;
}

/**
 * Login with OIDC and return the token.
 * @param {string} addr
 * @param {string} role
 * @returns {string}
 */
export async function loginWithOidc(addr, role) {
  const result = spawnSync('vault', ['login', '-method=oidc', `-address=${addr}`, `-field=token`, `role=${role}`], {
    encoding: 'utf8',
    env: { ...process.env, VAULT_ADDR: addr },
    timeout: 60_000,
  });
  if (result.status !== 0) {
    throw new Error(`Vault OIDC login failed: ${result.stderr}`);
  }
  return result.stdout.trim();
}

function vaultCmd(args) {
  const env = { ...process.env };
  if (vaultAddr) env.VAULT_ADDR = vaultAddr;
  if (vaultToken) env.VAULT_TOKEN = vaultToken;
  const result = spawnSync('vault', args, { encoding: 'utf8', env, timeout: 30_000 });
  if (result.status !== 0) {
    throw new Error(`vault ${args.join(' ')} failed: ${result.stderr}`);
  }
  return result.stdout.trim();
}

export async function mountKv(path) {
  vaultCmd(['secrets', 'enable', '-path', path, 'kv-v2']);
}

export async function createPolicy(name, { paths, capabilities }) {
  const rules = paths.map(p => `path "${p}" { capabilities = [${capabilities.map(c => `"${c}"`).join(', ')}] }`).join('\n');
  vaultCmd(['policy', 'write', name, '-']);
  // We can't pass stdin via spawnSync easily, so use a temp approach:
  const { writeFileSync, unlinkSync } = await import('node:fs');
  const tmp = `/tmp/vault-policy-${name}.hcl`;
  writeFileSync(tmp, rules);
  vaultCmd(['policy', 'write', name, tmp]);
  unlinkSync(tmp);
}

export async function enableApproleAuth() {
  try {
    vaultCmd(['auth', 'enable', 'approle']);
  } catch {
    // May already be enabled
  }
}

export async function createApprole(name, { policies, tokenTtl, tokenMaxTtl, bindSecretId }) {
  vaultCmd([
    'write', `auth/approle/role/${name}`,
    `policies=${policies.join(',')}`,
    `token_ttl=${tokenTtl}`,
    `token_max_ttl=${tokenMaxTtl}`,
    `bind_secret_id=${bindSecretId}`,
  ]);
}

export async function readRoleId(roleName) {
  const out = vaultCmd(['read', '-field=role_id', `auth/approle/role/${roleName}/role-id`]);
  return out;
}

export async function generateSecretId(roleName) {
  const out = vaultCmd(['write', '-field=secret_id', '-f', `auth/approle/role/${roleName}/secret-id`]);
  return out;
}

export async function writeKv(path, data) {
  const kvArgs = Object.entries(data).map(([k, v]) => `${k}=${v}`);
  vaultCmd(['kv', 'put', path, ...kvArgs]);
}

export async function deleteApprole(roleName) {
  vaultCmd(['delete', `auth/approle/role/${roleName}`]);
}

export async function deletePolicy(policyName) {
  vaultCmd(['policy', 'delete', policyName]);
}

export async function unmount(path) {
  vaultCmd(['secrets', 'disable', path]);
}
