# Security policy

Report vulnerabilities privately to the repository owner. Do not put tokens,
credentials, provider payloads, personal data, sensitive logs, or exploit
details in public or project issue content.

Fixture and state-management APIs are internal, disabled by default, and must
never target production or production-like systems. Cleanup must be restricted
to scenario-owned synthetic identities. Environment-management calls require
the uncommitted `X-System-Data-Key`; never disclose it in logs or reports. Live provider acquisition requires a
separate, explicitly authorized workflow and is never part of normal tests.

Do not bypass a failed secret, dependency, container, provenance, or safety
check. Current security blockers and unblock conditions are recorded in
`docs/OPERATIONAL_READINESS_AUDIT.md` and private issues #4, #5, #6, and #7.

CI runs `scripts/test-secret-history.sh` before
`scripts/verify-secret-history.sh`. The scanner configures the read-only Docker
mount as a safe Git directory, proves the repository has history, requires
Gitleaks to report a non-zero commit count, and uses redacted output. A Git
discovery error or zero-commit result fails the job; it is never valid evidence
of a clean history.
