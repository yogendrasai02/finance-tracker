# Frontend Coding Conventions

This document defines the coding standards for the React TypeScript frontend.
All AI agents and developers must strictly adhere to these practices.

---

## 1. TypeScript Language & Imports

### 1.1 Imports
- Wildcard imports are forbidden, except when required by third-party libraries (e.g., `import * as React from 'react'`).
- Every import statement must name imported members explicitly.
- Use path aliases (`@/...`) to cross a boundary: from a feature into `@/lib`, `@/components`, or another feature's public surface.
- Use relative imports for siblings inside the same feature (`./authApi`, `../useAuth`). Never go up more than one level; if you need `../../`, the import is crossing a boundary and should use `@/`.
- Keep imports ordered: external dependencies first, internal shared aliases second (`@/components`, `@/lib`), feature-local imports third.

### 1.2 TypeScript Strict Baseline
- Target modern TypeScript with strict checking enabled.
- Avoid the `any` type completely; use `unknown` when a type cannot be known in advance.
- Define prop structures using explicit `interface` or `type` declarations.
- Provide explicit return types on hooks, helper functions, and API client calls.
- Use discriminated unions for component state machines and API request states.

### 1.3 File Naming
- Use PascalCase for component files (e.g., `AccountCard.tsx`, `TransactionTable.tsx`).
- Use camelCase for utility functions, custom hooks, and type files (e.g., `formatPaise.ts`, `useAuth.ts`, `accountTypes.ts`).
- Name test files alongside the source file with `.test.ts` or `.test.tsx` suffix.

---

## 2. React & Component Architecture

### 2.1 Functional Components
- Use functional components exclusively with standard function declarations or arrow functions.
- Class components are forbidden.
- Type component props directly with standard TypeScript interfaces or types.
- Avoid monolithic components; decompose UI screens into focused, reusable components.

### 2.2 Hooks and State Management
- Keep state local to the component that needs it.
- Lift state only to the nearest common ancestor when multiple sibling components require it.
- Never write custom hooks that duplicate standard browser APIs when simple primitives suffice.
- Ensure all effect dependencies are complete and accurate.
- Do not use `useEffect` for data transformation that can be computed during render.
- Distinguish client state from server state. Client state is `useState` or context. Server state is anything the backend owns, and it belongs to TanStack Query (§9), never to a hand-written `useEffect` plus `useState` pair.

### 2.3 The One Allowed Class Component
- `src/components/AppErrorBoundary.tsx` is a class because React still provides no hook that catches a render error.
- It is the only exception to the rule above. Any other class component is a mistake.
- The boundary shows a generic message. It never renders the error text, which can carry backend detail the user should not see.

---

## 3. Styling & Tailwind CSS v4 Rules

### 3.1 CSS-First Architecture
- Styling uses Tailwind CSS v4 with CSS-first configuration in `src/index.css`.
- Use the `@theme inline` block for custom design tokens and font references.
- Never create a separate `tailwind.config.js` file.

### 3.2 Semantic Color Tokens
- Always use semantic color classes (`bg-background`, `text-foreground`, `text-muted-foreground`, `border-border`, `bg-primary`, `bg-card`).
- Never hardcode raw hex values (e.g., `bg-[#1e293b]`) or raw Tailwind color scales (e.g., `text-blue-600`) for standard UI surfaces.
- Semantic tokens guarantee automatic and consistent light and dark mode support.
- Do not write manual `dark:` overrides for colors that are already governed by semantic theme variables.

### 3.3 Layout and Utility Rules
- Always use `cn()` from `@/lib/utils` for merging conditional class names.
- Use `flex` or `grid` with `gap-*` for spacing child elements.
- Never use `space-x-*` or `space-y-*` utilities.
- Use `size-*` when width and height are equal (e.g., `size-8` instead of `w-8 h-8`).
- Use `truncate` shorthand instead of multiple manual text-overflow classes.
- Use `className` on Shadcn components for positioning and layout, not for overriding internal design tokens.

---

## 4. Shadcn UI Component Discipline

### 4.1 Primitives and Composition
- Shadcn UI primitives reside in `src/components/ui/`.
- Do not write custom HTML replacements when a standard Shadcn component exists.
- Callouts use `Alert`.
- Loading placeholders use `Skeleton`.
- Status indicators use `Badge` variants or semantic color tokens.
- Content containers use full `Card` composition (`CardHeader`, `CardTitle`, `CardDescription`, `CardContent`, `CardFooter`).
- Never put all content into a single `CardContent` without header or description structure.

### 4.2 Form and Input Conventions
- Input controls must use accessible labels linked via `htmlFor` and `id`.
- Validation errors must set `aria-invalid` on the input control and `data-invalid` on the parent field container.
- Use `ToggleGroup` for selecting among two to five choices instead of buttons with manual toggle logic.
- Buttons must not have a custom `isLoading` prop; compose them with a spinner icon and disabled state.

