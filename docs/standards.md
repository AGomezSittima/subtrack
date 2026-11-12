# Subtrack — Development Standards

Nov 10, 2026 · @Alvaro

How code is written across the Subtrack monorepo. These are defaults: break one only with a reason written in the PR or an ADR.

## Principles

When two principles pull apart, simplicity wins: this is a small single-user app, and readable code beats clever code.

| Principle | What it means here |
| --- | --- |
| Single responsibility | A module has one reason to change. Routes don't hold logic; the billing core doesn't do I/O; components don't fetch. |
| Open/closed | Add behavior by adding an implementation (a new logo provider, a new notification type), not by editing a growing `if`/`switch`. |
| Liskov substitution | Any implementation of an interface is a drop-in replacement. Both backends are the large-scale example: the frontend can't tell them apart. |
| Interface segregation | Small, focused interfaces (`LogoProvider`, `Clock`, `PaymentRepository`), not one large "service" interface. |
| Dependency inversion | Domain and service code depend on interfaces; concrete adapters are wired in at the edge (composition root, DI container). |
| KISS | Pick the simplest thing that meets the requirement: SQLite over Postgres, polling over websockets, one container per role. |
| DRY | Each fact has one source: the OpenAPI contract for types, the SQL migrations for the schema, `@theme` tokens for design values. Duplicate code is merged on the third copy, not the second (rule of three). |
| YAGNI | No hooks for features the SRS doesn't list. Out-of-scope items stay out until they move into scope. |

## External dependencies behind abstractions

Everything outside our own domain code sits behind an interface we own (ports and adapters). Application code never imports a vendor SDK, an HTTP client or the system clock directly.

| Dependency | Interface we own | Adapter(s) | Why |
| --- | --- | --- | --- |
| Subtrack API (from the frontend) | Feature hooks, e.g. `useSubscriptions()`, `useConfirmPayment()` | Generated OpenAPI client + TanStack Query | Components never call `fetch`; the client can change without touching UI |
| Logo.dev | `LogoProvider.urlFor(domain)` | `LogoDevProvider`, `MonogramProvider` (fallback) | Swap to Brandfetch or local files by changing one adapter |
| Time | `Clock` (`now()`, `today()`) | System clock, fixed clock for tests | Billing and reminders are testable on any date |
| Database | Repository interfaces per aggregate | SQLAlchemy / Spring Data implementations | Business logic tests run without SQLite |
| Scheduling | `SchedulerJob.run(clock)` | APScheduler / `@Scheduled` trigger, admin endpoint | The job is plain code; the trigger is a detail |
| Configuration | Typed settings object | Env vars (pydantic-settings, `@ConfigurationProperties`, Vite env) | No `process.env` / `os.environ` reads scattered in code |
| Date picker (shadcn Calendar, works with Date) | DatePicker: value and onChange as Temporal.PlainDate | One wrapper converting PlainDate ↔ Date at the boundary | The only file allowed to touch Date; the picker can be replaced without touching forms |
| UI primitives (Base UI) | Components in @/components/ui | shadcn/ui components owned in the repo | Feature code never depends on the primitive library |

Rules:

- Vendor types never cross the interface: `LogoProvider` returns a URL string, not a vendor response object.
- One adapter per file, named after the vendor (`LogoDevProvider`), next to its interface.
- Every adapter has a fake or in-memory version for tests.
- Secrets and keys come from configuration, never from source code.

## Dates, times and money

Each concept uses the same kind of type in all three languages. Temporal was modeled on `java.time`, so the TypeScript and Java code read almost the same.

