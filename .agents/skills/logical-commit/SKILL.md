---
name: logical-commit
description: Group and commit current git changes into logical, atomic commits following the repository commit conventions in AGENTS.md. Use when the user asks to commit changes, make logical commits, stage and commit files, or commit uncommitted work.
---

# Logical Commit

Group and commit uncommitted changes into clean, atomic git commits.
Every commit must strictly follow the repository commit conventions defined in AGENTS.md §9.

---

## Workflow

### 1. Inspect Working Tree and Branch

1. Check the active branch:
   ```bash
   git branch --show-current
   ```
2. Check uncommitted changes:
   ```bash
   git status --short
   ```
3. If the working tree is clean, inform the user and stop.
4. Inspect the unstaged and staged changes in detail:
   ```bash
   git diff
   git diff --staged
   ```
5. Review any untracked files:
   ```bash
   git status -u
   ```

### 2. Pre-Commit Safety and Secret Check

Before staging, verify that no unintended or sensitive files are included:
1. Check for temporary files, build artifacts, and environment files (`.env`, `.env.local`, scratch files).
2. Ensure untracked temporary files are added to `.gitignore` instead of committed.
3. Verify that changes do not contain secrets or raw financial PII (PAN numbers, full bank account numbers, credentials).
4. Run the secret scanner script if available:
   ```bash
   python3 .agents/skills/safe-push/scripts/scan_secrets.py
   ```

### 3. Partition Changes into Logical Groups

Group modified files into atomic units of work.
A commit should represent a single logical change.

Apply these grouping principles:
- **Separate concerns**: Keep documentation, configuration, database migrations, backend code, and frontend code in separate commits unless tightly coupled.
- **Atomic migrations**: A database migration and its corresponding schema docs belong together in one commit.
- **Dependency order**: Commit foundational changes (configs, schemas, migrations) before the application code that relies on them.
- **Independent features**: If two features or fixes were made in the same session, create separate commits for each.
- **Single commit when appropriate**: If all changes belong to a single cohesive task, create a single commit rather than arbitrarily splitting.

### 4. Draft Commit Messages

Every commit message must strictly comply with AGENTS.md §9 conventions:

| Rule | Requirement |
| :--- | :--- |
| **Format** | Single-line subject only. Never write a multi-line message, body, bullet list, or paragraph. |
| **Casing** | 100% lowercase for the entire message, including the first word. |
| **Verb tense** | Past tense by default (`added`, `created`, `fixed`, `updated`, `bumped`, `scaffolded`, `drafted`, `reviewed`, `configured`, `removed`). |
| **No prefixes** | Never use conventional commit tags (`feat:`, `fix:`, `chore:`, `refactor:`, `docs:`, `ci:`). |
| **No emojis** | Never use emojis in git commit messages. |
| **No trailing period** | Never end the message with a period (`.`). |
| **Tone** | Plain, concise, conversational engineer tone. |
| **Abbreviations** | Use standard engineering abbreviations freely (`db`, `ci`, `rls`, `ts`, `dev`, `appln`). |
| **Multiple changes** | Combine related actions with a comma or `and` (`fixed ci's missing db roles, documented step 2's schema decisions`). |

#### Examples

**Compliant messages**:
```text
added skill for logical git commits
fixed ci's missing db roles, documented step 2's schema decisions
created scripts for seed data (users row, accounts list, 101 categories)
created rls rules
bumped postgres to v18 and fixed the data volume mount path
added script to create the 2 db roles, and the appln schema
```

**Non-compliant messages**:
```text
feat(auth): add jwt authentication    # Fails: conventional prefix, uppercase, present tense
Added RLS rules.                      # Fails: capital A, trailing period
updated docker compose 🚀             # Fails: emoji
added skill\n\nthis adds a skill...   # Fails: multi-line message
```

### 5. Validate Commit Messages

Run the validation script to verify each proposed commit message:
```bash
python3 .agents/skills/logical-commit/scripts/validate_commit_message.py "<commit-message>"
```

If the script reports any error, fix the message before committing.

### 6. Stage and Commit Group by Group

For each logical group in sequence:
1. Stage only the files belonging to that group:
   ```bash
   git add <file1> <file2> ...
   ```
   Do not run `git add .` or `git add -A` when creating multiple logical commits.
2. Verify the staged files:
   ```bash
   git status --short
   ```
3. Commit with the validated message:
   ```bash
   git commit -m "<validated-message>"
   ```

Repeat this step for every logical group until all intended changes are committed.

### 7. Post-Commit Verification

1. Review recent commits to verify formatting:
   ```bash
   git log -n 5 --oneline
   ```
2. Confirm the working tree status:
   ```bash
   git status --short
   ```
3. Present the list of created commits and their summaries to the user.
