#!/usr/bin/env sh
set -eu

report=${1:-target/trivy-dependencies.json}
ignore_file=${2:-config/trivy/.trivyignore}

fail() {
    echo "dependency policy: $1" >&2
    exit 1
}

command -v jq >/dev/null 2>&1 || fail "jq is required"
test -s "$report" || fail "report is missing or empty: $report"
test -f "$ignore_file" || fail "ignore file is missing: $ignore_file"

jq -e '
    .SchemaVersion == 2 and
    (.ArtifactName | type == "string") and
    (.Results | type == "array")
' "$report" >/dev/null 2>&1 || fail "report is not valid Trivy JSON schema version 2"

tracking=""
today_epoch=$(date -u +%s)
maximum_epoch=$((today_epoch + 30 * 24 * 60 * 60))

while IFS= read -r raw || test -n "$raw"; do
    line=$(printf '%s' "$raw" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    case "$line" in
        "") tracking="" ;;
        "# Tracking: "*)
            tracking=${line#\# Tracking: }
            printf '%s' "$tracking" | grep -Eq '^https://github\.com/jobseekercopilot/system-data-service/issues/[1-9][0-9]*$' \
                || fail "risk exception has an invalid private issue URL"
            ;;
        \#*) ;;
        *)
            printf '%s' "$line" | grep -Eq '^[A-Za-z0-9_.:-]+ exp:[0-9]{4}-[0-9]{2}-[0-9]{2}$' \
                || fail "ignore entries require one finding ID and exp:YYYY-MM-DD"
            test -n "$tracking" || fail "ignore entry is not immediately linked to a tracking issue"
            expiry=${line##* exp:}
            expiry_epoch=$(date -u -d "$expiry" +%s 2>/dev/null) \
                || fail "ignore entry has an invalid expiry date"
            test "$expiry_epoch" -ge "$today_epoch" || fail "ignore entry has expired"
            test "$expiry_epoch" -le "$maximum_epoch" || fail "ignore entry exceeds the 30-day acceptance window"
            tracking=""
            ;;
    esac
done < "$ignore_file"

high_count=$(jq '[.Results[]?.Vulnerabilities[]? | select(.Severity == "HIGH" or .Severity == "CRITICAL")] | length' "$report") \
    || fail "could not evaluate report vulnerabilities"
java_package_count=$(jq '[.Results[]? | select(.Type == "jar") | .Packages[]?] | length' "$report") \
    || fail "could not evaluate report package coverage"
test "$java_package_count" -gt 0 || fail "report contains no Java dependency packages"
test "$high_count" -eq 0 || fail "$high_count unaccepted Critical/High vulnerability finding(s)"

echo "dependency policy: report valid; no unaccepted Critical/High findings"
