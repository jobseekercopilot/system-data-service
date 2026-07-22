#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
policy="$script_dir/verify-dependency-report.sh"
fixtures="$repository_dir/src/test/resources/security"
temporary_dir=$(mktemp -d)

cleanup() {
    rm -rf "$temporary_dir"
}
trap cleanup EXIT INT TERM

expect_failure() {
    name=$1
    shift
    if "$@" >/dev/null 2>&1; then
        echo "dependency policy test unexpectedly passed: $name" >&2
        exit 1
    fi
}

"$policy" "$fixtures/clean-trivy-report.json" "$fixtures/empty.trivyignore" >/dev/null
expect_failure critical-finding "$policy" "$fixtures/critical-trivy-report.json" "$fixtures/empty.trivyignore"
expect_failure malformed-report "$policy" "$fixtures/malformed-trivy-report.json" "$fixtures/empty.trivyignore"
expect_failure no-java-coverage "$policy" "$fixtures/no-packages-trivy-report.json" "$fixtures/empty.trivyignore"
expect_failure untracked-exception "$policy" "$fixtures/clean-trivy-report.json" "$fixtures/untracked.trivyignore"
expect_failure expired-exception "$policy" "$fixtures/clean-trivy-report.json" "$fixtures/expired.trivyignore"

future_date=$(date -u -d '+29 days' +%F)
{
    echo '# Tracking: https://github.com/jobseekercopilot/system-data-service/issues/1'
    echo "CVE-2099-12345 exp:$future_date"
} > "$temporary_dir/valid.trivyignore"
"$policy" "$fixtures/clean-trivy-report.json" "$temporary_dir/valid.trivyignore" >/dev/null

echo "dependency policy tests: passed"
