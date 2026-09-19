#!/usr/bin/env bash
set -euo pipefail

# Fails when an OSV-Scanner JSON report holds any vulnerability scored CVSS 9.0 or higher (SECURITY.md SR-19).
# OSV-Scanner has no severity threshold of its own: it fails on every finding, so this script is the threshold.
#
# The report must come from `osv-scanner scan source --format json --all-packages`.
# --all-packages is what makes the scanned-package list below complete, so a gap in coverage shows up in the job output.
#
# Usage: scripts/check-critical-cves.sh <osv-results.json>

report="${1:?usage: check-critical-cves.sh <osv-results.json>}"
threshold=9.0

echo "Scanned packages per source:"
jq -r '.results[] | "  \(.source.path): \(.packages | length)"' "$report"

echo "Maven packages scanned, including transitive ones:"
jq -r '[.results[].packages[].package | select(.ecosystem == "Maven") | "  \(.name) \(.version)"] | unique | .[]' "$report"

echo "Findings (ecosystem, package, version, ids, max CVSS; blank means unscored):"
jq -r '.results[].packages[] | . as $p | .groups[]? | "  \($p.package.ecosystem)\t\($p.package.name)\t\($p.package.version)\t\(.ids | join(","))\t\(.max_severity // "")"' "$report"

critical=$(jq --argjson threshold "$threshold" \
  '[.results[].packages[].groups[]? | select((.max_severity // "") != "" and (.max_severity | tonumber) >= $threshold)] | length' \
  "$report")

if [[ "$critical" -gt 0 ]]; then
  echo "check-critical-cves: $critical finding(s) at CVSS $threshold or higher." >&2
  exit 1
fi
echo "check-critical-cves: no finding at CVSS $threshold or higher."
