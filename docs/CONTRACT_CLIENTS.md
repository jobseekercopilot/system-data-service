# Reproducible downstream contracts

The service compiles four provider clients from pinned OpenAPI source contracts
during Maven's `generate-sources` phase. It also pins the Application Tracker
System Data contract used by environment orchestration. Generated Java and
binaries remain under `target/` and are never committed.

| Contract | Owner | Required operation |
|---|---|---|
| `adzuna-gateway-openapi.json` | `jobseekercopilot/adzuna-gateway` | `search` |
| `application-tracker-openapi-2.0.0.json` | `jobseekercopilot/application-tracker-service` | `seedApplications`, `verifyApplications`, `resetApplications` |
| `jsearch-gateway-openapi.json` | `jobseekercopilot/jsearch-gateway` | `search` |
| `postcode-io-gateway-openapi.json` | `jobseekercopilot/postcode-io-gateway` | `getLocationByPostcode` |
| `reed-gateway-openapi.json` | `jobseekercopilot/reed-gateway` | `searchJobs` |

The gateway contracts were exported from the corresponding private source
trees on 22 July 2026. The Application Tracker artifact is OpenAPI `2.0.0`
from reviewed producer commit
[`f92cbbd`](https://github.com/jobseekercopilot/application-tracker-service/commit/f92cbbde4c8850893602fb2c53b527bef288b99a).
`contracts/SHA256SUMS` pins every reviewed input, and OpenAPI Generator 7.22.0
is pinned in `pom.xml`.

Provider clients are generated because acquisition code consumes their wider
schemas. Application orchestration intentionally uses the small closed
`SystemDataApplicationSeedRequest` and `SystemDataApplicationSeedRecord`
consumer records plus the existing authenticated `RestTemplate`: only three
internal operations are permitted, and the contract policy checks their exact
routes, security, schemas, operations and absence of legacy routes. LLM clients
remain absent because acquisition does not use one.

## Verify and generate

From a clean clone with Java 17, Maven, `jq`, and `sha256sum`:

```bash
./scripts/verify-contracts.sh
./scripts/test-contract-policy.sh
mvn -B clean verify
```

The policy check rejects missing files, modified checksums, non-OpenAPI-3
documents, removal of required operations, Application Tracker version/schema
drift, record-level ownership, and unsafe legacy application routes. The Maven
generator then validates each provider schema and compiles the generated
clients with the application tests.

## Approved update workflow

1. Merge the contract change in its owning private repository.
2. Export that repository's canonical OpenAPI document without generated
   output, credentials, examples containing user data, or provider responses.
3. Replace only the affected file in `contracts/` on a focused feature branch.
4. Review the semantic API change and confirm it is not an unapproved breaking
   change. Recompute `contracts/SHA256SUMS` with `sha256sum *.json` from inside
   the `contracts/` directory.
5. For Application Tracker, update the closed consumer records and route tests
   in the same change; breaking producer versions and consumer rollout must be
   coordinated.
6. Run the policy tests and `mvn -B clean verify`. Commit the source contract,
   checksum, application changes, and tests—never the generated client tree.

Provider acquisition remains disabled by default and is outside normal build,
test, demo, and CI execution. SD-06 owns the eventual live-acquisition boundary.
