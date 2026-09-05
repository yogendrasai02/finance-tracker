#!/usr/bin/env bash

# SessionStart hook: prints repo state that changes too often to keep in a checked-in file.
# plans/STATUS.md carries the stable "which step are we on" context; this fills in the live git state.
# Always exits 0 so a failure here never blocks a session.

set -u

cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0

echo "## Live repo state"
echo
echo "Branch: $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
echo
echo "Recent commits:"
git log --oneline -5 2>/dev/null

echo
echo "Uncommitted changes:"
changes=$(git status --porcelain 2>/dev/null | head -20)
if [ -n "$changes" ]; then
    echo "$changes"
else
    echo "(none)"
fi

echo
echo "Plan files:"
ls plans 2>/dev/null | sed 's|^|- plans/|'

exit 0
