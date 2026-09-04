#!/usr/bin/env python3
"""Validate git commit message against AGENTS.md §9 conventions.

Conventions enforced:
1. Single-line subject only (no multi-line, body, bullet list, or paragraph).
2. 100% lowercase for the entire message, including the first word.
3. Past tense by default (e.g. added, created, fixed, updated, bumped, scaffolded).
4. No prefixes (no conventional commit tags like feat:, fix:, chore:, docs:, ci:).
5. No emojis.
6. No trailing period.
7. Plain, concise, conversational tone.
"""

import argparse
import re
import sys
from typing import List, Tuple

# Present-tense verbs mapped to recommended past-tense alternatives
PRESENT_TO_PAST = {
    "add": "added",
    "create": "created",
    "fix": "fixed",
    "update": "updated",
    "bump": "bumped",
    "scaffold": "scaffolded",
    "draft": "drafted",
    "review": "reviewed",
    "remove": "removed",
    "delete": "deleted",
    "change": "changed",
    "modify": "modified",
    "refactor": "refactored",
    "setup": "set up",
    "implement": "implemented",
    "configure": "configured",
    "wire": "wired",
    "point": "pointed",
    "pause": "paused",
    "tell": "told",
    "switch": "switched",
    "enable": "enabled",
    "disable": "disabled",
    "clean": "cleaned",
    "move": "moved",
    "merge": "merged",
    "reorganize": "reorganized",
    "align": "aligned",
    "adjust": "adjusted",
    "simplify": "simplified",
    "document": "documented",
    "drop": "dropped",
    "rename": "renamed",
    "support": "supported",
    "replace": "replaced",
    "enforce": "enforced",
    "integrate": "integrated",
    "allow": "allowed",
    "prevent": "prevented",
    "make": "made",
    "build": "built",
    "write": "wrote",
}

CONVENTIONAL_PREFIX_PATTERN = re.compile(
    r"^([a-z0-9_\-]+)(\([a-z0-9_\-/\.]+\))?:\s*", re.IGNORECASE
)


def contains_emoji(text: str) -> bool:
    """Check if text contains any emoji characters or symbols."""
    for char in text:
        cp = ord(char)
        if (
            0x1F600 <= cp <= 0x1F64F  # Emoticons
            or 0x1F300 <= cp <= 0x1F5FF  # Misc Symbols and Pictographs
            or 0x1F680 <= cp <= 0x1F6FF  # Transport and Map
            or 0x1F700 <= cp <= 0x1F77F  # Alchemical Symbols
            or 0x1F780 <= cp <= 0x1F7FF  # Geometric Shapes Extended
            or 0x1F800 <= cp <= 0x1F8FF  # Supplemental Arrows-C
            or 0x1F900 <= cp <= 0x1F9FF  # Supplemental Symbols and Pictographs
            or 0x1FA00 <= cp <= 0x1FA6F  # Chess Symbols
            or 0x1FA70 <= cp <= 0x1FAFF  # Symbols and Pictographs Extended-A
            or 0x2600 <= cp <= 0x26FF  # Misc symbols (sun, umbrella, etc.)
            or 0x2700 <= cp <= 0x27BF  # Dingbats
            or 0xFE00 <= cp <= 0xFE0F  # Variation selectors
            or 0x1F1E6 <= cp <= 0x1F1FF  # Flags
        ):
            return True
    return False


