# Operational-readiness audit

Audit date: 22 July 2026

Status: **Not operationally or beta ready.**

## Origin and build evidence

The source originated in the untracked root `system-data-service/` directory.
Root Git tracks zero files beneath it, so there is no useful Git history to
migrate. The root repository was not altered or rewritten.

A source backup excluding generated `target/` output contains 153 entries:

```text
/tmp/jobseekercopilot-system-data-service-source-backup-20260722.tar.gz
SHA-256 9e660d361543ce797d0c473d0af201379802cf4751d23af11c8e14585ff3afee
```

Gitleaks 8.30.1 scanned approximately 4.05 MB and found no credential. A clean
Java 17 compile/package passed six original tests, with Maven warnings for ten
project-local generated client JARs. SD-02 replaced those binaries with four
pinned OpenAPI source contracts and OpenAPI Generator 7.22.0. A clean Maven
build now validates, generates, compiles, and tests the clients without a local
JAR or `systemPath`.

## Verified responsibilities

This is a combined internal fixture runtime and non-production state
orchestrator, with a third live dataset-acquisition responsibility that needs a
separate boundary. It models provider-style job responses for Adzuna, JSearch,
and Reed; postcode responses; deterministic LLM and Stripe responses; and
canonical job/location datasets.

It coordinates service-owned APIs for authentication, profile, application,
document/file, and AI-credit/payment databases. It does not directly write SQL.
Job, matching, CV orchestration, export, and reporting persistence are outside
its ownership.

Current named states are only `EMPTY` and `DEMO_READY`. Current operations are
reset, seed (append-like), reset-and-seed, verify, and fixture query. There is
no separately defined append or clean contract.

## Data and boundary findings

The excluded runtime repository contains 120 job records collected through
Adzuna/JSearch/Reed gateways and two OpenAI outputs explicitly marked
`LIVE_CAPTURED_FIXTURE`. No provider licence, attribution, redistribution,
retention, approval, or refresh evidence accompanies them. They and two backup
copies are quarantined in the local backup and are not in Git history.

The included Alex Taylor identity and scenario records are synthetic. Stable
UUIDs, fixed reference dates, deterministic ordering, and immutable version
paths exist, but repeat seed/reset idempotency and guard behavior are not yet
adequately tested.

Provider gateways own external calls. Production services own production data
and behavior. This service owns safe datasets/state preparation. E2E requests a
named state over HTTP and must not duplicate seeding logic. Broad root Compose
and Python orchestration remain legacy inputs to replace in SD-07/E2E-03.

## Findings and dependencies

| Issue | Finding | Severity / priority | Dependency | Beta blocker |
|---|---|---|---|---|
| SD-01 | Safe source-only baseline | High / P1 | None | Yes |
| SD-02 | Reproducible contract clients; no JARs | High / P1 | SD-01 | Yes — complete |
| SD-03 | Production-safe authenticated reset/seed | Critical / P0 | SD-01/02 | Yes |
| SD-04 | Governed synthetic datasets/provenance | High / P1 | SD-01 | Yes |
| SD-05 | Named states, determinism, idempotency | High / P1 | SD-02/03/04 | Yes |
| SD-06 | Separate live acquisition boundary and validate dataset paths | High / P1 | SD-01/04 | Yes |
| SD-07 | E2E named-state/minimum-stack integration | High / P1 | SD-03/04/05 and E2E-02/03 | Yes |

## Definition of Done

- Clean clone builds/tests without committed generated binaries.
- Every fixture is synthetic or explicitly approved, versioned, minimised, and
  covered by provenance/licence/attribution and deterministic checksums.
- Normal tests, E2E, demos, and CI cannot call live providers.
- Fixture/state APIs require an approved non-production environment and caller;
  production-like targets cannot be mutated under misconfiguration.
- Named states are discoverable, deterministic, isolated, idempotent, safely
  resettable, and verified through service-owned contracts.
- CI, full-history secrets, dependency/container scans, and cross-service tests
  pass without unaccepted Critical/High findings.
- E2E proves reset -> prepare -> verify -> journey -> reset without duplicated
  seeding logic or exposed authentication/personal data.
- Boundaries, provenance, refresh procedures, limitations, and evidence are
  current.

This Definition of Done has not been demonstrated.
