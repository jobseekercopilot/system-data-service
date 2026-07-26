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
    application-tracker-openapi-2.0.0.json \
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

application_contract=$contract_dir/application-tracker-openapi-2.0.0.json
if ! jq -e '
    .info.version == "2.0.0"
    and .components.securitySchemes.environmentDataToken.type == "apiKey"
    and .components.securitySchemes.environmentDataToken.in == "header"
    and .components.securitySchemes.environmentDataToken.name == "X-Environment-Data-Token"
    and .paths["/internal/system-data/v1/application-scenarios"].post.operationId == "seedApplications"
    and .paths["/internal/system-data/v1/application-scenarios"].post.requestBody.content["application/json"].schema["$ref"]
        == "#/components/schemas/SystemDataApplicationSeedRequest"
    and .paths["/internal/system-data/v1/application-scenarios/{scenarioId}/owners/{userId}"].get.operationId
        == "verifyApplications"
    and .paths["/internal/system-data/v1/application-scenarios/{scenarioId}/owners/{userId}"].delete.operationId
        == "resetApplications"
    and ([.paths["/internal/system-data/v1/application-scenarios"].post,
          .paths["/internal/system-data/v1/application-scenarios/{scenarioId}/owners/{userId}"].get,
          .paths["/internal/system-data/v1/application-scenarios/{scenarioId}/owners/{userId}"].delete]
        | all(.security == [{"environmentDataToken": []}]))
    and .components.schemas.SystemDataApplicationSeedRequest.additionalProperties == false
    and (.components.schemas.SystemDataApplicationSeedRequest.required | sort)
        == ["applications", "scenarioId", "schemaVersion", "userId"]
    and .components.schemas.SystemDataApplicationSeedRequest.properties.schemaVersion.pattern == "1\\.0\\.0"
    and .components.schemas.SystemDataApplicationSeedRequest.properties.applications.maxItems == 100
    and .components.schemas.SystemDataApplicationSeedRecord.additionalProperties == false
    and (.components.schemas.SystemDataApplicationSeedRecord.required | sort)
        == ["companyName", "coverLetterDocumentId", "createdAt", "cvDocumentId", "id",
            "jobId", "jobTitle", "status", "updatedAt"]
    and (.components.schemas.SystemDataApplicationSeedRecord.properties | keys | sort)
        == ["appliedAt", "canonicalJobId", "companyName", "coverLetterDocumentId", "createdAt",
            "cvDocumentId", "externalJobId", "id", "jobId", "jobTitle", "location", "provider",
            "status", "updatedAt"]
    and (.components.schemas.SystemDataApplicationSeedRecord.properties.status.enum | sort)
        == ["ACCEPTED", "APPLIED", "DOCUMENTS_GENERATED", "INTERVIEW", "OFFER",
            "REJECTED_BY_USER", "UNSUCCESSFUL", "WITHDRAWN"]
    and (.paths | has("/internal/system-data/seed/applications") | not)
    and (.paths | has("/internal/system-data/verify/applications/{userId}") | not)
    and (.paths | has("/internal/system-data/scenario/{scenarioId}/applications/{userId}") | not)
' "$application_contract" >/dev/null; then
    echo "contract policy: Application Tracker contract is not the reviewed closed 2.0.0 System Data boundary" >&2
    exit 1
fi

if test "$checksum_file" != "-"; then
    test -f "$checksum_file" || {
        echo "contract policy: checksum manifest is missing: $checksum_file" >&2
        exit 1
    }
    (cd "$contract_dir" && sha256sum -c "$(basename "$checksum_file")")
fi

require_operation "$contract_dir/adzuna-gateway-openapi.json" search
require_operation "$application_contract" seedApplications
require_operation "$application_contract" verifyApplications
require_operation "$application_contract" resetApplications
require_operation "$contract_dir/jsearch-gateway-openapi.json" search
require_operation "$contract_dir/postcode-io-gateway-openapi.json" getLocationByPostcode
require_operation "$contract_dir/reed-gateway-openapi.json" searchJobs

echo "contract policy: five pinned OpenAPI contracts are present, intact, and compatible"
