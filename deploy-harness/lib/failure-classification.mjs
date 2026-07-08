/**
 * Failure classification library for T2 harness shard results.
 * Classifies test output as transient or deterministic.
 */

const TRANSIENT_PATTERNS = [
  { name: 'flux-not-ready', regex: /Kustomization.*not ready|OCIRepository.*not ready/i },
  { name: 'oom-killed', regex: /OOMKilled|exit code 137/i },
  { name: 'network-timeout', regex: /connection timed out|dial tcp.*i\/o timeout/i },
  { name: 'dns-resolution', regex: /no such host|NXDOMAIN|DNS resolution failed/i },
  { name: 'vso-mount-pending', regex: /VaultStaticSecret.*not synced|secret not yet mounted/i },
  { name: 'context-cancelled', regex: /context deadline exceeded|context canceled/i },
];

/**
 * Classify test output as transient or deterministic.
 * @param {string} output
 * @param {string} suite
 * @param {string} shard
 * @param {number} attempt
 * @returns {{ type: 'transient'|'deterministic', pattern: string, snippet: string }}
 */
export function classifyFailure(output, suite, shard, attempt) {
  for (const pattern of TRANSIENT_PATTERNS) {
    const match = output.match(pattern.regex);
    if (match) {
      const idx = output.indexOf(match[0]);
      const snippet = sanitizeSnippet(output.slice(Math.max(0, idx - 100), idx + 200));
      return { type: 'transient', pattern: pattern.name, snippet };
    }
  }

  const lastLines = output.split('\n').slice(-20).join('\n');
  const snippet = sanitizeSnippet(lastLines);
  return { type: 'deterministic', pattern: 'deterministic-test-failure', snippet };
}

/**
 * Remove potential secrets from a snippet string.
 * @param {string} raw
 * @returns {string}
 */
export function sanitizeSnippet(raw) {
  return raw
    .replace(/\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/g, '<ip-redacted>')
    .replace(/Bearer [A-Za-z0-9._-]+/g, '<token-redacted>')
    .replace(/Authorization: [^\n]*/g, 'Authorization: <redacted>')
    .slice(0, 1000);
}

export { TRANSIENT_PATTERNS };
