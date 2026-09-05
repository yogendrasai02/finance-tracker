#!/usr/bin/env python3
"""Hook: enforce repository comment-formatting rules (AGENTS.md sec 6 and 7).

Supports both Antigravity (PreToolUse on write_to_file/replace_file_content)
and Claude Code (PostToolUse on Write/Edit) protocols (WORA).

Rule 1: a sentence in a // or -- or # line comment, or in a Javadoc block,
        must not be wrapped across two physical lines.
Rule 2: a comment must not reference internal planning artifacts such as
        "step 3", "sub-step 3b", or "plan 3e".
"""

import json
import re
import sys

CHECKED_EXTENSIONS = {".java", ".sql", ".sh"}

LINE_COMMENT_RE = re.compile(r"^(\s*)(//|--|#)\s?(.*)$")
JAVADOC_RE = re.compile(r"^(\s*)\*(?!/)\s?(.*)$")

SENTENCE_END_CHARS = set(".:;)")

PLANNING_PATTERNS = [
    re.compile(r"\bstep\s+\d+[a-z]?\b", re.IGNORECASE),
    re.compile(r"\bsub-step\b", re.IGNORECASE),
    re.compile(r"\bplan\s+\d+[a-z]?\b", re.IGNORECASE),
]


def comment_text(line):
    match = LINE_COMMENT_RE.match(line)
    if match:
        return "line", match.group(2), match.group(3)
    match = JAVADOC_RE.match(line)
    if match:
        return "javadoc", "*", match.group(2)
    return None, None, None


def find_wrapped_sentences(lines):
    violations = []
    for i in range(len(lines) - 1):
        kind1, marker1, text1 = comment_text(lines[i])
        kind2, marker2, text2 = comment_text(lines[i + 1])
        if kind1 is None or kind2 is None or kind1 != kind2 or marker1 != marker2:
            continue
        text1 = text1.strip()
        text2 = text2.strip()
        if not text1 or not text2:
            continue
        if text1[-1] in SENTENCE_END_CHARS:
            continue
        if not text2[0].isalpha() or not text2[0].islower():
            continue
        violations.append((i + 1, i + 2, text1, text2))
    return violations


def find_planning_references(lines):
    violations = []
    for i, line in enumerate(lines):
        kind, _marker, text = comment_text(line)
        if kind is None:
            continue
        for pattern in PLANNING_PATTERNS:
            match = pattern.search(text)
            if match:
                violations.append((i + 1, match.group(0), text.strip()))
    return violations


def is_checked_file(file_path):
    if not file_path:
        return False
    return any(file_path.endswith(ext) for ext in CHECKED_EXTENSIONS)


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0

    is_antigravity = "toolCall" in payload

    lines = []
    file_path = None

    if is_antigravity:
        tool_call = payload.get("toolCall", {})
        tool_name = tool_call.get("name", "")
        args = tool_call.get("args", {})
        file_path = args.get("TargetFile") or args.get("file_path") or ""

        if not is_checked_file(file_path):
            print(json.dumps({"decision": "allow"}))
            return 0

        # In PreToolUse: inspect content to be written
        if "CodeContent" in args:
            lines = args["CodeContent"].splitlines()
        elif "ReplacementContent" in args:
            lines = args["ReplacementContent"].splitlines()
        elif file_path:
            try:
                with open(file_path, "r", encoding="utf-8") as handle:
                    lines = handle.read().splitlines()
            except OSError:
                print(json.dumps({"decision": "allow"}))
                return 0
        else:
            print(json.dumps({"decision": "allow"}))
            return 0
    else:
        # Claude Code format
        file_path = payload.get("tool_input", {}).get("file_path") or ""
        if not is_checked_file(file_path):
            return 0
        try:
            with open(file_path, "r", encoding="utf-8") as handle:
                lines = handle.read().splitlines()
        except OSError:
            return 0

    wrapped = find_wrapped_sentences(lines)
    planning = find_planning_references(lines)

    if not wrapped and not planning:
        if is_antigravity:
            print(json.dumps({"decision": "allow"}))
        return 0

    report_lines = [f"Comment style check failed for {file_path or 'input content'}:", ""]
    if wrapped:
        report_lines.append("Rule: one sentence per physical comment line, never wrapped across two lines.")
        for line1, line2, text1, text2 in wrapped:
            report_lines.append(f'  line {line1}-{line2}: "{text1}" continues into "{text2}"')
        report_lines.append("")

    if planning:
        report_lines.append("Rule: comments must not reference internal planning artifacts (step/sub-step/plan numbers).")
        for line_no, matched, text in planning:
            report_lines.append(f'  line {line_no}: "{matched}" in "{text}"')
        report_lines.append("")

    report_lines.append("Fix the comment(s) above before finishing this turn.")
    report_text = "\n".join(report_lines)

    if is_antigravity:
        print(json.dumps({"decision": "deny", "reason": report_text}))
        return 0
    else:
        print(report_text, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
