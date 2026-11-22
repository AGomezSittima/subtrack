package dev.subtrack.config;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed application settings ({@code subtrack.*} in application.yml). Nothing else reads the
 * environment.
 *
 * @param version application version, filled in from the Gradle build
 * @param testMode enables the test-only endpoints ({@code SUBTRACK_TEST_MODE})
 * @param dbPath the SQLite file ({@code DB_PATH})
 * @param templateDbPath freshly migrated database that test mode resets from ({@code
 *     SUBTRACK_TEMPLATE_DB})
 */
@ConfigurationProperties(prefix = "subtrack")
public record SubtrackProperties(
    String version, boolean testMode, Path dbPath, @Nullable Path templateDbPath) {}
