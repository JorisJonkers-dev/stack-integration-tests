# acceptance-runner

The acceptance runner for fleet-infra #251: it loads the committed knowledge
evaluation set, seeds its fixtures into a target, runs the evaluation
queries, scores recall quality / misleading-memory rate / latency /
capture-to-availability / backlog / token cost, cleans up what it seeded,
and writes the result to a JSON + Markdown file pair.

Two targets are seeded and scored: Hindsight (memory bank) and Basic Memory
(MCP project). A third, knowledge-api, is queried read-only for comparison
against its own existing corpus — it is never seeded by this runner, so its
report carries latency and backlog but not recall/misleading-memory scores.

This module is tooling: it does not run against the live platform by itself.
Someone runs it, pointed at a config, when the pilot (#250) and the
acceptance run (#251) are ready.

## Safety

- **Bank/project prefix guard.** `assertSafeToSeed` refuses to run unless
  every enabled seeded target's bank/project name starts with
  `safety.allowedBankPrefix` (default `eval-`). This is unconditional — it
  applies to `--dry-run` runs too.
- **`--dry-run` production-host guard.** `assertOfflineSafe` additionally
  refuses to run if any enabled target's `baseUrl` host is one of the
  estate's known production hosts (`memory.jorisjonkers.dev`,
  `memory-api.jorisjonkers.dev`, `memory-mcp.jorisjonkers.dev`,
  `kb.jorisjonkers.dev`). A dry run can only ever reach a stub or staging
  endpoint.
- **Cleanup always runs.** Every note the runner seeds is tracked and
  deleted again at the end of that target's run, even if a later step threw.

## Running

```bash
./gradlew :acceptance-runner:run --args="--config path/to/target-config.yaml"

# or, after `./gradlew :acceptance-runner:installDist`:
acceptance-runner/build/install/acceptance-runner/bin/acceptance-runner \
  --config path/to/target-config.yaml \
  --evaluation-set ../system-tests/src/test/resources/evaluation-set.json \
  --results-dir ./acceptance-runner/results \
  --dry-run
```

See `config/example.yaml` for the full config shape, including the three
endpoint paths Hindsight is assumed to expose — those are this runner's
best-effort default, confirmed against the live API only when it is
actually wired up (that wiring is explicitly out of scope here).

## Testing offline

`./gradlew :acceptance-runner:check` runs the whole suite, including
`AcceptanceRunnerDryRunTest`, which drives the full seed → poll → query →
score → clean up → write-results pipeline against three in-process stub
HTTP servers (`src/test/.../stub/`) standing in for Hindsight, Basic
Memory's MCP endpoint, and knowledge-api. No network call leaves the JVM.
That test is this module's "--dry-run/offline mode tested in CI against
stubbed HTTP".
