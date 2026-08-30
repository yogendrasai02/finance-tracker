#!/usr/bin/env python3
import os
import re
import subprocess
import sys

PATTERNS = [
    # Infrastructure & API Secrets
    ("AWS Access Key", re.compile(r"(?:A3T[A-Z0-9]|AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}")),
    ("AWS Secret Key", re.compile(r"(?i)aws_secret_access_key\s*=\s*['\"]?[A-Za-z0-9/+=]{40}['\"]?")),
    ("GitHub Token", re.compile(r"(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{36,255}")),
    ("GitHub Fine-grained PAT", re.compile(r"github_pat_[A-Za-z0-9_]{82}")),
    ("Google API Key", re.compile(r"AIza[0-9A-Za-z-_]{35}")),
    ("OpenAI / Anthropic Key", re.compile(r"sk-(?:proj-|ant-)?[A-Za-z0-9_-]{20,}")),
    ("Stripe Secret Key", re.compile(r"(?:sk|rk)_live_[0-9a-zA-Z]{24,}")),
    ("Private Key", re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----")),
    ("Slack Token", re.compile(r"xox[baprs]-[0-9]{10,13}-[0-9]{10,13}[a-zA-Z0-9-]*")),
    ("Database Connection String with Password", re.compile(r"(?i)(?:postgres|mysql|mongodb|redis|jdbc:[a-z]+)://[^:\s]+:([^@\s]+)@")),
    ("Generic Secret/Password Assignment", re.compile(r"(?i)(?:api_key|apikey|secret|password|passwd|auth_token|access_token|private_key)\s*[:=]\s*['\"]([^'\"\s]{8,})['\"]")),
    ("Generic Bearer Token", re.compile(r"(?i)bearer\s+[a-zA-Z0-9_\-\.]{30,}")),

    # Financial & Banking PII (Indian Context)
    ("Indian Bank Account Number (Unmasked)", re.compile(r"(?i)(?:account_number|account_no|acc_no|accountnumber|bank_account|accountnum|acct_no)\s*[:=]\s*['\"]?([0-9]{9,18})['\"]?")),
    ("Indian PAN Number", re.compile(r"\b[A-Z]{5}[0-9]{4}[A-Z]\b")),
    ("Indian Aadhaar Number (Formatted)", re.compile(r"\b[2-9]\d{3}[ -]\d{4}[ -]\d{4}\b")),
    ("Indian Aadhaar Number (Contextual)", re.compile(r"(?i)(?:aadhaar|uidai|aadhaar_no|aadhaar_num|aadhaar_number)\s*[:=]\s*['\"]?([2-9]\d{11})['\"]?")),
    ("Card CVV / PIN / Password Assignment", re.compile(r"(?i)(?:cvv|cvc|card_pin|atm_pin|upi_pin|netbanking_password|netbanking_pwd)\s*[:=]\s*['\"]?([0-9a-zA-Z]{3,16})['\"]?")),
    ("Phone Number UPI VPA", re.compile(r"\b[6-9]\d{9}@(upi|okaxis|okhdfcbank|okicici|oksbi|paytm|ybl|ibl|axl|apl|pingpay)\b")),
]

CARD_PATTERN = re.compile(r"\b(?:\d{4}[ -]?){3}\d{4}\b")

IGNORE_SUBSTRINGS = [
    "example", "placeholder", "xxxx", "dummy", "sample", "your_",
    "<branch>", "<password>", "<account>", "change_me", "sha512-", "sha256-",
    "com.financetracker", "backend-local", "backend-prod", "000000000", "123456789"
]

def luhn_check(num_str):
    digits = [int(c) for c in num_str if c.isdigit()]
    if len(digits) < 13 or len(digits) > 19:
        return False
    # Avoid trivial sequences like 1111111111111111
    if len(set(digits)) == 1:
        return False
    checksum = 0
    reverse_digits = digits[::-1]
    for i, d in enumerate(reverse_digits):
        if i % 2 == 1:
            doubled = d * 2
            checksum += doubled - 9 if doubled > 9 else doubled
        else:
            checksum += d
    return checksum % 10 == 0

def mask_val(val):
    if len(val) > 8:
        return val[:3] + "*" * (len(val) - 6) + val[-3:]
    return "****"

def scan_text(text, source_name):
    findings = []
    lines = text.splitlines()
    for idx, line in enumerate(lines, 1):
        # 1. Regex Pattern Checks
        for name, pattern in PATTERNS:
            for match in pattern.finditer(line):
                val = match.group(0)
                val_lower = val.lower()
                if any(sub in val_lower for sub in IGNORE_SUBSTRINGS):
                    continue
                # Special validation for Indian PAN: 4th char must indicate entity type
                if name == "Indian PAN Number":
                    if val[3] not in "PCHFATBLJG":
                        continue
                findings.append((source_name, idx, name, mask_val(val), line.strip()))

        # 2. Credit / Debit Card Number check with Luhn validation
        for match in CARD_PATTERN.finditer(line):
            raw_card = match.group(0)
            clean_digits = re.sub(r"\D", "", raw_card)
            if any(sub in raw_card.lower() for sub in IGNORE_SUBSTRINGS):
                continue
            if luhn_check(clean_digits):
                findings.append((source_name, idx, "Payment Card (Luhn Validated)", mask_val(raw_card), line.strip()))

    return findings

def get_git_root():
    res = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True)
    if res.returncode != 0:
        return None
    return res.stdout.strip()

