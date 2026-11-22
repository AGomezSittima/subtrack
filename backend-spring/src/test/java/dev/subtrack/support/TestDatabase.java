package dev.subtrack.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;

public final class TestDatabase {

  private static final Path ROOT = createRoot();

  private TestDatabase() {}

  public static Path migrated(Path file) {
    Flyway.configure()
        .dataSource(url(file), null, null)
        .locations("filesystem:" + System.getProperty("subtrack.migrations.dir"))
        .load()
        .migrate();
    return file;
  }

  public static Path shared(String name) {
    Path file = ROOT.resolve(name);
    return Files.exists(file) ? file : migrated(file);
  }

  public static void execute(Path file, String sql, @Nullable Object... arguments) {
    try (Connection connection = connect(file);
        PreparedStatement statement = prepare(connection, sql, arguments)) {
      statement.execute();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void executeIgnoringChecks(Path file, String sql) {
    try (Connection connection = connect(file);
        var statement = connection.createStatement()) {
      statement.execute("PRAGMA ignore_check_constraints = ON");
      statement.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static List<List<@Nullable Object>> rows(
      Path file, String sql, @Nullable Object... arguments) {
    try (Connection connection = connect(file);
        PreparedStatement statement = prepare(connection, sql, arguments);
        ResultSet result = statement.executeQuery()) {
      ResultSetMetaData meta = result.getMetaData();
      List<List<@Nullable Object>> rows = new ArrayList<>();
      while (result.next()) {
        List<@Nullable Object> row = new ArrayList<>();
        for (int column = 1; column <= meta.getColumnCount(); column++) {
          row.add(result.getObject(column));
        }
        rows.add(row);
      }
      return rows;
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static List<List<@Nullable Object>> table(Path file, String table) {
    return rows(file, "SELECT * FROM " + table + " ORDER BY id");
  }

  private static Connection connect(Path file) throws SQLException {
    Connection connection = DriverManager.getConnection(url(file));
    try (var pragma = connection.createStatement()) {
      pragma.execute("PRAGMA foreign_keys = ON");
      pragma.execute("PRAGMA busy_timeout = 5000");
    }
    return connection;
  }

  private static PreparedStatement prepare(
      Connection connection, String sql, @Nullable Object... arguments) throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    for (int i = 0; i < arguments.length; i++) {
      statement.setObject(i + 1, arguments[i]);
    }
    return statement;
  }

  private static String url(Path file) {
    return "jdbc:sqlite:" + file;
  }

  private static Path createRoot() {
    try {
      return Files.createTempDirectory("subtrack-test");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
