package dev.subtrack.repository;

import dev.subtrack.config.SubtrackProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty("subtrack.test-mode")
public class TemplateRepository {

  private static final String TABLES =
      "SELECT name FROM template.sqlite_master WHERE type = 'table'"
          + " AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' AND name <> 'flyway_schema_history'"
          + " ORDER BY name";

  private final DataSource dataSource;
  private final Path templatePath;

  public TemplateRepository(DataSource dataSource, SubtrackProperties properties) {
    Path path = properties.templateDbPath();
    if (path == null || !Files.isRegularFile(path)) {
      throw new IllegalStateException(
          "test mode needs a migrated template database (SUBTRACK_TEMPLATE_DB=" + path + ")");
    }
    this.dataSource = dataSource;
    this.templatePath = path;
  }

  public void restore() {
    try (Connection connection = dataSource.getConnection()) {
      // SQLite refuses ATTACH and DETACH inside a transaction.
      try (PreparedStatement attach =
          connection.prepareStatement("ATTACH DATABASE ? AS template")) {
        attach.setString(1, templatePath.toString());
        attach.execute();
      }
      try {
        copyTemplate(connection);
      } finally {
        try (Statement detach = connection.createStatement()) {
          detach.execute("DETACH DATABASE template");
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("could not restore the template database", e);
    }
  }

  private static void copyTemplate(Connection connection) throws SQLException {
    connection.setAutoCommit(false);
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA defer_foreign_keys = ON");
      List<String> tables = tables(statement);
      for (String table : tables) {
        statement.execute("DELETE FROM main." + table);
      }
      for (String table : tables) {
        statement.execute("INSERT INTO main." + table + " SELECT * FROM template." + table);
      }
      connection.commit();
    } catch (SQLException | RuntimeException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  private static List<String> tables(Statement statement) throws SQLException {
    List<String> tables = new ArrayList<>();
    try (ResultSet rows = statement.executeQuery(TABLES)) {
      while (rows.next()) {
        tables.add(quote(rows.getString(1)));
      }
    }
    return tables;
  }

  private static String quote(String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }
}