### 4.3 Icon Guidelines
- Use Lucide icons (`lucide-react`) exclusively.
- Pass icons as React components, never as string names.
- Icons inside buttons must use `data-icon="inline-start"` or `data-icon="inline-end"`.
- Do not add manual sizing classes to icons inside components that handle icon sizing automatically.

### 4.4 Document Structure
- Every page renders its own `<main>` and exactly one `<h1>`, which names that page.
- The app header is a `<header>` and its brand text is not a heading. It repeats on every page and would otherwise compete with the page's own `<h1>`.
- A loading region carries `role="status"` and an `aria-label`, so it is announced and so tests can wait for it.

---

## 5. Directory Structure: Feature-Driven Layout

The frontend codebase is organized by business feature to maintain high cohesion:

```text
frontend/src/
├── assets/                  # Static assets and images
├── components/
│   ├── ui/                  # Shadcn UI primitives (button, card, dialog, etc.)
│   ├── layout/              # App shell, navigation header, sidebar
│   ├── AppErrorBoundary.tsx # Catches a render throw so a bug does not blank the page
│   └── QueryProvider.tsx    # Builds the one TanStack Query client, wired to the auth 401 handler
├── features/
│   ├── auth/                # Login, session state, credentials
│   │   ├── index.ts         # The feature's public surface, and the only file others may import
│   │   ├── authApi.ts       # login, logout, getMe
│   │   ├── authContext.ts   # The context object and its types, no component
│   │   ├── useAuth.ts       # The hook every other part of the app reads auth state through
│   │   ├── types.ts         # UserProfile
│   │   ├── components/      # AuthProvider, LoginForm, ProtectedRoute
│   │   └── pages/           # LoginPage
│   ├── accounts/            # Accounts list, account card, summary
│   │   ├── index.ts
│   │   ├── accountsApi.ts   # Calls and the query keys that name them
│   │   ├── types.ts         # AccountResponse, AccountType
│   │   ├── AccountList.tsx
│   │   └── AccountsPage.tsx
│   ├── statements/          # Statement upload, parsing preview
│   ├── transactions/        # Transaction ledger, quick entry, filters
│   └── dashboard/           # Metrics cards, income vs expense charts
├── hooks/                   # Cross-cutting custom hooks (theme, media queries)
├── lib/                     # apiClient.ts, queryClient.ts, money.ts, routes.ts, utils.ts
├── test/                    # Test harness: MSW server, handlers, provider render helper
└── types/                   # Cross-cutting application types
```

### 5.1 Feature Subfolders Are Earned, Not Required
- Start a feature flat. Add `api/`, `components/`, `pages/`, or `types/` only when that folder would hold more than one file.
- A folder containing a single file adds a directory level and gives nothing back.
- Split a feature into subfolders once it grows past roughly six files, as `auth` has.

### 5.2 Cross-Feature Boundaries
- Every feature exposes a public surface in its own `index.ts`. Everything else in the folder is internal.
- Other code imports `@/features/<name>` and never a path inside it. This is enforced by `no-restricted-imports` in `eslint.config.js`, not by review.
- Inside a feature, siblings are imported relatively (`./authApi`, `../useAuth`), which is what keeps the rule above unambiguous.
- Feature code may import from `src/components/ui/`, `src/lib/`, and `src/hooks/` freely.
- When two features share business data, move the shared contract or type to `src/types/` or coordinate through a top-level route.

---

## 6. Financial Domain & Data Integrity

### 6.1 Monetary Representation
- The backend stores and transmits all currency amounts as signed 64-bit integer paise (`number` in TypeScript).
- All arithmetic on money stays in integer paise. Adding, subtracting, summing a list, comparing: integers only.
- Division by 100 happens in exactly one place, `formatPaiseToInr` in `src/lib/money.ts`, at the moment of display. Nowhere else.
- That one division is written with integer operations (`absolute % 100`, then `(absolute - fraction) / 100`) so it is exact rather than merely close enough. Do not simplify it back to `paise / 100`.
- `formatPaiseToInr` throws on a value that is not an exact integer. A visible failure is better than a rounded amount on screen.
- Going the other way, `parseInrToPaise` turns user input into integer paise and returns a `ParsedAmount` result rather than throwing, because invalid input is a normal state of a form.
- More than two decimal places is rejected, never rounded. The user meant something specific and the application does not get to decide what.
- Both directions are covered by `src/lib/money.test.ts`. A change to either without a test is not finished.
- Follow the database sign convention: negative paise for outflow (expenses), positive paise for inflow (income).

### 6.2 Dates and Timestamps
- Dates for transactions are posting dates without time (`YYYY-MM-DD`).
- UTC audit timestamps (`created_at`, `updated_at`) must be formatted according to Indian Standard Time (IST) for display.

---

