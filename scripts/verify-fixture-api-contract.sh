#!/usr/bin/env bash
set -euo pipefail

contract_dir="${1:-api}"
contract="$contract_dir/openapi.json"
manifest="$contract_dir/SHA256SUMS"

for required_file in "$contract" "$manifest"; do
    if [[ ! -f "$required_file" || -L "$required_file" ]]; then
        echo "fixture API policy: required regular file is missing or is a symlink: $required_file" >&2
        exit 1
    fi
done

(
    cd "$contract_dir"
    sha256sum --check --strict SHA256SUMS
)

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.paths["/internal/fixtures/status"].get.operationId == "status") and
    (.paths["/internal/fixtures/jobs/search"].get.operationId == "searchJobs") and
    (.paths["/internal/fixtures/jobs/{jobId}"].get.operationId == "job") and
    (.paths["/internal/fixtures/postcodes/{postcode}"].get.operationId == "postcode") and
    (.paths["/internal/fixtures/llm/respond"].post.operationId == "llm") and
    (.paths["/internal/fixtures/stripe/respond"].post.operationId == "stripe") and
    (
      [.paths["/internal/fixtures/jobs/search"].get.parameters[].name] ==
      ["datasetId", "datasetVersion", "scenario", "provider", "query", "location", "page", "pageSize", "sort", "salaryMin", "salaryMax", "remoteType"]
    ) and
    (.paths["/internal/fixtures/jobs/search"].get.responses["200"].content["application/json"].schema["$ref"] == "#/components/schemas/FixtureJobSearchResponse") and
    (.components.schemas.FixtureJobSearchResponse.properties.jobs.items["$ref"] == "#/components/schemas/DemoJob") and
    (.components.schemas.FixtureJobSearchResponse.properties.totalResults.format == "int32") and
    (.components.schemas.DemoJob.properties.externalReference.type == "string") and
    (.components.schemas.DemoJob.properties.salaryMinimum.format == "int32") and
    (.components.schemas.DemoJob.properties.salaryMaximum.format == "int32") and
    (.components.schemas.DemoJob.properties.sourceRetrievedAt.format == "date-time") and
    (.components.schemas.DemoJob.properties.suitableForDemo.type == "boolean") and
    (.components.schemas.DemoJob.properties.qualityScore.format == "double")
' "$contract" >/dev/null

echo "fixture API policy: producer OpenAPI source is present, intact and compatible"
