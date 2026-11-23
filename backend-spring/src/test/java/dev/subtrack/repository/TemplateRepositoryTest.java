package dev.subtrack.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import dev.subtrack.config.SubtrackProperties;
import dev.subtrack.persistence.DataSourceConfig;
import dev.subtrack.support.Rows;
import dev.subtrack.support.TestDatabase;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateRepositoryTest {

  private static final List<String> TABLES =
      List.of(
          "catalog_service",
          "subscription",
          "price_history",
          "payment",
          "notification",
          "setting",
          "session");

  @TempDir Path dir;

  private Path database() {
    return TestDatabase.migrated(dir.resolve("subs.db"));
  }

  private Path template() {
    return TestDatabase.migrated(dir.resolve("template.db"));
  }

  private static void restore(Path database, Path template) throws Exception {
    SubtrackProperties properties = new SubtrackProperties("0.1.0", true, database, template);
    try (HikariDataSource dataSource = new DataSourceConfig().dataSource(properties)) {
      new TemplateRepository(dataSource, properties).restore();
      try (Connection connection = dataSource.getConnection();
          Statement statement = connection.createStatement()) {
        assertThat(single(statement, "PRAGMA foreign_keys")).isEqualTo(1);
        List<Object> attached = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery("PRAGMA database_list")) {
          while (rows.next()) {
            attached.add(rows.getString("name"));
          }
        }
        assertThat(attached).containsExactly("main");
      }
    }
  }

  private static Object single(Statement statement, String sql) throws Exception {
    try (ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getObject(1);
    }
  }

  private static Map<String, List<List<@Nullable Object>>> snapshot(Path db) {
    return TABLES.stream()
        .collect(Collectors.toMap(Function.identity(), table -> TestDatabase.table(db, table)));
  }

  @Test
  void everyTableReturnsToItsStateRightAfterMigrations() throws Exception {
    Path database = database();
    Path template = template();
    Rows.fillEveryUserTable(database);

    restore(database, template);

    assertThat(snapshot(database)).isEqualTo(snapshot(template));
  }

  @Test
  void idsStartAgainFromOne() throws Exception {
    Path database = database();
    Rows.fillEveryUserTable(database);

    restore(database, template());
    Rows.fillEveryUserTable(database);

    assertThat(TestDatabase.rows(database, "SELECT id FROM subscription"))
        .containsExactly(List.of(1));
  }

  @Test
  void theTemplateCatalogueIsKept() throws Exception {
    Path database = database();
    Path template = template();
    String insert =
        "INSERT INTO catalog_service (name, domain, category, created_at, updated_at)"
            + " VALUES (?, 'netflix.com', 'video', ?, ?)";
    TestDatabase.execute(template, insert, "Netflix", Rows.TS, Rows.TS);
    TestDatabase.execute(database, insert, "Netflix (mine)", Rows.TS, Rows.TS);
    List<List<@Nullable Object>> seeded = TestDatabase.table(template, "catalog_service");

    restore(database, template);

    assertThat(TestDatabase.table(database, "catalog_service")).isEqualTo(seeded);
    assertThat(seeded).filteredOn(row -> "Netflix".equals(row.get(2))).hasSize(1);
  }

  @Test
  void tablesAddedByLaterMigrationsAreRestoredToo() throws Exception {
    Path database = database();
    Path template = template();
    String create = "CREATE TABLE later (id INTEGER PRIMARY KEY, label TEXT NOT NULL) STRICT";
    TestDatabase.execute(template, create);
    TestDatabase.execute(template, "INSERT INTO later (label) VALUES ('seeded')");
    TestDatabase.execute(database, create);
    TestDatabase.execute(database, "INSERT INTO later (label) VALUES ('user data')");

    restore(database, template);

    assertThat(TestDatabase.table(database, "later")).containsExactly(List.of(1, "seeded"));
  }

  @Test
  void aFailedRestoreChangesNothing() throws Exception {
    Path database = database();
    Path broken = dir.resolve("broken.db");
    TestDatabase.execute(broken, "CREATE TABLE subscription (id INTEGER PRIMARY KEY) STRICT");
    TestDatabase.execute(broken, "INSERT INTO subscription (id) VALUES (1)");
    Rows.fillEveryUserTable(database);
    Map<String, List<List<@Nullable Object>>> before = snapshot(database);

    assertThatThrownBy(() -> restore(database, broken)).isInstanceOf(IllegalStateException.class);

    assertThat(snapshot(database)).isEqualTo(before);
  }

  @Test
  void testModeRefusesToStartWithoutATemplate() {
    SubtrackProperties properties =
        new SubtrackProperties("0.1.0", true, dir.resolve("subs.db"), dir.resolve("missing.db"));

    assertThatThrownBy(() -> new TemplateRepository(new HikariDataSource(), properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("template");
  }
}
