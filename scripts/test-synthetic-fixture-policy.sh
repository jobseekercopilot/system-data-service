#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
source_file="$repository_root/fixtures/source/uk-software-developer-demo-v1.json"
fixture_dir="$repository_root/fixtures/datasets/uk-software-developer-demo/1.0.0"
temporary_dir=$(mktemp -d)
cleanup() { rm -rf "$temporary_dir"; }
trap cleanup EXIT INT TERM

"$script_dir/verify-synthetic-fixtures.sh" "$fixture_dir" >/dev/null

"$script_dir/generate-synthetic-fixtures.sh" "$source_file" "$temporary_dir/first" >/dev/null
"$script_dir/generate-synthetic-fixtures.sh" "$source_file" "$temporary_dir/second" >/dev/null
diff -ru "$temporary_dir/first" "$temporary_dir/second" >/dev/null || {
    echo "synthetic fixture policy: repeated generation was not byte-stable" >&2
    exit 1
}
diff -ru "$fixture_dir" "$temporary_dir/first" >/dev/null || {
    echo "synthetic fixture policy: committed output differs from deterministic generation" >&2
    exit 1
}

expect_rejected() {
    name=$1
    directory=$2
    expected=$3
    if "$script_dir/verify-synthetic-fixtures.sh" "$directory" >"$temporary_dir/$name.out" 2>&1; then
        echo "synthetic fixture negative test unexpectedly accepted: $name" >&2
        exit 1
    fi
    grep -F "$expected" "$temporary_dir/$name.out" >/dev/null || {
        echo "synthetic fixture negative test failed for the wrong reason: $name" >&2
        exit 1
    }
}

cp -R "$fixture_dir" "$temporary_dir/captured"
jq '.classification = "LIVE_CAPTURED_FIXTURE"' "$temporary_dir/captured/provenance.json" \
    > "$temporary_dir/captured/changed.json"
mv "$temporary_dir/captured/changed.json" "$temporary_dir/captured/provenance.json"
expect_rejected captured "$temporary_dir/captured" "provenance schema"

cp -R "$fixture_dir" "$temporary_dir/pii"
jq '.jobs[0].description += " Contact jane.doe@real-company.co.uk."' "$temporary_dir/pii/jobs.json" \
    > "$temporary_dir/pii/changed.json"
mv "$temporary_dir/pii/changed.json" "$temporary_dir/pii/jobs.json"
expect_rejected pii "$temporary_dir/pii" "non-reserved email"

cp -R "$fixture_dir" "$temporary_dir/secret"
jq '.jobs[0].description += " sk-abcdefghijklmnopqrstuvwxyz123456"' "$temporary_dir/secret/jobs.json" \
    > "$temporary_dir/secret/changed.json"
mv "$temporary_dir/secret/changed.json" "$temporary_dir/secret/jobs.json"
expect_rejected secret "$temporary_dir/secret" "credential, secret"

cp -R "$fixture_dir" "$temporary_dir/url"
jq '.jobs[0].sourceUrl = "https://jobs.production.example/vacancy/1"' "$temporary_dir/url/jobs.json" \
    > "$temporary_dir/url/changed.json"
mv "$temporary_dir/url/changed.json" "$temporary_dir/url/jobs.json"
expect_rejected url "$temporary_dir/url" "job dataset schema"

cp -R "$fixture_dir" "$temporary_dir/licence"
jq 'del(.licence)' "$temporary_dir/licence/provenance.json" > "$temporary_dir/licence/changed.json"
mv "$temporary_dir/licence/changed.json" "$temporary_dir/licence/provenance.json"
expect_rejected licence "$temporary_dir/licence" "provenance schema"

cp -R "$fixture_dir" "$temporary_dir/checksum"
printf '\n' >> "$temporary_dir/checksum/jobs.json"
expect_rejected checksum "$temporary_dir/checksum" "provenance checksum mismatch"

echo "synthetic fixture policy tests: passed"
