# System Data fixture API contract

`openapi.json` is the producer-owned source contract for the guarded fixture API
under `/internal/fixtures`. It is distinct from `contracts/`, which contains
pinned API sources for clients that System Data itself consumes.

The fixture endpoints remain disabled unless the service's explicit
non-production environment controls allow them. This contract documents request
and response compatibility; it does not weaken or replace runtime guards.

The contract follows additive semantic versioning. Removing or renaming a path,
operation, parameter, field or enum value, changing parameter order relied on by
generated clients, making an optional value required, or narrowing an accepted
constraint requires a coordinated major-version migration.

To update the contract:

1. Change `FixtureController`, its record models and `openapi.json` together.
2. Update `info.version`.
3. Run `sha256sum openapi.json` from this directory and replace the entry in
   `SHA256SUMS`.
4. Run `../scripts/test-fixture-api-contract-policy.sh`,
   `../scripts/verify-fixture-api-contract.sh` and `mvn -B clean verify`.
5. Merge the producer change before updating consumers. Each consumer records
   the exact System Data commit and checksum it vendors.

The policy protects every published operation plus the job-search parameter
order, `DemoJob` model and paged fixture response consumed by Reed, Adzuna and
JSearch.
