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

SD-05 adds versioned states for empty, registration, login/session,
profile/location, duplicate registration, cross-user security, provider
failure, and the populated demo. Discoverable list/describe/prepare/reset/verify
operations consume a validated declarative catalog. Every state has isolated
deterministic synthetic identities and bounded component cleanup. Seed and
reset-and-seed remain compatibility aliases. SD-03 requires an
authenticated internal caller, rejects ambiguous/default and production-like
profiles and targets, bounds mutation to the scenario identity, and defines a
fail-fast non-atomic recovery contract. SD-07 supplies the authoritative E2E
lifecycle adapter; E2E-03 still owns actual minimum-stack persistence proof.

## Data and boundary findings

The excluded runtime repository contains 120 job records collected through
Adzuna/JSearch/Reed gateways and two OpenAI outputs explicitly marked
`LIVE_CAPTURED_FIXTURE`. No provider licence, attribution, redistribution,
retention, approval, or refresh evidence accompanies them. They and two backup
copies are quarantined in the local backup and are not in Git history.

SD-04 adds a separately authored nine-job synthetic dataset. Its fictional
employers, reserved `.test` URLs, null postcode/coordinate fields, provenance,
proprietary licence, approval scope, lifecycle dates, deterministic source and
payload checksums are validated in CI and again by runtime consumers. Repeated
generation is byte-stable and negative PII/secret/captured-data/URL/licence
tests pass. It contains none of the quarantined records or model responses.

All included identities and scenario records are synthetic. Stable UUIDs,
fixed reference dates, deterministic ordering, immutable version paths,
catalog validation, and repeated prepare/verify/reset behavior are tested.

Provider gateways own external calls. Production services own production data
and behavior. This service owns safe datasets/state preparation. E2E requests a
named state over HTTP and must not duplicate seeding logic. Broad root Compose
and Python orchestration remain legacy inputs to replace through the SD-07
lifecycle adapter and E2E-03 minimum stack.

SD-06 separates acquisition into an exact, non-web `live-acquisition` profile.
Normal runtime has no gateway client, collection service, generation service,
or acquisition runner beans even if gateway flags are plausibly misconfigured.
The one-shot path requires explicit enable/execute/confirmation gates, an
approval reference, a provenance reviewer, allowlisted local gateway targets,
approved enabled providers, immutable path-safe identifiers, and bounded
output. It cannot run with test/demo/E2E/CI/production profiles, cannot enrich
through an LLM, and writes only to a Git-ignored quarantine with a pending,
non-redistributable, non-runtime review record. Tests cover fail-closed modes,
unsafe URLs and profiles, bounds, overwrite, traversal, containment, and
symlink paths without making a provider call.

APP-11-D1 pins Application Tracker OpenAPI `2.0.0` from producer commit
`f92cbbd` and replaces the bare application array plus owner-wide legacy routes.
System Data now sends a closed versioned owner/scenario envelope, and reset and
verify share the producer-owned scoped route. Contract checksums and negative
policy tests detect schema, security, ownership, operation and legacy-route
drift. Local orchestration tests preserve all nine deterministic IDs,
timestamps and status counts, cover repeated lifecycle operations and isolated
empty-state cleanup, and make contract rejection/unavailability recoverable
without exposing transport details.

## Findings and dependencies

| Issue | Finding | Severity / priority | Dependency | Beta blocker |
|---|---|---|---|---|
| SD-01 | Safe source-only baseline | High / P1 | None | Yes |
| SD-02 | Reproducible contract clients; no JARs | High / P1 | SD-01 | Yes — complete |
| SD-03 | Production-safe authenticated reset/seed | Critical / P0 | SD-01/02 | Yes — complete locally; stack proof in E2E-03 |
| SD-04 | Governed synthetic datasets/provenance | High / P1 | SD-01 | Yes — complete |
| SD-05 | Named states, determinism, idempotency | High / P1 | SD-02/03/04 | Yes — complete locally; stack proof in E2E-03 |
| SD-06 | Separate live acquisition boundary and validate dataset paths | High / P1 | SD-01/04 | Yes — complete locally |
| SD-07 | E2E named-state lifecycle integration | High / P1 | SD-03/04/05/06 and E2E-02 | Yes — E2E client in delivery; stack proof remains E2E-03 |
| SD-09 | Authenticate service-owned named-state operations | High / P1 | SD-03/07 | Yes — downstream credential contract fixed locally; stack proof remains E2E-03 |
| SD-10 | Fail closed when the history secret scan cannot inspect commits | High / P1 | None | Yes — complete |
| APP-11-D1 | Consume Application Tracker System Data OpenAPI 2.0.0 | Critical / P0 | APP-11 producer commit `f92cbbd`; APP-16 stack proof | Yes — repository implementation complete; integrated proof remains |

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
