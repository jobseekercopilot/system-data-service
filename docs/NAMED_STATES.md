# Versioned named states

`src/main/resources/scenarios/named-states.json` is the authoritative catalog
for local, test, demo and E2E state preparation. State definitions are data, not
browser step implementations. The service validates the complete catalog at
startup and refuses duplicate state IDs, unsafe email domains, unknown
components, invalid versions, or a seed component outside its cleanup boundary.

Every identity is fictional, uses the reserved `example.com` domain and receives
a UUID derived from `<scenarioId>:<identityKey>:user`. Consequently one state
cannot reset another state's records, and repeated preparation generates the
same identities and request bodies. Non-demo seeded identities use the published
non-production test password `PublicTestPassword123!`; it is test data rather
than a secret and must never be reused by a real account. The catalog never
contains an access token, provider credential, authentication state, or real
personal data.

## States

| State | Versioned ID | Purpose | Seeded scope |
| --- | --- | --- | --- |
| `EMPTY` | `empty-v1` | Scenario-owned data is absent | cleanup only |
| `REGISTRATION_CLEAN` | `registration-clean-v1` | Alex Taylor new-account product showcase | cleanup across payment, documents, applications, profile and authentication |
| `LOGIN_SESSION` | `login-session-v1` | Login, reload, expiry and logout | authentication |
| `PROFILE_LOCATION` | `profile-location-v1` | Profile, location and application-start journeys | authentication and profile seed; application/profile/auth cleanup |
| `DUPLICATE_REGISTRATION` | `duplicate-registration-v1` | Duplicate-account denial | authentication |
| `CROSS_USER_SECURITY` | `cross-user-security-v1` | Ownership denial using two claimants | authentication, profile |
| `REAL_WORLD_PERSONAS` | `real-world-personas-v2` | Seven sparse, typical, rich, very-rich consultant, CV-led, manual-first and career-change users | authentication and current profile contract |
| `PROVIDER_FAILURE` | `provider-failure-v1` | Offline all-provider failure | none; deterministic `502` fixture |
| `DEMO_READY` | `demo-ready-v1` | Existing populated demonstration | full governed demo state |

All current named states select the approved synthetic
`uk-software-developer-demo` dataset at version `1.1.0`. The immutable
`1.0.0` predecessor remains available for legacy local fixture fallbacks but is
not the governed dataset for these state definitions.

## Authenticated contract

All endpoints require `X-System-Data-Key`, and the environment guard must first
approve an explicit `local`, `test` or `demo` profile and bounded service
targets. Responses never expose the key or target URLs.

```text
GET  /internal/environments/states
GET  /internal/environments/states/{scenario}
POST /internal/environments/prepare
GET  /internal/environments/verify?scenario={scenario}
POST /internal/environments/reset
```

`prepare` is fail-fast reset-then-seed. A failed reset prevents all seeding. A
failed seed stops later writes. Retry `prepare` after recovery: service-owned
seed operations must upsert their deterministic IDs, and reset paths name both
the state and identity. `verify` reports per-service results and measurable
counts. `REGISTRATION_CLEAN`, `EMPTY`, and `PROVIDER_FAILURE` deliberately have
no seed payload.

The `REAL_WORLD_PERSONAS` profiles are owned by
`src/main/resources/personas/personas.json`. They intentionally use only fields
accepted by the current User Profile contract, include canonical location
provenance and structured work/commute/availability preferences, and vary from
three skills/no roles to a plausible 60-skill/18-engagement very-rich
consultant profile. This is a realistic boundary fixture, not a claim that the
product's maximum storage or rendering scale has been reached. Legacy
roles and qualifications are migrated by User Profile into its versioned
evidence library; System Data does not write evidence tables directly.

Application Tracker uses its closed OpenAPI `3.0.0` boundary. `DEMO_READY`
sends `schemaVersion`, `scenarioId`, `userId`, and at most 100 constrained
records to `POST /internal/system-data/v1/application-scenarios`; records do
not contain an owner or persistence-only fixture field. Each record carries
the exact CV and cover-letter document ID, family ID, version and SHA-256
evidence also sent to Document Store. Application reset and verification both use
`/internal/system-data/v1/application-scenarios/{scenarioId}/owners/{userId}`.
Consequently `empty-v1` cleanup cannot delete `demo-ready-v1` records even if
the same service participates in both states.

The legacy `seed` and `reset-and-seed` aliases remain for existing local demo
scripts. New E2E scenarios should use list/describe/prepare/verify/reset and
must perform reset in failure cleanup. E2E must not implement database seeding.

## Change procedure

1. Add or update a catalog entry without reusing an immutable versioned ID for
   incompatible semantics.
2. Use only isolated fictional identities and the smallest component set.
3. Run `mvn -B clean verify`, fixture/repository policies, Gitleaks and the
   hardened container check.
4. Coordinate the tagged E2E lifecycle client under SD-07, then prove its
   downstream state contracts in E2E-03's minimum stack before claiming
   stack-level readiness.

The current tests mock service-owned internal state contracts and pin the
Application Tracker producer artifact and checksum. They prove exact payloads,
routes, repeatability, failure recovery and owner/scenario isolation. Actual
cross-service state persistence remains an APP-16/E2E-03 dependency and is not
claimed here.
