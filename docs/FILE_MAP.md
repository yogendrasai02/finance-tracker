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
| `.env.example` | Template for the gitignored `.env`, listing the owner credential and database variables. |
| `.github/workflows/ci.yml` | CI pipeline running Gitleaks, backend tests, and the frontend lint, test, and build steps. |
| `.claude/launch.json` | Dev server registration so the Claude Code browser preview can attach to `npm run dev`. |

## 2. Plans & Status (`plans/`)

| Path | Purpose |
| :--- | :--- |
| `plans/STATUS.md` | Current milestone state, completed steps, active work, and open questions. |
| `plans/STEP2_PLAN.md` | Implementation plan for Step 2 (Flyway migrations and PostgreSQL roles). |
| `plans/STEP3_PLAN.md` | Implementation plan for Step 3 (Schema Testcontainers test suite). |
| `plans/STEP4_PLAN.md` | Implementation plan for Step 4 (authentication, session, and the tenant execution path). |

## 3. Documentation (`docs/`)

| Path | Purpose |
| :--- | :--- |
| `docs/SPEC.md` | Functional requirements, financial business rules, and transaction states. |
| `docs/DATA_MODEL.md` | Database schema, constraints, indexes, triggers, and decisions DM-01 to DM-40. |
| `docs/SECURITY.md` | Threat model, data classifications, PII handling, and security rules SR-01 to SR-78. |
| `docs/BACKEND_CONVENTIONS.md` | Spring Boot architecture, package-by-feature layout, and coding conventions. |
| `docs/FRONTEND_CONVENTIONS.md` | React 19 architecture, Tailwind CSS v4 rules, Shadcn UI discipline, and financial formatting conventions. |
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
| `.agents/hooks.json` | Antigravity lifecycle hooks configuration (commit message and comment style validation). |
| `.agents/hooks/check_commit_message.py` | WORA hook verifying git commit messages follow AGENTS.md §9 conventions. |
| `.agents/hooks/check_comment_style.py` | WORA hook verifying code comments follow AGENTS.md §6 and §7 conventions. |
| `.agents/hooks/session_context.sh` | Hook gathering live git and project state on session start. |
| `.claude/hooks` | Symlink pointing to `../.agents/hooks` for Claude Code cross-tool compatibility. |
| `.claude/skills` | Symlink pointing to `../.agents/skills` for Claude Code cross-tool compatibility. |
| `.claude/settings.json` | Claude Code settings configuring hooks. |

## 5. Database Initialization & Migrations

| Path | Purpose |
| :--- | :--- |
| `db/init/01-roles-and-schema.sh` | Container startup script creating the `app` and `auth` schemas, `ft_migrator`, and `ft_app` roles. |
| `backend/src/main/resources/db/migration/V1__base_schema.sql` | Base DDL creating all 11 domain tables, composite keys, and indexes. |
| `backend/src/main/resources/db/migration/V2__triggers.sql` | Triggers enforcing bank data immutability and updating timestamps. |
| `backend/src/main/resources/db/migration/V3__row_level_security.sql` | RLS enablement and tenant isolation policies on all domain tables. |
| `backend/src/main/resources/db/migration/V4__seed_data.sql` | Starter user, default accounts, and standard category seeds. |
| `backend/src/main/resources/db/migration/V5__auth.sql` | Credential columns on `app.users` and the `SECURITY DEFINER` login lookup function. |
| `backend/src/main/resources/db/migration/V6__session_store.sql` | Spring Session JDBC's session tables, created in the `auth` schema instead of `app`. |

## 6. Backend Application (`backend/src/main/`)

