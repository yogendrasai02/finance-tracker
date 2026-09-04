#!/usr/bin/env python3
"""PreToolUse hook: enforce this repo's git commit message conventions (AGENTS.md sec 9) on Bash git commit calls.

Rule 1: single-line subject only, no multi-line message or body.
Rule 2: the entire message is lowercase.
Rule 3: no conventional-commit prefix (feat:, fix:, chore:, etc).
Rule 4: no emoji.
Rule 5: no trailing period.
Rule 6: no reference to internal planning artifacts such as "step 3", "sub-step 3b", or "plan 3e".

This is a heuristic that parses `-m "..."` and `-m '...'`, plus the `-m "$(cat <<EOF ... EOF)"`
heredoc form, out of the raw shell command string. It only looks at the Bash command about to run.
"""

import json
import re
import sys

CONVENTIONAL_PREFIXES = (
    "feat", "fix", "chore", "refactor", "docs", "ci", "test", "build", "perf", "style", "revert",
)

PLANNING_PATTERNS = [
    re.compile(r"\bstep\s+\d+[a-z]?\b", re.IGNORECASE),
    re.compile(r"\bsub-step\b", re.IGNORECASE),
    re.compile(r"\bplan\s+\d+[a-z]?\b", re.IGNORECASE),
]

EMOJI_RE = re.compile(
    "["
    "\U0001F300-\U0001FAFF"
    "\U00002600-\U000026FF"
    "\U00002700-\U000027BF"
    "\U0001F1E6-\U0001F1FF"
    "\U00002B00-\U00002BFF"
    "\U0001F900-\U0001F9FF"
    "️"
    "]+"
)

HEREDOC_RE = re.compile(
    r"-m\s+\"\$\(cat\s*<<'?(?P<delim>[A-Za-z_]+)'?\s*\n(?P<body>.*?)\n(?P=delim)\s*\)\"",
    re.DOTALL,
)
SIMPLE_M_DOUBLE_RE = re.compile(r'-m\s+"((?:[^"\\]|\\.)*)"')
SIMPLE_M_SINGLE_RE = re.compile(r"-m\s+'((?:[^'\\]|\\.)*)'")


def extract_message(command):
    if "git commit" not in command:
        return None
    match = HEREDOC_RE.search(command)
    if match:
        return match.group("body")
    match = SIMPLE_M_DOUBLE_RE.search(command)
    if match:
        return match.group(1)
    match = SIMPLE_M_SINGLE_RE.search(command)
    if match:
        return match.group(1)
    return None


def validate(message):
    violations = []
    lines = message.split("\n")
    non_empty_lines = [line for line in lines if line.strip()]

    if len(non_empty_lines) > 1:
        violations.append("Format: single-line subject only. Never write a multi-line message or body.")

    subject = non_empty_lines[0].strip() if non_empty_lines else ""

    if subject != subject.lower():
        violations.append("Casing: the entire message must be lowercase, including the first word.")

    for prefix in CONVENTIONAL_PREFIXES:
        if re.match(rf"^{prefix}(\([^)]*\))?!?:", subject, re.IGNORECASE):
            violations.append(f"No prefixes: remove the conventional-commit tag (\"{prefix}:\").")
            break

    if EMOJI_RE.search(subject):
        violations.append("No emojis: remove the emoji from the message.")

    if subject.endswith("."):
        violations.append("No trailing period: remove the trailing \".\"")

    for pattern in PLANNING_PATTERNS:
        match = pattern.search(subject)
        if match:
            violations.append(
                f"No task references: remove \"{match.group(0)}\" and describe the change, not the plan step."
            )
            break

    return violations


def main():
    payload = json.load(sys.stdin)
    if payload.get("tool_name") != "Bash":
        return 0

    command = payload.get("tool_input", {}).get("command", "")
    if not command:
        return 0

    message = extract_message(command)
    if message is None:
        return 0

    violations = validate(message)
    if not violations:
        return 0

    print("Commit message check failed (AGENTS.md sec 9).", file=sys.stderr)
    print(f'  message: "{message.strip()}"', file=sys.stderr)
    print(file=sys.stderr)
    for violation in violations:
        print(f"  - {violation}", file=sys.stderr)
    print(file=sys.stderr)
    print("Fix the -m message and retry the commit.", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
