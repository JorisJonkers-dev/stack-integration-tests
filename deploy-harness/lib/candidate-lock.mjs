import { createHash } from 'node:crypto';
import { readFileSync, existsSync, statSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';
import { load as yamlLoad } from 'js-yaml';

/**
 * Error class for candidate-lock related failures.
 */
class HarnessError extends Error {
  constructor(code, details) {
    super(`${code}: ${JSON.stringify(details)}`);
    this.code = code;
    this.details = details;
  }
}

/**
 * Load and validate a SC-5 cluster-composition lock from a YAML file.
 * @param {string} lockPath
 * @returns {object} parsed lock
 */
export function loadCandidateLock(lockPath) {
  if (!existsSync(lockPath)) {
    throw new HarnessError('E_CANDIDATE_LOCK_MISSING', { path: lockPath });
  }
  const raw = readFileSync(lockPath, 'utf8');
  const lock = yamlLoad(raw);
  if (!lock || typeof lock !== 'object') {
    throw new HarnessError('E_CANDIDATE_LOCK_MISSING', { path: lockPath, reason: 'empty or invalid YAML' });
  }
  if (lock.apiVersion !== 'deployment.jorisjonkers.dev/cluster-composition-lock/v1') {
    throw new HarnessError('E_CANDIDATE_LOCK_MISSING', { path: lockPath, reason: `unexpected apiVersion: ${lock.apiVersion}` });
  }
  const required = ['composedRootDigest', 'generatedFromCommit', 'units', 'publicContext', 'internalContext'];
  for (const field of required) {
    if (!lock.spec || lock.spec[field] === undefined || lock.spec[field] === null) {
      throw new HarnessError('E_LOCK_MISSING_FIELD', { field, path: lockPath });
    }
  }
  return lock;
}

/**
 * Compute a deterministic sha256 tree digest of a directory.
 * Algorithm: sorted relative paths; sha256(concat of "relpath\nsha256hex(content)\n" per file)
 * @param {string} dir absolute path
 * @returns {string} hex digest (no "sha256:" prefix)
 */
export function sha256TreeDigest(dir) {
  const files = [];
  collectFiles(dir, dir, files);
  files.sort();

  const parts = files.map((relPath) => {
    const absPath = join(dir, relPath);
    const content = readFileSync(absPath);
    const contentHash = createHash('sha256').update(content).digest('hex');
    return `${relPath}\n${contentHash}\n`;
  });

  return createHash('sha256').update(parts.join('')).digest('hex');
}

function collectFiles(base, dir, out) {
  const entries = readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const abs = join(dir, entry.name);
    if (entry.isDirectory()) {
      collectFiles(base, abs, out);
    } else if (entry.isFile()) {
      out.push(relative(base, abs));
    }
  }
}
