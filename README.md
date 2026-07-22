# System Data Service

Private, proprietary tooling for deterministic Job Seeker Copilot fixtures and
explicitly bounded non-production state preparation.

This repository owns safe, versioned development/test/demo datasets, named
state definitions, fixture responses, and orchestration of service-owned
reset/seed/verify APIs. Provider gateways own external API communication;
production services own their data and behaviour; the E2E repository requests
a named state over HTTP and does not duplicate database-seeding logic.

## Baseline status

Status: **not operationally or beta ready**.

The initial source-only baseline deliberately excludes:

- 120 live-captured Adzuna/JSearch/Reed job records and two captured OpenAI
  outputs whose redistribution/provenance is not approved;
- runtime dataset backups and generated outputs;
- ten generated client JARs and all build output;
- live dataset-acquisition classes that cannot build without those clients.

The complete pre-import tree is preserved in a verified local backup. SD-02
owns a reproducible contract-client build; SD-04 owns governed synthetic data;
SD-06 owns the live-acquisition boundary. Until those land, fixture endpoints
have no imported provider dataset and dataset generation is unavailable.

## Current safe responsibilities

- Define synthetic `EMPTY` and `DEMO_READY` scenario state.
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
mvn -B verify
./scripts/verify-repository-policy.sh
./scripts/test-repository-policy.sh
./scripts/test-dependency-report-policy.sh
```

Spring Boot [3.5.16](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)
is used as the supported Java 17 maintenance line. CI also
runs a complete-history secret scan, resolved-runtime dependency scan, and
hardened container build/start/health proof.

## Safety defaults

Fixture and environment-management endpoints are disabled by default. Provider
gateway configuration is disabled in this baseline. Do not enable destructive
operations against shared, production, production-like, or ambiguous/default
targets. SD-03 must add authenticated caller and target-boundary enforcement
before these APIs are relied upon.

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