def validate_commit_message(raw_message: str) -> Tuple[bool, List[str]]:
    """Validate a commit message against AGENTS.md conventions.

    Returns (is_valid, errors).
    """
    errors: List[str] = []

    # Strip surrounding blank lines / carriage returns
    stripped = raw_message.strip()

    if not stripped:
        errors.append("Commit message cannot be empty.")
        return False, errors

    # Check 1: Single-line format
    lines = [line for line in stripped.splitlines() if line.strip() and not line.startswith("#")]
    if len(lines) > 1:
        errors.append(
            f"Commit message must be a single-line subject only (found {len(lines)} lines). "
            "Never write multi-line messages, bodies, bullet lists, or paragraphs."
        )

    first_line = lines[0] if lines else stripped

    # Check 2: 100% lowercase
    if any(c.isupper() for c in first_line):
        uppercase_chars = [c for c in first_line if c.isupper()]
        errors.append(
            f"Commit message must be 100% lowercase throughout. "
            f"Found uppercase characters: {''.join(set(uppercase_chars))}"
        )

    # Check 3: No conventional commit prefixes (e.g. feat:, fix:, chore:)
    prefix_match = CONVENTIONAL_PREFIX_PATTERN.match(first_line)
    if prefix_match:
        errors.append(
            f"Conventional commit prefix '{prefix_match.group(0).strip()}' is not allowed. "
            "Never use prefixes like feat:, fix:, chore:, refactor:, docs:, ci:. "
            "Describe the action directly in conversational past tense."
        )

    # Check 4: No emojis
    if contains_emoji(first_line):
        errors.append("Emojis are not allowed in commit messages.")

    # Check 5: No trailing period
    if first_line.rstrip().endswith("."):
        errors.append("Trailing period ('.') is not allowed at the end of commit messages.")

    # Check 6: Verb tense recommendation (past tense)
    clean_line = prefix_match.group(0) if prefix_match else ""
    remaining = first_line[len(clean_line):].strip()
    words = remaining.split()
    if words:
        first_word = words[0].lower().strip("',\"()")
        if first_word in PRESENT_TO_PAST:
            suggested = PRESENT_TO_PAST[first_word]
            errors.append(
                f"First word '{first_word}' is present tense. "
                f"Use past tense by default: consider '{suggested}' instead."
            )

    return len(errors) == 0, errors


def run_self_tests() -> bool:
    """Run internal test suite to verify validation logic."""
    test_cases = [
        # Valid historical commits from this repo
        ("added coding conventions for spring boot dev", True),
        ("fixed ci's missing db roles, documented step 2's schema decisions", True),
        ("created scripts for seed data (users row, accounts list, 101 categories)", True),
        ("created rls rules", True),
        ("created initial set of db level triggers", True),
        ("created the base schema & indexes based out of data model", True),
        ("pointing application and flyway to the right db accounts", True),
        ("added script to create the 2 db roles, and the appln schema", True),
        ("paused dependabot version updates until production, kept security alerts on", True),
        ("told dependabot to skip major typescript bumps until typescript-eslint supports ts7", True),
        ("setting up github actions ci pipeline", True),
        ("added skill to start postgres via docker compose", True),
        ("bumped postgres to v18 and fixed the data volume mount path", True),
        # Invalid cases
        ("Added coding conventions for spring boot dev", False),  # Capital A
        ("added coding conventions for spring boot dev.", False),  # Trailing period
        ("feat: added coding conventions", False),  # Prefix
        ("fix(ci): fixed db roles", False),  # Scope prefix
        ("add skill to start postgres", False),  # Present tense verb
        ("added skill to start postgres 🚀", False),  # Emoji
        ("added skill\n\nthis adds a skill to start postgres", False),  # Multi-line
        ("", False),  # Empty
    ]

    all_passed = True
    for message, expected_valid in test_cases:
        is_valid, errors = validate_commit_message(message)
        if is_valid != expected_valid:
            print(f"FAILED test: {message!r}")
            print(f"  Expected: {expected_valid}, Got: {is_valid}, Errors: {errors}")
            all_passed = False

    if all_passed:
        print(f"All {len(test_cases)} self-tests passed successfully.")
    return all_passed


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate git commit message against AGENTS.md §9 conventions."
    )
    parser.add_argument(
        "message",
        nargs="?",
        help="Commit message text to validate. If omitted or '-', reads from stdin.",
    )
    parser.add_argument(
        "--file",
        "-f",
        help="Path to file containing commit message (e.g. .git/COMMIT_EDITMSG).",
    )
    parser.add_argument(
        "--test",
        action="store_true",
        help="Run internal self-tests.",
    )

    args = parser.parse_args()

    if args.test:
        return 0 if run_self_tests() else 1

    content = ""
    if args.file:
        try:
            with open(args.file, "r", encoding="utf-8") as f:
                content = f.read()
        except OSError as e:
            print(f"Error reading file '{args.file}': {e}", file=sys.stderr)
            return 1
    elif args.message and args.message != "-":
        content = args.message
    else:
        content = sys.stdin.read()

    is_valid, errors = validate_commit_message(content)
    if is_valid:
        print("OK: Commit message complies with AGENTS.md conventions.")
        return 0
    else:
        print("ERROR: Commit message violates AGENTS.md conventions:", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
