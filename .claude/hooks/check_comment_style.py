#!/usr/bin/env python3
"""PostToolUse hook: enforce this repo's comment-formatting rules on Write/Edit.

Rule 1: a sentence in a // or -- line comment, or in a Javadoc block, must not be wrapped
across two physical lines.
Rule 2: a comment must not reference internal planning artifacts such as "step 3",
"sub-step 3b", or "plan 3e".

Both rules come from the project's code-comment-formatting memory. This is a heuristic,
not a parser, and only looks at the file just written or edited.
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


def main():
    payload = json.load(sys.stdin)
    file_path = payload.get("tool_input", {}).get("file_path")
    if not file_path:
        return 0

    ext = None
    for candidate in CHECKED_EXTENSIONS:
        if file_path.endswith(candidate):
            ext = candidate
            break
    if ext is None:
        return 0

    try:
        with open(file_path, "r", encoding="utf-8") as handle:
            lines = handle.read().splitlines()
    except OSError:
        return 0

    wrapped = find_wrapped_sentences(lines)
    planning = find_planning_references(lines)

    if not wrapped and not planning:
        return 0

    print(f"Comment style check failed for {file_path}", file=sys.stderr)
    print(file=sys.stderr)

    if wrapped:
        print("Rule: one sentence per physical comment line, never wrapped across two lines.", file=sys.stderr)
        for line1, line2, text1, text2 in wrapped:
            print(f"  line {line1}-{line2}: \"{text1}\" continues into \"{text2}\"", file=sys.stderr)
        print(file=sys.stderr)

    if planning:
        print("Rule: comments must not reference internal planning artifacts (step/sub-step/plan numbers).", file=sys.stderr)
        for line_no, matched, text in planning:
            print(f"  line {line_no}: \"{matched}\" in \"{text}\"", file=sys.stderr)
        print(file=sys.stderr)

    print("Fix the comment(s) above before finishing this turn.", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
