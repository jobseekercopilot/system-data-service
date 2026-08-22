# Live acquisition boundary

Live provider acquisition is an operator-only, non-production dataset-building
tool. It is not part of the fixture runtime and must never run from unit,
integration, E2E, demo, CI, production, or normal service-start profiles.

## Ownership

- Provider gateways own external-provider authentication, terms compliance,
  rate limiting, and API communication.
- This tool calls only approved loopback or Docker gateway endpoints, then
  normalises, validates, deduplicates, bounds, and quarantines the output.
- Production services own production behaviour and data.
- The normal System Data runtime serves reviewed synthetic fixtures and
  prepares named states. E2E requests those states and contains no acquisition
  or database-seeding implementation.

The acquisition Spring beans exist only under the exact `live-acquisition`
profile. Starting that profile is still fail-closed unless every gate below is
present. The profile is a non-web, one-shot command; there is no HTTP endpoint
that can trigger acquisition.

## Required gates

An operator must explicitly set all of the following:

- `SPRING_PROFILES_ACTIVE=live-acquisition` with no `test`, `demo`, `e2e`,
  `ci`, `prod`, or `production` profile;
- both acquisition enable and execute flags to `true`;
- the exact confirmation phrase `I UNDERSTAND LIVE PROVIDERS WILL BE CALLED`;
- a non-secret terms-approval reference and named provenance reviewer;
- one or more approved providers and the matching provider enable flags;
- an explicit new semantic dataset version for the immutable output;
- a distinct quarantine output directory;
- bounded queries, locations, total gateway request combinations, provider
  result counts, and total output.

Only `http` gateway URLs on the documented loopback/Docker hosts and ports are
accepted. User-info, query strings, fragments, and unexpected paths are
rejected. Dataset identifiers and semantic versions are allowlisted, resolved
canonically, containment checked, and symlink traversal rejected. Existing
versions are immutable; overwrite and LLM enrichment are prohibited.

## Example operator invocation

This template is documentation only. Confirm provider terms, credentials in
each gateway, and local gateway health before adapting it. Never place real
credentials in this repository or shell history.

```bash
SPRING_PROFILES_ACTIVE=live-acquisition \
SYSTEM_DATA_LIVE_ACQUISITION_ENABLED=true \
SYSTEM_DATA_LIVE_ACQUISITION_EXECUTE=true \
SYSTEM_DATA_LIVE_ACQUISITION_CONFIRMATION='I UNDERSTAND LIVE PROVIDERS WILL BE CALLED' \
SYSTEM_DATA_LIVE_ACQUISITION_TERMS_APPROVAL_REFERENCE='approved-change-or-ticket' \
SYSTEM_DATA_LIVE_ACQUISITION_PROVENANCE_REVIEWER='named-reviewer' \
SYSTEM_DATA_LIVE_ACQUISITION_APPROVED_PROVIDERS=ADZUNA \
SYSTEM_DATA_ACQUISITION_ADZUNA_ENABLED=true \
SYSTEM_DATA_ACQUISITION_DATASET_VERSION=1.0.0 \
SYSTEM_DATA_ACQUISITION_OUTPUT_DIRECTORY=./quarantined-acquisitions \
java -jar target/system-data-service-1.0.0.jar
```

Normal tests and CI must not run this command or set these gates.

## Quarantine and promotion

Every output contains `acquisition-review.json` with
`PENDING_PROVENANCE_REVIEW`, `redistributionApproved=false`, and
`runtimeEligible=false`. Output is capped at 120 records, ignored by Git, and
is never copied into the governed fixture repository automatically.

Before any record can become a fixture, a reviewer must establish provenance,
licence/terms, redistribution and attribution requirements, remove personal or
proprietary content, replace unsuitable records with synthetic equivalents,
run the governed fixture policy and checksum verification, and import the
result through its own reviewed issue and pull request. Reject and delete
unapproved local output through an explicitly targeted local operation.

## Failure behaviour

Missing or conflicting gates abort startup before a gateway call. Gateway
failures are recorded with stable, non-sensitive statuses; raw exception
messages, credentials, request URLs, and provider payloads are not logged.
Partially collected output remains subject to the same quarantine and review.
