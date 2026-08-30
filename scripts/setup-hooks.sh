#!/usr/bin/env bash
set -euo pipefail

# .git/hooks/ isn't tracked by git, so hooks live in scripts/hooks/ and get symlinked in here.
repo_root="$(git rev-parse --show-toplevel)"

for hook in "$repo_root"/scripts/hooks/*; do
  name="$(basename "$hook")"
  target="$repo_root/.git/hooks/$name"
  ln -sf "$hook" "$target"
  chmod +x "$hook"
  echo "Installed $name -> .git/hooks/$name"
done
