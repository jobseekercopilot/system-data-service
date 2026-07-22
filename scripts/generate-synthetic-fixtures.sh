#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
source_file=${1:-$repository_root/fixtures/source/uk-software-developer-demo-v1.json}
output_dir=${2:-$repository_root/fixtures/datasets/uk-software-developer-demo/1.0.0}
scenario_dir="$repository_root/src/main/resources/scenarios/demo-ready-v1"
temporary_dir=$(mktemp -d)
cleanup() { rm -rf "$temporary_dir"; }
trap cleanup EXIT INT TERM

for command in jq sha256sum; do
    command -v "$command" >/dev/null 2>&1 || {
        echo "synthetic fixture generation: required command '$command' is unavailable" >&2
        exit 1
    }
done
jq -e '.dataset.id and .dataset.version and (.jobs | length == 9) and (.locations | length > 0)' \
    "$source_file" >/dev/null

jq -S '{
  schemaVersion: "1.0",
  jobs: [.jobs[] | {
    id,
    externalReference,
    sourceProvider,
    title,
    companyName: .company,
    companyDisplayName: .company,
    locationName: .location,
    postcode: null,
    region: null,
    latitude: null,
    longitude: null,
    salaryMinimum,
    salaryMaximum,
    salaryCurrency: "GBP",
    salaryPeriod: "YEAR",
    employmentType: "FULL_TIME",
    workingPattern: "FULL_TIME",
    remoteType,
    contractType: "PERMANENT",
    category: "Software Development",
    seniority,
    description,
    shortDescription: (.description[0:140]),
    skills,
    qualifications: [],
    benefits: ["Synthetic benefit description"],
    datePosted,
    closingDate: "2026-08-31",
    sourceUrl: ("https://jobs.example.test/synthetic/" + .externalReference),
    sourceRetrievedAt: "2026-07-10T09:00:00Z",
    sourceQuery: .title,
    sourceLocation: .location,
    sourceMetadata: {fixture: true, classification: "FULLY_SYNTHETIC"},
    generatedMetadata: {generator: "generate-synthetic-fixtures.sh", sourceKey: .externalReference},
    suitableForDemo: true,
    qualityScore: 95.0
  }]
}' "$source_file" > "$temporary_dir/jobs.json"

jq -S '{
  schemaVersion: "1.0",
  locations: [.locations[] | {
    id,
    postcode: null,
    postcodeDistrict: null,
    postcodeArea: null,
    placeName,
    district: null,
    county: null,
    region,
    country: "United Kingdom",
    latitude: null,
    longitude: null,
    sourceProvider: "synthetic-generator",
    sourceRetrievedAt: "2026-07-10T09:00:00Z"
  }]
}' "$source_file" > "$temporary_dir/locations.json"

jq -S '{"DEMO_READY:CV_COVER_LETTER_GENERATION": .llmFixture}' \
    "$source_file" > "$temporary_dir/llm-fixtures.json"

payload_checksum=$(
    cd "$temporary_dir"
    sha256sum jobs.json locations.json | sha256sum | cut -d ' ' -f 1
)

jq -S --arg checksum "$payload_checksum" '{
  datasetId: .dataset.id,
  name: .dataset.name,
  version: .dataset.version,
  schemaVersion: "1.0",
  createdAt: .dataset.createdAt,
  description: .dataset.description,
  status: "APPROVED_SYNTHETIC",
  sourceProviderNames: ["synthetic-generator"],
  queries: [.jobs[].title] | unique,
  locations: [.locations[].placeName] | unique,
  sources: [{
    gateway: "synthetic-generator",
    query: "deterministic-source-specification",
    location: "non-production",
    retrievedAt: .dataset.createdAt,
    status: "SYNTHETIC",
    warning: null
  }],
  recordCounts: {jobs: (.jobs | length), locations: (.locations | length)},
  checksum: $checksum,
  validation: {valid: true, warnings: []},
  sanitised: true,
  generationParameters: {
    method: "deterministic-jq-transform",
    liveProvidersCalled: false,
    containsCapturedProviderData: false
  },
  warnings: [],
  parentDatasetVersion: null
}' "$source_file" > "$temporary_dir/manifest.json"