def get_unpushed_commits(git_root):
    res = subprocess.run(["git", "rev-parse", "--abbrev-ref", "@{u}"], cwd=git_root, capture_output=True, text=True)
    if res.returncode == 0:
        range_spec = "@{u}..HEAD"
    else:
        res_main = subprocess.run(["git", "rev-parse", "--verify", "origin/main"], cwd=git_root, capture_output=True, text=True)
        if res_main.returncode == 0:
            range_spec = "origin/main..HEAD"
        else:
            range_spec = "HEAD"
    
    res_diff = subprocess.run(["git", "log", "-p", range_spec], cwd=git_root, capture_output=True, text=True)
    if res_diff.returncode == 0:
        return res_diff.stdout, range_spec
    return "", ""

def main():
    git_root = get_git_root()
    if not git_root:
        print("Error: Not in a git repository.")
        sys.exit(1)

    print(f"Running secrets and financial PII scan in: {git_root}\n")

    # 1. Scan all tracked files in working directory
    res = subprocess.run(["git", "ls-files"], cwd=git_root, capture_output=True, text=True, check=True)
    files = res.stdout.strip().splitlines()

    all_findings = []
    for f in files:
        filepath = os.path.join(git_root, f)
        if not os.path.isfile(filepath):
            continue
        try:
            with open(filepath, "r", encoding="utf-8", errors="ignore") as fp:
                content = fp.read()
            findings = scan_text(content, f)
            all_findings.extend(findings)
        except Exception as e:
            print(f"Warning: Could not read {f}: {e}")

    # 2. Scan unpushed commit diffs
    diff_text, diff_range = get_unpushed_commits(git_root)
    if diff_text:
        diff_findings = scan_text(diff_text, f"commit-diff ({diff_range})")
        all_findings.extend(diff_findings)

    print(f"Scanned {len(files)} tracked files.")

    if all_findings:
        print("\n❌ SENSITIVE DATA / SECRETS DETECTED! PUSH BLOCKED.")
        print(f"Total violations found: {len(all_findings)}\n")
        for source, line_num, rule, masked, raw_line in all_findings:
            print(f"  - [{rule}] {source}:{line_num}")
            print(f"    Match: {masked}")
            print(f"    Line:  {raw_line[:100]}")
        sys.exit(1)

    print("✅ No secrets or unmasked financial PII detected. Ready to push.")
    sys.exit(0)

if __name__ == "__main__":
    main()
