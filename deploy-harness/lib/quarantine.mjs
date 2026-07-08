import { readFileSync, existsSync } from 'node:fs';
import { load as yamlLoad } from 'js-yaml';

/**
 * @typedef {object} QuarantineEntry
 * @property {string} testClass
 * @property {boolean} ownerApproved
 * @property {string} issueUrl
 * @property {string} expiresAt
 * @property {string} [reason]
 */

/**
 * @typedef {object} QuarantineManifest
 * @property {QuarantineEntry[]} entries
 */

/**
 * Load a quarantine manifest YAML file.
 * @param {string} path
 * @returns {QuarantineManifest}
 */
export function loadQuarantineManifest(path) {
  if (!existsSync(path)) {
    return { entries: [] };
  }
  const raw = readFileSync(path, 'utf8');
  const parsed = yamlLoad(raw);
  return { entries: parsed?.entries ?? [] };
}

/**
 * Validate a quarantine manifest: every entry must have required fields.
 * Throws on invalid entry.
 * @param {QuarantineManifest} manifest
 */
export function validateQuarantineManifest(manifest) {
  for (const entry of manifest.entries) {
    if (!entry.testClass) {
      throw new Error(`Quarantine entry missing testClass: ${JSON.stringify(entry)}`);
    }
    if (typeof entry.ownerApproved !== 'boolean') {
      throw new Error(`Quarantine entry ${entry.testClass} missing ownerApproved (boolean)`);
    }
    if (!entry.issueUrl || !entry.issueUrl.startsWith('https://github.com/JorisJonkers-dev/')) {
      throw new Error(`Quarantine entry ${entry.testClass} has invalid issueUrl: ${entry.issueUrl}`);
    }
    if (!entry.expiresAt) {
      throw new Error(`Quarantine entry ${entry.testClass} missing expiresAt`);
    }
  }
}