## 7. API Client & Security Standards

### 7.1 Same-Origin Credentials
- All HTTP requests to backend endpoints must include `credentials: 'include'` to send the session cookie.
- The development frontend uses the Vite reverse proxy to route `/api/*` to the Spring Boot backend on the same origin.
- Never store session identifiers or authentication tokens in `localStorage` or `sessionStorage`.

### 7.2 Anti-CSRF Token Handling
- The backend loads the CSRF token eagerly, so every response carries the `XSRF-TOKEN` cookie, including the 401 from the first `GET /me`.
- Every state-modifying request (`POST`, `PUT`, `DELETE`, `PATCH`) must read this cookie value and attach it to the `X-XSRF-TOKEN` HTTP header.
- Safe HTTP methods (`GET`, `HEAD`, `OPTIONS`) must not send the CSRF header.
- A state-changing call with no cookie to read fails as `CsrfTokenMissingError` before the request is sent. Because the cookie should always be there, its absence is a fault on this side, and failing here says so instead of producing a 403 that looks like a permission problem.

### 7.3 Error Response Processing
- The backend reports errors using RFC 7807 Problem Details (`application/problem+json`).
- Parse the problem detail body into a strongly-typed structure containing `title`, `status`, `detail`, and optional `invalidParams`.
- Display friendly, actionable error messages based on the problem detail.
- Never leak sensitive backend stack traces or internal details into the user interface.

### 7.4 Logging and PII
- Never log passwords, account numbers, or raw bank transaction narration to the browser console.
- In production builds, all debug logging must be stripped or disabled.
- `AppErrorBoundary` logs only under `import.meta.env.DEV`, for the same reason.

### 7.5 Cancellation
- `apiFetch` accepts an `AbortSignal` and passes it to `fetch`.
- Every query function takes the signal TanStack Query supplies, so a query that is no longer needed stops its request instead of finishing into a discarded result.

---

## 8. Verification & Tooling

### 8.1 TypeScript Verification
- Run type checking across the entire frontend:
```bash
cd frontend && npm run build
```
- Type errors will fail the build and must be resolved before committing.
- `strict` and `noUncheckedIndexedAccess` are both set explicitly in `tsconfig.app.json`. Do not rely on a compiler default for either.

### 8.2 Linting
- Verify code style and lint rules:
```bash
cd frontend && npm run lint
```
- Zero ESLint errors and warnings are permitted on commits.
- The script passes `--max-warnings 0`, so a warning fails the command. Without that flag ESLint exits 0 on warnings and the rule above is unenforceable.

### 8.3 Tests
- Run the frontend test suite:
```bash
cd frontend && npm test
```
- Vitest with jsdom, Testing Library for rendering, and MSW for the backend. Tests never mock `fetch` by hand and never mock `apiClient`, so the client's own behaviour is exercised too.
- A test file sits next to the file it covers, named `<source>.test.ts` or `.test.tsx`.
- `src/test/` holds the shared harness: `server.ts` and `handlers.ts` for MSW, `renderWithProviders.tsx` for rendering inside the same provider stack `App` uses, and `setup.ts` for the per-run wiring.
- Query text the way a user finds it: `getByRole` and `getByLabelText`, not a CSS class or a test id.
- Anything that computes or parses money needs a test. That is the code where a wrong answer is silent.
- CI runs `npm run lint`, `npm test`, and `npm run build` on every push.

---

## 9. Server State: TanStack Query

### 9.1 Every Backend Read Goes Through a Query
- Data the backend owns is fetched with `useQuery`, never with `useEffect` plus `useState`.
- A hand-written fetching effect has no cache, no deduplication, no refetch after a mutation, and no cancellation. Each page that writes its own gets those wrong in its own way.
- The one exception is `AuthProvider`'s first `GET /me`. The query client is built from that provider's 401 handler, so it does not exist yet when that call runs.

### 9.2 Query Keys
- Keys live beside the calls they name, as a `const` object in the feature's api file (`accountQueryKeys`).
- A mutation invalidates by referencing that object, never by retyping the string.

### 9.3 One Client, Built Once
- `createQueryClient` in `src/lib/queryClient.ts` is the only place query defaults are set.
- `QueryProvider` creates it once through a lazy `useState` initialiser. Creating it during render would discard the cache on every re-render.
- A 4xx is never retried; it will fail the same way every time. A 5xx or a dropped connection is retried twice.

### 9.4 Expired Sessions Are Handled Once
- The query client's `onError` recognises a 401 and calls the auth feature's `handleUnauthorized` (D-41).
- `ProtectedRoute` turns that state change into a redirect to the login page.
- A page therefore never handles a 401 itself. It only renders an error for failures that are not the session, which `isUnauthorized` distinguishes.
- When auth state becomes unauthenticated, `QueryProvider` clears the cache, so no account data is left in memory for whoever logs in next.
