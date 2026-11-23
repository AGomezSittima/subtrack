# 0007. The catalogue belongs to the user; updates only add built-ins

Date: 2026-11-23 · Status: Accepted

## Context

The catalogue prefills new subscriptions (FR-2). It ships with built-in services without prices, and the user adds their own, edits any of them, enters prices and hides built-ins they don't use (FR-47 to FR-51). Later versions of the app will add built-ins, and installed apps must receive them without losing anything the user changed (FR-52). The SDD said catalogue rows change only through migrations and that seeds insert explicit ids; neither survives user editing.

## Decision

- Built-in rows carry a stable `builtin_key` (`spotify`, `cloudflare-registrar`); user rows have none. Ids are always assigned by SQLite, never written by a migration.
- Once installed, every row belongs to the user. Nothing the app ships ever updates or deletes a catalogue row.
- Built-ins are listed in `db/catalog/catalog.json`. `just catalog-migration` generates the next migration from it, with one `INSERT … ON CONFLICT (builtin_key) DO NOTHING` per key not yet installed.
- `catalog.json` only grows. An entry that has been released is never edited or removed, because installed apps would never receive the change. `just lint` fails if a key is missing from the migrations or if a released entry differs from what its migration inserted.
- Built-ins can be hidden and shown again, but not deleted, so every installed key stays and keeps its edits while hidden. User rows can be deleted; their subscriptions stay, unlinked (`ON DELETE SET NULL`).
- Service names are not unique. The key is the identity, so a new built-in can never clash with a user's row.
- Exports reference built-ins by key, not by id, since ids differ between installs.

## Alternatives

- **Migrations that update built-ins:** would overwrite the user's edits and prices.
- **Explicit ids in the seed:** would collide with rows the user created before the update.
- **Deletable built-ins:** an import from another install would bring a deleted key back, and telling "deleted" from "never shipped" needs a tombstone; hiding gives the same result with less state.
- **A separate table of user overrides on read-only built-ins:** every read joins two tables for the same result, and both backends would merge them.
- **Shipping the catalogue as a file read at startup:** a second source of seeded data next to the migrations, and `/admin/reset` restores only what migrations create.

## Consequences

- Updates are safe by construction: an insert keyed on `builtin_key` can't touch an existing row or fail on the user's data.
- A typo fixed in a released built-in never reaches installed apps; only new keys do.
- `db/` gains a small Node package (`@subtrack/db`) with the JSON Schema, the generator and the lint check.
- Export and import carry the built-ins' edits and hidden state, and map subscriptions to built-ins by key.
- SDD: Database schema (`catalog_service`, Indexes), Migrations and ownership, API contract, Export format. Replaces "Catalogue rows change only through migrations, so the table has no timestamps. Catalogue seeds insert explicit ids".
