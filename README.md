# System Data Service

Private, proprietary tooling for deterministic Job Seeker Copilot fixtures and
explicitly bounded non-production state preparation.

This repository owns safe, versioned development/test/demo datasets, named
state definitions, fixture responses, and orchestration of service-owned
reset/seed/verify APIs. Provider gateways own external API communication;
production services own their data and behaviour; the E2E repository requests
a named state over HTTP and does not duplicate database-seeding logic.

The Infrastructure
[Job Search architecture ADR](https://github.com/jobseekercopilot/infrastructure/blob/develop/docs/adr/0001-job-search-architecture-and-ownership.md)
defines System Data as the non-production fixture owner, never the owner of
production Job Search behaviour or state.

## Baseline status

Status: **not operationally or beta ready**.

The initial source-only baseline deliberately excludes:

- 120 live-captured Adzuna/JSearch/Reed job records and two captured OpenAI
  outputs whose redistribution/provenance is not approved;
- runtime dataset backups and generated outputs;
- ten generated client JARs and all build output.

The complete pre-import tree is preserved in a verified local backup. SD-02
provides a reproducible contract-client build. SD-04 replaces quarantined
captures with a governed, deterministic synthetic dataset; SD-06 owns the
separate live-acquisition boundary.

## Current safe responsibilities

- Define isolated, versioned synthetic states for registration, session,
  profile/location, duplicate-account, cross-user, provider-failure, empty and
  populated-demo journeys.
- Serve deterministic fixture responses when explicitly enabled in an allowed
  non-production profile and a governed dataset is present.
- Coordinate reset, seed, reset-and-seed, and verify through service-owned
  internal APIs for authentication, profile, applications, documents, and
  payment state.
- Store/read versioned dataset models and validate, sanitise, and deduplicate
  canonical fixture records.

The service never writes directly to another service's database. Job,
job-matching, reporting, CV orchestration, and document export own no database
state here.

## Build and test

Requires Java 17 and Maven 3.6.3 or later.

```bash
./scripts/verify-contracts.sh
./scripts/test-contract-policy.sh
./scripts/verify-fixture-api-contract.sh
./scripts/test-fixture-api-contract-policy.sh
./scripts/verify-synthetic-fixtures.sh
./scripts/test-synthetic-fixture-policy.sh
mvn -B clean verify
./scripts/verify-repository-policy.sh
./scripts/test-repository-policy.sh
./scripts/test-dependency-report-policy.sh
```

Spring Boot [3.5.16](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)
is used as the supported Java 17 maintenance line. CI also
runs a complete-history secret scan, resolved-runtime dependency scan, and
hardened container build/start/health proof.

Required gateway clients are generated exclusively from pinned private source
contracts. See [the contract-client workflow](docs/CONTRACT_CLIENTS.md).
Application Tracker seed, reset and verification also consume its pinned
OpenAPI `2.0.0` contract: requests use a closed `1.0.0` schema envelope and
the same scenario-and-owner path for reset and verification. The producer and
consumer feature branches must be released together.
The fixture API that this service produces is versioned separately in
[`api/openapi.json`](api/openapi.json). Reed, Adzuna, JSearch and other fixture
consumers pin an exact producer revision and the checksum recorded in
[`api/SHA256SUMS`](api/SHA256SUMS); see the
[`api` ownership guide](api/README.md).
Fixture provenance, licence, expiry, generation, and refresh rules are in
[the fixture-governance guide](docs/FIXTURE_GOVERNANCE.md).
The discoverable prepare/verify/reset contract and state ownership rules are in
[the named-state guide](docs/NAMED_STATES.md).
[The E2E lifecycle contract](https://github.com/jobseekercopilot/e2e/blob/develop/docs/SYSTEM_DATA_LIFECYCLE.md)
is the only browser-framework integration. It calls these APIs and contains no
database seeding. E2E-03 separately owns minimum-stack wiring and persistence
proof.

Environment management requires two distinct runtime-only values of at least
32 bytes: `SYSTEM_DATA_INTERNAL_CALLER_KEY` authenticates lifecycle callers,
while `SYSTEM_DATA_DOWNSTREAM_ENVIRONMENT_DATA_TOKEN` authenticates the
orchestrator to service-owned state endpoints. See
[the environment-management security guide](docs/ENVIRONMENT_MANAGEMENT_SECURITY.md).
[Live acquisition](docs/LIVE_ACQUISITION.md) is a separate, fail-closed,
operator-only one-shot profile whose bounded output is quarantined and never
used by E2E, demos, normal runtime, or CI.

## Safety defaults

Fixture and environment-management endpoints are disabled by default. Provider
gateway configuration is disabled in this baseline. Do not enable destructive
operations against shared, production, production-like, or ambiguous/default
targets. Environment management requires an authenticated internal caller, one
explicit local/test/demo profile, and bounded loopback or Docker service
targets. See [the security and recovery contract](docs/ENVIRONMENT_MANAGEMENT_SECURITY.md).

No normal build, unit test, E2E test, demo, or CI task may call a live provider.
Do not commit real `.env` files, credentials, user data, captured provider
responses, generated JARs, runtime databases, logs, reports, datasets, or
backups.

## Consumers and overlap

- `adzuna-gateway`, `jsearch-gateway`, `reed-gateway`, `postcode-io-gateway`,
  `llm-gateway`, and `stripe-gateway` consume fixture responses.
- `authentication-service`, `user-profile-service`,
  `application-tracker-service`, `document-store-service`, and
  `payment-service` own the persistent state this service coordinates.
- `job-finder-gateway` and `job-service` keep application/provider behaviour;
  this repository supplies test inputs only.
- `e2e` requests named states and collects browser evidence. It does not seed
  databases itself.

See [the operational-readiness audit](docs/OPERATIONAL_READINESS_AUDIT.md),
[CONTRIBUTING.md](CONTRIBUTING.md), and [SECURITY.md](SECURITY.md).
