# File Map

This document lists the files in the repository and the purpose of each file.
The AI must self-update this map whenever a file is created, moved, renamed, or deleted. (IMPORTANT: not optional, its MANDATORY)
Make this update in the same commit or change set as the code change.

## 1. Root & Infrastructure

| Path | Purpose |
| :--- | :--- |
| `AGENTS.md` | Golden instructions, behavioral rules, conventions, and architecture contract. |
| `CLAUDE.md` | Session context loader for Claude Code. |
| `GEMINI.md` | Session context loader for Antigravity / Gemini. |
| `README.md` | Quickstart guide and Docker / test commands. |
| `docker-compose.yml` | Local PostgreSQL 18 service with healthcheck and volume configuration. |
| `.github/workflows/ci.yml` | CI pipeline running Gitleaks, backend tests, and frontend build. |

## 2. Plans & Status (`plans/`)

| Path | Purpose |
| :--- | :--- |
| `plans/STATUS.md` | Current milestone state, completed steps, active work, and open questions. |
| `plans/STEP2_PLAN.md` | Implementation plan for Step 2 (Flyway migrations and PostgreSQL roles). |
| `plans/STEP3_PLAN.md` | Implementation plan for Step 3 (Schema Testcontainers test suite). |

## 3. Documentation (`docs/`)

| Path | Purpose |
| :--- | :--- |
| `docs/SPEC.md` | Functional requirements, financial business rules, and transaction states. |
| `docs/DATA_MODEL.md` | Database schema, constraints, indexes, triggers, and decisions DM-01 to DM-38. |
| `docs/SECURITY.md` | Threat model, data classifications, PII handling, and security rules SR-01 to SR-78. |
| `docs/BACKEND_CONVENTIONS.md` | Spring Boot architecture, package-by-feature layout, and coding conventions. |
| `docs/STATEMENT_DATA_EXPLORATION.md` | Analysis of real SBI, HDFC, and ICICI statement structures. |
| `docs/FILE_MAP.md` | Codebase inventory table mapping file paths to their responsibilities. |
| `docs/reviews/SPEC_REVIEW.md` | Review report and approval record for SPEC.md. |
| `docs/reviews/DATA_MODEL_REVIEW.md` | Review report and approval record for DATA_MODEL.md. |
| `docs/reviews/SECURITY_REVIEW.md` | Review report and approval record for SECURITY.md. |

## 4. Scripts & Hooks

| Path | Purpose |
| :--- | :--- |
| `scripts/setup-hooks.sh` | Shell script to configure local git hooks. |
| `scripts/hooks/pre-commit` | Pre-commit hook running Gitleaks against staged changes. |
| `.claude/hooks/check_commit_message.py` | Hook verifying git commit messages follow the lowercase conventions. |

## 5. Database Initialization & Migrations

| Path | Purpose |
| :--- | :--- |
| `db/init/01-roles-and-schema.sh` | Container startup script creating `app` schema, `ft_migrator`, and `ft_app` roles. |
| `backend/src/main/resources/db/migration/V1__base_schema.sql` | Base DDL creating all 11 domain tables, composite keys, and indexes. |
| `backend/src/main/resources/db/migration/V2__triggers.sql` | Triggers enforcing bank data immutability and updating timestamps. |
| `backend/src/main/resources/db/migration/V3__row_level_security.sql` | RLS enablement and tenant isolation policies on all domain tables. |
| `backend/src/main/resources/db/migration/V4__seed_data.sql` | Starter user, default accounts, and standard category seeds. |

## 6. Backend Application (`backend/src/main/`)

| Path | Purpose |
| :--- | :--- |
| `backend/pom.xml` | Maven build definition targeting Java 25 and Spring Boot 4.1.1. |
| `backend/src/main/resources/application.yml` | Base application configuration setting active profile to `local`. |
| `backend/src/main/resources/application-local.yml` | Local configuration with `ft_app` datasource and `ft_migrator` Flyway. |
| `backend/src/main/resources/application-prod.yml` | Production configuration reading credentials from environment variables. |
| `backend/src/main/java/com/financetracker/BackendApplication.java` | Spring Boot application entry point. |
| `backend/src/main/java/com/financetracker/controller/HelloController.java` | Baseline probe endpoint at `GET /api/v1/hello`. |

## 7. Backend Tests (`backend/src/test/`)

| Path | Purpose |
| :--- | :--- |
| `backend/src/test/java/com/financetracker/BackendApplicationTests.java` | Spring context load test with dynamic Testcontainers properties. |
| `backend/src/test/java/com/financetracker/controller/HelloControllerTest.java` | WebMvc slice test verifying `HelloController`. |
| `backend/src/test/java/com/financetracker/db/PostgresTestContainer.java` | Shared singleton Testcontainer running PostgreSQL 18 with role setup. |
| `backend/src/test/java/com/financetracker/db/SchemaTestBase.java` | Base JDBC test harness running Flyway once and providing role connections. |
| `backend/src/test/java/com/financetracker/db/TestFixtures.java` | Reusable JDBC fixture generation helpers for tests. |
| `backend/src/test/java/com/financetracker/db/MigrationApplyTest.java` | Verifies clean migration application. |
| `backend/src/test/java/com/financetracker/db/SchemaConventionsTest.java` | Catalog sweep checking paise integers, UTC timestamps, and tenant keys. |
| `backend/src/test/java/com/financetracker/db/RolePrivilegeTest.java` | Verifies `ft_app` cannot run DDL or alter triggers and policies. |
| `backend/src/test/java/com/financetracker/db/RowLevelSecurityTest.java` | Proves RLS isolates tenant data when `app.user_id` is set or unset. |
| `backend/src/test/java/com/financetracker/db/RlsCoverageTest.java` | Catalog sweep proving RLS is active on every domain table. |
| `backend/src/test/java/com/financetracker/db/TriggerImmutabilityTest.java` | Proves triggers refuse edits to bank-imported data. |
| `backend/src/test/java/com/financetracker/db/TriggerTimestampTest.java` | Proves `updated_at` automatically updates on modification. |
| `backend/src/test/java/com/financetracker/db/TransactionConstraintTest.java` | Tests transaction-specific constraints, dedup, and category matching. |
| `backend/src/test/java/com/financetracker/db/CrossTenantConstraintTest.java` | Proves foreign keys reject cross-tenant references. |
| `backend/src/test/java/com/financetracker/db/SupportingTableConstraintTest.java` | Tests constraints on categories, rules, checkpoints, and imports. |
| `backend/src/test/java/com/financetracker/db/DeleteRuleTest.java` | Proves `RESTRICT`, `CASCADE`, and `SET NULL` behaviors. |

## 8. Frontend Application (`frontend/`)

| Path | Purpose |
| :--- | :--- |
| `frontend/package.json` | Frontend dependencies and scripts (React 19, TypeScript, Vite 8). |
| `frontend/vite.config.ts` | Vite configuration with React plugin. |
| `frontend/eslint.config.js` | ESLint configuration for React and TypeScript. |
| `frontend/src/main.tsx` | React application root entry point. |
| `frontend/src/App.tsx` | Main application shell component. |
