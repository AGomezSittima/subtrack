package dev.subtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed application settings ({@code subtrack.*} in application.yml). Nothing else reads the
 * environment.
 *
 * @param version application version, filled in from the Gradle build
 * @param testMode enables the test-only endpoints ({@code SUBTRACK_TEST_MODE})
 */
@ConfigurationProperties(prefix = "subtrack")
public record SubtrackProperties(String version, boolean testMode) {}
