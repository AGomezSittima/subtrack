package dev.subtrack.support;

import java.nio.file.Path;

public final class Rows {

  public static final String TS = "2026-11-10T15:18:00.000Z";
  public static final String PASSCODE_HASH = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA";

  private Rows() {}

  public static void fillEveryUserTable(Path db) {
    TestDatabase.execute(
        db,
        "INSERT INTO catalog_service (name, domain, category, created_at, updated_at) VALUES"
            + " ('Spotify', 'spotify.com', 'music', ?, ?)",
        TS,
        TS);
    TestDatabase.execute(
        db,
        "INSERT INTO subscription (catalog_service_id, name, category, price_cents, interval_count,"
            + " interval_unit, anchor_date, status, created_at, updated_at) VALUES"
            + " ((SELECT max(id) FROM catalog_service), 'Spotify', 'music', 1199, 1, 'month', '2026-01-31', 'active', ?, ?)",
        TS,
        TS);
    TestDatabase.execute(
        db,
        "INSERT INTO price_history (subscription_id, price_cents, effective_from, created_at)"
            + " VALUES (1, 1199, '2026-01-31', ?)",
        TS);
    TestDatabase.execute(
        db,
        "INSERT INTO payment (subscription_id, due_date, expected_cents, status, source, created_at,"
            + " updated_at) VALUES (1, '2026-02-28', 1199, 'expected', 'scheduled', ?, ?)",
        TS,
        TS);
    TestDatabase.execute(
        db,
        "INSERT INTO notification (type, severity, subscription_id, payment_id, trigger_date,"
            + " dedupe_key, params, created_at, updated_at) VALUES ('renewal', 'info', 1, 1,"
            + " '2026-02-25', 'renewal:1:2026-02-25', '{}', ?, ?)",
        TS,
        TS);
    TestDatabase.execute(
        db,
        "INSERT INTO session (token_hash, created_at, last_activity_at) VALUES (?, ?, ?)",
        "a".repeat(64),
        TS,
        TS);
    TestDatabase.execute(
        db, "UPDATE setting SET value = 'dark', updated_at = ? WHERE key = 'theme'", TS);
  }

  public static void setPasscode(Path db, String lockPolicy) {
    TestDatabase.execute(
        db, "UPDATE setting SET value = ? WHERE key = 'passcode_hash'", PASSCODE_HASH);
    TestDatabase.execute(db, "UPDATE setting SET value = ? WHERE key = 'lock_policy'", lockPolicy);
  }
}