| Concept | TypeScript | Python | Java | Example |
| --- | --- | --- | --- | --- |
| Calendar date (billing, due, trial end) | `Temporal.PlainDate` | `datetime.date` | `LocalDate` | `2026-12-01` |
| Exact moment (created\_at, read\_at) | `Temporal.Instant` | aware `datetime` in UTC | `Instant` | `2026-11-10T15:18:00Z` |
| Moment shown to the user | `Temporal.ZonedDateTime` | `datetime` + `ZoneInfo` | `ZonedDateTime` | Europe/Madrid |
| Interval | `Temporal.Duration` | `dateutil.relativedelta` | `Period` | 3 months |
| Current time | injected `Clock` | injected `Clock` | `java.time.Clock` bean |  |
| Money | `number` of integer cents | `int` cents | `long` cents | `1199` = 11,99 € |

Rules:

- **No `Date` in TypeScript.** Use Temporal everywhere. Chrome 144+ and Firefox 139+ ship it natively, but Safari does not yet, so load a polyfill (`temporal-polyfill` or `@js-temporal/polyfill`) once at app entry.
- Dates travel as ISO strings (`YYYY-MM-DD`, `…Z`) and are parsed into these types at the boundary, never passed around as strings.
- Billing dates are always plain dates; time zones apply only to timestamps and display.
- Money is never a float. Format only at the edge with `Intl.NumberFormat` (`style: 'currency', currency: 'EUR'`).
- In the database, dates are `YYYY-MM-DD` text and timestamps are UTC ISO text with milliseconds and `Z`. Never rely on a driver's default date storage.

## TypeScript and React

**TypeScript**

- `strict: true` plus `noUncheckedIndexedAccess` and `exactOptionalPropertyTypes`.
- No `any`. Use `unknown` and narrow it; validate untrusted data with Zod at the boundary.
- API types are generated from `contract/openapi.yaml`, never written by hand.
- Prefer union types and discriminated unions over enums; `as const` objects when a runtime list is needed.
- Named exports only; one component or module per file.

**React**

- Function components and hooks only; components stay small and presentational where possible.
- Server state lives only in TanStack Query. No copying query data into local state or a global store.
- Forms use TanStack Form with Zod schemas (Standard Schema, no adapter needed).
- Feature folders: `features/subscriptions/{components,hooks,api,schemas}`; shared UI in `components/ui`.
- Every async view handles loading, empty and error states.
- Accessibility: semantic elements first, every input has a label, everything reachable by keyboard.
- Routing uses TanStack Router with file-based routes in `src/routes`. Search params are validated with Zod; route loaders prefetch with `queryClient.query({ ...options, staleTime: 'static' })` (returns cached data or fetches it; replaces the deprecated `ensureQueryData`) so pages render without loading flashes.

**Linting and formatting**

ESLint finds problems; Prettier owns formatting. They never overlap: `eslint-config-prettier` turns off every ESLint style rule.

| Tool | Configuration |
| --- | --- |
| ESLint | Flat config (`eslint.config.js`) with `typescript-eslint` type-checked rules, `eslint-plugin-react-hooks`, `eslint-plugin-jsx-a11y`, `@tanstack/eslint-plugin-query`, `@tanstack/eslint-plugin-router` |
| Import order | `eslint-plugin-simple-import-sort`: external packages, then `@/` aliases, then relative imports, then styles |
| Unused imports | `eslint-plugin-unused-imports`: removed automatically on `--fix`; unused variables are errors (prefix `_` to keep one on purpose) |
| Prettier | Defaults plus `prettier-plugin-tailwindcss` for class order |

- Manually format in the editor; `lint-staged` runs `eslint --fix` and `prettier --write` on staged files before each commit.
- CI runs `eslint --max-warnings 0` and `prettier --check`; any warning or formatting diff fails the build.

**Localization**

- No hard-coded user-facing text in components; every string comes from `src/locales/{en,es}`. ESLint's `i18next/no-literal-string` rule enforces it in JSX.
- Translation keys are namespaced by feature (`subscriptions.list.empty`). A key added in one language is added in the other in the same PR.
- Dates, numbers and amounts are formatted with `Intl` / Temporal `toLocaleString` for the active locale, never assembled by hand.
- The backend never sends display text: notifications carry a type and parameters, errors a problem `type`; the frontend translates both.
- E2E tests run in English by default, plus one smoke flow in Spanish.

