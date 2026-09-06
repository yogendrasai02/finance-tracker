# Frontend Coding Conventions

This document defines the coding standards for the React TypeScript frontend.
All AI agents and developers must strictly adhere to these practices.

---

## 1. TypeScript Language & Imports

### 1.1 Imports
- Wildcard imports are forbidden, except when required by third-party libraries (e.g., `import * as React from 'react'`).
- Every import statement must name imported members explicitly.
- Use path aliases (`@/...`) for internal imports instead of deep relative paths (`../../`).
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

---

## 5. Directory Structure: Feature-Driven Layout

The frontend codebase is organized by business feature to maintain high cohesion:

```text
frontend/src/
├── assets/                  # Static assets and images
├── components/
│   ├── ui/                  # Shadcn UI primitives (button, card, dialog, etc.)
│   └── layout/              # App shell, navigation header, sidebar
├── features/
│   ├── auth/                # Login, session state, credentials
│   │   ├── api/             # Auth API calls (login, logout, getMe)
│   │   ├── components/      # LoginForm, AuthGuard
│   │   └── types/           # Auth DTOs and principal types
│   ├── accounts/            # Accounts list, account card, summary
│   │   ├── api/             # Account API calls
│   │   ├── components/      # AccountCard, AccountList
│   │   └── types/           # AccountResponse, AccountType
│   ├── statements/          # Statement upload, parsing preview
│   ├── transactions/        # Transaction ledger, quick entry, filters
│   └── dashboard/           # Metrics cards, income vs expense charts
├── hooks/                   # Cross-cutting custom hooks (theme, media queries)
├── lib/                     # Utilities (utils.ts, apiClient.ts, formatters.ts)
└── types/                   # Cross-cutting application types
```

### 5.1 Cross-Feature Boundaries
- Feature components may import from `src/components/ui/`, `src/lib/`, and `src/hooks/`.
- A feature must not directly import internal components or internal state from another feature.
- When two features share business data, move the shared contract or type to `src/types/` or coordinate through a top-level route.

---

## 6. Financial Domain & Data Integrity

### 6.1 Monetary Representation
- The backend stores and transmits all currency amounts as signed 64-bit integer paise (`number` in TypeScript).
- Never use floating-point numbers for financial calculations in the frontend.
- When displaying money to the user, always format integer paise using a dedicated helper (`formatPaiseToInr`):
```typescript
export function formatPaiseToInr(paise: number): string {
  const rupees = paise / 100;
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(rupees);
}
```
- When accepting rupee inputs from the user, parse the string to integer paise before sending it in an API payload.
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
- The backend issues a `XSRF-TOKEN` cookie on login.
- Every state-modifying request (`POST`, `PUT`, `DELETE`, `PATCH`) must read this cookie value and attach it to the `X-XSRF-TOKEN` HTTP header.
- Safe HTTP methods (`GET`, `HEAD`, `OPTIONS`) must not send the CSRF header.

### 7.3 Error Response Processing
- The backend reports errors using RFC 7807 Problem Details (`application/problem+json`).
- Parse the problem detail body into a strongly-typed structure containing `title`, `status`, `detail`, and optional `invalidParams`.
- Display friendly, actionable error messages based on the problem detail.
- Never leak sensitive backend stack traces or internal details into the user interface.

### 7.4 Logging and PII
- Never log passwords, account numbers, or raw bank transaction narration to the browser console.
- In production builds, all debug logging must be stripped or disabled.

---

## 8. Verification & Tooling

### 8.1 TypeScript Verification
- Run type checking across the entire frontend:
```bash
cd frontend && npm run build
```
- Type errors will fail the build and must be resolved before committing.

### 8.2 Linting
- Verify code style and lint rules:
```bash
cd frontend && npm run lint
```
- Zero ESLint errors and warnings are permitted on commits.
