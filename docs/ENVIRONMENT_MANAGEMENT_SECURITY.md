# Environment-management security and recovery

The `/internal/environments/**` API is a non-production control surface. It is
disabled by default and is not a production administration API.

## Required controls

Every request, including status and verify, requires the exact
`X-System-Data-Key` value configured through
`SYSTEM_DATA_INTERNAL_CALLER_KEY`. The key must contain at least 32 characters,
has no repository default, is compared in constant time, and must never be put
in Git, logs, screenshots, reports, issue comments, or command output.

Enabling the API is insufficient on its own. The process must have exactly one
active Spring profile: `local`, `test`, or `demo`. No profile, `default`,
multiple profiles, production-like profiles, or a configured allow-list that
tries to expand this hard-coded boundary is rejected.

All five downstream base URLs are validated before any request. Only plain HTTP
to loopback or the exact Docker Compose service DNS name on the service's
assigned development port is accepted. Credentials, paths, query strings,
fragments, external/private IP addresses, cross-wired service names, and other
ports are rejected. This service has no database connection and never accepts
a database target.

Downstream service-owned environment guards remain mandatory. The services
must not expose their internal state endpoints outside the bounded development
network. This caller key authorizes the orchestrator endpoint; it is not sent
to, or treated as a credential for, downstream application APIs.

The orchestrator instead uses the separately configured
`SYSTEM_DATA_DOWNSTREAM_ENVIRONMENT_DATA_TOKEN` in exactly one
`X-Environment-Data-Token` header on service-owned seed, reset and verify
requests. Both runtime values must contain at least 32 bytes and must be
distinct. Enabling environment management with a missing, weak or reused
downstream credential fails startup. Neither credential may be committed,
logged, returned in an API response, or reused outside a disposable
local/test/demo stack.

The target guard runs before the authenticated downstream client is invoked,
and redirects remain disabled. Consequently the credential can only be sent
to the exact loopback or Compose service names and ports described above.

## Identity and mutation boundary

Reset paths are constructed internally from the selected named scenario and
its deterministic synthetic identity. A caller cannot provide a user ID,
service URL, path, database, or delete predicate. Seed bodies use deterministic
IDs and the same scenario-owned identity. No broad-delete endpoint is invoked.

Application Tracker is additionally constrained by its pinned OpenAPI `3.0.0`
contract. Its `2.0.0` seed envelope is closed; individual records have no owner
or fixture-persistence field and must include exact immutable document family,
version and checksum evidence. Its GET verification and DELETE reset share one
scenario-and-owner path. The previous bare-array seed, owner-wide verify, and
legacy reset paths are rejected by repository policy and are not compatibility
aliases.

## Atomicity and retry contract

There is no distributed transaction across the five independently owned
services. Operations therefore use a fail-fast saga-style contract:

- reset runs payment, documents, applications, profile, then authentication;
- seed runs authentication, profile, payment, documents, then applications;
- after the first failed response or transport error, later mutations are
  marked `SKIPPED` and are not attempted;
- `reset-and-seed` never starts seed if reset is incomplete;
- failure details and internal URLs are not returned to callers;
- a successful repeated reset is a no-op for already absent scenario records;
- repeated seed sends byte-identical deterministic scenario payloads to
  service-owned idempotent seed endpoints;
- an Application Tracker contract rejection or unavailable producer is reported
  as a failed downstream phase, after which the same scenario must recover via
  reset-and-seed and verify.

A failed standalone reset may be retried after the named dependency recovers.
A failed seed, or any ambiguous client-side timeout, must be recovered by
running `reset-and-seed` for the same scenario, followed by `verify`. Never
attempt manual database cleanup. SD-07 supplies the bounded E2E lifecycle
client; E2E-03 owns final local-stack proof of this full recovery journey.

## Local invocation

Use a key supplied outside Git and avoid shell history where practical:

```text
GET  /internal/environments/states
GET  /internal/environments/states/{scenario}
POST /internal/environments/prepare
POST /internal/environments/reset
POST /internal/environments/seed
POST /internal/environments/reset-and-seed
GET  /internal/environments/verify?scenario=DEMO_READY
Header: X-System-Data-Key: <local secret of at least 32 characters>
```

The versioned catalog, state-specific identities and component boundaries are
documented in [NAMED_STATES.md](NAMED_STATES.md). `prepare` is the preferred E2E
operation; it retains reset-before-seed and fail-fast semantics.

Responses report `SUCCESS` or `FAILED`, per-service results, skipped phases,
and safe retry guidance. The status response does not disclose target URLs.