| Path | Purpose |
| :--- | :--- |
| `backend/pom.xml` | Maven build definition targeting Java 25 and Spring Boot 4.1.1. |
| `backend/src/main/resources/application.yml` | Base application configuration setting active profile to `local`. |
| `backend/src/main/resources/application-local.yml` | Local configuration with `ft_app` datasource and `ft_migrator` Flyway. |
| `backend/src/main/resources/application-prod.yml` | Production configuration reading credentials from environment variables. |
| `backend/src/main/java/com/financetracker/BackendApplication.java` | Spring Boot application entry point. |
| `backend/src/main/java/com/financetracker/user/User.java` | JPA entity mapping `app.users` (V1 + V5 columns). |
| `backend/src/main/java/com/financetracker/user/UserRepository.java` | Package-private Spring Data JPA repository for `User`. |
| `backend/src/main/java/com/financetracker/account/Account.java` | JPA entity mapping `app.accounts`. |
| `backend/src/main/java/com/financetracker/account/AccountRepository.java` | Package-private Spring Data JPA repository for `Account`. |
| `backend/src/main/java/com/financetracker/account/AccountResponse.java` | FR-1's response DTO: id, name, type, active — no balance yet and no dedup method. |
| `backend/src/main/java/com/financetracker/account/AccountService.java` | Lists the caller's accounts; logs how many were found. Tenant scoping and RLS do the filtering, not this class. |
| `backend/src/main/java/com/financetracker/account/AccountController.java` | `GET /api/v1/accounts`, the first tenant-scoped, domain-data endpoint. |
| `backend/src/main/java/com/financetracker/user/UserService.java` | Public surface of the user feature: the password-hash write, the profile read, and the login stamp. |
| `backend/src/main/java/com/financetracker/user/UserProfile.java` | The identity record returned by login and `GET /api/v1/me`. |
| `backend/src/main/java/com/financetracker/auth/PasswordEncoderConfig.java` | Delegating password encoder with Argon2id as the default algorithm. |
| `backend/src/main/java/com/financetracker/auth/LoginIdentity.java` | Projection of the credential columns login is allowed to read. |
| `backend/src/main/java/com/financetracker/auth/LoginIdentityRepository.java` | The one privileged read: calls `app.find_login_identity` with no tenant set. |
| `backend/src/main/java/com/financetracker/auth/OwnerCredentialProperties.java` | Binds `FT_OWNER_EMAIL` and `FT_OWNER_PASSWORD`. |
| `backend/src/main/java/com/financetracker/auth/OwnerCredentialBootstrap.java` | Sets the owner's password on startup when the account has none. |
| `backend/src/main/java/com/financetracker/auth/AppUserDetailsService.java` | Loads the credential for a login attempt, and fails uniformly for every reason it can fail. |
| `backend/src/main/java/com/financetracker/auth/AuthenticatedUser.java` | The session principal: user id, email, and a password hash that is erased after the check. |
| `backend/src/main/java/com/financetracker/auth/LoginRequest.java` | The validated login body. |
| `backend/src/main/java/com/financetracker/auth/AuthController.java` | `POST /api/v1/auth/login` and `GET /api/v1/me`. |
| `backend/src/main/java/com/financetracker/common/security/SecurityConfiguration.java` | The deny-by-default filter chain, cookie and CSRF settings, security headers, and the authentication manager. |
| `backend/src/main/java/com/financetracker/common/security/SessionAuthenticator.java` | Checks the password and starts the session, including session fixation and CSRF token replacement. |
| `backend/src/main/java/com/financetracker/common/security/TenantContextFilter.java` | Puts the logged-in user's id into the tenant scope for the length of one request. |
| `backend/src/main/java/com/financetracker/common/security/SecurityErrorHandler.java` | Writes the 401 and 403 problem bodies for requests the filter chain rejects. |
| `backend/src/main/java/com/financetracker/common/security/SessionStoreConfiguration.java` | The plain, non-tenant-aware transaction manager Spring Session JDBC uses to read and write session rows. |
| `backend/src/main/java/com/financetracker/common/security/SessionProperties.java` | Binds `ft.session.absolute-timeout`, SR-38's second, independent session lifetime. |
| `backend/src/main/java/com/financetracker/common/security/AbsoluteSessionTimeoutFilter.java` | Invalidates a session once it is older than the configured absolute lifetime, regardless of activity. |
| `backend/src/main/java/com/financetracker/common/security/LoginRateLimitProperties.java` | Binds `ft.login-rate-limit.per-minute` and `.per-hour` (SR-39, D-40). |
| `backend/src/main/java/com/financetracker/common/security/LoginRateLimiter.java` | One Bucket4j bucket per key, holding the two stacked limits. |
| `backend/src/main/java/com/financetracker/common/security/CachedBodyHttpServletRequest.java` | Reads a request body into memory once so a filter and the controller behind it can both read it. |
| `backend/src/main/java/com/financetracker/common/security/LoginRateLimitFilter.java` | Rejects an over-limit login attempt before the password check runs, keyed by client IP and the submitted email. |
| `backend/src/main/java/com/financetracker/common/error/ApiProblems.java` | The single source of the error bodies that have to be indistinguishable from each other. |
| `backend/src/main/java/com/financetracker/common/error/GlobalExceptionHandler.java` | The one `@RestControllerAdvice`: the uniform login failure, not-found, and validation-failure mappings. |
| `backend/src/main/java/com/financetracker/common/error/ProblemResponseWriter.java` | Writes a `ProblemDetail` straight to a servlet response, for the code that runs before a controller can return one. |
| `backend/src/main/java/com/financetracker/common/error/NotFoundException.java` | The one exception every feature throws for both "missing" and "not yours" (SR-04, SR-78). |
| `backend/src/main/java/com/financetracker/common/error/FieldViolation.java` | A field name and constraint message, never the rejected value, for a validation-failure body. |
| `backend/src/main/java/com/financetracker/common/tenant/TenantPrincipal.java` | The seam letting `common` read the logged-in user's id without depending on the auth feature. |
| `backend/src/main/java/com/financetracker/common/tenant/CurrentTenantContext.java` | Per-thread tenant, exposed only through scoped runners that always restore the previous value. |
| `backend/src/main/java/com/financetracker/common/tenant/TenantAwareJpaTransactionManager.java` | Applies `app.user_id` to every transaction and refuses one that has no tenant. |
| `backend/src/main/java/com/financetracker/common/tenant/MissingTenantContextException.java` | Thrown when a transaction would run with no tenant and is not a system transaction. |
| `backend/src/main/java/com/financetracker/common/tenant/TenantConfiguration.java` | Registers the tenant-aware transaction manager in place of the auto-configured one. |