## Tailwind CSS and shadcn/ui

Use the standard scale first, a project token second, and an arbitrary value only as a last resort. Tokens follow shadcn's convention, so shadcn components and our own code share one theme.

1. **Standard utilities**: `p-4`, `text-sm`, `rounded-lg`, `gap-2`. If one is close enough, use it.
2. **Custom token**: if no standard value fits and the value will be reused, add a variable to `:root` in `src/styles/global.css` and map it in `@theme inline`. It becomes a normal utility (`bg-primary`, `text-muted-foreground`, `bg-success`).
3. **Arbitrary value** (`w-[37px]`): only for a true one-off with no meaning elsewhere, with a short comment explaining why.

```css
/* src/styles/global.css */
@import "tailwindcss";

:root {
  --radius: 0.75rem;
  --background: oklch(0.99 0.005 260);
  --foreground: oklch(0.2 0.02 260);
  --primary: oklch(0.55 0.18 160);          /* Subtrack brand */
  --primary-foreground: oklch(0.98 0 0);
  --muted: oklch(0.95 0.01 260);
  --muted-foreground: oklch(0.5 0.02 260);
  --destructive: oklch(0.58 0.22 25);
  --success: oklch(0.62 0.16 150);          /* project token, not in shadcn */
  --warning: oklch(0.78 0.15 80);           /* project token, not in shadcn */
  --font-sans: "Inter Variable", system-ui, sans-serif;
}

@theme inline {
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-destructive: var(--destructive);
  --color-success: var(--success);
  --color-warning: var(--warning);
  --font-sans: var(--font-sans);
  --radius-lg: var(--radius);
}
```

Values above are placeholders until the visual identity is designed.

**Tailwind rules**

