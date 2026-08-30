---
name: safe-push
description: Scan repository files and unpushed commits for secrets and financial PII, and push the current branch to the remote repository only if no secrets or unmasked PII are found. Use when the user asks to safely push code, push commits, or run pre-push security verification.
---

# Safe Push

Push code to the remote repository with mandatory pre-push secret and financial PII scanning.
This project contains financial code, statement exploration records, and architecture specs.
Preventing accidental credential, token, or financial data leakage into GitHub is a hard requirement.

This skill scans all tracked files and unpushed commits before running `git push`.
If any secret or unmasked financial identifier is detected, the push is blocked immediately.

## Workflow

### 1. Check Git Branch and Status
1. Identify the active branch:
   ```bash
   git branch --show-current
   ```
2. Check for uncommitted changes:
   ```bash
   git status --short
   ```
3. If uncommitted changes exist, inform the user.
   Do not commit untracked or uncommitted files automatically.

### 2. Execute Secret & Financial PII Scanning
Run the bundled scanner script from the project root:
```bash
python3 .agents/skills/safe-push/scripts/scan_secrets.py
```

The script inspects:
- All tracked files in the repository.
- Commit diffs for all unpushed commits on the current branch (`@{u}..HEAD`).
- **Developer secrets**: AWS keys, GitHub tokens, Google API keys, OpenAI/Anthropic keys, Stripe keys, private keys, database connection strings, bearer tokens, and generic password assignments.
- **Financial & Banking PII**: Unmasked Indian bank account numbers, PAN card numbers, formatted/contextual Aadhaar numbers, payment card numbers (validated via Luhn algorithm), card CVV/PINs, and phone-number-based UPI VPAs.

### 3. Handle Scan Results

#### If Secrets or PII Are Detected (Exit Code 1)
1. **STOP immediately.** Do not execute `git push`.
2. Display the list of detected violations, offending files, and line numbers.
3. Instruct the user to:
   - Remove or mask the sensitive value from the file.
   - Amend or rewrite the commit to purge the secret from Git history:
     ```bash
     git commit --amend
     ```
   - Re-run the safe push workflow.

#### If No Secrets or PII Are Detected (Exit Code 0)
1. Proceed to push the current branch.

### 4. Push to Remote Repository
1. Push the current branch to origin:
   - If upstream tracking exists:
     ```bash
     git push
     ```
   - If upstream is not yet configured:
     ```bash
     git push -u origin <current-branch>
     ```
2. Confirm the push status and provide the remote branch link.
