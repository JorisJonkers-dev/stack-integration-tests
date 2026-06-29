# stack-integration-tests

Private whole-stack integration gate for JorisJonkers-dev deployments.

## What It Is

The imported suite lives under `system-tests/` and is wired as a standalone Gradle subproject. The default `check` path compiles and runs non-system unit coverage only; the actual end-to-end suites are split into:

```bash
IMAGE_TAGS="$(deploy-config-schema lock images --lock deployment.lock.yml --format image-tags)" \
  ./gradlew :system-tests:testNonPlaywright :system-tests:testPlaywright
```

`IMAGE_TAGS` accepts either legacy `service=tag` entries or exact image refs emitted from `deployment.lock.yml`. `latest` is rejected so deploy PRs are validated against the same pinned images that Flux will apply.

## Links

- [Organization profile](https://github.com/JorisJonkers-dev)
- [Security policy](https://github.com/JorisJonkers-dev/.github/security/policy)
- [Changelog](./CHANGELOG.md)
- [License](./LICENSE)

Copyright (c) Joris Jonkers. Source available for viewing only; use, copying,
modification, redistribution, deployment, or reuse is not licensed. See
[LICENSE](./LICENSE).
