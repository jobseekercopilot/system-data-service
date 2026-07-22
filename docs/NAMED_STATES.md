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
| `REGISTRATION_CLEAN` | `registration-clean-v1` | New registration | cleanup only |
| `LOGIN_SESSION` | `login-session-v1` | Login, reload, expiry and logout | authentication |
| `PROFILE_LOCATION` | `profile-location-v1` | Profile and location journeys | authentication, profile |
| `DUPLICATE_REGISTRATION` | `duplicate-registration-v1` | Duplicate-account denial | authentication |
| `CROSS_USER_SECURITY` | `cross-user-security-v1` | Ownership denial using two claimants | authentication, profile |
| `PROVIDER_FAILURE` | `provider-failure-v1` | Offline all-provider failure | none; deterministic `502` fixture |
| `DEMO_READY` | `demo-ready-v1` | Existing populated demonstration | full governed demo state |

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

The legacy `seed` and `reset-and-seed` aliases remain for existing local demo
scripts. New E2E scenarios should use list/describe/prepare/verify/reset and
must perform reset in failure cleanup. E2E must not implement database seeding.

## Change procedure

1. Add or update a catalog entry without reusing an immutable versioned ID for
   incompatible semantics.
2. Use only isolated fictional identities and the smallest component set.
3. Run `mvn -B clean verify`, fixture/repository policies, Gitleaks and the
   hardened container check.
4. Coordinate downstream state contracts and the tagged E2E profile under
   SD-07/E2E-03 before claiming stack-level readiness.

The current tests mock service-owned internal state contracts. Cross-service
state persistence remains an explicit SD-07 dependency and is not claimed here.
