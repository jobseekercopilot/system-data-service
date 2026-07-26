#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
temporary_dir=$(mktemp -d)
cleanup() { rm -rf "$temporary_dir"; }
trap cleanup EXIT INT TERM

cp -R "$repository_root/contracts" "$temporary_dir/valid"
"$script_dir/verify-contracts.sh" "$temporary_dir/valid" >/dev/null

cp -R "$repository_root/contracts" "$temporary_dir/missing"
rm "$temporary_dir/missing/reed-gateway-openapi.json"
if "$script_dir/verify-contracts.sh" "$temporary_dir/missing" - >/dev/null 2>&1; then
    echo "contract policy negative test accepted a missing contract" >&2
    exit 1
fi

cp -R "$repository_root/contracts" "$temporary_dir/missing-application"
rm "$temporary_dir/missing-application/application-tracker-openapi-2.0.0.json"
if "$script_dir/verify-contracts.sh" "$temporary_dir/missing-application" - >/dev/null 2>&1; then
    echo "contract policy negative test accepted a missing Application Tracker contract" >&2
    exit 1
fi

cp -R "$repository_root/contracts" "$temporary_dir/incompatible"
jq '.openapi = "2.0"' "$temporary_dir/incompatible/adzuna-gateway-openapi.json" \
    > "$temporary_dir/incompatible/changed.json"
mv "$temporary_dir/incompatible/changed.json" "$temporary_dir/incompatible/adzuna-gateway-openapi.json"
if "$script_dir/verify-contracts.sh" "$temporary_dir/incompatible" - >/dev/null 2>&1; then
    echo "contract policy negative test accepted an incompatible OpenAPI version" >&2
    exit 1
fi

cp -R "$repository_root/contracts" "$temporary_dir/operation"
jq 'walk(if type == "object" and .operationId? == "searchJobs" then del(.operationId) else . end)' \
    "$temporary_dir/operation/reed-gateway-openapi.json" > "$temporary_dir/operation/changed.json"
mv "$temporary_dir/operation/changed.json" "$temporary_dir/operation/reed-gateway-openapi.json"
if "$script_dir/verify-contracts.sh" "$temporary_dir/operation" - >/dev/null 2>&1; then
    echo "contract policy negative test accepted a missing required operation" >&2
    exit 1
fi

cp -R "$repository_root/contracts" "$temporary_dir/application-version"
jq '.info.version = "2.1.0"' \
    "$temporary_dir/application-version/application-tracker-openapi-2.0.0.json" \
    > "$temporary_dir/application-version/changed.json"
mv "$temporary_dir/application-version/changed.json" \
    "$temporary_dir/application-version/application-tracker-openapi-2.0.0.json"
if "$script_dir/verify-contracts.sh" "$temporary_dir/application-version" - >/dev/null 2>&1; then
    echo "contract policy negative test accepted unreviewed Application Tracker version drift" >&2
    exit 1
fi

cp -R "$repository_root/contracts" "$temporary_dir/application-owner"
jq '.components.schemas.SystemDataApplicationSeedRecord.properties.userId = {"type":"string","format":"uuid"}' \
    "$temporary_dir/application-owner/application-tracker-openapi-2.0.0.json" \
    > "$temporary_dir/application-owner/changed.json"
mv "$temporary_dir/application-owner/changed.json" \
    "$temporary_dir/application-owner/application-tracker-openapi-2.0.0.json"
if "$script_dir/verify-contracts.sh" "$temporary_dir/application-owner" - >/dev/null 2>&1; then
    echo "contract policy negative test accepted record-level Application Tracker ownership" >&2
    exit 1
fi

cp -R "$repository_root/contracts" "$temporary_dir/application-route"
jq '.paths["/internal/system-data/seed/applications"] = .paths["/internal/system-data/v1/application-scenarios"]' \
    "$temporary_dir/application-route/application-tracker-openapi-2.0.0.json" \
    > "$temporary_dir/application-route/changed.json"
mv "$temporary_dir/application-route/changed.json" \
    "$temporary_dir/application-route/application-tracker-openapi-2.0.0.json"
if "$script_dir/verify-contracts.sh" "$temporary_dir/application-route" - >/dev/null 2>&1; then
    echo "contract policy negative test accepted an unsafe legacy Application Tracker route" >&2
    exit 1
fi

echo "contract policy tests: passed"
