# 0004. The privacy lock is a privacy screen, not data protection

Date: 2026-11-10 · Status: Accepted

## Context

The app runs on the user's own computer, which is often left logged in. The user wants to stop someone at that computer from browsing their subscriptions (FR-40 to FR-45). The SRS assumes the host disk is encrypted and that the operating system's screen lock protects the machine; the app does not encrypt its own data (SRS, Assumptions).

## Decision

- The lock is an optional passcode (off by default) that guards the app and its API against casual use of an open session. It does not protect the data file.
- The server enforces it: when a passcode is set, every endpoint returns 401 without a valid session, except `/health`, `/auth/status` and `/auth/unlock` (NFR-11). Enforcement lives in one place per backend (a router dependency in FastAPI, the Spring Security filter chain in Spring).
- The passcode is hashed with Argon2id; session tokens are random and stored only as hashes. The passcode is never logged or returned.
- The UI calls it a privacy lock and never describes it as encryption or data protection.
- It doubles as a learning comparison of session auth in FastAPI and Spring Security.

## Alternatives

- **Encrypting the database (for example SQLCipher):** protects the file, but adds key management and a non-standard driver to both backends; disk encryption already covers a lost or stolen machine. Out of scope for v1.
- **A lock enforced only in the UI:** the API would still return data to anyone at the machine (NFR-11).

## Consequences

- Anyone with access to the host's files can read `subs.db`; this is by design and must stay clear in the UI and docs.
- A forgotten passcode is removed with a command run on the host (`subtrack reset-passcode` in the backend container), since there is no account to recover.
- SDD: Privacy lock. SRS: Privacy lock, Assumptions, Out of scope.