jq -S '{
  startedAt: .dataset.createdAt,
  finishedAt: .dataset.createdAt,
  durationMillis: 0,
  providersCalled: [],
  providerStatuses: {"synthetic-generator": "SUCCESS"},
  rawResultCounts: {"synthetic-generator": (.jobs | length)},
  normalisedResultCount: (.jobs | length),
  invalidRecordsRemoved: 0,
  rejectedRecords: 0,
  invalidSalariesDetected: 0,
  paidTrainingRecordsDetected: 0,
  duplicatesRemoved: 0,
  finalJobCount: (.jobs | length),
  locationRecordsGenerated: (.locations | length),
  postcodeLookupsAttempted: 0,
  postcodeLookupsSucceeded: 0,
  warnings: []
}' "$source_file" > "$temporary_dir/generation-report.json"

source_checksum=$(sha256sum "$source_file" | cut -d ' ' -f 1)
jobs_checksum=$(sha256sum "$temporary_dir/jobs.json" | cut -d ' ' -f 1)
locations_checksum=$(sha256sum "$temporary_dir/locations.json" | cut -d ' ' -f 1)
llm_checksum=$(sha256sum "$temporary_dir/llm-fixtures.json" | cut -d ' ' -f 1)
scenario_checksum=$(
    cd "$scenario_dir"
    sha256sum activities.json applications.json documents.json payment-ledger.json scenario.json user.json \
        | sha256sum | cut -d ' ' -f 1
)

jq -n -S \
    --arg datasetId "$(jq -r '.dataset.id' "$source_file")" \
    --arg version "$(jq -r '.dataset.version' "$source_file")" \
    --arg createdAt "$(jq -r '.dataset.createdAt' "$source_file")" \
    --arg reviewBy "$(jq -r '.dataset.reviewBy' "$source_file")" \
    --arg expiresAt "$(jq -r '.dataset.expiresAt' "$source_file")" \
    --arg sourceChecksum "$source_checksum" \
    --arg jobsChecksum "$jobs_checksum" \
    --arg locationsChecksum "$locations_checksum" \
    --arg llmChecksum "$llm_checksum" \
    --arg scenarioChecksum "$scenario_checksum" \
    '{
      schemaVersion: "1.0",
      datasetId: $datasetId,
      datasetVersion: $version,
      classification: "FULLY_SYNTHETIC",
      purpose: ["LOCAL_DEVELOPMENT", "AUTOMATED_TESTING", "DEMO"],
      creation: {
        method: "DETERMINISTIC_LOCAL_GENERATOR",
        generator: "scripts/generate-synthetic-fixtures.sh",
        sourceSpecification: "fixtures/source/uk-software-developer-demo-v1.json",
        sourceSpecificationSha256: $sourceChecksum,
        createdAt: $createdAt,
        liveProvidersCalled: false
      },
      dataPolicy: {
        containsRealPersonalData: false,
        containsCapturedProviderData: false,
        containsProviderCredentials: false,
        externalUrlsAllowed: ["https://jobs.example.test/"],
        minimisation: "Only fields required by fixture consumers are populated."
      },
      licence: {
        identifier: "LicenseRef-JobSeekerCopilot-Proprietary",
        owner: "jobseekercopilot",
        redistribution: "PROHIBITED",
        externalDatasets: []
      },
      attribution: [],
      approval: {
        status: "APPROVED_FOR_NON_PRODUCTION_TESTING",
        approvedBy: "jobseekercopilot repository owner",
        approvedAt: "2026-07-22",
        scope: "Private beta test, local demo and CI fixtures only"
      },
      lifecycle: {
        reviewBy: $reviewBy,
        expiresAt: $expiresAt,
        refreshTrigger: "Schema, consumer, policy or scenario change",
        immutableVersion: true
      },
      scenarioBundle: {
        path: "src/main/resources/scenarios/demo-ready-v1",
        files: ["activities.json", "applications.json", "documents.json", "payment-ledger.json", "scenario.json", "user.json"],
        sha256: $scenarioChecksum
      },
      payloadChecksums: {
        "jobs.json": $jobsChecksum,
        "locations.json": $locationsChecksum,
        "llm-fixtures.json": $llmChecksum
      }
    }' > "$temporary_dir/provenance.json"

(
    cd "$temporary_dir"
    sha256sum generation-report.json jobs.json llm-fixtures.json locations.json manifest.json provenance.json \
        > SHA256SUMS
)

mkdir -p "$output_dir"
for file in generation-report.json jobs.json llm-fixtures.json locations.json manifest.json provenance.json SHA256SUMS; do
    cp "$temporary_dir/$file" "$output_dir/$file"
done

echo "synthetic fixture generation: wrote deterministic $output_dir"
