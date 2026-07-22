#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
contract_dir=${1:-$repository_root/contracts}
checksum_file=${2:-$contract_dir/SHA256SUMS}

require_operation() {
    contract=$1
    operation=$2
    if ! jq -e --arg operation "$operation" \
        '[.. | objects | .operationId? // empty] | index($operation) != null' \
        "$contract" >/dev/null; then
        echo "contract policy: $(basename "$contract") is missing required operationId '$operation'" >&2
        exit 1
    fi
}

for command in jq sha256sum; do
    command -v "$command" >/dev/null 2>&1 || {
        echo "contract policy: required command '$command' is unavailable" >&2
        exit 1
    }
done

for contract in \
    adzuna-gateway-openapi.json \
    jsearch-gateway-openapi.json \
    postcode-io-gateway-openapi.json \
    reed-gateway-openapi.json; do
    test -f "$contract_dir/$contract" || {
        echo "contract policy: required contract is missing: $contract" >&2
        exit 1
    }
    if ! jq -e '.openapi | type == "string" and startswith("3.")' "$contract_dir/$contract" >/dev/null; then
        echo "contract policy: $contract must declare a compatible OpenAPI 3.x document" >&2
        exit 1
    fi
done

if test "$checksum_file" != "-"; then
    test -f "$checksum_file" || {
        echo "contract policy: checksum manifest is missing: $checksum_file" >&2
        exit 1
    }
    (cd "$contract_dir" && sha256sum -c "$(basename "$checksum_file")")
fi

require_operation "$contract_dir/adzuna-gateway-openapi.json" search
require_operation "$contract_dir/jsearch-gateway-openapi.json" search
require_operation "$contract_dir/postcode-io-gateway-openapi.json" getLocationByPostcode
require_operation "$contract_dir/reed-gateway-openapi.json" searchJobs

echo "contract policy: four pinned OpenAPI contracts are present, intact, and compatible"
