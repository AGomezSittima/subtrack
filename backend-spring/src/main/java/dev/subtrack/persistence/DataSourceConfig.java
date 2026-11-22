package dev.subtrack.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.subtrack.config.SubtrackProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

  public static final int BUSY_TIMEOUT_MS = 5000;

  @Bean
  public HikariDataSource dataSource(SubtrackProperties properties) {
    SQLiteConfig sqlite = new SQLiteConfig();
    sqlite.enforceForeignKeys(true);
    sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
    sqlite.setBusyTimeout(BUSY_TIMEOUT_MS);
    SQLiteDataSource connections = new SQLiteDataSource(sqlite);
    connections.setUrl("jdbc:sqlite:" + properties.dbPath());

    HikariConfig pool = new HikariConfig();
    pool.setPoolName("subtrack");
    pool.setDataSource(connections);
    pool.setMaximumPoolSize(1);
    pool.setMinimumIdle(1);
    return new HikariDataSource(pool);
  }
}
