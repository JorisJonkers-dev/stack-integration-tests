#!/usr/bin/env node
import { writeFileSync } from 'node:fs';
import { env } from 'node:process';
import { requireEnv, setOutput } from '../lib/github-run.mjs';

async function main() {
  const status = requireEnv('RERUN_POLICY_STATUS');
  const reason = requireEnv('RERUN_POLICY_REASON');
  const flakyCandidatesRaw = env.FLAKY_CANDIDATES_JSON ?? '[]';
  const flakyCandidates = JSON.parse(flakyCandidatesRaw);

  const summary = {
    gate: 'stack-integration-gate',
    check_name: 'Stack Integration Gate',
    status,
    reason,
    flaky_candidates: flakyCandidates,
    actor_decision: 'none',
    redacted: false,
  };

  writeFileSync('gate-summary.json', JSON.stringify(summary, null, 2), 'utf8');
  setOutput('gate-summary-artifact', 'gate-summary-stack-integration-gate');
  setOutput('status', status);
  console.log(`Gate summary emitted: ${status} (${reason})`);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
