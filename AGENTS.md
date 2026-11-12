# Agents

Guidance for any coding agent working in this repository. It is the private whole-stack integration gate for estate deployments.

The repo's agent contract is [CLAUDE.md](CLAUDE.md); the estate-wide conventions are at https://github.com/JorisJonkers-dev/workspace/blob/main/CLAUDE.md.

## Build and test

- Deploy harness unit tests: `npm ci` then `npm test`
- System tests, with `IMAGE_TAGS` set to pinned image refs: `./gradlew :system-tests:testNonPlaywright :system-tests:testPlaywright`
- The suite lives under `system-tests`; the harness scripts under `deploy-harness`.
