# 0006. FastAPI enforces the privacy lock in ASGI middleware

Date: 2026-11-22 · Status: Accepted

## Context

[0004](0004-privacy-lock-scope.md) puts lock enforcement in one place per backend: a router dependency in FastAPI and the Spring Security filter chain in Spring. The two backends must answer identically ([0001](0001-two-interchangeable-backends.md), NFR-6), and a locked API must give nothing away (NFR-11).

A FastAPI router dependency runs only for a request that matched a route, and only after FastAPI has parsed the JSON body. A servlet filter runs before both. With a passcode set and no session, the two backends would answer differently:

| Request | Router dependency | Spring filter chain |
| --- | --- | --- |
| Unknown path under `/api/v1` | 404 | 401 |
| Known path, wrong method | 405 | 401 |
| Malformed JSON body | 400 | 401 |

## Decision

- FastAPI enforces the lock in a pure ASGI middleware (`security/middleware.py`) in front of routing. It is the counterpart of the servlet filter chain: it sees every request before routing and body parsing, and calls the same `LockEnforcer` decision.
- Both backends check the lock first. With a passcode set and no valid session, every `/api/v1` path except `/health`, `/auth/status` and `/auth/unlock` answers 401, whether the route exists, the method is allowed or the body is valid. Paths outside `/api/v1` are not locked and answer 404.
- The rest of 0004 stands.

## Alternatives

- **Router dependency plus extra hooks:** a custom `APIRoute` class to run before body parsing, and the 404/405 handler calling the same guard. That makes three entry points into one decision, and it relies on FastAPI internals for routing errors.
- **Routing errors before the lock:** answer 404/405/400 first in both. Spring would have to resolve handlers and parse bodies inside the security filter, and a locked API would reveal which routes exist.

## Consequences

- One enforcement point in each backend, with the same order, covered by the backend lock tests.
- Middleware can't use FastAPI `Depends`, so the enforcer is passed in when the app is built (`main.create_app`).
- SDD: Privacy lock, Errors. Standards: Security. Amends 0004 (enforcement point in FastAPI).