## 7. Backend Tests (`backend/src/test/`)

| Path | Purpose |
| :--- | :--- |
| `backend/src/test/java/com/financetracker/BackendApplicationTests.java` | Spring context load test with dynamic Testcontainers properties. |
| `backend/src/test/java/com/financetracker/db/PostgresTestContainer.java` | Shared singleton Testcontainer running PostgreSQL 18 with role setup. |
| `backend/src/test/java/com/financetracker/db/SchemaTestBase.java` | Base JDBC test harness running Flyway once and providing role connections. |
| `backend/src/test/java/com/financetracker/db/TestFixtures.java` | Reusable JDBC fixture generation helpers for tests. |
| `backend/src/test/java/com/financetracker/db/MigrationApplyTest.java` | Verifies clean migration application. |
| `backend/src/test/java/com/financetracker/db/AuthSchemaTest.java` | Proves the credential column rules hold and the login lookup is the only tenant-free read of a user. |
| `backend/src/test/java/com/financetracker/db/SessionStoreSchemaTest.java` | Proves V6's session tables exist in `auth`, carry no Row-Level Security, and are reachable by `ft_app`. |
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
| `backend/src/test/java/com/financetracker/account/JpaBaselineTest.java` | DataJpaTest proving entity mappings validate and RLS is active through Hibernate. |
| `backend/src/test/java/com/financetracker/account/AccountsIntegrationTest.java` | Real HTTP: an authenticated caller gets their own accounts, an unauthenticated one gets 401, and two tenants never see each other's rows. |
| `backend/src/test/java/com/financetracker/common/tenant/CurrentTenantContextTest.java` | Proves the tenant scope restores and clears, including when the work throws. |
| `backend/src/test/java/com/financetracker/common/tenant/TenantTransactionTest.java` | Proves the tenant reaches the connection Hibernate uses and that each tenant sees only its own rows. |
| `backend/src/test/java/com/financetracker/architecture/ArchitectureTest.java` | ArchUnit rules protecting the tenant mechanism and the layering conventions. |
| `backend/src/test/java/com/financetracker/auth/OwnerCredentialBootstrapTest.java` | Proves the credential is hashed, set once, and kept out of the logs. |
| `backend/src/test/java/com/financetracker/auth/AuthenticationIntegrationTest.java` | Drives login, `/me` and logout over real HTTP, checking cookie attributes, CSRF, and uniform failures. |
| `backend/src/test/java/com/financetracker/common/security/SessionStoreTest.java` | Proves the session lives in `auth.spring_session`: a row appears on login, disappears on logout, and disappears when the absolute lifetime is exceeded. |
| `backend/src/test/java/com/financetracker/common/security/LoginRateLimitFilterTest.java` | Real HTTP: proves the sixth login attempt in a minute for one email and client returns 429 with `Retry-After`, and that the body names no account. |
| `backend/src/test/java/com/financetracker/common/security/LoginRateLimiterTest.java` | Plain unit test of the Bucket4j bucket math, no Spring context. |
| `backend/src/test/java/com/financetracker/common/error/GlobalExceptionHandlerTest.java` | Plain unit test proving `NotFoundException` always maps to the same body and a validation failure never carries the rejected value. |
| `backend/src/test/java/com/financetracker/testsupport/CookieJarHttpClient.java` | Shared hand-written cookie jar for tests that drive the application over real HTTP, including arbitrary methods for the route sweep. |
| `backend/src/test/java/com/financetracker/testsupport/TwoUserTestHarness.java` | Reusable base for IDOR tests: seeds two users with accounts and credentials once per class, and shares one session per user so the login rate limit is not spent on setup (SR-07). |
| `backend/src/test/java/com/financetracker/security/EndpointSecurityTest.java` | Sweeps every route in every handler mapping and proves each non-public one returns 401, then proves user B gets a 404 for user A's account id. Extends `TwoUserTestHarness`. |

