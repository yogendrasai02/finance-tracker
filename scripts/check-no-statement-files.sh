#!/usr/bin/env bash
set -euo pipefail

# Fails if the repository holds anything that could be a real bank statement (SECURITY.md SR-47).
# Test spreadsheets are generated in test code and never committed (D-50), so no spreadsheet or PDF belongs in the repo at all.
# That makes an extension check exact: it has no allowed exceptions to get wrong.
#
# Default mode checks every tracked file; CI runs it this way.
# --staged checks only what the next commit adds or changes; the pre-commit hook runs it this way.

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

if [[ "${1:-}" == "--staged" ]]; then
  list_files=(git diff --cached --name-only --diff-filter=ACMR -z)
else
  list_files=(git ls-files -z)
fi

# statements/ is anchored to the root, so frontend/src/features/statements/ is not caught.
pattern='(^statements/|\.(xlsx|xlsm|xlsb|xls|csv|pdf)$)'

found=0
while IFS= read -r -d '' path; do
  if [[ "$(printf '%s' "$path" | tr '[:upper:]' '[:lower:]')" =~ $pattern ]]; then
    echo "check-no-statement-files: blocked $path" >&2
    found=1
  fi
done < <("${list_files[@]}")

if [[ "$found" -ne 0 ]]; then
  echo "Spreadsheets, CSV and PDF files, and anything under statements/, must never be committed (SECURITY.md SR-47)." >&2
  echo "Build test files in test code instead (D-50)." >&2
  exit 1
fi
