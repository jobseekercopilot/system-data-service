#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
temporary_dir=$(mktemp -d)
trap 'rm -rf "$temporary_dir"' EXIT INT TERM

git -C "$temporary_dir" init --quiet
git -C "$temporary_dir" config user.name "Secret Scan Test"
git -C "$temporary_dir" config user.email "secret-scan@example.com"
printf '%s\n' 'clean history fixture' > "$temporary_dir/README.md"
git -C "$temporary_dir" add README.md
git -C "$temporary_dir" commit --quiet -m clean

"$script_dir/verify-secret-history.sh" zricethezav/gitleaks:v8.30.1 "$temporary_dir" >/dev/null

# Assemble the synthetic credential only in the disposable repository so this
# policy test never places a secret-shaped value in the real Git history.
printf '%s%s\n' 'AKIA' 'ABCDEFGHIJKLMNOP' > "$temporary_dir/synthetic-leak.txt"
git -C "$temporary_dir" add synthetic-leak.txt
git -C "$temporary_dir" commit --quiet -m synthetic-leak

if "$script_dir/verify-secret-history.sh" zricethezav/gitleaks:v8.30.1 "$temporary_dir" \
        >"$temporary_dir/rejected.log" 2>&1; then
    echo "secret history test: controlled synthetic leak was accepted" >&2
    exit 1
fi
if grep -F 'AKIA' "$temporary_dir/rejected.log" >/dev/null; then
    echo "secret history test: redacted output exposed the synthetic credential" >&2
    exit 1
fi

echo "secret history scan tests: passed"
