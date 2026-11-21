# 0005. Contract lint exceptions

Date: 2026-11-21 · Status: Accepted

## Context

`just lint` runs Redocly CLI with `recommended-strict`, so any finding fails the build. Three of its findings are wrong for this contract, and the configs that silence them used to explain themselves in comments, which `docs/standards.md` (Comments) no longer allows for deliberate trade-offs.

## Decision

- `info-license` and `info-license-strict` are off in `contract/redocly.yaml`: Subtrack is a private, single-user app with no published license.
- `operation-4xx-response` is ignored for `GET /health` and `GET /auth/status` only, in `contract/.redocly.lint-ignore.yaml`. Both are public and take no parameters or body, so no 4xx can happen. Every other operation must still declare one.
- `just gen` validates with `contract/redocly.gen.yaml` (`extends: [minimal]`). openapi-typescript validates the contract before generating types but doesn't read the ignore file, so with the strict config it would fail on the two ignored findings. The strict rules still run in `just lint`.

## Alternatives

- **Declare a 4xx response on `/health` and `/auth/status`:** documents an error the backends never send, which the shared API suite would have to accept.
- **Turn `operation-4xx-response` off globally:** loses the check on every endpoint that does take input.
- **Lint with `recommended` instead of `recommended-strict`:** warnings would no longer fail the build.

## Consequences

- A new public endpoint without input needs its own entry in the ignore file, and this ADR is the place to say why.
- `just gen` catches only structural errors; style findings surface in `just lint`.
- SDD: API contract, Linting.
