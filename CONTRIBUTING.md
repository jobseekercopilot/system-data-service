# Contributing

Start from `develop` and use one focused `feature/*` branch and pull request per
issue. Do not use `main`, force-push, mix findings, or commit feature work
directly to `develop`.

```bash
git switch develop
git pull --ff-only origin develop
git switch -c feature/issue-short-description
mvn -B verify
./scripts/verify-repository-policy.sh
```

Never commit generated binaries, live/captured provider responses, real user
data, credentials, environment files, runtime databases, logs, backups, or
generated datasets. New fixtures require documented provenance, licence,
attribution, deterministic generation, validation, and approval.

Destructive state operations must remain bounded to explicit local/test/demo
targets and scenario-owned synthetic identities. Do not weaken a guard or use a
live provider to make a test pass.
