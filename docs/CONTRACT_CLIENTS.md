# Reproducible contract clients

The service compiles four provider clients from pinned OpenAPI source contracts
during Maven's `generate-sources` phase. Generated Java and binaries remain
under `target/` and are never committed.

| Contract | Owner | Required operation |
|---|---|---|
| `adzuna-gateway-openapi.json` | `jobseekercopilot/adzuna-gateway` | `search` |
| `jsearch-gateway-openapi.json` | `jobseekercopilot/jsearch-gateway` | `search` |
| `postcode-io-gateway-openapi.json` | `jobseekercopilot/postcode-io-gateway` | `getLocationByPostcode` |
| `reed-gateway-openapi.json` | `jobseekercopilot/reed-gateway` | `searchJobs` |

The contracts were exported from the corresponding private gateway source
trees on 22 July 2026. `contracts/SHA256SUMS` pins the reviewed inputs, and
OpenAPI Generator 7.22.0 is pinned in `pom.xml`. LLM and state-service clients
are intentionally absent: the current acquisition code does not use an LLM
client, while state orchestration uses service-owned HTTP contracts. Add a
client only when production source requires it.

## Verify and generate

From a clean clone with Java 17, Maven, `jq`, and `sha256sum`:

```bash
./scripts/verify-contracts.sh
./scripts/test-contract-policy.sh
mvn -B clean verify
```

The policy check rejects missing files, modified checksums, non-OpenAPI-3
documents, and removal of operations required by this service. The Maven
generator then validates each complete schema and compiles the generated
clients with the application tests.

## Approved update workflow

1. Merge the contract change in its owning private gateway repository.
2. Export that repository's canonical OpenAPI document without generated
   output, credentials, examples containing user data, or provider responses.
3. Replace only the affected file in `contracts/` on a focused feature branch.
4. Review the semantic API change and confirm it is not an unapproved breaking
   change. Recompute `contracts/SHA256SUMS` with `sha256sum *.json` from inside
   the `contracts/` directory.
5. Run the policy tests and `mvn -B clean verify`. Commit the source contract,
   checksum, application changes, and tests—never the generated client tree.

Provider acquisition remains disabled by default and is outside normal build,
test, demo, and CI execution. SD-06 owns the eventual live-acquisition boundary.
