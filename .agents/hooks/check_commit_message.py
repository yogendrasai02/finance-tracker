#!/usr/bin/env python3
"""PreToolUse hook: enforce repository git commit message conventions (AGENTS.md sec 9).

Supports both Antigravity and Claude Code tool execution protocols (WORA).

Conventions enforced:
Rule 1: single-line subject only, no multi-line message or body.
Rule 2: the entire message is 100% lowercase, including the first word.
Rule 3: no conventional-commit prefix (feat:, fix:, chore:, etc).
Rule 4: no emoji.
Rule 5: no trailing period.
Rule 6: no reference to internal planning artifacts (e.g. "step 3", "sub-step 3b", "plan 3e").
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


def extract_message(command: str):
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


def validate(message: str):
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
            violations.append(f'No prefixes: remove the conventional-commit tag ("{prefix}:").')
            break

    if EMOJI_RE.search(subject):
        violations.append("No emojis: remove the emoji from the message.")

    if subject.endswith("."):
        violations.append('No trailing period: remove the trailing "."')

    for pattern in PLANNING_PATTERNS:
        match = pattern.search(subject)
        if match:
            violations.append(
                f'No task references: remove "{match.group(0)}" and describe the change, not the plan step.'
            )
            break

    return violations


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0

    is_antigravity = "toolCall" in payload

    command = ""
    if is_antigravity:
        tool_call = payload.get("toolCall", {})
        tool_name = tool_call.get("name", "")
        if tool_name not in ("run_command", "bash", "Bash"):
            print(json.dumps({"decision": "allow"}))
            return 0
        args = tool_call.get("args", {})
        command = args.get("CommandLine", "") or args.get("command", "")
    else:
        tool_name = payload.get("tool_name", "")
        if tool_name not in ("Bash", "bash", "run_command"):
            return 0
        command = payload.get("tool_input", {}).get("command", "")

    if not command:
        if is_antigravity:
            print(json.dumps({"decision": "allow"}))
        return 0

    message = extract_message(command)
    if message is None:
        if is_antigravity:
            print(json.dumps({"decision": "allow"}))
        return 0

    violations = validate(message)
    if not violations:
        if is_antigravity:
            print(json.dumps({"decision": "allow"}))
        return 0

    report_lines = [
        "Commit message check failed (AGENTS.md sec 9).",
        f'  message: "{message.strip()}"',
        "",
    ]
    for violation in violations:
        report_lines.append(f"  - {violation}")
    report_lines.append("")
    report_lines.append("Fix the -m message and retry the commit.")
    report_text = "\n".join(report_lines)

    if is_antigravity:
        print(json.dumps({"decision": "deny", "reason": report_text}))
        return 0
    else:
        print(report_text, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