- Colors are semantic tokens (`primary`, `muted`, `destructive`, `success`, `warning`), never raw palette names (`bg-emerald-600`) in components, so the whole identity lives in one file.
- No inline `style` and no hand-written CSS for things a utility can do.
- Repeated class lists become a component, not an `@apply` rule. `@apply` only in `global.css` for base element styles.
- Conditional classes through the `cn()` helper (`clsx` + `tailwind-merge`); never build class names by string concatenation (Tailwind can't detect them).
- Class order is enforced by `prettier-plugin-tailwindcss`.

**shadcn/ui rules**

- Components are added with the shadcn CLI on Base UI and live in `src/components/ui`. They are our code: restyle them freely to match the Subtrack identity.
- The identity comes from tokens first (colors, radius, font) and from component variants (`cva`) second; feature code never overrides a component's look with one-off classes.
- Feature code imports from `@/components/ui`, never from Base UI directly.
- Composite components (e.g. `SubscriptionCard`, `MoneyInput`, `DatePicker`) live in `src/components` and are built from `ui` parts.
- Before upgrading a component, check `shadcn diff` and merge upstream changes by hand, keeping our customizations.

**Appearance settings rules**

- Sizes are in `rem` only (Tailwind's standard scale already is), so the font-size setting scales the whole UI. No `px` for text, spacing or component sizes; `px` only for hairline borders.
- Every color token has a dark value under `.dark`; components never use `dark:` with raw colors, only tokens.
- Animations use `motion-safe:` or are disabled under `[data-reduced-motion]`; nothing essential is conveyed by motion alone.

## Backend standards

Both backends follow the same layering (API → service → billing core → repository) and the same module names, so they can be compared file by file.

| Topic | Python / FastAPI | Java / Spring Boot |
| --- | --- | --- |
| Dependencies | `uv`, lockfile committed | Gradle wrapper, version catalog |
| Format + lint | Ruff (format and lint) | Spotless with a standard Java formatter |
| Types | Full type hints, strict type checker (mypy or pyright) | Compiler warnings as errors, nullness annotations |
| DTOs | Pydantic models, separate from ORM models | Java records, separate from JPA entities |
| Dependency injection | FastAPI `Depends`, wired in one module | Constructor injection only, no field `@Autowired` |
| Errors | Domain exceptions mapped to problem JSON in one handler | Domain exceptions mapped via `@RestControllerAdvice` + `ProblemDetail` |
| Logging | stdlib `logging`, structured, no `print` | SLF4J, no `System.out` |

Shared rules:

- Controllers and routers translate HTTP to service calls and back; nothing else.
- Transactions are opened in the service layer, one per use case.
- The billing core imports nothing from the web, ORM or framework packages.
- Behavior differences between the two backends are bugs, unless the contract allows both.

## Security

- Secrets (passcode, session tokens, API keys) are never logged, returned by the API, or stored in readable form. Passcodes are hashed with Argon2id; session tokens are stored as hashes.
- Access control lives in one place per backend (FastAPI router dependency, Spring Security filter chain); individual endpoints don't re-implement it.
- New endpoints are protected by default; exceptions (`/health`, `/auth/status`, `/auth/unlock`) are listed explicitly and covered by tests.
- Cookies are HttpOnly and SameSite=Strict. The frontend never reads or stores the session token.
- On any 401 the frontend drops all cached data before showing the lock screen.

## Testing

Priority: **end to end > integration > unit**, on a base of static analysis (types, lint, format). Write the highest-level test that can catch the bug at a reasonable speed; drop to a lower level only when the higher one would be slow, flaky or unable to reach the case.

1. **End to end** (most tests): user flows in the browser with Playwright against the full stack, run on both Compose profiles.
2. **Integration**: the shared API suite in `e2e/api/`, written once in TypeScript and run against each backend. Every BR table has a case here.
3. **Unit** (fewest): the billing core date math from the shared JSON cases, and hooks or components with real logic (Vitest + Testing Library).

Rules:

- No test reads the real clock. Tests set the date through the test clock (`POST /admin/clock`) or inject a fixed `Clock`.
- Each test creates the data it needs on a fresh database; no test depends on another's leftovers. The fresh database comes from the test-mode `POST /admin/reset`.
- E2E selectors use roles and labels (`getByRole`, `getByLabel`), never CSS classes, so tests also check accessibility.
- A bug fix starts with a failing test that reproduces it, at the highest level that can.
- Test names describe behavior: `confirms payment with a different amount`, not `test_confirm_2`.
- CI cadence: static checks and the API suite on every push, against both backends; the full browser suite only on `main` and the staging branch.

## Monorepo, Git and tooling

One repository, `subtrack`, holds the contract, migrations, frontend, both backends, end-to-end tests and docs.

- **Contract first**: a change to the API starts in `contract/openapi.yaml`, then both backends and the frontend follow, ideally in the same PR.
- **Commits**: Conventional Commits with a scope per area: `feat(frontend):`, `fix(spring):`, `feat(fastapi):`, `chore(contract):`, `chore(db):`, `docs:`.
- **Branches**: short-lived branches off `main`; `main` always builds and passes tests.
- **One entry point**: a root `justfile` (or `Makefile`) with the same verbs for every part: `just test`, `just lint`, `just up fastapi`, `just up spring`.
- **Pre-commit hooks** run formatters and linters for the files touched.
- **CI** runs only the jobs for changed paths, plus the full contract and end-to-end suite on `main`.
- **Decisions** that change these standards or the SDD are recorded as short ADRs in `docs/adr/`.
- **Versions**: the latest stable versions that are compatible with each other, pinned in lockfiles; upgrades happen in their own PRs.
- **Docs** live in `docs/` (`srs.md`, `sdd.md`, `standards.md`, `adr/`). A change in behavior updates the SRS or SDD in the same PR.

## Sources

- [Temporal API: types, browser support and polyfills](https://flaviocopes.com/temporal-api/)
- [TanStack Form: Standard Schema example](https://tanstack.com/form/v1/docs/framework/react/examples/standard-schema)