## 8. Frontend Application (`frontend/`)

| Path | Purpose |
| :--- | :--- |
| `frontend/package.json` | Frontend dependencies and scripts (React 19, TypeScript, Vite 8, Tailwind v4, Shadcn, TanStack Query, Vitest, MSW). `lint` passes `--max-warnings 0` so a warning fails the command. |
| `frontend/components.json` | Shadcn UI CLI configuration (Tailwind v4, Base UI, Geist font, Nova preset). |
| `frontend/vite.config.ts` | Vite configuration with React plugin, Tailwind v4 plugin, `@` path alias, the dev proxy from `/api` to the backend (D-39), and the Vitest jsdom setup. |
| `frontend/tsconfig.json` | Root TypeScript project references and path alias mapping. |
| `frontend/tsconfig.app.json` | Frontend application TypeScript compiler options with `@/*` path mapping, and `strict` plus `noUncheckedIndexedAccess` set explicitly rather than left to the compiler default. |
| `frontend/eslint.config.js` | ESLint configuration for React 19, TypeScript, and Shadcn component variants, plus the `no-restricted-imports` rule that enforces the feature public-surface boundary. |
| `frontend/src/main.tsx` | React application root entry point. |
| `frontend/src/App.tsx` | Route table: `/login`, the protected `/accounts` under `AppLayout`, and a catch-all redirect. |
| `frontend/src/index.css` | Global stylesheet importing Tailwind CSS v4, font variables, and Shadcn semantic theme tokens. |
| `frontend/src/lib/utils.ts` | Shared frontend utility functions, including the `cn()` class name merger. |
| `frontend/src/lib/routes.ts` | Every path the router knows, as one `ROUTES` constant, so a rename is a single edit. |
| `frontend/src/lib/money.ts` | `formatPaiseToInr` and `parseInrToPaise`: the only place integer paise is turned into rupees or back. |
| `frontend/src/lib/apiClient.ts` | The one fetch wrapper every feature calls through: same-origin credentials, the CSRF header on state-changing methods, an `AbortSignal` pass-through, and every non-2xx response turned into an `ApiError` carrying the RFC 7807 body. |
| `frontend/src/lib/queryClient.ts` | Builds the single TanStack Query client: retry policy, stale time, and the one place a 401 becomes a call to the auth feature's `handleUnauthorized`. |
| `frontend/src/components/QueryProvider.tsx` | Creates the query client once under `AuthProvider`, and clears the cache when the session ends. |
| `frontend/src/components/AppErrorBoundary.tsx` | The one class component: catches a render throw so a bug shows a message instead of a blank page. |
| `frontend/src/components/ui/button.tsx` | Shadcn Button component supporting variants (default, secondary, outline, destructive) and sizes. |
| `frontend/src/components/ui/card.tsx` | Shadcn Card component suite (Card, CardHeader, CardTitle, CardDescription, CardAction, CardContent, CardFooter). |
| `frontend/src/components/ui/badge.tsx` | Shadcn Badge component supporting semantic status variants. |
| `frontend/src/components/ui/input.tsx` | Shadcn Input component for styled text inputs. |
| `frontend/src/components/ui/label.tsx` | Shadcn Label component for accessible form field labels. |
| `frontend/src/components/ui/field.tsx` | Shadcn Field composition (Field, FieldGroup, FieldLabel, FieldDescription, FieldError, and related layout primitives) for form layout. |
| `frontend/src/components/ui/alert.tsx` | Shadcn Alert component suite for callouts, including login and load-failure error messages. |
| `frontend/src/components/ui/spinner.tsx` | Shadcn Spinner component used inside disabled buttons and page-level loading states. |
| `frontend/src/components/ui/separator.tsx` | Shadcn Separator component (a Field composition dependency). |
| `frontend/src/components/layout/AppHeader.tsx` | Header shown on authenticated pages: app name, the logged-in user's display name, and the logout button. |
| `frontend/src/components/layout/AppLayout.tsx` | Wraps `AppHeader` and an `Outlet` for every route nested under `ProtectedRoute`. |
| `frontend/src/features/auth/index.ts` | The auth feature's public surface, and the only path other code may import from it. |
| `frontend/src/features/auth/types.ts` | The `UserProfile` type returned by login and `GET /api/v1/me`. |
| `frontend/src/features/auth/authApi.ts` | `login`, `logout`, and `getMe` calls through `apiClient`. |
| `frontend/src/features/auth/authContext.ts` | The `AuthContext` object and its value type only; no component, so it cannot break React Fast Refresh. |
| `frontend/src/features/auth/useAuth.ts` | The `useAuth()` hook other features and layout components call to read auth state. |
| `frontend/src/features/auth/components/AuthProvider.tsx` | Calls `GET /api/v1/me` once on mount and holds `status` (`loading` / `authenticated` / `unauthenticated`) and the current `UserProfile`; exposes `login`, `logout`, and `handleUnauthorized` (D-41). |
| `frontend/src/features/auth/components/LoginForm.tsx` | Email and password fields, one uniform error message on failure, and a disabled-plus-spinner submit state. |
| `frontend/src/features/auth/components/LoginForm.test.tsx` | Drives the form against MSW: labelled fields, the posted body, the uniform failure message, and the non-API fallback message. |
| `frontend/src/features/auth/components/ProtectedRoute.tsx` | Route guard: redirects to `/login` while unauthenticated, otherwise renders its nested routes. |
| `frontend/src/features/auth/pages/LoginPage.tsx` | The `/login` route: redirects to `/accounts` if already authenticated, otherwise renders `LoginForm` in a Card. |
| `frontend/src/features/accounts/index.ts` | The accounts feature's public surface. |
| `frontend/src/features/accounts/types.ts` | The `AccountResponse` type and the `AccountType` union (`ASSET`, `LIABILITY`, `VIRTUAL`) mirroring the backend DTO. |
| `frontend/src/features/accounts/accountsApi.ts` | The `listAccounts` call and the `accountQueryKeys` that name its cache entry. |
| `frontend/src/features/accounts/AccountList.tsx` | Renders the caller's accounts as Cards with a type badge and an active/inactive badge. |
| `frontend/src/features/accounts/AccountsPage.tsx` | The `/accounts` route: one `useQuery`, an empty state, and an error alert for failures that are not the session. |
| `frontend/src/features/accounts/AccountsPage.test.tsx` | The list, the empty state, the error alert, and the whole expired-session chain through the real route guard. |
| `frontend/src/lib/money.test.ts` | Formatting and parsing of integer paise, including exactness at the safe-integer limit and rejection of a third decimal place. |
| `frontend/src/lib/apiClient.test.ts` | The CSRF header rules, the problem-detail mapping, the 204 case, and the abort signal. |
| `frontend/src/test/setup.ts` | Per-run test wiring: jest-dom matchers, the MSW lifecycle, the CSRF cookie, and a relative-URL shim for Node's fetch. |
| `frontend/src/test/server.ts` | The MSW node server every test shares. |
| `frontend/src/test/handlers.ts` | Default backend responses and the RFC 7807 problem builder tests override with. |
| `frontend/src/test/renderWithProviders.tsx` | Renders a component inside the same provider stack `App` uses, in the same order. |
