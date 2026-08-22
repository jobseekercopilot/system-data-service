#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
fixture_root="$repository_root/fixtures/datasets/uk-software-developer-demo"
fixture_dir=${1:-$fixture_root/1.2.0}
scenario_dir="$repository_root/src/main/resources/scenarios/demo-ready-v1"
scenario_catalog="$repository_root/src/main/resources/scenarios/named-states.json"
source_specification="$repository_root/fixtures/source/uk-software-developer-demo-v1.2.json"

fail() {
    echo "synthetic fixture policy: $1" >&2
    exit 1
}

for command in jq sha256sum; do
    command -v "$command" >/dev/null 2>&1 || fail "required command '$command' is unavailable"
done

for file in generation-report.json jobs.json llm-fixtures.json locations.json manifest.json provenance.json SHA256SUMS; do
    test -f "$fixture_dir/$file" || fail "required file is missing: $file"
done

if [ "$#" -eq 0 ]; then
    while IFS= read -r candidate || test -n "$candidate"; do
        case "$candidate" in
            "$fixture_root/1.0.0"/*|"$fixture_root/1.1.0"/*|"$fixture_root/1.2.0"/*) ;;
            *) fail "ungoverned dataset file detected: $candidate" ;;
        esac
    done <<EOF
$(find "$repository_root/fixtures/datasets" -type f | sort)
EOF
    source_count=$(find "$repository_root/fixtures/source" -type f | wc -l | tr -d ' ')
    test "$source_count" = "3" || fail "unexpected fixture source specification detected"
fi

jq -e '
  .scenarioId == "demo-ready-v1"
  and .scenario == "DEMO_READY"
  and .datasetId == "uk-software-developer-demo"
  and .datasetVersion == "1.2.0"
  and .provenance == "fixtures/datasets/uk-software-developer-demo/1.2.0/provenance.json"
' "$scenario_dir/scenario.json" >/dev/null || fail "demo scenario does not reference the governed dataset"
jq -e '
  .syntheticIdentity == true
  and .credentialClassification == "PUBLIC_TEST_CREDENTIAL_NON_PRODUCTION"
  and (.email | endswith("@example.com"))
' "$scenario_dir/user.json" >/dev/null || fail "demo identity is not explicitly synthetic and reserved"
jq -e '
  length == 10
  and ([.[].scenario] | unique | length == 10)
  and ([.[].scenarioId] | unique | length == 10)
  and all(.[];
    (.scenarioId | test("^[a-z0-9-]+-v[1-9][0-9]*$"))
    and (.version | test("^[1-9][0-9]*\\.[0-9]+\\.[0-9]+$"))
    and (.identities | type == "array")
    and all(.identities[]; . as $identity |
      ($identity.email | endswith("@example.com"))
      and ($identity.resetComponents | contains($identity.seedComponents))))
' "$scenario_catalog" >/dev/null || fail "named-state catalog schema or isolation policy is invalid"

jq -e '
  .datasetId == "uk-software-developer-demo"
  and .version == "1.2.0"
  and .schemaVersion == "1.0"
  and .status == "APPROVED_SYNTHETIC"
  and .sanitised == true
  and .validation.valid == true
  and .generationParameters.liveProvidersCalled == false
  and .generationParameters.containsCapturedProviderData == false
  and .recordCounts.jobs == 16
  and .recordCounts.locations == 6
  and (.sources | length == 1)
  and .sources[0].status == "SYNTHETIC"
' "$fixture_dir/manifest.json" >/dev/null || fail "manifest schema or policy is invalid"

jq -e '
  .schemaVersion == "1.0"
  and (.jobs | length == 16)
  and ([.jobs[].id] | unique | length == 16)
  and ([.jobs[].externalReference] | unique | length == 16)
  and ([.jobs[] | select(.suitableForDemo == true)] | length == 15)
  and ([.jobs[] | select(.suitableForDemo == false)] | length == 1)
  and ([.jobs[] | select(.latitude != null and .longitude != null)] | length >= 1)
  and all(.jobs[];
    (.id | test("^[0-9a-f-]{36}$"))
    and (.externalReference | startswith("SYNTH-JOB-"))
    and (.sourceProvider | test("^(adzuna|jsearch|reed)-gateway-fixture$"))
    and (.title | length > 0)
    and (.companyName | length > 0)
    and (.description | length > 140)
    and (.description | ascii_downcase | test("synthetic|fictional"))
    and (.salaryMinimum >= 20000)
    and (.salaryMaximum >= .salaryMinimum)
    and ((.latitude == null and .longitude == null)
      or ((.latitude >= -90 and .latitude <= 90)
        and (.longitude >= -180 and .longitude <= 180)))
    and (.sourceUrl | test("^https://jobs\\.example\\.test/synthetic/SYNTH-JOB-[0-9]{3}$"))
    and .sourceMetadata.fixture == true
    and .sourceMetadata.classification == "FULLY_SYNTHETIC"
    and (.suitableForDemo | type == "boolean"))
' "$fixture_dir/jobs.json" >/dev/null || fail "job dataset schema or synthetic-content policy is invalid"

jq -e '
  .schemaVersion == "1.0"
  and (.locations | length == 6)
  and ([.locations[].id] | unique | length == 6)
  and all(.locations[];
    (.placeName | length > 0)
    and .postcode == null
    and .latitude == null
    and .longitude == null
    and .sourceProvider == "synthetic-generator")
' "$fixture_dir/locations.json" >/dev/null || fail "location dataset schema or minimisation policy is invalid"

jq -e '
  .schemaVersion == "1.0"
  and .datasetId == "uk-software-developer-demo"
  and .datasetVersion == "1.2.0"
  and .classification == "FULLY_SYNTHETIC"
  and .creation.method == "DETERMINISTIC_LOCAL_GENERATOR"
  and .creation.liveProvidersCalled == false
  and .creation.parentSourceSpecification == "fixtures/source/uk-software-developer-demo-v1.1.json"
  and (.creation.parentSourceSpecificationSha256 | test("^[0-9a-f]{64}$"))
  and .dataPolicy.containsRealPersonalData == false
  and .dataPolicy.containsCapturedProviderData == false
  and .dataPolicy.containsProviderCredentials == false
  and .licence.identifier == "LicenseRef-JobSeekerCopilot-Proprietary"
  and .licence.redistribution == "PROHIBITED"
  and (.licence.externalDatasets | length == 0)
  and (.attribution | length == 0)
  and .approval.status == "APPROVED_FOR_NON_PRODUCTION_TESTING"
  and (.approval.approvedBy | length > 0)
  and (.approval.approvedAt | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))
  and (.lifecycle.reviewBy | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))
  and (.lifecycle.expiresAt | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))
  and .lifecycle.immutableVersion == true
  and .scenarioBundle.path == "src/main/resources/scenarios/demo-ready-v1"
  and (.scenarioBundle.files | length == 6)
  and (.scenarioBundle.sha256 | test("^[0-9a-f]{64}$"))
' "$fixture_dir/provenance.json" >/dev/null || fail "provenance schema, licence, approval, or lifecycle policy is invalid"

scenario_checksum=$(
    cd "$scenario_dir"
    sha256sum activities.json applications.json documents.json payment-ledger.json scenario.json user.json \
        | sha256sum | cut -d ' ' -f 1
)
test "$scenario_checksum" = "$(jq -r '.scenarioBundle.sha256' "$fixture_dir/provenance.json")" \
    || fail "scenario bundle checksum mismatch"

if jq -r '.. | strings' "$fixture_dir"/*.json "$scenario_dir"/*.json "$scenario_catalog" "$source_specification" \
        | grep -Eiq '(sk-[a-z0-9]{16,}|gh[pousr]_[a-z0-9]{12,}|AKIA[0-9A-Z]{12,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|LIVE_CAPTURED_FIXTURE)'; then
    fail "credential, secret, or captured-provider marker detected"
fi

email_file=$(mktemp)
url_file=$(mktemp)
cleanup() { rm -f "$email_file" "$url_file"; }
trap cleanup EXIT INT TERM
jq -r '.. | strings | select(test("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"))' \
    "$fixture_dir"/*.json "$scenario_dir"/*.json "$scenario_catalog" "$source_specification" > "$email_file"
while IFS= read -r email || test -n "$email"; do
    printf '%s' "$email" | grep -Eiq '^[A-Za-z0-9._%+-]+@example\.(com|test)$' \
        || fail "non-reserved email address detected"
done < "$email_file"

jq -r '.. | strings | select(test("^https?://"))' \
    "$fixture_dir"/*.json "$scenario_dir"/*.json "$scenario_catalog" "$source_specification" > "$url_file"
while IFS= read -r url || test -n "$url"; do
    printf '%s' "$url" | grep -Eq '^https://[A-Za-z0-9.-]+\.example\.test(/|$)' \
        || fail "unsafe external URL detected"
done < "$url_file"

for payload in jobs.json locations.json llm-fixtures.json; do
    expected=$(jq -r --arg payload "$payload" '.payloadChecksums[$payload]' "$fixture_dir/provenance.json")
    actual=$(sha256sum "$fixture_dir/$payload" | cut -d ' ' -f 1)
    test "$expected" = "$actual" || fail "provenance checksum mismatch: $payload"
done

source_path=$(jq -r '.creation.sourceSpecification' "$fixture_dir/provenance.json")
source_checksum=$(jq -r '.creation.sourceSpecificationSha256' "$fixture_dir/provenance.json")
test -f "$repository_root/$source_path" || fail "source specification is unavailable"
test "$source_checksum" = "$(sha256sum "$repository_root/$source_path" | cut -d ' ' -f 1)" \
    || fail "source specification checksum mismatch"

(cd "$fixture_dir" && sha256sum -c SHA256SUMS >/dev/null) \
    || fail "fixture checksum manifest is invalid"

echo "synthetic fixture policy: governed dataset is valid"
