package dev.subtrack.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import dev.subtrack.support.TestDatabase;
import dev.subtrack.support.TestModeApiTest;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class PersistenceTest extends TestModeApiTest {

  @Autowired HikariDataSource dataSource;
  @Autowired EntityManager entityManager;
  @Autowired TransactionTemplate transactions;

  @Test
  void everyConnectionEnablesForeignKeysWalAndABusyTimeout() throws Exception {
    for (int i = 0; i < 2; i++) {
      try (Connection connection = dataSource.getConnection();
          Statement statement = connection.createStatement()) {
        List<Object> pragmas = new ArrayList<>();
        for (String name : List.of("foreign_keys", "journal_mode", "busy_timeout")) {
          try (ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            result.next();
            pragmas.add(result.getObject(1));
          }
        }
        assertThat(pragmas).containsExactly(1, "wal", DataSourceConfig.BUSY_TIMEOUT_MS);
      }
      dataSource.getHikariPoolMXBean().softEvictConnections();
    }
  }

  @Test
  void thePoolHoldsASingleConnection() {
    assertThat(dataSource.getMaximumPoolSize()).isEqualTo(1);
  }

  @Test
  void timestampsAreStoredAsCanonicalText() {
    transactions.executeWithoutResult(status ->
        entityManager.persist(
            new SessionEntity(
                "a".repeat(64),
                Instant.parse("2026-11-10T15:18:00Z"),
                Instant.parse("2026-11-10T15:18:00.005Z"))));

    assertThat(TestDatabase.rows(DATABASE, "SELECT created_at, last_activity_at FROM session"))
        .containsExactly(List.of("2026-11-10T15:18:00.000Z", "2026-11-10T15:18:00.005Z"));
  }

  @Test
  void catalogPlansAreStoredAsCanonicalJson() {
    transactions.executeWithoutResult(status ->
        entityManager.persist(
            new CatalogServiceEntity(
                "Café Ñu",
                null,
                "other",
                List.of(
                    new StoredPlan("Año", 1999L, 1, "year"),
                    new StoredPlan("Mes", null, 1, "month")),
                Instant.parse("2026-11-10T15:18:00Z"))));

    assertThat(
            TestDatabase.rows(
                DATABASE,
                "SELECT default_plans, hidden FROM catalog_service WHERE name = 'Café Ñu'"))
        .containsExactly(
            List.of(
                "[{\"plan\":\"Año\",\"price_cents\":1999,\"interval_count\":1,\"interval_unit\":\"year\"},"
                    + "{\"plan\":\"Mes\",\"price_cents\":null,\"interval_count\":1,\"interval_unit\":\"month\"}]",
                0));
  }

  @Test
  void nonCanonicalStoredTextFailsToRead() {
    TestDatabase.executeIgnoringChecks(
        DATABASE, "UPDATE setting SET updated_at = '2026-11-10T15:18:00Z' WHERE key = 'theme'");

    assertThatThrownBy(() ->
            transactions.executeWithoutResult(status ->
                entityManager
                    .createQuery(
                        "select s from SettingEntity s where s.key = 'theme'", SettingEntity.class)
                    .getSingleResult()))
        .rootCause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timestamp");
  }
}
