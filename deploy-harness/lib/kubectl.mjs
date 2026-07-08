import { execSync, spawnSync } from 'node:child_process';

/**
 * Run kubectl with a given kubeconfig string (written to a temp file).
 */
import { writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

function withKubeconfigFile(kubeconfig, fn) {
  const dir = mkdtempSync(join(tmpdir(), 'kubectl-'));
  const kcFile = join(dir, 'kubeconfig');
  writeFileSync(kcFile, kubeconfig, { mode: 0o600 });
  try {
    return fn(kcFile);
  } finally {
    try { execSync(`rm -rf ${dir}`); } catch {}
  }
}

/**
 * Run kubectl and return stdout as string.
 * @param {string[]} args
 * @param {object} [opts]
 * @returns {string}
 */
export function kubectl(args, opts = {}) {
  const result = spawnSync('kubectl', args, {
    encoding: 'utf8',
    env: { ...process.env, ...opts.env },
    timeout: (opts.timeoutSeconds ?? 120) * 1000,
  });
  if (result.status !== 0) {
    throw new Error(`kubectl ${args.join(' ')} failed (exit ${result.status}):\n${result.stderr ?? ''}`);
  }
  return result.stdout ?? '';
}

/**
 * Run kubectl with an explicit kubeconfig string.
 * @param {string} kubeconfig - kubeconfig YAML/JSON content
 * @param {string[]} args
 * @param {object} [opts]
 * @returns {string}
 */
export function kubectlWithKubeconfig(kubeconfig, args, opts = {}) {
  return withKubeconfigFile(kubeconfig, (kcFile) =>
    kubectl(args, { ...opts, env: { KUBECONFIG: kcFile } }),
  );
}

/**
 * Create a namespace on the host cluster.
 * @param {string} namespace
 * @param {object} [opts] - { labels }
 */
export function createNamespace(namespace, opts = {}) {
  const labels = opts.labels ?? {};
  const labelArgs = Object.entries(labels).flatMap(([k, v]) => ['--labels', `${k}=${v}`]);
  try {
    kubectl(['create', 'namespace', namespace, ...labelArgs]);
  } catch (err) {
    // If already exists, check ownership
    const nsJson = kubectl(['get', 'namespace', namespace, '-o', 'json']);
    const ns = JSON.parse(nsJson);
    const existingLabels = ns.metadata?.labels ?? {};
    const runId = labels['t2-run-id'];
    if (runId && existingLabels['t2-run-id'] !== runId) {
      const e = new Error(`E_NAMESPACE_COLLISION: namespace ${namespace} exists with different t2-run-id`);
      e.code = 'E_NAMESPACE_COLLISION';
      throw e;
    }
    // Owned by this run — reuse
  }
}

/**
 * Delete a namespace.
 * @param {string} namespace
 */
export function deleteNamespace(namespace) {
  kubectl(['delete', 'namespace', namespace, '--ignore-not-found=true']);
}

/**
 * Wait for a namespace to be fully gone.
 * @param {string} namespace
 * @param {number} timeoutSeconds
 */
export function waitForNamespaceGone(namespace, timeoutSeconds = 120) {
  kubectl(['wait', '--for=delete', `namespace/${namespace}`, `--timeout=${timeoutSeconds}s`]);
}

/**
 * Server-side apply a directory with a given kubeconfig.
 * @param {string} kubeconfig
 * @param {string} dirPath
 * @param {object} [opts] - { fieldManager }
 */
export function serverSideApplyWithKubeconfig(kubeconfig, dirPath, opts = {}) {
  const fieldManager = opts.fieldManager ?? 't2-harness';
  kubectlWithKubeconfig(kubeconfig, [
    'apply',
    '--server-side',
    `--field-manager=${fieldManager}`,
    '-R',
    '-f', dirPath,
  ]);
}

/**
 * Wait for a condition on a resource with a given kubeconfig.
 */
export function waitForConditionWithKubeconfig(kubeconfig, { kind, name, namespace, condition, timeoutSeconds }) {
  const nsArgs = namespace ? ['-n', namespace] : [];
  kubectlWithKubeconfig(kubeconfig, [
    'wait',
    kind + '/' + name,
    ...nsArgs,
    `--for=condition=${condition}`,
    `--timeout=${timeoutSeconds}s`,
  ]);
}

/**
 * Get conditions for a resource with a given kubeconfig.
 * @returns {Array}
 */
export function getConditionsWithKubeconfig(kubeconfig, kind, name, namespace) {
  const nsArgs = namespace ? ['-n', namespace] : [];
  const out = kubectlWithKubeconfig(kubeconfig, [
    'get', kind, name, ...nsArgs, '-o', 'jsonpath={.status.conditions}',
  ]);
  try {
    return JSON.parse(out);
  } catch {
    return [];
  }
}

/**
 * List resources of a kind with a given kubeconfig.
 * @param {string} kubeconfig
 * @param {string} kind
 * @param {string} namespace - use '--all-namespaces' for all
 * @returns {Array}
 */
export function listWithKubeconfig(kubeconfig, kind, namespace) {
  const nsArgs = namespace === '--all-namespaces' ? ['-A'] : ['-n', namespace];
  const out = kubectlWithKubeconfig(kubeconfig, [
    'get', kind, ...nsArgs, '-o', 'json',
  ]);
  const parsed = JSON.parse(out);
  return parsed.items ?? [];
}

/**
 * List pods in a namespace with a given kubeconfig.
 */
export function listPodsInNamespaceWithKubeconfig(kubeconfig, namespace) {
  return listWithKubeconfig(kubeconfig, 'pods', namespace);
}

/**
 * Create a secret with a given kubeconfig.
 */
export function createSecretWithKubeconfig(kubeconfig, { name, namespace, data }) {
  const fromLiteralArgs = Object.entries(data).flatMap(([k, v]) => [`--from-literal=${k}=${v}`]);
  kubectlWithKubeconfig(kubeconfig, [
    'create', 'secret', 'generic', name,
    '-n', namespace,
    ...fromLiteralArgs,
    '--dry-run=client', '-o', 'yaml',
  ]);
  // Actually apply it
  const yaml = kubectlWithKubeconfig(kubeconfig, [
    'create', 'secret', 'generic', name,
    '-n', namespace,
    ...fromLiteralArgs,
    '--dry-run=client', '-o', 'yaml',
  ]);
  kubectlWithKubeconfig(kubeconfig, ['apply', '-f', '-'], { stdin: yaml });
}
