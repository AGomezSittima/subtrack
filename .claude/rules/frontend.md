---
paths:
  - "frontend/**/*"
---

# Frontend rules

Full detail: `docs/standards.md` (TypeScript and React, Tailwind CSS and shadcn/ui) and `docs/sdd.md` (Frontend design).

## TypeScript
- `strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`. No `any`; use `unknown` and narrow.
- API types come from `@subtrack/contract` (generated). Never hand-write API types.
- Union types over enums. Named exports only. One component or module per file.
- Validate untrusted data with Zod at the boundary.

## Structure
- Routes (`src/routes/`, TanStack Router file-based) stay thin and compose feature components.
- Domain code lives in `src/features/<area>/{components,api,schemas}`.
- `src/components/ui/` is shadcn code we own. Feature code imports from `@/components/ui`, never from Base UI directly.
- Composite shared components (`DatePicker`, `MoneyInput`, `ServiceLogo`) live in `src/components/`.
- External dependencies sit behind our interfaces in `src/lib/` (`logo/`, `api/`, `time/`).

## Data and forms
- Server state only in TanStack Query; never copy it into local state or a store.
- Components never call `fetch`; use feature hooks (`useSubscriptions()`).
- Search params validated with Zod; loaders prefetch with `queryClient.ensureQueryData`.
- Forms: TanStack Form + Zod schemas (Standard Schema, no adapter).
- Every async view handles loading, empty and error states.
- On any 401, clear the whole query cache and go to the lock screen.

## Dates and money
- Temporal only. `Date` is allowed only inside `src/components/date-picker.tsx`.
- Amounts are integer cents; format with `Intl.NumberFormat` (`style: 'currency', currency: 'EUR'`) for the active locale.

## Styling (Tailwind CSS 4 + shadcn tokens)
- Order of preference: standard utility → token in `src/styles/global.css` (`:root` + `@theme inline`) → arbitrary value with a comment.
- Semantic color tokens only (`primary`, `muted`, `destructive`, `success`, `warning`); no raw palette classes.
- Sizes in `rem` only, so the font-size setting scales the whole UI. Every token has a `.dark` value.
- Conditional classes via `cn()`; never build class names by concatenation. No inline `style`, no `@apply` outside `global.css`.
- Animations use `motion-safe:` or respect `[data-reduced-motion]`.

## Localization and accessibility
- No literal user-facing strings in JSX; use `src/locales/{en,es}` with feature-namespaced keys. Add every key to both languages.
- Semantic elements first, every input has a label, everything keyboard-reachable, never color alone for meaning.

## Tooling
- ESLint flat config: typescript-eslint (type-checked), react-hooks, jsx-a11y, TanStack query/router plugins, simple-import-sort, unused-imports, i18next/no-literal-string. Prettier with prettier-plugin-tailwindcss.
- Zero warnings allowed (`--max-warnings 0`).
