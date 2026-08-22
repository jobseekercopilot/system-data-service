# Synthetic fixture governance

## Approved dataset

The immutable `1.0.0`, `1.1.0` and `1.2.0` versions of
`fixtures/datasets/uk-software-developer-demo` are approved synthetic runtime
datasets. Release-candidate E2E and all current named states pin `1.2.0`, which
retains the ten fictional software vacancies from `1.1.0` and adds four
fictional administration, finance and project-support vacancies. `1.0.0` is
retained as the nine-job predecessor and remains a legacy local fallback; no
published version is rewritten. All three versions contain six minimised place
records and one deterministic LLM response.
Employers, vacancy references, content, and URLs were authored for this
repository. No provider API, copied vacancy, real user record, provider
credential, or captured model output was used.

The dataset is private proprietary material under
`LicenseRef-JobSeekerCopilot-Proprietary`; redistribution is prohibited. There
is no external dataset to attribute or license. City names are labels only:
postcode and coordinate fields are deliberately null, so the location records
are not an authoritative geographic dataset.

`provenance.json` records classification, purpose, creation method, source
specification checksum, minimisation, licence, attribution, approval scope,
review/expiry dates, and payload checksums. `SHA256SUMS` protects every runtime
file. Runtime consumers reject missing, expired, captured, unapproved, or
checksum-invalid fixtures before reading them.

## Reproducible generation and validation

Requires `jq` and GNU `sha256sum`:

```bash
./scripts/generate-synthetic-fixtures.sh
./scripts/verify-synthetic-fixtures.sh
./scripts/test-synthetic-fixture-policy.sh
mvn -B clean verify
```

The generator transforms the reviewed source specification with sorted JSON,
fixed IDs, and fixed timestamps. The policy suite generates two independent
copies and byte-compares them with each other and with the committed output.
Negative cases cover captured-data classification, non-reserved email/PII,
secret markers, unsafe URLs, missing licence metadata, and checksum tampering.

## Deterministic Job Search expectations

The current release-candidate `1.2.0` dataset contains sixteen jobs: six for
Adzuna and five each for JSearch and Reed. A request with no search filters
returns all sixteen jobs, or the provider's exact subset. The `1.1.0`
predecessor contains ten jobs and `1.0.0` contains nine. Known provider-specific
query and location pairs remain stable, and `1.2.0` adds these aligned journeys:

| Provider | Query | Location | Expected job |
| --- | --- | --- | --- |
| Adzuna | `Junior` | `Manchester` | Junior Software Developer |
| JSearch | `Angular` | `Bristol` | Angular Developer |
| Reed | `Spring Boot` | `Leeds` | Spring Boot Developer |
| Adzuna | `Administrative Assistant` | `Birmingham` | Administrative Assistant |
| JSearch | `Accounts Assistant` | `Leeds` | Accounts Assistant |
| Reed | `Project Coordinator` | `Bristol` | Project Coordinator |
| Adzuna | `Project Coordinator` | `Manchester` | Programme Support Officer |
| JSearch | `Senior Software Engineer` | `Manchester` | Senior Software Engineer |
| Reed | `Platform Architect` | `London` | Platform Architect |

A supplied query, location, salary or remote filter that matches no fixture
returns `totalResults: 0` and an empty `jobs` collection. Filtered empty results
are never replaced with the unfiltered provider dataset. `Atlantis` is the
documented invalid-location value and `COBOL mainframe archaeologist` is the
documented no-match query used by the contract tests.

## Review, refresh, and expiry

Review by 10 January 2027 and expire on 10 July 2027. Review earlier whenever a
consumer contract, scenario, schema, licence, policy, or required field changes.

1. Confirm every input remains authored and fictional. Do not paste provider
   responses, production logs, real applications, resumes, emails, or model
   output into the source specification.
2. Treat the published `fixtures/source/uk-software-developer-demo-v1.json`
   `uk-software-developer-demo-v1.1.json` and the compositional
   `uk-software-developer-demo-v1.2.json` specifications as immutable. For
   any dataset change, create the next semantic-version source specification
   alongside them and update the generator deliberately; never silently
   rewrite a version already relied upon as evidence.
3. Update provenance review/expiry and approval scope. Approval must identify
   the private repository owner and remain limited to non-production use.
4. Regenerate and run all policy, Java, secret, dependency, and container tests.
5. Review the full generated diff. Confirm all URLs use the reserved `.test`
   domain and all email addresses, if ever required, use reserved `example.com`
   or `example.test` domains.
6. Merge through the issue/feature-branch/PR process. Never refresh from a live
   provider as part of CI, E2E, a demo, or the normal application runtime.

SD-06 owns any separately authorized live acquisition workflow. Data produced
there is quarantined pending an explicit provenance and licence review; it does
not automatically become an approved fixture.
