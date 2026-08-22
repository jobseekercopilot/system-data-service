#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

copy_contract() {
    local destination="$1"
    mkdir -p "$destination"
    cp "$repository_root/api/openapi.json" "$repository_root/api/SHA256SUMS" "$destination/"
}

"$repository_root/scripts/verify-fixture-api-contract.sh" "$repository_root/api" >/dev/null

copy_contract "$temporary_dir/missing"
rm "$temporary_dir/missing/openapi.json"
if "$repository_root/scripts/verify-fixture-api-contract.sh" "$temporary_dir/missing" >/dev/null 2>&1; then
    echo "fixture API policy negative test accepted a missing contract" >&2
    exit 1
fi

copy_contract "$temporary_dir/drift"
jq '.info.description = "unreviewed drift"' "$temporary_dir/drift/openapi.json" > "$temporary_dir/drift/changed.json"
mv "$temporary_dir/drift/changed.json" "$temporary_dir/drift/openapi.json"
if "$repository_root/scripts/verify-fixture-api-contract.sh" "$temporary_dir/drift" >/dev/null 2>&1; then
    echo "fixture API policy negative test accepted checksum drift" >&2
    exit 1
fi

copy_contract "$temporary_dir/operation"
jq 'del(.paths["/internal/fixtures/jobs/search"].get)' "$temporary_dir/operation/openapi.json" > "$temporary_dir/operation/changed.json"
mv "$temporary_dir/operation/changed.json" "$temporary_dir/operation/openapi.json"
(cd "$temporary_dir/operation" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-fixture-api-contract.sh" "$temporary_dir/operation" >/dev/null 2>&1; then
    echo "fixture API policy negative test accepted removal of job search" >&2
    exit 1
fi

copy_contract "$temporary_dir/parameters"
jq '.paths["/internal/fixtures/jobs/search"].get.parameters |= reverse' "$temporary_dir/parameters/openapi.json" > "$temporary_dir/parameters/changed.json"
mv "$temporary_dir/parameters/changed.json" "$temporary_dir/parameters/openapi.json"
(cd "$temporary_dir/parameters" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-fixture-api-contract.sh" "$temporary_dir/parameters" >/dev/null 2>&1; then
    echo "fixture API policy negative test accepted incompatible generated-client parameter order" >&2
    exit 1
fi

echo "fixture API contract policy tests passed"
