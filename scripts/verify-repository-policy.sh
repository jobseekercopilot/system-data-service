#!/usr/bin/env sh
set -eu

temporary_dir=$(mktemp -d)
cleanup() { rm -rf "$temporary_dir"; }
trap cleanup EXIT INT TERM

candidate_file=${1:-$temporary_dir/candidates}
if [ "$#" -eq 0 ]; then
    git ls-files --cached --others --exclude-standard > "$candidate_file"
fi

failures=0
while IFS= read -r file || test -n "$file"; do
    case "$file" in
        target/*|*/target/*|libs/*|*/libs/*|dataset-repository/*|*/dataset-repository/*|generated-datasets/*|*/generated-datasets/*|backups/*|*/backups/*|logs/*|*/logs/*|reports/*|*/reports/*|*.jar|*.class|*.db|*.sqlite|*.sqlite3|*.log|*.zip|*.tar|*.tar.gz)
            echo "repository policy: forbidden generated/runtime/captured file: $file" >&2
            failures=$((failures + 1))
            ;;
        .env|*/.env|.env.*|*/.env.*)
            case "$file" in *.env.example|.env.example) ;; *) echo "repository policy: forbidden environment file: $file" >&2; failures=$((failures + 1));; esac
            ;;
    esac
done < "$candidate_file"

test "$failures" -eq 0 || exit 1
count=$(wc -l < "$candidate_file" | tr -d ' ')
echo "repository policy: $count candidate files checked; no forbidden artifact"
