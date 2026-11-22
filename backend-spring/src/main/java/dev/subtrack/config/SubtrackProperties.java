package dev.subtrack.config;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "subtrack")
public record SubtrackProperties(
    String version, boolean testMode, Path dbPath, @Nullable Path templateDbPath) {}
