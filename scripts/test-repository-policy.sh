#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
temporary_dir=$(mktemp -d)
cleanup() { rm -rf "$temporary_dir"; }
trap cleanup EXIT INT TERM

printf '%s\n' 'README.md' '.env.example' > "$temporary_dir/clean"
"$script_dir/verify-repository-policy.sh" "$temporary_dir/clean" >/dev/null

for forbidden in 'libs/client.jar' 'dataset-repository/live/jobs.json' 'backups/data.json' '.env' 'target/app.class' 'runtime.db'; do
    printf '%s\n' "$forbidden" > "$temporary_dir/forbidden"
    if "$script_dir/verify-repository-policy.sh" "$temporary_dir/forbidden" >/dev/null 2>&1; then
        echo "repository policy negative test unexpectedly accepted: $forbidden" >&2
        exit 1
    fi
done

echo "repository policy tests: passed"
