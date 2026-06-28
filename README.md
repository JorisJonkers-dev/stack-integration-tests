# stack-integration-tests

Private integration-test harness for the Joris Jonkers stack migration.

The imported suite lives under `system-tests/` and is wired as a standalone Gradle subproject. The default `check` path compiles and runs non-system unit coverage only; the actual end-to-end suites are split into:

```bash
IMAGE_TAGS='auth-api=v0.1.0 auth-ui=v0.1.0 home-portal=v0.1.0 knowledge-api=v0.1.0 agents-api=v0.16.0 agents-ui=v0.16.0 agent-runtime=v0.16.0' \
  ./gradlew :system-tests:testNonPlaywright :system-tests:testPlaywright
```

`IMAGE_TAGS` is a whitespace-separated set of explicit service tags. `latest` is rejected so deploy PRs are validated against the same pinned images that Flux will apply.
